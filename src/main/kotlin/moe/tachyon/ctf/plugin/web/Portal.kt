package moe.tachyon.ctf.plugin.web

import com.sun.net.httpserver.HttpServer
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin

fun HttpServer.portal()
{
    // Serve portal.html at /
    createContext("/")
    { exchange ->
        val html = MinecraftCtfPlugin.javaClass.getResourceAsStream("/web/portal.html")?.readAllBytes()
            ?: return@createContext exchange.sendResponseHeaders(404, -1)
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        val os = exchange.responseBody
        os.write(html)
        os.close()
    }
}
