package moe.tachyon.ctf.plugin.web

import com.sun.net.httpserver.HttpServer
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin

fun HttpServer.cdn()
{
    registerCDN("axios.min.js")
    registerCDN("three.min.js")
    registerCDN("vue.global.prod.min.js")
    registerCDN("echarts.min.js")
    registerCDN("fzstd.min.js")
}

private fun HttpServer.registerCDN(path: String)
{
    createContext("/cdn/$path")
    { exchange ->
        val js = MinecraftCtfPlugin.javaClass.getResourceAsStream("/cdn/$path")?.readAllBytes() ?: return@createContext exchange.sendResponseHeaders(404, -1)
        exchange.sendResponseHeaders(200, js.size.toLong())
        exchange.responseHeaders.add("Content-Type", "application/javascript; charset=UTF-8")
        exchange.responseHeaders.add("Cache-Control", "public, max-age=86400")
        exchange.responseBody.use { it.write(js) }
    }
}