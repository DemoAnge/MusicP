package com.example.music.player.web

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

class LocalBridgeServer(
    private val html: String,
    private val token: String,
    private val onClientMessage: (JSONObject) -> Unit,
) {
    private val commands = ConcurrentLinkedQueue<String>()
    private val waiters = CopyOnWriteArrayList<CountDownLatch>()
    private val running = AtomicBoolean(false)
    val connected = AtomicBoolean(false)
    private var server: ServerSocket? = null
    var port: Int = 0
        private set

    fun start(): Int {
        stop()
        val sock = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        server = sock
        port = sock.localPort
        running.set(true)
        thread(name = "music-bridge", isDaemon = true) {
            while (running.get()) {
                val client = runCatching { sock.accept() }.getOrNull() ?: break
                thread(name = "music-bridge-conn", isDaemon = true) {
                    runCatching { handle(client) }
                    runCatching { client.close() }
                }
            }
        }
        return port
    }

    fun stop() {
        running.set(false)
        connected.set(false)
        runCatching { server?.close() }
        server = null
        wakePoll()
    }

    fun send(json: JSONObject) {
        commands.add(json.toString())
        wakePoll()
    }

    private fun wakePoll() {
        waiters.forEach { it.countDown() }
        waiters.clear()
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 8_000
        val input = socket.getInputStream()
        val reader = BufferedReader(InputStreamReader(input, Charsets.ISO_8859_1))
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1]
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (length > 0) {
            val buf = CharArray(length)
            var read = 0
            while (read < length) {
                val n = reader.read(buf, read, length - read)
                if (n < 0) break
                read += n
            }
            String(buf, 0, read)
        } else {
            ""
        }
        val uri = path.substringBefore("?")
        val query = path.substringAfter("?", "")
        val params = query.split("&").mapNotNull {
            if (it.isBlank()) return@mapNotNull null
            val k = it.substringBefore("=")
            val v = it.substringAfter("=", "")
            k to java.net.URLDecoder.decode(v, "UTF-8")
        }.toMap()
        if (params["token"] != token) {
            write(socket.getOutputStream(), 403, "text/plain; charset=utf-8", "forbidden")
            return
        }
        when {
            method == "GET" && (uri == "/" || uri == "/index.html") -> {
                write(socket.getOutputStream(), 200, "text/html; charset=utf-8", html)
            }
            method == "GET" && uri == "/poll" -> {
                connected.set(true)
                if (commands.isEmpty()) {
                    val latch = CountDownLatch(1)
                    waiters.add(latch)
                    runCatching { latch.await(2, TimeUnit.SECONDS) }
                    waiters.remove(latch)
                }
                val batch = JSONArray()
                while (true) {
                    val item = commands.poll() ?: break
                    batch.put(JSONObject(item))
                }
                write(socket.getOutputStream(), 200, "application/json; charset=utf-8", batch.toString())
            }
            method == "POST" && uri == "/state" -> {
                connected.set(true)
                runCatching { onClientMessage(JSONObject(body.ifBlank { "{}" })) }
                write(socket.getOutputStream(), 200, "application/json; charset=utf-8", "{\"ok\":true}")
            }
            else -> write(socket.getOutputStream(), 404, "text/plain; charset=utf-8", "not found")
        }
    }

    private fun write(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charset.forName("UTF-8"))
        val status = when (code) {
            200 -> "OK"
            403 -> "Forbidden"
            else -> "Not Found"
        }
        val header = buildString {
            append("HTTP/1.1 $code $status\r\n")
            append("Content-Type: $type\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }
}
