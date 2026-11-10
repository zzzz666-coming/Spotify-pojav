package dev.spotify_pojav.bridge

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import org.json.JSONObject

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var track = JSONObject().put("title", "Waiting for Spotify").put("artist", "Enable Device Broadcast Status").put("playing", false).put("positionMs", 0).put("durationMs", 0).put("lyric", "")
    private lateinit var status: TextView
    private var server: ServerSocket? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.spotify.music.metadatachanged" -> {
                    track.put("title", intent.getStringExtra("track") ?: "Unknown track")
                    track.put("artist", intent.getStringExtra("artist") ?: "Unknown artist")
                    track.put("album", intent.getStringExtra("album") ?: "")
                    track.put("durationMs", intent.getIntExtra("length", 0) * 1000)
                    track.put("spotifyId", intent.getStringExtra("id") ?: "")
                }
                "com.spotify.music.playbackstatechanged" -> {
                    track.put("playing", intent.getBooleanExtra("playing", false))
                    track.put("positionMs", intent.getIntExtra("playbackPosition", 0))
                }
            }
            runOnUiThread { status.text = "${track.optString("title")} — ${track.optString("artist")}\nBridge: 127.0.0.1:8765" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 18f; setPadding(28, 48, 28, 28); text = "Spotify Pojav Bridge\nOpen Spotify settings and enable Device Broadcast Status.\nKeep this app open while playing." }
        setContentView(status)
        val filter = IntentFilter().apply {
            addAction("com.spotify.music.metadatachanged")
            addAction("com.spotify.music.playbackstatechanged")
            addAction("com.spotify.music.queuechanged")
        }
        registerReceiver(receiver, filter)
        startLocalServer()
    }

    private fun startLocalServer() {
        executor.execute {
            try {
                server = ServerSocket(8765)
                while (!server!!.isClosed) {
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
                val body = if (request.startsWith("GET /now ")) track.toString() else "{\"error\":\"not_found\"}"
                val bytes = body.toByteArray(Charsets.UTF_8)
                val out = s.getOutputStream()
                val statusCode = if (request.startsWith("GET /now ")) "200 OK" else "404 Not Found"
                out.write("HTTP/1.1 $statusCode\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                out.write(bytes); out.flush()
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) { }
        try { server?.close() } catch (_: Exception) { }
        executor.shutdownNow()
        super.onDestroy()
    }
}
