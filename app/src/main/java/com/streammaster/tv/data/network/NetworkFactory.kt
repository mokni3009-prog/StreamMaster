package com.streammaster.tv.data.network

import android.content.Context
import android.util.Log
import com.streammaster.tv.data.model.AppSettings
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.chromium.net.CronetEngine
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

private const val TAG = "StreamMaster"

/**
 * Minimal DNS-over-UDP resolver that asks one specific DNS server (A records only).
 * Used when the user picks a custom DNS server in Settings. Falls back to the
 * system resolver on any failure so a bad DNS choice never kills playback.
 */
class SingleServerDns(private val serverIp: String) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            queryA(hostname, serverIp).ifEmpty { Dns.SYSTEM.lookup(hostname) }
        } catch (e: Exception) {
            Log.w(TAG, "DNS via $serverIp failed for $hostname, using system DNS", e)
            Dns.SYSTEM.lookup(hostname)
        }
    }

    private fun queryA(hostname: String, serverIp: String): List<InetAddress> {
        val id = (Math.random() * 0xFFFF).toInt()
        val query = buildQuery(id, hostname)
        val socket = DatagramSocket()
        socket.soTimeout = 3000
        try {
            val server = InetAddress.getByName(serverIp)
            socket.send(DatagramPacket(query, query.size, server, 53))
            val buf = ByteArray(2048)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            // SEC-08: only accept an answer that actually came from the
            // server we asked (the 16-bit ID check alone is spoofable).
            if (packet.address != server) return emptyList()
            return parseARecords(packet.data, packet.length, id)
        } finally {
            socket.close()
        }
    }

    private fun buildQuery(id: Int, hostname: String): ByteArray {
        val out = mutableListOf<Byte>()
        fun u16(v: Int) { out += (v shr 8).toByte(); out += v.toByte() }
        u16(id); u16(0x0100); u16(1); u16(0); u16(0); u16(0)
        for (label in hostname.split(".")) {
            out += label.length.toByte()
            out += label.toByteArray(Charsets.US_ASCII).toList()
        }
        out += 0.toByte()
        u16(1) // QTYPE A
        u16(1) // QCLASS IN
        return out.toByteArray()
    }

    private fun parseARecords(data: ByteArray, len: Int, expectedId: Int): List<InetAddress> {
        if (len < 12) return emptyList()
        fun u16At(i: Int): Int = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
        if (u16At(0) != expectedId) return emptyList()
        val qdCount = u16At(4)
        val anCount = u16At(6)
        var pos = 12
        repeat(qdCount) { // skip question section
            while (pos < len && data[pos].toInt() != 0) {
                val l = data[pos].toInt() and 0xFF
                pos += if ((l and 0xC0) == 0xC0) 2 else l + 1
                if ((l and 0xC0) == 0xC0) return@repeat
            }
            pos += 1 + 4
        }
        val out = mutableListOf<InetAddress>()
        repeat(anCount) {
            if (pos + 10 > len) return@repeat
            // NAME (may be a compression pointer)
            pos += if ((data[pos].toInt() and 0xC0) == 0xC0) 2 else {
                var p = pos
                while (p < len && data[p].toInt() != 0) p += (data[p].toInt() and 0xFF) + 1
                (p - pos) + 1
            }
            if (pos + 10 > len) return@repeat
            val type = u16At(pos)
            val rdLen = u16At(pos + 8)
            pos += 10
            if (type == 1 && rdLen == 4 && pos + 4 <= len) {
                val addr = data.copyOfRange(pos, pos + 4)
                try { out += InetAddress.getByAddress(addr) } catch (_: UnknownHostException) { }
            }
            pos += rdLen
        }
        return out
    }
}

object NetworkFactory {

    /** DNS server IPs selectable in Settings ("auto" = system DNS). */
    val DNS_SERVERS: Map<String, String> = mapOf(
        "google" to "8.8.8.8",
        "cloudflare" to "1.1.1.1",
        "quad9" to "9.9.9.9"
    )

    fun dnsFor(settings: AppSettings): Dns {
        if (settings.dns == "auto") return Dns.SYSTEM
        val ip = when (settings.dns) {
            "custom" -> settings.dnsCustom.trim().ifEmpty { null }
            else -> DNS_SERVERS[settings.dns]
        } ?: return Dns.SYSTEM
        return SingleServerDns(ip)
    }

    /** User-Agent values selectable in Settings. */
    fun userAgentFor(settings: AppSettings): String = when (settings.ua) {
        "chrome-tv" -> "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        "vlc" -> "VLC/3.0.20 LibVLC/3.0.20"
        "custom" -> settings.uaCustom.trim().ifEmpty { "StreamMaster/1.2.0" }
        else -> "StreamMaster/1.2.0"
    }

    fun buildOkHttpClient(settings: AppSettings): OkHttpClient {
        // SEC-01: NEVER log request URLs — Xtream calls carry username +
        // password as query params, and BASIC logs the full URL to logcat.
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE }
        return OkHttpClient.Builder()
            .dns(dnsFor(settings))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", userAgentFor(settings))
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging)
            .build()
    }

    /**
     * Cronet engine for the player (Media3 CronetDataSource / HttpEngine fallback).
     * Returns null if Cronet is unavailable on this device/build.
     */
    fun createCronetEngine(context: Context): CronetEngine? {
        return try {
            CronetEngine.Builder(context.applicationContext)
                .enableHttp2(true)
                .enableQuic(true)
                .enableBrotli(true)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "CronetEngine unavailable", e)
            null
        }
    }
}
