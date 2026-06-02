package moe.tachyon.ctf.plugin.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin
import moe.tachyon.ctf.plugin.group.*
import moe.tachyon.ctf.plugin.whitelist.whiteListMode

private fun HttpServer.authenticatedContext(path: String, handler: (HttpExchange) -> Unit)
{
    createContext(path) { exchange ->
        val password = exchange.requestHeaders.getFirst("X-Admin-Password")
        if (password == null || password != MinecraftCtfPlugin.config.adminPassword)
        {
            exchange.sendResponseHeaders(401, -1)
            exchange.responseBody.close()
            return@createContext
        }
        handler(exchange)
    }
}

fun HttpServer.admin()
{
    // Serve admin.html
    createContext("/admin")
    { exchange ->
        val html = MinecraftCtfPlugin.javaClass.getResourceAsStream("/web/admin.html")?.readAllBytes()
            ?: return@createContext exchange.sendResponseHeaders(404, -1)
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        val os = exchange.responseBody
        os.write(html)
        os.close()
    }

    // POST /admin/auth - validate password
    createContext("/admin/auth")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        @Serializable
        data class AuthData(val password: String)

        try
        {
            val data = Json.decodeFromString<AuthData>(body)
            if (data.password == MinecraftCtfPlugin.config.adminPassword)
            {
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.close()
            }
            else
            {
                exchange.sendResponseHeaders(401, -1)
                exchange.responseBody.close()
            }
        }
        catch (_: Throwable)
        {
            exchange.sendResponseHeaders(400, -1)
            exchange.responseBody.close()
        }
    }

    // GET /admin/players - get all players
    authenticatedContext("/admin/players")
    { exchange ->
        val players = getAllPlayers()
        val r = Json.encodeToString(players).toByteArray()
        exchange.sendResponseHeaders(200, r.size.toLong())
        exchange.responseHeaders.add("Content-Type", "application/json")
        val os = exchange.responseBody
        os.write(r)
        os.close()
    }

    // POST /admin/players/create - add a new player
    authenticatedContext("/admin/players/create")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        @Serializable
        data class CreatePlayerData(val id: String, val name: String, val password: String = "")

        try
        {
            val data = Json.decodeFromString<CreatePlayerData>(body)
            val player = addPlayer(data.id.trim(), data.name.trim(), data.password)
            val r = Json.encodeToString(player).toByteArray()
            exchange.sendResponseHeaders(200, r.size.toLong())
            exchange.responseHeaders.add("Content-Type", "application/json")
            val os = exchange.responseBody
            os.write(r)
            os.close()
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, e.message?.toByteArray()?.size?.toLong() ?: 0)
            val os = exchange.responseBody
            os.write(e.message?.toByteArray() ?: ByteArray(0))
            os.close()
        }
    }

    // POST /admin/players/delete - delete a player
    authenticatedContext("/admin/players/delete")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        @Serializable
        data class DeletePlayerData(val id: String)

        try
        {
            val data = Json.decodeFromString<DeletePlayerData>(body)
            deletePlayer(data.id)
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.close()
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, e.message?.toByteArray()?.size?.toLong() ?: 0)
            val os = exchange.responseBody
            os.write(e.message?.toByteArray() ?: ByteArray(0))
            os.close()
        }
    }

    // POST /admin/players/rename - rename a player
    authenticatedContext("/admin/players/rename")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        @Serializable
        data class RenamePlayerData(val id: String, val name: String)

        try
        {
            val data = Json.decodeFromString<RenamePlayerData>(body)
            renamePlayer(data.id, data.name.trim())
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.close()
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, e.message?.toByteArray()?.size?.toLong() ?: 0)
            val os = exchange.responseBody
            os.write(e.message?.toByteArray() ?: ByteArray(0))
            os.close()
        }
    }

    // POST /admin/players/password - update player password
    authenticatedContext("/admin/players/password")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        @Serializable
        data class PasswordData(val id: String, val password: String)

        try
        {
            val data = Json.decodeFromString<PasswordData>(body)
            updatePlayerPassword(data.id, data.password)
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.close()
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, e.message?.toByteArray()?.size?.toLong() ?: 0)
            val os = exchange.responseBody
            os.write(e.message?.toByteArray() ?: ByteArray(0))
            os.close()
        }
    }

    // GET /admin/groups - get all groups
    authenticatedContext("/admin/groups")
    { exchange ->
        val groups = getGroups()
        val r = Json.encodeToString(groups).toByteArray()
        exchange.sendResponseHeaders(200, r.size.toLong())
        exchange.responseHeaders.add("Content-Type", "application/json")
        val os = exchange.responseBody
        os.write(r)
        os.close()
    }

    // POST /admin/groups - create group
    authenticatedContext("/admin/groups/create")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        @Serializable
        data class CreateData(val name: String, val players: List<String>)

        try
        {
            val data = Json.decodeFromString<CreateData>(body)
            val id = createGroup(data.name, data.players)
            val r = id.toString()
            exchange.sendResponseHeaders(200, r.toByteArray().size.toLong())
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=UTF-8")
            val os = exchange.responseBody
            os.write(r.toByteArray())
            os.close()
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, e.message?.toByteArray()?.size?.toLong() ?: 0)
            val os = exchange.responseBody
            os.write(e.message?.toByteArray() ?: ByteArray(0))
            os.close()
        }
    }

    // POST /admin/groups/delete - delete group
    authenticatedContext("/admin/groups/delete")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        @Serializable
        data class DeleteData(val id: Int)

        try
        {
            val data = Json.decodeFromString<DeleteData>(body)
            deleteGroup(data.id)
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.close()
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, e.message?.toByteArray()?.size?.toLong() ?: 0)
            val os = exchange.responseBody
            os.write(e.message?.toByteArray() ?: ByteArray(0))
            os.close()
        }
    }

    // POST /admin/groups/rename - rename group
    authenticatedContext("/admin/groups/rename")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        @Serializable
        data class RenameData(val id: Int, val name: String)

        try
        {
            val data = Json.decodeFromString<RenameData>(body)
            renameGroup(data.id, data.name)
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.close()
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, e.message?.toByteArray()?.size?.toLong() ?: 0)
            val os = exchange.responseBody
            os.write(e.message?.toByteArray() ?: ByteArray(0))
            os.close()
        }
    }

    // POST /admin/groups/member/add - add member to group
    authenticatedContext("/admin/groups/member/add")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        @Serializable
        data class AddMemberData(val groupId: Int, val playerName: String)

        try
        {
            val data = Json.decodeFromString<AddMemberData>(body)
            addGroupMember(data.groupId, data.playerName)
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.close()
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, e.message?.toByteArray()?.size?.toLong() ?: 0)
            val os = exchange.responseBody
            os.write(e.message?.toByteArray() ?: ByteArray(0))
            os.close()
        }
    }

    // POST /admin/groups/member/remove - remove member from group
    authenticatedContext("/admin/groups/member/remove")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        @Serializable
        data class RemoveMemberData(val groupId: Int, val playerId: String)

        try
        {
            val data = Json.decodeFromString<RemoveMemberData>(body)
            removeGroupMember(data.groupId, data.playerId)
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.close()
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, e.message?.toByteArray()?.size?.toLong() ?: 0)
            val os = exchange.responseBody
            os.write(e.message?.toByteArray() ?: ByteArray(0))
            os.close()
        }
    }

    // GET /admin/whitelist - get whitelist status
    authenticatedContext("/admin/whitelist")
    { exchange ->
        val status = whiteListMode
        val r = Json.encodeToString(mapOf("enabled" to status)).toByteArray()
        exchange.sendResponseHeaders(200, r.size.toLong())
        exchange.responseHeaders.add("Content-Type", "application/json")
        val os = exchange.responseBody
        os.write(r)
        os.close()
    }

    // POST /admin/whitelist - set whitelist status
    authenticatedContext("/admin/whitelist/set")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        @Serializable
        data class WhitelistData(val enabled: Boolean)

        try
        {
            val data = Json.decodeFromString<WhitelistData>(body)
            whiteListMode = data.enabled
            val r = Json.encodeToString(mapOf("enabled" to data.enabled)).toByteArray()
            exchange.sendResponseHeaders(200, r.size.toLong())
            exchange.responseHeaders.add("Content-Type", "application/json")
            val os = exchange.responseBody
            os.write(r)
            os.close()
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, e.message?.toByteArray()?.size?.toLong() ?: 0)
            val os = exchange.responseBody
            os.write(e.message?.toByteArray() ?: ByteArray(0))
            os.close()
        }
    }
}
