package com.tarnished.grace.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToInt

class Link(val a: Socket, val b: Socket, private val owner: MutableSet<Link>) {
    private val done = AtomicInteger(0)

    fun finish() {
        if (done.incrementAndGet() >= 2) close()
    }

    fun close() {
        owner.remove(this)
        runCatching { a.close() }
        runCatching { b.close() }
    }
}

class Node(val cfg: VlessConfig) {
    @Volatile var ewma: Double? = null
    @Volatile var last: Double? = null
    @Volatile var fails = 0
    @Volatile var samples = 0
    @Volatile var lastProbe = 0L
    @Volatile var inflight = 0
    @Volatile var hport = 0
    @Volatile var sport = 0
    val links: MutableSet<Link> = ConcurrentHashMap.newKeySet()
}

class Engine(private val context: Context) {
    val state = MutableStateFlow(UiState())

    private val pool: ExecutorService = Executors.newCachedThreadPool()
    private val probePool: ExecutorService = Executors.newFixedThreadPool(12)
    private val lock = Any()
    private val tail = ArrayDeque<String>()

    @Volatile private var nodes: List<Node> = emptyList()
    @Volatile private var active: Node? = null
    @Volatile private var phase = Phase.OFF
    @Volatile private var message: String? = null
    @Volatile private var running = false
    @Volatile private var generation = 0

    private var candidate: Node? = null
    private var candidateSince = 0L
    private var process: Process? = null
    private var xrayConfig: File? = null
    private var httpServer: ServerSocket? = null
    private var socksServer: ServerSocket? = null
    private var monitor: Thread? = null

    fun markStarting() {
        message = null
        phase = Phase.STARTING
        publish()
    }

    fun markOn() {
        phase = Phase.ON
        publish()
    }

    fun fail(text: String) {
        message = text
        phase = Phase.OFF
        publish()
    }

    fun stop() {
        stopInternal()
        phase = Phase.OFF
        publish()
    }

    fun start(configs: List<VlessConfig>, httpPort: Int, socksPort: Int): String? {
        stopInternal()
        val usable = configs.filter { c ->
            val ip = resolve(c.host)
            c.connectIp = ip
            ip != null
        }
        if (usable.isEmpty()) return "Aucun serveur n'a pu être résolu en IPv4."

        val list = usable.map { Node(it) }
        val ports = freePorts(list.size * 2)
        list.forEachIndexed { i, n ->
            n.hport = ports[2 * i]
            n.sport = ports[2 * i + 1]
        }

        val file = File(context.filesDir, "xray.json")
        file.writeText(buildXrayConfig(list).toString())
        xrayConfig = file

        nodes = list
        val err = launchXray(list)
        if (err != null) {
            nodes = emptyList()
            return err
        }

        repeat(2) {
            val futures = list.map { n ->
                val now = System.currentTimeMillis()
                synchronized(lock) {
                    n.inflight++
                    n.lastProbe = now
                }
                probePool.submit(Runnable { record(n, probeOnce(n.hport)) })
            }
            futures.forEach { runCatching { it.get() } }
        }
        decide(System.currentTimeMillis())

        try {
            httpServer = bind(httpPort)
            socksServer = bind(socksPort)
        } catch (e: Exception) {
            stopInternal()
            return "Ports locaux ${httpPort}/${socksPort} indisponibles."
        }

        running = true
        val gen = generation
        val http = httpServer!!
        val socks = socksServer!!
        pool.execute { acceptLoop(http, false, gen) }
        pool.execute { acceptLoop(socks, true, gen) }
        monitor = Thread { monitorLoop(gen) }.also {
            it.isDaemon = true
            it.start()
        }
        publish()
        return null
    }

    private fun stopInternal() {
        running = false
        generation++
        monitor?.interrupt()
        monitor = null
        runCatching { httpServer?.close() }
        runCatching { socksServer?.close() }
        httpServer = null
        socksServer = null
        process?.let { runCatching { it.destroy() } }
        process = null
        nodes.forEach { n -> n.links.toList().forEach { it.close() } }
        nodes = emptyList()
        active = null
        candidate = null
    }

    private fun resolve(host: String): String? = try {
        InetAddress.getAllByName(host).firstOrNull { it is Inet4Address }?.hostAddress
    } catch (e: Exception) {
        null
    }

    private fun freePorts(count: Int): List<Int> {
        val sockets = ArrayList<ServerSocket>()
        val ports = ArrayList<Int>()
        try {
            repeat(count) {
                val s = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
                sockets.add(s)
                ports.add(s.localPort)
            }
        } finally {
            sockets.forEach { runCatching { it.close() } }
        }
        return ports
    }

    private fun bind(port: Int): ServerSocket {
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
        return s
    }

    private fun buildXrayConfig(list: List<Node>): JSONObject {
        val inbounds = JSONArray()
        val outbounds = JSONArray()
        val rules = JSONArray()
        list.forEachIndexed { i, n ->
            inbounds.put(
                JSONObject().put("tag", "h$i").put("listen", "127.0.0.1").put("port", n.hport).put("protocol", "http")
            )
            inbounds.put(
                JSONObject().put("tag", "s$i").put("listen", "127.0.0.1").put("port", n.sport)
                    .put("protocol", "socks").put("settings", JSONObject().put("udp", true))
            )
            outbounds.put(n.cfg.outbound("o$i"))
            rules.put(
                JSONObject().put("type", "field")
                    .put("inboundTag", JSONArray().put("h$i").put("s$i"))
                    .put("outboundTag", "o$i")
            )
        }
        outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        return JSONObject()
            .put("log", JSONObject().put("loglevel", "error"))
            .put("inbounds", inbounds)
            .put("outbounds", outbounds)
            .put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules))
    }

    private fun isAlive(p: Process): Boolean = try {
        p.exitValue()
        false
    } catch (e: IllegalThreadStateException) {
        true
    }

    private fun portOpen(port: Int): Boolean = try {
        Socket().use {
            it.connect(InetSocketAddress("127.0.0.1", port), 200)
            true
        }
    } catch (e: Exception) {
        false
    }

    private fun launchXray(list: List<Node>): String? {
        val exe = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
        if (!exe.exists()) return "libxray.so est absent de l'application (jniLibs/armeabi-v7a)."
        val cfg = xrayConfig ?: return "Configuration xray absente."
        synchronized(tail) { tail.clear() }
        val builder = ProcessBuilder(exe.absolutePath, "run", "-c", cfg.absolutePath)
        builder.directory(context.filesDir)
        builder.environment()["XRAY_LOCATION_ASSET"] = context.filesDir.absolutePath
        builder.redirectErrorStream(true)
        val p = try {
            builder.start()
        } catch (e: Exception) {
            return "Impossible de lancer xray : ${e.message}"
        }
        process = p
        pool.execute {
            runCatching {
                p.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(tail) {
                        tail.addLast(line)
                        while (tail.size > 12) tail.removeFirst()
                    }
                }
            }
        }
        val deadline = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < deadline) {
            if (!isAlive(p)) break
            if (portOpen(list.first().hport) && portOpen(list.last().sport)) return null
            Thread.sleep(150)
        }
        val lines = synchronized(tail) { tail.toList() }
        runCatching { p.destroy() }
        return "xray ne démarre pas.\n" + lines.joinToString("\n")
    }

    private fun probeOnce(port: Int): Double? {
        val t0 = System.nanoTime()
        var result: Double? = null
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), PROBE_TIMEOUT)
                s.soTimeout = PROBE_TIMEOUT
                val request = "GET http://www.gstatic.com/generate_204 HTTP/1.1\r\n" +
                    "Host: www.gstatic.com\r\nUser-Agent: Mozilla/5.0\r\nConnection: close\r\n\r\n"
                s.getOutputStream().write(request.toByteArray())
                val buf = ByteArray(256)
                val n = s.getInputStream().read(buf)
                if (n > 12) {
                    val head = String(buf, 0, n, Charsets.ISO_8859_1)
                    val code = head.substring(9, 12).toIntOrNull()
                    if (head.startsWith("HTTP/") && code != null && code < 400) {
                        result = (System.nanoTime() - t0) / 1e6
                    }
                }
            }
        } catch (e: Exception) {
            result = null
        }
        return result
    }

    private fun record(n: Node, ms: Double?) {
        synchronized(lock) {
            n.inflight = max(0, n.inflight - 1)
            if (ms == null) {
                n.fails++
            } else {
                val e = n.ewma
                n.ewma = if (e == null || n.fails >= FAIL_THRESHOLD) ms else ALPHA * ms + (1 - ALPHA) * e
                n.last = ms
                n.fails = 0
                n.samples++
            }
        }
    }

    private fun aliveNodes(): List<Node> = nodes.filter { it.ewma != null && it.fails < FAIL_THRESHOLD }

    private fun submit(n: Node, now: Long) {
        synchronized(lock) {
            n.inflight++
            n.lastProbe = now
        }
        probePool.execute { record(n, probeOnce(n.hport)) }
    }

    private fun schedule(now: Long) {
        val fast = aliveNodes().sortedBy { it.ewma ?: Double.MAX_VALUE }.take(3).toSet()
        val act = active
        for (n in nodes) {
            val interval = when {
                n === act -> ACTIVE_MS
                n.fails >= FAIL_THRESHOLD -> DEAD_MS
                n.fails > 0 -> 2000L
                n in fast -> FAST_MS
                else -> OTHER_MS
            }
            if (now - n.lastProbe >= interval && (n === act || n.inflight == 0) && n.inflight < 3) {
                submit(n, now)
            }
        }
    }

    private fun switchTo(best: Node, kill: Boolean) {
        val old = active
        active = best
        candidate = null
        if (kill && old != null) old.links.toList().forEach { it.close() }
    }

    private fun decide(now: Long) {
        synchronized(lock) {
            val alive = aliveNodes()
            val act = active
            if (act == null || act.fails >= FAIL_THRESHOLD) {
                val best = alive.minByOrNull { it.ewma ?: Double.MAX_VALUE }
                if (best != null) switchTo(best, true)
                candidate = null
                return
            }
            val actMs = act.ewma ?: return
            val best = alive.filter { it !== act && it.samples >= 3 }
                .minByOrNull { it.ewma ?: Double.MAX_VALUE } ?: return
            val bestMs = best.ewma ?: return
            if (actMs - bestMs >= MIN_GAIN_MS && bestMs <= actMs * (1 - MIN_GAIN_PCT / 100.0)) {
                if (candidate !== best) {
                    candidate = best
                    candidateSince = now
                } else if (now - candidateSince >= CONFIRM_MS) {
                    switchTo(best, false)
                }
            } else {
                candidate = null
            }
        }
    }

    private fun monitorLoop(gen: Int) {
        var lastPublish = 0L
        while (running && generation == gen) {
            val now = System.currentTimeMillis()
            val p = process
            if (p != null && !isAlive(p)) {
                val list = nodes
                if (list.isNotEmpty()) launchXray(list)
            }
            schedule(now)
            decide(now)
            if (now - lastPublish >= 500) {
                lastPublish = now
                publish()
            }
            try {
                Thread.sleep(250)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun publish() {
        val list = nodes
        val act = active
        val views = list.map { n ->
            val dead = n.fails >= FAIL_THRESHOLD
            NodeView(
                name = n.cfg.name,
                latency = if (dead) null else n.ewma?.roundToInt(),
                alive = !dead,
                active = n === act
            )
        }.sortedWith(compareBy({ !it.alive }, { it.latency ?: Int.MAX_VALUE }))
        state.value = UiState(
            phase = phase,
            nodes = views,
            activeName = act?.cfg?.name,
            activeMs = act?.ewma?.roundToInt(),
            allDead = running && list.isNotEmpty() && list.all { it.fails >= FAIL_THRESHOLD },
            message = message
        )
    }

    private fun order(): List<Node> {
        val act = active
        val result = ArrayList<Node>()
        if (act != null && act.fails < FAIL_THRESHOLD) result.add(act)
        aliveNodes().sortedBy { it.ewma ?: Double.MAX_VALUE }.forEach { if (it !== act) result.add(it) }
        return result.take(3)
    }

    private fun acceptLoop(server: ServerSocket, socks: Boolean, gen: Int) {
        while (running && generation == gen) {
            val client = try {
                server.accept()
            } catch (e: Exception) {
                break
            }
            pool.execute { handle(client, socks) }
        }
    }

    private fun handle(client: Socket, socks: Boolean) {
        var upstream: Socket? = null
        var node: Node? = null
        for (n in order()) {
            val s = Socket()
            try {
                s.connect(InetSocketAddress("127.0.0.1", if (socks) n.sport else n.hport), 2000)
                upstream = s
                node = n
                break
            } catch (e: Exception) {
                runCatching { s.close() }
            }
        }
        if (upstream == null || node == null) {
            runCatching { client.close() }
            return
        }
        runCatching { client.tcpNoDelay = true }
        runCatching { upstream.tcpNoDelay = true }
        val link = Link(client, upstream, node.links)
        node.links.add(link)
        pool.execute { pipe(upstream, client, link) }
        pipe(client, upstream, link)
    }

    private fun pipe(from: Socket, to: Socket, link: Link) {
        val buffer = ByteArray(16384)
        try {
            val input = from.getInputStream()
            val output = to.getOutputStream()
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                output.write(buffer, 0, n)
            }
            runCatching { to.shutdownOutput() }
            link.finish()
        } catch (e: Exception) {
            link.close()
        }
    }

    companion object {
        private const val PROBE_TIMEOUT = 2500
        private const val FAIL_THRESHOLD = 2
        private const val ACTIVE_MS = 1000L
        private const val FAST_MS = 2000L
        private const val OTHER_MS = 8000L
        private const val DEAD_MS = 10000L
        private const val CONFIRM_MS = 6000L
        private const val MIN_GAIN_MS = 40.0
        private const val MIN_GAIN_PCT = 20.0
        private const val ALPHA = 0.4
    }
}
