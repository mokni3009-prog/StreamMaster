package com.streammaster.tv.player.remote

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD

/**
 * Phone web remote (2026-11-22): tiny embedded HTTP server (NanoHTTPD) that
 * runs inside the TV app on the local network — no cloud server, no domain,
 * no internet needed. The phone opens http://<tv-ip>:<port>/?code=<6 digits>
 * (QR code on the Télécommande screen) and gets the remote page.
 *
 * Endpoints (all calls, including the page itself, require the pairing
 * code, as a `code` query parameter or an `X-Remote-Code` header — S-02:
 * the page used to be served to anyone on the LAN with no code at all):
 * - GET  /            → the remote page (assets/remote.html)
 * - GET  /api/status  → {"playing":bool,"kind":"live|film|...","title":"..."}
 * - POST /api/key     → {"key":"up|down|left|right|ok|back|playpause|..."}
 * - POST /api/text    → {"text":"..."} (appended to the search query)
 *
 * The server runs on NanoHTTPD's own threads; [RemoteCommandHandler] posts
 * key events to the main thread and uses tryEmit for the command bus, so no
 * blocking work happens here.
 */
class RemoteHttpServer(
    port: Int,
    private val context: Context,
    private val handler: RemoteCommandHandler,
    private val codeProvider: () -> String,
    private val statusProvider: () -> String
) : NanoHTTPD(port) {

    private val pageHtml: String by lazy {
        context.assets.open("remote.html").bufferedReader().readText()
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.uri == "/" -> {
                    if (!authorized(session)) return unauthorized()
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", pageHtml)
                }
                session.uri == "/api/status" -> {
                    if (!authorized(session)) return unauthorized()
                    newFixedLengthResponse(Response.Status.OK, "application/json", statusProvider())
                }
                session.uri == "/api/key" && session.method == Method.POST -> {
                    if (!authorized(session)) return unauthorized()
                    val key = extractString(readBody(session), "key")
                        ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing key")
                    handler.onKey(key)
                    ok()
                }
                session.uri == "/api/text" && session.method == Method.POST -> {
                    if (!authorized(session)) return unauthorized()
                    val text = extractString(readBody(session), "text")
                        ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing text")
                    handler.onText(text)
                    ok()
                }
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote serve failed", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error")
        }
    }

    private fun authorized(session: IHTTPSession): Boolean {
        val expected = codeProvider()
        if (expected.isBlank()) return false
        val given = session.parameters["code"]?.firstOrNull()
            ?: session.headers["x-remote-code"]
        return given == expected
    }

    private fun unauthorized(): Response =
        newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Wrong code")

    private fun ok(): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: ""
    }

    /** Minimal JSON string extraction (no JSON library on this path). */
    private fun extractString(json: String, field: String): String? {
        val match = Regex("\"$field\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(json)
            ?: return null
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
    }

    companion object {
        private const val TAG = "RemoteHttpServer"
    }
}
