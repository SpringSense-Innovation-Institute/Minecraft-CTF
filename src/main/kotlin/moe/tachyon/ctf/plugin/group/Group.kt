package moe.tachyon.ctf.plugin.group

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.chat.colored
import taboolib.platform.BukkitPlugin

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

private val groupFile get() = BukkitPlugin.getInstance().dataFolder.resolve("groups.json")

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

private fun getAllPlayers(): List<GroupInfo.PlayerInfo>
{
    val file = BukkitPlugin.getInstance().dataFolder.resolve("players.json")
    if (!file.exists()) return emptyList()
    return runCatching { Json.decodeFromString<List<GroupInfo.PlayerInfo>>(file.readText()) }.getOrNull() ?: emptyList()
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