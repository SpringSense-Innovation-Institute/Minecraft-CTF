package moe.tachyon.ctf.plugin.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val webThreadCounter = AtomicInteger(1)

private var webExecutor: ThreadPoolExecutor? = null

private fun createWebExecutor() = ThreadPoolExecutor(
    8,
    128,
    60L,
    TimeUnit.SECONDS,
    SynchronousQueue(),
    { task ->
        Thread(task, "Minecraft-CTF-Web-${webThreadCounter.getAndIncrement()}").apply { isDaemon = true }
    },
    ThreadPoolExecutor.CallerRunsPolicy()
)

fun HttpExchange.args() =
    requestURI.query
        ?.split("&")
        ?.map { it.split("=") }
        ?.filter { it.size == 2 }
        ?.associate { it[0] to it[1] } ?: emptyMap()

fun HttpExchange.sseHeaders()
{
    responseHeaders.add("Content-Type", "text/event-stream; charset=UTF-8")
    responseHeaders.add("Cache-Control", "no-cache")
    responseHeaders.add("Connection", "keep-alive")
    responseHeaders.add("Access-Control-Allow-Origin", "*")
    sendResponseHeaders(200, 0)
}

fun HttpServer.setupServer()
{
    cdn()

    portal()
    login()
    register()
    viewer2d()
    viewer3d()
    statistics()
    admin()

    val executor = createWebExecutor()
    webExecutor = executor
    this.executor = executor
    start()
}

fun shutdownWebExecutor()
{
    webExecutor?.shutdownNow()
    webExecutor = null
}
