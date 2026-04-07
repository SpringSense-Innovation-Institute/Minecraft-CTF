package moe.tachyon.ctf.plugin

import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.player.PlayerMoveEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.chat.colored
import taboolib.platform.util.buildItem
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class GameWorld(
    val name: String,
    val leftGroupNumber: Int,
    val rightGroupNumber: Int,
    val record: GameRecord,
    val randomMap: Boolean,
    val playerCountPerGroup: Int,
    val practice: String?,
)
{
    private val leftGroupPlayers = mutableSetOf<String>()
    private val rightGroupPlayers = mutableSetOf<String>()

    var gameStatus = GameStatus.NONE
        set(value)
        {
            field = value
            if (value == GameStatus.IN_GAME)
            {
                warning("游戏已开始，玩家分组将被冻结!")
            }
            else
            {
                updateTeam()
            }
        }

    @OptIn(ExperimentalAtomicApi::class)
    val used = AtomicBoolean(false)

    @OptIn(ExperimentalAtomicApi::class)
    fun canRecycle(): Boolean
    {
        warning("check: $gameStatus ${getPlayers().size} ${used.load()}")
        if (!gameStatus.preparing() && !gameStatus.none()) return false
        if (getPlayers().isNotEmpty()) return false
        if (!used.load()) return false
        return true
    }

    fun getPlayers(): List<String>
        = bukkitWorld().players.map { it.name }
    
    fun broadcastMessage(message: String)
        = bukkitWorld().players.forEach { it.sendMessage(message) }

    fun getPlayerTeam(player: String): PlayerTeam = when (player)
    {
        in leftGroupPlayers -> PlayerTeam.LEFT
        in rightGroupPlayers -> PlayerTeam.RIGHT
        else -> PlayerTeam.NONE
    }

    fun leftGroupPlayers() = leftGroupPlayers.toList()
    fun rightGroupPlayers() = rightGroupPlayers.toList()
    fun bukkitWorld() = Bukkit.getWorld(name)!!

    fun updateTeam()
    {
        if (gameStatus == GameStatus.IN_GAME) return
        val players = getPlayers()
        players.forEach()
        {
            if (it.startsWith("CTF-${leftGroupNumber}-"))
            {
                leftGroupPlayers += it
                rightGroupPlayers -= it
            }
            else if (it.startsWith("CTF-${rightGroupNumber}-"))
            {
                rightGroupPlayers += it
                leftGroupPlayers -= it
            }
        }

        leftGroupPlayers().forEach()
        {
            if (!it.startsWith("CTF-${leftGroupNumber}-") || players.none { player -> player == it })
            {
                leftGroupPlayers -= it
            }
        }

        rightGroupPlayers().forEach()
        {
            if (!it.startsWith("CTF-${rightGroupNumber}-") || players.none { player -> player == it })
            {
                rightGroupPlayers -= it
            }
        }

        players.forEach()
        {
            val player = Bukkit.getPlayer(it) ?: return@forEach
            Bukkit.getPluginManager().callEvent(PlayerMoveEvent(player, player.location, player.location))
        }
    }

    private var task: PlatformExecutor.PlatformTask? = null
    private val preparedPlayers = mutableSetOf<String>()

    fun prepareGame(timeout: Boolean = true)
    {
        gameStatus = GameStatus.PREPARING
        broadcastMessage("&6$leftGroupNumber VS $rightGroupNumber 的对决即将开始，请做好准备!".colored())

        preparedPlayers.clear()
        if (timeout) submit(delay = 600)
        {
            if (gameStatus == GameStatus.PREPARING)
            {
                if (leftGroupNumber == 0 && rightGroupNumber == 0) return@submit

                val leftGroupIsReady = leftGroupPlayers.size == playerCountPerGroup && leftGroupPlayers.all { it in preparedPlayers } || leftGroupNumber == 0
                val rightGroupIsReady = rightGroupPlayers.size == playerCountPerGroup && rightGroupPlayers.all { it in preparedPlayers } || rightGroupNumber == 0

                if (!leftGroupIsReady || !rightGroupIsReady)
                    stopGame(PlayerTeam.NONE)
            }
        }
    }

    fun playerPrepared(playerName: String)
    {
        if (gameStatus != GameStatus.PREPARING) return
        val team = getPlayerTeam(playerName)
        if (team == PlayerTeam.NONE) return
        preparedPlayers.add(playerName)

        broadcastMessage(
            "${if (team == PlayerTeam.LEFT) "&c" else "&b"}$playerName &6is ready! &7(${preparedPlayers.size}/${playerCountPerGroup * 2})".colored()
        )

        val status = getPlayersPreparedStatus()
        if (status.leftPlayerCount != playerCountPerGroup && leftGroupNumber != 0)
            return
        if (status.rightPlayerCount != playerCountPerGroup && rightGroupNumber != 0)
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
        getPlayers().forEach()
        {
            when (getPlayerTeam(it))
            {
                PlayerTeam.LEFT -> left.add(it)
                PlayerTeam.RIGHT -> right.add(it)
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


    fun onPlayerQuit(name: String)
    {
        updateTeam()
        preparedPlayers.remove(name)
        checkGameWorldRecycle(this.name)
    }

    fun onPlayerJoin(name: String)
    {
        val player = Bukkit.getPlayer(name) ?: return
        updateTeam()
        @OptIn(ExperimentalAtomicApi::class)
        used.store(true)
        if (!gameStatus.inGame())
        {
            player.gameMode = GameMode.CREATIVE
            if (gameStatus.preparing() &&
                (player.name.startsWith("CTF-${leftGroupNumber}-") ||
                player.name.startsWith("CTF-${rightGroupNumber}-")))
            {
                player.sendMessage(GAME_PREPARED_MESSAGE)
            }
            return
        }
        val team = getPlayerTeam(name)
        when (team)
        {
            PlayerTeam.LEFT -> player.teleport(LocationStatus.IN_LEFT_PRISON.location(this))
            PlayerTeam.RIGHT -> player.teleport(LocationStatus.IN_RIGHT_PRISON.location(this))
            PlayerTeam.NONE -> Unit
        }
        if (team != PlayerTeam.NONE)
            player.gameMode = GameMode.ADVENTURE
    }

    fun startGame()
    {
        if (gameStatus != GameStatus.PREPARING) return
        gameStatus = GameStatus.IN_GAME

        var tick = 0L

        task = submit(period = 1)
        {
            if (gameStatus != GameStatus.IN_GAME)
            {
                task?.cancel()
                return@submit
            }
            if (tick == 0L)
            {
                resetGameMap()
                record.start()
                bukkitWorld().difficulty = Difficulty.HARD
                getPlayers().forEach()
                {
                    val player = Bukkit.getPlayer(it) ?: return@forEach
                    player.inventory.clear()
                    player.foodLevel = 20
                    player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                    player.health = 20.0
                    val team = getPlayerTeam(it)
                    if (team != PlayerTeam.NONE)
                    {
                        player.gameMode = GameMode.ADVENTURE
                        val l = leftGroupPlayers().joinToString(",") { it1 -> "\"$it1\"" }
                        val r = rightGroupPlayers().joinToString(",") { it1 -> "\"$it1\"" }
                        player.sendMessage(GAME_START_MESSAGE + "{\"left\":[$l],\"right\":[$r]}")
                        val color = if (team == PlayerTeam.LEFT) Color.RED else Color.BLUE

                        player.inventory.chestplate = buildItem(Material.LEATHER_CHESTPLATE)
                        {
                            this.color = color
                            enchants[Enchantment.BINDING_CURSE] = 1
                        }
                        player.inventory.leggings = buildItem(Material.LEATHER_LEGGINGS)
                        {
                            this.color = color
                            enchants[Enchantment.BINDING_CURSE] = 1
                        }
                        player.inventory.boots = buildItem(Material.LEATHER_BOOTS)
                        {
                            this.color = color
                            enchants[Enchantment.BINDING_CURSE] = 1
                        }
                    }
                    else
                    {
                        player.gameMode = GameMode.CREATIVE
                    }

                    player.sendTitle("&6$leftGroupNumber VS $rightGroupNumber 开始了!".colored(), "", 10, 70, 20)
                }
                bukkitWorld().setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)

                updateStatisticData(practice)
                {
                    if (leftGroupNumber == 0 || rightGroupNumber == 0)
                    {
                        val group = if (leftGroupNumber == 0) rightGroupNumber else leftGroupNumber
                        val value = withNoneTimes[group] ?: 0
                        withNoneTimes[group] = value + 1
                    }
                    else
                    {
                        withOtherTimes[leftGroupNumber] = withOtherTimes[leftGroupNumber]?.plus(1) ?: 1
                        withOtherTimes[rightGroupNumber] = withOtherTimes[rightGroupNumber]?.plus(1) ?: 1
                    }
                }
            }
            val end = tick == 3L * 60 * 20
            getWinner(end = end)?.let()
            { winner ->
                stopGame(winner)
                task?.cancel()
                updateStatisticData(practice)
                {
                    if (leftGroupNumber != 0 && rightGroupNumber != 0)
                    {
                        totalTime[leftGroupNumber] = totalTime[leftGroupNumber]?.plus(tick) ?: tick
                        totalTime[rightGroupNumber] = totalTime[rightGroupNumber]?.plus(tick) ?: tick
                        if (winner == PlayerTeam.LEFT)
                            winTimes[leftGroupNumber] = winTimes[leftGroupNumber]?.plus(1) ?: 1
                        else
                            winTimes[rightGroupNumber] = winTimes[rightGroupNumber]?.plus(1) ?: 1
                    }
                }
                return@submit
            }
            tick++
        }
    }

    fun stopGame(winner: PlayerTeam = getWinner(end = true)!!)
    {
        if (gameStatus == GameStatus.NONE) return

        gameStatus = GameStatus.NONE
        if (gameStatus == GameStatus.PREPARING)
        {
            broadcastMessage("&c游戏已取消".colored())
            getPlayers().forEach()
            {
                val p = Bukkit.getPlayer(it) ?: return@forEach
                if (p.world.name == name) p.teleportToWorld()
            }
            return
        }

        getPlayers().forEach()
        {
            val player = Bukkit.getPlayer(it) ?: return@forEach
            player.gameMode = GameMode.CREATIVE
            if (getPlayerTeam(it) != PlayerTeam.NONE)
                player.sendMessage(GAME_END_MESSAGE)
            if (player.isOp)
                player.sendMessage("&7游戏录像已保存至 &e${record.file.name} &7文件".colored())
        }

        submit(async = false, delay = 1)
        {
            record.stop()
            submit(async = true, delay = 5)
            {
                uploadRecord(record.file)
            }
        }

        getPlayers().forEach()
        {
            val player = Bukkit.getPlayer(it) ?: return@forEach
            if (winner == PlayerTeam.NONE)
                player.sendTitle("&6$leftGroupNumber 与 $rightGroupNumber 平局!".colored(), "", 10, 70, 20)
            else
                player.sendTitle("&6${if (winner == PlayerTeam.LEFT) leftGroupNumber else rightGroupNumber} 队获胜!".colored(), "", 10, 70, 20)

            player.inventory.clear()
        }
    }

    private fun resetGameMap()
    {
        clearMap()
        generateMap()
    }

    private fun getWinner(end: Boolean): PlayerTeam?
    {
        val leftTeamPlayer = getPlayers().count { it in leftGroupPlayers }
        val rightTeamPlayer = getPlayers().count { it in rightGroupPlayers }

        val leftTeamEmpty = leftTeamPlayer == 0 && leftGroupNumber != 0
        val rightTeamEmpty = rightTeamPlayer == 0 && rightGroupNumber != 0

        if (leftTeamEmpty && rightTeamEmpty) return PlayerTeam.NONE
        if (leftTeamEmpty) return PlayerTeam.RIGHT
        if (rightTeamEmpty) return PlayerTeam.LEFT

        var leftCount = 0
        var rightCount = 0

        for (x in -23..23)
        {
            for (z in -36..36)
            {
                val location = Location(bukkitWorld(), x.toDouble(), 0.0, z.toDouble())
                if (location.block.type == Material.GOLD_BLOCK)
                {
                    val flagLocation = location.clone().add(0.0, 2.0, 0.0)
                    if (flagLocation.block.type == Material.BLUE_BANNER)
                        leftCount++
                    else if (flagLocation.block.type == Material.RED_BANNER)
                        rightCount++
                }
            }
        }

        var x = -52
        val y = -5
        var z = -46

        repeat(leftCount)
        {
            val location = Location(bukkitWorld(), x.toDouble(), y.toDouble(), z.toDouble())
            if (location.block.type != Material.REDSTONE_BLOCK) location.block.type = Material.REDSTONE_BLOCK
            z += 4
        }
        repeat(8 - leftCount)
        {
            val location = Location(bukkitWorld(), x.toDouble(), y.toDouble(), z.toDouble())
            if (location.block.type != Material.AIR) location.block.type = Material.AIR
            z += 4
        }

        x = 8
        z = -46

        repeat(rightCount)
        {
            val location = Location(bukkitWorld(), x.toDouble(), y.toDouble(), z.toDouble())
            if (location.block.type != Material.REDSTONE_BLOCK) location.block.type = Material.REDSTONE_BLOCK
            z += 4
        }
        repeat(8 - rightCount)
        {
            val location = Location(bukkitWorld(), x.toDouble(), y.toDouble(), z.toDouble())
            if (location.block.type != Material.AIR) location.block.type = Material.AIR
            z += 4
        }

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
}