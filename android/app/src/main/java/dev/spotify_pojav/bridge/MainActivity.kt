package dev.spotify_pojav.bridge

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import org.json.JSONObject

class MainActivity : Activity() {
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var title = "Waiting for Spotify"
    @Volatile private var artist = "Enable Device Broadcast Status"
    @Volatile private var playing = false
    @Volatile private var durationMs = 0L
    @Volatile private var basePositionMs = 0L
    @Volatile private var positionUpdatedAt = 0L
    @Volatile private var lyrics: List<Pair<Long, String>> = emptyList()
    @Volatile private var lyricFile = "No .lrc file selected"
    private lateinit var status: TextView
    private var server: ServerSocket? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.spotify.music.metadatachanged" -> {
                    title = intent.getStringExtra("track") ?: "Unknown track"
                    artist = intent.getStringExtra("artist") ?: "Unknown artist"
                    durationMs = intent.getIntExtra("length", 0).toLong().coerceAtLeast(0) * 1000L
                }
                "com.spotify.music.playbackstatechanged" -> {
                    playing = intent.getBooleanExtra("playing", false)
                    basePositionMs = intent.getIntExtra("playbackPosition", 0).toLong().coerceAtLeast(0)
                    positionUpdatedAt = SystemClock.elapsedRealtime()
                }
            }
            refreshStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 40, 28, 28)
        }
        val heading = TextView(this).apply {
            text = "♫  SPOTIFY POJAV"
            textSize = 24f
            setTextColor(0xFF8D54D8.toInt())
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 24)
            gravity = Gravity.CENTER
        }
        val choose = Button(this).apply {
            text = "Cargar letras (.lrc)"
            setOnClickListener {
                val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(picker, PICK_LRC)
            }
        }
        val hint = TextView(this).apply {
            text = "Activa Device Broadcast Status en Spotify. Deja esta app abierta mientras juegas. Carga el archivo .lrc correspondiente a la canción."
            textSize = 13f
            setPadding(0, 20, 0, 0)
        }
        root.addView(heading)
        root.addView(status)
        root.addView(choose)
        root.addView(hint)
        setContentView(root)

        val filter = IntentFilter().apply {
            addAction("com.spotify.music.metadatachanged")
            addAction("com.spotify.music.playbackstatechanged")
            addAction("com.spotify.music.queuechanged")
        }
        registerReceiver(receiver, filter)
        refreshStatus()
        startLocalServer()
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_LRC || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        executor.execute {
            try {
                val source = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val parsed = parseLrc(source)
                runOnUiThread {
                    lyrics = parsed
                    lyricFile = if (parsed.isEmpty()) "No timed lines found" else "Lyrics loaded: ${parsed.size} lines"
                    refreshStatus()
                }
            } catch (_: Exception) {
                runOnUiThread { lyricFile = "Could not read this LRC file"; refreshStatus() }
            }
        }
    }

    private fun parseLrc(source: String): List<Pair<Long, String>> {
        val output = mutableListOf<Pair<Long, String>>()
        val pattern = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
        source.lineSequence().forEach { line ->
            val tags = pattern.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val words = line.substring(tags.last().range.last + 1).trim()
            if (words.isEmpty()) return@forEach
            tags.forEach { tag ->
                val fractionText = tag.groupValues[3]
                val fraction = when (fractionText.length) {
                    1 -> fractionText.toLong() * 100
                    2 -> fractionText.toLong() * 10
                    3 -> fractionText.toLong()
                    else -> 0L
                }
                val time = tag.groupValues[1].toLong() * 60_000L +
                    tag.groupValues[2].toLong() * 1000L + fraction
                output.add(time to words)
            }
        }
        return output.sortedBy { it.first }
    }

    private fun currentPosition(): Long {
        val elapsed = if (playing && positionUpdatedAt > 0) SystemClock.elapsedRealtime() - positionUpdatedAt else 0L
        val value = (basePositionMs + elapsed).coerceAtLeast(0)
        return if (durationMs > 0) value.coerceAtMost(durationMs) else value
    }

    private fun nowJson(): JSONObject {
        val position = currentPosition()
        val index = lyrics.indexOfLast { it.first <= position }
        val current = if (index >= 0) lyrics[index].second else ""
        val next = if (index >= 0 && index + 1 < lyrics.size) lyrics[index + 1].second else ""
        return JSONObject()
            .put("title", title).put("artist", artist).put("playing", playing)
            .put("positionMs", position).put("durationMs", durationMs)
            .put("lyric", current).put("nextLyric", next)
            .put("lyricsLoaded", lyrics.isNotEmpty())
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        runOnUiThread {
            status.text = "${title}\n${artist}\n${if (playing) "▶ Playing" else "Ⅱ Paused"}  •  ${lyricFile}\nBridge: 127.0.0.1:8765"
        }
    }

    private fun startLocalServer() {
        executor.execute {
            try {
                server = ServerSocket(8765)
                while (server?.isClosed == false) {
                    val socket = server!!.accept()
                    executor.execute { respond(socket) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun respond(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 1500
                val input = s.getInputStream().bufferedReader()
                val request = input.readLine() ?: return
                while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break }
                val ok = request.startsWith("GET /now ")
                val body = if (ok) nowJson().toString() else "{\"error\":\"not_found\"}"
                val bytes = body.toByteArray(Charsets.UTF_8)
                val out = s.getOutputStream()
                out.write("HTTP/1.1 ${if (ok) "200 OK" else "404 Not Found"}\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                out.write(bytes)
                out.flush()
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) { }
        try { server?.close() } catch (_: Exception) { }
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object { private const val PICK_LRC = 71 }
}
