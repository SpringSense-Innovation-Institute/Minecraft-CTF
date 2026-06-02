package moe.tachyon.ctf.plugin.group

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.chat.colored
import taboolib.platform.BukkitPlugin

private const val GROUP_FILE_NAME = "groups.json"
private const val PLAYER_FILE_NAME = "players.json"

@Serializable
data class GroupInfo(
    val id: Int,
    val name: String,
    val players: List<PlayerInfo>,
)
{
    @Serializable
    data class PlayerInfo(
        val id: String,
        val name: String,
        val password: String = "",
    )
}

private val lock = Any()

fun createGroup(
    name: String,
    players: List<String>,
): Int = synchronized(lock)
{
    if (' ' in name)
        error("分组名称不能包含空格")

    val groups = getGroups().toMutableList()
    if (groups.any { it.name == name })
        error("分组名称已存在")

    val allPlayers = getAllPlayers()

    players.filter { it !in allPlayers.map(GroupInfo.PlayerInfo::name) }.forEach()
    {
        error("玩家 $it 不存在")
    }

    val existedPlayers = groups.flatMap { it.players }.map(GroupInfo.PlayerInfo::name).toSet()

    players.filter { it in existedPlayers }.forEach()
    {
        error("玩家 $it 已经在其他分组中")
    }

    val group = GroupInfo(
        id = (groups.maxOfOrNull { it.id } ?: 0) + 1,
        name = name,
        players = players.map { allPlayers.first { p -> p.name == it } },
    )

    groups.add(group)

    saveGroups(groups)
    return group.id
}

private val groupFile get() = BukkitPlugin.getInstance().dataFolder.resolve(GROUP_FILE_NAME)
private val playerFile get() = BukkitPlugin.getInstance().dataFolder.resolve(PLAYER_FILE_NAME)

fun getGroups(): List<GroupInfo> = synchronized(lock)
{
    if (!groupFile.exists())
    {
        saveGroups(emptyList())
        return emptyList()
    }
    return runCatching { Json.decodeFromString<List<GroupInfo>>(groupFile.readText()) }.getOrNull() ?: emptyList()
}

fun saveGroups(groups: List<GroupInfo>) = synchronized(lock)
{
    if (!groupFile.parentFile.exists()) groupFile.parentFile.mkdirs()
    groupFile.writeText(Json.encodeToString(groups))
}

fun getAllPlayers(): List<GroupInfo.PlayerInfo>
{
    if (!playerFile.exists()) return emptyList()
    return runCatching { Json.decodeFromString<List<GroupInfo.PlayerInfo>>(playerFile.readText()) }.getOrNull() ?: emptyList()
}

private fun saveAllPlayers(players: List<GroupInfo.PlayerInfo>)
{
    if (!playerFile.parentFile.exists()) playerFile.parentFile.mkdirs()
    playerFile.writeText(Json.encodeToString(players))
}

fun addPlayer(id: String, name: String, password: String = ""): GroupInfo.PlayerInfo = synchronized(lock)
{
    val players = getAllPlayers().toMutableList()
    if (players.any { it.id == id }) error("玩家 ID $id 已存在")
    if (players.any { it.name == name }) error("玩家名称 $name 已存在")
    val player = GroupInfo.PlayerInfo(id = id, name = name, password = password)
    players.add(player)
    saveAllPlayers(players)
    return player
}

fun deletePlayer(playerId: String): Unit = synchronized(lock)
{
    val players = getAllPlayers().toMutableList()
    val idx = players.indexOfFirst { it.id == playerId }
    if (idx == -1) error("玩家不存在")
    val player = players[idx]
    players.removeAt(idx)
    saveAllPlayers(players)
    val groups = getGroups().toMutableList()
    var changed = false
    groups.forEachIndexed { gi, g ->
        val newPlayers = g.players.filter { it.id != playerId }
        if (newPlayers.size != g.players.size)
        {
            groups[gi] = g.copy(players = newPlayers)
            changed = true
        }
    }
    if (changed) saveGroups(groups)
    runCatching {
        val scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard ?: return@runCatching
        scoreboard.teams.forEach { it.removeEntry(player.name) }
    }
}

fun renamePlayer(playerId: String, newName: String): Unit = synchronized(lock)
{
    val players = getAllPlayers().toMutableList()
    val idx = players.indexOfFirst { it.id == playerId }
    if (idx == -1) error("玩家不存在")
    if (players.any { it.name == newName && it.id != playerId }) error("玩家名称已存在")
    players[idx] = players[idx].copy(name = newName)
    saveAllPlayers(players)
    // Also update in groups
    val groups = getGroups().toMutableList()
    var changed = false
    groups.forEachIndexed { gi, g ->
        val newPlayers = g.players.map { if (it.id == playerId) it.copy(name = newName) else it }
        if (newPlayers != g.players)
        {
            groups[gi] = g.copy(players = newPlayers)
            changed = true
        }
    }
    if (changed) saveGroups(groups)
}

fun getPlayerById(playerId: String): GroupInfo.PlayerInfo?
{
    return getAllPlayers().firstOrNull { it.id == playerId }
}

fun updatePlayerPassword(playerId: String, newPassword: String): Unit = synchronized(lock)
{
    val players = getAllPlayers().toMutableList()
    val idx = players.indexOfFirst { it.id == playerId }
    if (idx == -1) error("玩家不存在")
    players[idx] = players[idx].copy(password = newPassword)
    saveAllPlayers(players)
}

fun deleteGroup(groupId: Int): Unit = synchronized(lock)
{
    val groups = getGroups().toMutableList()
    val idx = groups.indexOfFirst { it.id == groupId }
    if (idx == -1) error("分组不存在")
    val group = groups[idx]
    groups.removeAt(idx)
    saveGroups(groups)
    runCatching {
        val scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard ?: return@runCatching
        scoreboard.getTeam(group.name)?.unregister()
    }
}

fun renameGroup(groupId: Int, newName: String): Unit = synchronized(lock)
{
    if (' ' in newName) error("分组名称不能包含空格")
    val groups = getGroups().toMutableList()
    val group = groups.firstOrNull { it.id == groupId } ?: error("分组不存在")
    if (groups.any { it.name == newName && it.id != groupId }) error("分组名称已存在")
    val oldName = group.name
    groups[groups.indexOf(group)] = group.copy(name = newName)
    saveGroups(groups)
    runCatching {
        val scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard ?: return@runCatching
        val oldTeam = scoreboard.getTeam(oldName)
        val entries = oldTeam?.entries?.toList() ?: emptyList()
        oldTeam?.unregister()
        val newTeam = scoreboard.registerNewTeam(newName)
        entries.forEach { newTeam.addEntry(it) }
        newTeam.prefix = "&6[$newName] ".colored()
    }
}

fun addGroupMember(groupId: Int, playerName: String): Unit = synchronized(lock)
{
    val groups = getGroups().toMutableList()
    val groupIdx = groups.indexOfFirst { it.id == groupId }
    if (groupIdx == -1) error("分组不存在")
    val group = groups[groupIdx]
    if (group.players.any { it.name == playerName }) error("玩家已在该分组中")
    val allPlayers = getAllPlayers()
    val existedPlayers = groups.flatMap { it.players }.map(GroupInfo.PlayerInfo::name).toSet()
    if (playerName in existedPlayers) error("玩家已经在其他分组中")
    val player = allPlayers.firstOrNull { it.name == playerName } ?: error("玩家 $playerName 不存在")
    groups[groupIdx] = group.copy(players = group.players + player)
    saveGroups(groups)
}

fun removeGroupMember(groupId: Int, playerId: String): Unit = synchronized(lock)
{
    val groups = getGroups().toMutableList()
    val groupIdx = groups.indexOfFirst { it.id == groupId }
    if (groupIdx == -1) error("分组不存在")
    val group = groups[groupIdx]
    val player = group.players.firstOrNull { it.id == playerId } ?: error("玩家不在该分组中")
    groups[groupIdx] = group.copy(players = group.players.filter { it.id != playerId })
    saveGroups(groups)
    runCatching {
        val scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard ?: return@runCatching
        scoreboard.getTeam(group.name)?.removeEntry(player.name)
    }
}

@SubscribeEvent
private fun onPlayerJoin(event: PlayerJoinEvent)
{
    val groups = getGroups()
    val player = event.player
    val group = groups.firstOrNull { it.players.any { p -> p.id == player.name } } ?: return
    val scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard ?: return
    val team = scoreboard.getTeam(group.name) ?: scoreboard.registerNewTeam(group.name)
    scoreboard.teams.forEach()
    {
        if (it.name == group.name) return@forEach
        it.removeEntry(player.name)
    }
    team.addEntry(player.name)
    team.prefix = "&6[${group.name}] ".colored()
}

fun getPlayerGroup(playerName: String): GroupInfo?
{
    val groups = getGroups()
    return groups.firstOrNull { it.players.any { p -> p.id == playerName } }
}