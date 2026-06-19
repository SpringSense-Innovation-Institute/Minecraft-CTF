package moe.tachyon.ctf.plugin.web

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin
import moe.tachyon.ctf.plugin.getStatisticData
import moe.tachyon.ctf.plugin.getStatisticFiles
import moe.tachyon.ctf.plugin.isSafeStatisticName
import moe.tachyon.ctf.plugin.practiceMode

fun HttpServer.statistics()
{
    createContext("/statistics")
    { exchange ->
        val html = MinecraftCtfPlugin.javaClass.getResourceAsStream("/web/statistics.html")?.readAllBytes() ?: return@createContext exchange.sendResponseHeaders(404, -1)
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        val os = exchange.responseBody
        os.write(html)
        os.close()
    }

    createContext("/statistics/data")
    { exchange ->
        val args = exchange.args()
        val name = args["name"]
        if (name != null && !isSafeStatisticName(name))
        {
            exchange.sendResponseHeaders(400, -1)
            exchange.responseBody.close()
            return@createContext
        }
        val statistic = getStatisticData(name ?: practiceMode?.name)
        val r = Json.encodeToString(statistic).toByteArray()
        exchange.sendResponseHeaders(200, r.size.toLong())
        exchange.responseHeaders.add("Content-Type", "application/json")
        val os = exchange.responseBody
        os.write(r)
        os.close()
    }

    createContext("/statistics/list")
    { exchange ->
        val list = getStatisticFiles()
        val r = Json.encodeToString(list).toByteArray()
        exchange.sendResponseHeaders(200, r.size.toLong())
        exchange.responseHeaders.add("Content-Type", "application/json")
        val os = exchange.responseBody
        os.write(r)
        os.close()
    }
}