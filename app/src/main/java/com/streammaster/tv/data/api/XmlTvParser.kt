package com.streammaster.tv.data.api

import com.streammaster.tv.data.model.EpgProgram
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory

/** One XMLTV channel id -> display name, from the <channel> elements. */
data class XmlTvChannel(val id: String, val displayName: String)

/** Result of parsing an XMLTV document. */
data class XmlTvResult(
    val channels: List<XmlTvChannel>,
    val programs: List<EpgProgram>
)

/**
 * Streaming SAX parser for XMLTV (used for M3U EPG and any external XMLTV URL).
 * Handles <channel id="..." />, <programme start="20261119180000 +0100"
 * stop="..." channel="..."><title>..</title><desc>..</desc></programme>.
 * Dates are parsed with their timezone offset; a missing offset is treated as UTC.
 */
object XmlTvParser {

    fun parse(input: InputStream): XmlTvResult {
        val handler = Handler()
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        // SEC-06 (XXE): an XMLTV file comes from an untrusted URL — never
        // allow DOCTYPE / external entities (file read / SSRF). Features
        // are set best-effort: a parser that rejects one still parses.
        runCatching {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        factory.isXIncludeAware = false
        val parser = factory.newSAXParser()
        parser.parse(input, handler)
        return XmlTvResult(handler.channels, handler.programs)
    }

    fun parseDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
        // OP-17: SimpleDateFormat is not thread-safe — never share the
        // formatters (an EPG worker + a manual refresh used to corrupt
        // each other's parses). Fresh instances per call are cheap here.
        val formats = listOf(
            SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }
        for (fmt in formats) {
            runCatching { return fmt.parse(cleaned)?.time }.getOrNull()?.let { return it }
            // Try without the offset part if the first format failed.
            runCatching { return fmt.parse(cleaned.substringBefore(' '))?.time }
                .getOrNull()?.let { return it }
        }
        return null
    }

    private class Handler : DefaultHandler() {
        val channels = mutableListOf<XmlTvChannel>()
        val programs = mutableListOf<EpgProgram>()

        private var currentChannelId: String? = null
        private var currentChannelName = StringBuilder()
        private var inChannelName = false

        private var progChannel: String? = null
        private var progStart: Long = 0
        private var progStop: Long = 0
        private var progTitle = StringBuilder()
        private var progDesc = StringBuilder()
        private var inTitle = false
        private var inDesc = false

        override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
            when (qName) {
                "channel" -> {
                    currentChannelId = attrs?.getValue("id")
                    currentChannelName = StringBuilder()
                }
                "display-name" -> inChannelName = currentChannelId != null
                "programme" -> {
                    progChannel = attrs?.getValue("channel")
                    progStart = parseDate(attrs?.getValue("start")) ?: 0L
                    progStop = parseDate(attrs?.getValue("stop")) ?: 0L
                    progTitle = StringBuilder()
                    progDesc = StringBuilder()
                }
                "title" -> inTitle = progChannel != null
                "desc" -> inDesc = progChannel != null
            }
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            if (ch == null) return
            val text = String(ch, start, length)
            if (inChannelName) currentChannelName.append(text)
            if (inTitle) progTitle.append(text)
            if (inDesc) progDesc.append(text)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (qName) {
                "channel" -> {
                    val id = currentChannelId
                    if (id != null) channels += XmlTvChannel(id, currentChannelName.toString().trim())
                    currentChannelId = null
                    inChannelName = false
                }
                "display-name" -> inChannelName = false
                "title" -> inTitle = false
                "desc" -> inDesc = false
                "programme" -> {
                    val ch = progChannel
                    if (ch != null && progStart > 0) {
                        programs += EpgProgram(
                            channelId = ch,
                            title = progTitle.toString().trim(),
                            startMillis = progStart,
                            endMillis = if (progStop > progStart) progStop else progStart + 3600_000L,
                            description = progDesc.toString().trim()
                        )
                    }
                    progChannel = null
                }
            }
        }
    }
}
