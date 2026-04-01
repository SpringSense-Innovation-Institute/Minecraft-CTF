package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.gameStatus
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.leftGroup
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.playerCountPerGroup
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.recordsFolder
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.rightGroup
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.ProxyGameMode
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.console
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.chat.colored
import taboolib.platform.util.isNotAir
import java.text.SimpleDateFormat
import java.util.Date

const val PLAYER_PREPARE_MESSAGE = "I'm ready!"
const val GAME_START_MESSAGE = "Game start!"
const val GAME_END_MESSAGE = "Game over!"

private var currentRecordFile: String? = null
private var currentRecord: GameRecord? = null
private var task: PlatformExecutor.PlatformTask? = null
private val preparedPlayers = mutableSetOf<String>()

fun prepareGame()
{
    if (gameStatus != GameStatus.NONE) return
    gameStatus = GameStatus.PREPARING
    Bukkit.broadcastMessage("&6$leftGroup VS $rightGroup 的对决即将开始，请做好准备!".colored())

    preparedPlayers.clear()
    submit()
    {
        onlinePlayers().forEach { it.gameMode = ProxyGameMode.CREATIVE }
    }
}

fun playerPrepared(playerName: String)
{
    if (gameStatus != GameStatus.PREPARING) return
    val team = getPlayerTeam(playerName)
    if (team == PlayerTeam.NONE) return
    preparedPlayers.add(playerName)

    Bukkit.broadcastMessage(
        "${if (team == PlayerTeam.LEFT) "&c" else "&b"}$playerName &6is ready! &7(${preparedPlayers.size}/${playerCountPerGroup * 2})".colored()
    )

    val status = getPlayersPreparedStatus()
    if (status.leftPlayerCount != playerCountPerGroup)
        return
    if (status.rightPlayerCount != playerCountPerGroup)
        return
    if (status.leftGroupUnprepared.isNotEmpty() || status.rightGroupUnprepared.isNotEmpty())
        return
    startGame()
}

data class PlayerPreparedStatus(
    val leftGroupPrepared: Set<String>,
    val leftGroupUnprepared: Set<String>,
    val rightGroupPrepared: Set<String>,
    val rightGroupUnprepared: Set<String>,
)
{
    val leftPlayerCount = leftGroupPrepared.size + leftGroupUnprepared.size
    val rightPlayerCount = rightGroupPrepared.size + rightGroupUnprepared.size
}

fun getPlayersPreparedStatus(): PlayerPreparedStatus
{
    val left = mutableSetOf<String>()
    val right = mutableSetOf<String>()
    onlinePlayers().forEach()
    {
        when (getPlayerTeam(it.name))
        {
            PlayerTeam.LEFT -> left.add(it.name)
            PlayerTeam.RIGHT -> right.add(it.name)
            PlayerTeam.NONE -> {}
        }
    }

    return PlayerPreparedStatus(
        leftGroupPrepared = left.filter { preparedPlayers.contains(it) }.toSet(),
        leftGroupUnprepared = left.filter { !preparedPlayers.contains(it) }.toSet(),
        rightGroupPrepared = right.filter { preparedPlayers.contains(it) }.toSet(),
        rightGroupUnprepared = right.filter { !preparedPlayers.contains(it) }.toSet(),
    )
}

@SubscribeEvent
private fun onPlayerQuit(event: PlayerQuitEvent) = preparedPlayers.remove(event.player.name)
@SubscribeEvent
private fun onPlayerJoin(event: PlayerJoinEvent)
{
    if (gameStatus != GameStatus.IN_GAME)
    {
        event.player.gameMode = GameMode.CREATIVE
        return
    }
    val team = getPlayerTeam(event.player.name)
    when (team)
    {
        PlayerTeam.LEFT -> event.player.teleport(LocationStatus.IN_LEFT_PRISON.location)
        PlayerTeam.RIGHT -> event.player.teleport(LocationStatus.IN_RIGHT_PRISON.location)
        PlayerTeam.NONE -> Unit
    }
    if (team != PlayerTeam.NONE)
        event.player.gameMode = GameMode.ADVENTURE
}
@SubscribeEvent
fun onPlayerMessage(event: AsyncPlayerChatEvent)
{
    if (event.message == PLAYER_PREPARE_MESSAGE)
    {
        playerPrepared(event.player.name)
        event.isCancelled = true
    }
}

fun startGame()
{
    if (gameStatus != GameStatus.PREPARING) return
    gameStatus = GameStatus.IN_GAME

    var tick = 0L

    task = submit(period = 1)
    {
        if (tick == 0L)
        {
            resetGameMap()
            val date = Date()
            val format = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
            currentRecordFile = "${format.format(date)}_${leftGroup}vs$rightGroup"
            currentRecord = GameRecord.file(currentRecordFile!!).apply { start() }
            onlinePlayers().forEach()
            {
                if (getPlayerTeam(it.name) != PlayerTeam.NONE)
                {
                    it.gameMode = ProxyGameMode.ADVENTURE
                    it.sendMessage(GAME_START_MESSAGE)
                }
                else
                {
                    it.gameMode = ProxyGameMode.CREATIVE
                }

                it.sendTitle("&6$leftGroup VS $rightGroup 开始了!".colored(), "", 10, 70, 20)
            }
        }
        val end = tick == 3L * 60 * 20
        val winner = getWinner(end = end)
        if (winner in listOf(PlayerTeam.LEFT, PlayerTeam.RIGHT) || end)
        {
            stopGame(winner!!)
            task?.cancel()
            return@submit
        }
        tick++
    }
}

fun stopGame(winner: PlayerTeam = getWinner(end = true)!!)
{
    if (gameStatus == GameStatus.NONE) return
    if (gameStatus == GameStatus.PREPARING)
    {
        gameStatus = GameStatus.NONE
        Bukkit.broadcastMessage("&c游戏已取消".colored())
        return
    }

    gameStatus = GameStatus.NONE
    onlinePlayers().forEach()
    {
        it.gameMode = ProxyGameMode.CREATIVE
        if (getPlayerTeam(it.name) != PlayerTeam.NONE)
            it.sendMessage(GAME_END_MESSAGE)
    }

    currentRecord?.stop()

    val recordFile = currentRecordFile!!

    onlinePlayers().forEach()
    {
        if (it.isOp)
            it.sendMessage("&7游戏录像已保存至 &e${recordFile}.dat &7文件".colored())
    }

    submit(async = true)
    {
        uploadRecord(recordsFolder.resolve("$recordFile.dat"))
    }

    currentRecord = null
    currentRecordFile = null

    if (winner == PlayerTeam.NONE)
    {
        onlinePlayers().forEach()
        {
            it.sendTitle("&6$leftGroup 与 $rightGroup 平局!".colored(), "", 10, 70, 20)
        }
    }
    else
    {
        onlinePlayers().forEach()
        {
            it.sendTitle("&6${if (winner == PlayerTeam.LEFT) leftGroup else rightGroup} 队获胜!".colored(), "", 10, 70, 20)
        }
    }
}

private fun resetGameMap() = console().performCommand("function ctf:reset")

private fun getWinner(end: Boolean): PlayerTeam?
{
    val rightFlagLocations = listOf(
        Location(Bukkit.getWorld("world"), 22.0, 2.0, 5.0),
        Location(Bukkit.getWorld("world"), 22.0, 2.0, 9.0),
        Location(Bukkit.getWorld("world"), 22.0, 2.0, 13.0),
        Location(Bukkit.getWorld("world"), 22.0, 2.0, 17.0),
        Location(Bukkit.getWorld("world"), 22.0, 2.0, 21.0),
        Location(Bukkit.getWorld("world"), 22.0, 2.0, 25.0),
        Location(Bukkit.getWorld("world"), 22.0, 2.0, 29.0),
        Location(Bukkit.getWorld("world"), 22.0, 2.0, 33.0),
    )

    val leftFlagLocations = rightFlagLocations.map { Location(Bukkit.getWorld("world"), -22.0, 2.0, it.z) }

    val leftCount = leftFlagLocations.count { it.block.type.isNotAir() }
    val rightCount = rightFlagLocations.count { it.block.type.isNotAir() }

    if (end)
    {
        if (leftCount > rightCount)
            return PlayerTeam.LEFT
        if (rightCount > leftCount)
            return PlayerTeam.RIGHT
        return PlayerTeam.NONE
    }

    if (leftCount == 8 && rightCount == 8) return PlayerTeam.NONE
    if (leftCount == 8) return PlayerTeam.LEFT
    if (rightCount == 8) return PlayerTeam.RIGHT
    return null
}