package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.GameRecord.Companion.box
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.MushroomCow
import org.bukkit.entity.Silverfish
import org.bukkit.event.player.PlayerMoveEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.chat.colored
import taboolib.platform.util.buildItem
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class GameWorld(
    val name: String,
    val leftGroup: Group,
    val rightGroup: Group,
    val record: GameRecord,
    val randomMap: Boolean,
    val playerCountPerGroup: Int,
    val practice: String?,
)
{
    private val leftGroupPlayers = mutableSetOf<String>()
    private val rightGroupPlayers = mutableSetOf<String>()

    fun addPlayer(player: String, group: Group)
    {
        val p = Bukkit.getPlayer(player) ?: return
        if (group == leftGroup) leftGroupPlayers += player
        else if (group == rightGroup) rightGroupPlayers += player
        p.teleport(LocationStatus.IN_FIELD.location(this))
        updateTeam()
    }

    var gameStatus = GameStatus.NONE
        set(value)
        {
            field = value
            updateTeam()
        }

    @OptIn(ExperimentalAtomicApi::class)
    val used = AtomicBoolean(false)

    @OptIn(ExperimentalAtomicApi::class)
    fun canRecycle(): Boolean
    {
        if (!gameStatus.preparing() && !gameStatus.none()) return false
        if (getPlayers().isNotEmpty()) return false
        if (!used.load()) return false
        return true
    }

    fun getPlayers(): List<String>
        = bukkitWorld()?.players?.map { it.name } ?: emptyList()
    
    fun broadcastMessage(message: String)
        = bukkitWorld()?.players?.forEach { it.sendMessage(message) }

    fun getPlayerTeam(player: String): PlayerTeam = when (player)
    {
        in leftGroupPlayers -> PlayerTeam.LEFT
        in rightGroupPlayers -> PlayerTeam.RIGHT
        else -> PlayerTeam.NONE
    }

    fun leftGroupPlayers() = leftGroupPlayers.toList()
    fun rightGroupPlayers() = rightGroupPlayers.toList()
    fun leftGroupBukkitPlayers() = leftGroupPlayers.mapNotNull(Bukkit::getPlayer).filter { it.world.name == name }
    fun rightGroupBukkitPlayers() = rightGroupPlayers.mapNotNull(Bukkit::getPlayer).filter { it.world.name == name }
    fun bukkitWorld(): World? = Bukkit.getWorld(name)

    fun updateTeam()
    {
        if (gameStatus == GameStatus.IN_GAME) return
        val players = getPlayers()
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
        broadcastMessage("&6${leftGroup.displayName} VS ${rightGroup.displayName} 的对决即将开始，请做好准备!".colored())

        preparedPlayers.clear()
        if (timeout) submit(delay = 600)
        {
            if (gameStatus == GameStatus.PREPARING)
            {
                if (leftGroup.isNone && rightGroup.isNone) return@submit

                val leftGroupIsReady = leftGroupPlayers.size == playerCountPerGroup && leftGroupPlayers.all { it in preparedPlayers } || leftGroup.isNone
                val rightGroupIsReady = rightGroupPlayers.size == playerCountPerGroup && rightGroupPlayers.all { it in preparedPlayers } || rightGroup.isNone

                if (!leftGroupIsReady || !rightGroupIsReady)
                    stopGame(PlayerTeam.NONE)
            }
        }
    }

    fun playerPrepared(playerName: String?)
    {
        if (gameStatus != GameStatus.PREPARING) return

        playerName?.let()
        {
            if (playerName in preparedPlayers) return
            val team = getPlayerTeam(playerName)
            if (team == PlayerTeam.NONE) return
            preparedPlayers.add(playerName)

            broadcastMessage(
                "${if (team == PlayerTeam.LEFT) "&c" else "&b"}$playerName &6is ready! &7(${preparedPlayers.size}/${playerCountPerGroup * 2})".colored()
            )
        }

        val sb = StringBuilder()
        sb.append("&6已准备: ")
        leftGroupPlayers().filter { preparedPlayers.contains(it) }.forEach { sb.append("&c$it &6") }
        rightGroupPlayers().filter { preparedPlayers.contains(it) }.forEach { sb.append("&b$it &6") }
        broadcastMessage(sb.toString().colored())

        val status = getPlayersPreparedStatus()
        if (status.leftPlayerCount != playerCountPerGroup && !leftGroup.isNone)
            return
        if (status.rightPlayerCount != playerCountPerGroup && !rightGroup.isNone)
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
            leftGroupPrepared = left.filter { it in preparedPlayers }.toSet(),
            leftGroupUnprepared = left.filter { it !in preparedPlayers }.toSet(),
            rightGroupPrepared = right.filter { it in preparedPlayers }.toSet(),
            rightGroupUnprepared = right.filter { it !in preparedPlayers }.toSet(),
        )
    }


    fun onPlayerQuit(name: String)
    {
        preparedPlayers -= name
        leftGroupPlayers -= name
        rightGroupPlayers -= name
        updateTeam()
        playerPrepared(null)
        checkGameWorldRecycle(this.name)
    }

    fun onPlayerJoin(name: String)
    {
        val player = Bukkit.getPlayer(name) ?: return
        player.inventory.clear()
        updateTeam()
        @OptIn(ExperimentalAtomicApi::class)
        used.store(true)
        if (!gameStatus.inGame())
        {
            player.gameMode = GameMode.CREATIVE
            if (gameStatus.preparing() && (player.name in leftGroupPlayers || player.name in rightGroupPlayers))
                player.sendMessage(GAME_PREPARED_MESSAGE)
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

    private var tick = 0L
    fun startGame()
    {
        if (gameStatus != GameStatus.PREPARING) return
        gameStatus = GameStatus.IN_GAME

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
                bukkitWorld()?.difficulty = Difficulty.HARD
                getPlayers().forEach()
                {
                    val player = Bukkit.getPlayer(it) ?: return@forEach
                    player.inventory.clear()

                    player.foodLevel = 9
                    player.exhaustion = 0f
                    player.saturation = 0f

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

                    player.sendTitle("&6${leftGroup.displayName} VS ${rightGroup.displayName} 开始了!".colored(), "", 10, 70, 20)
                }
                bukkitWorld()?.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                bukkitWorld()?.pvp = true

                updateStatisticData(practice)
                {
                    if (leftGroup is Group.PlayerGroup)
                    {
                        val mp = when (rightGroup)
                        {
                            is Group.Bot         -> withBotTimes
                            Group.None           -> withNoneTimes
                            is Group.PlayerGroup -> withOtherTimes
                        }

                        mp[leftGroup.id] = (mp[leftGroup.id] ?: 0) + 1
                    }

                    if (rightGroup is Group.PlayerGroup)
                    {
                        val mp = when (leftGroup)
                        {
                            is Group.Bot         -> withBotTimes
                            Group.None           -> withNoneTimes
                            is Group.PlayerGroup -> withOtherTimes
                        }

                        mp[rightGroup.id] = (mp[rightGroup.id] ?: 0) + 1
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
                    if (leftGroup is Group.PlayerGroup && rightGroup is Group.PlayerGroup)
                    {
                        totalTime[leftGroup.id] = totalTime[leftGroup.id]?.plus(tick) ?: tick
                        totalTime[rightGroup.id] = totalTime[rightGroup.id]?.plus(tick) ?: tick
                        if (winner == PlayerTeam.LEFT)
                            winTimes[leftGroup.id] = winTimes[leftGroup.id]?.plus(1) ?: 1
                        else
                            winTimes[rightGroup.id] = winTimes[rightGroup.id]?.plus(1) ?: 1
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
        }

        getPlayers().forEach()
        {
            val player = Bukkit.getPlayer(it) ?: return@forEach
            if (winner == PlayerTeam.NONE)
                player.sendTitle("&6${leftGroup.displayName} 与 ${rightGroup.displayName} 平局!".colored(), "", 10, 70, 20)
            else
                player.sendTitle("&6${if (winner == PlayerTeam.LEFT) leftGroup.displayName else rightGroup.displayName} 获胜!".colored(), "", 10, 70, 20)

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

        val leftTeamEmpty = leftTeamPlayer == 0 && leftGroup !is Group.None
        val rightTeamEmpty = rightTeamPlayer == 0 && rightGroup !is Group.None

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

    enum class MapElement
    {
        LEFT_FLAG,
        LEFT_TARGET,
        LEFT_TARGET_WITH_FLAG,
        RIGHT_FLAG,
        RIGHT_TARGET,
        RIGHT_TARGET_WITH_FLAG,

        BLANK,
        OBSTACLE,
        ;
        fun passable(): Boolean
        {
            return this == BLANK || this == LEFT_TARGET || this == RIGHT_TARGET
        }
    }

    private val updateInterval = 5
    private var buf = listOf<List<MapElement>>()
    private var bufTick = - updateInterval - 1L

    fun getMap(): List<List<MapElement>>
    {
        if (tick - bufTick <= updateInterval)
            return buf

        val map = (-24..24).map()
        { x ->
            (-36..36).map()
            { z ->
                getElement(x, z)
            }
        }
        buf = map
        bufTick = tick
        return map
    }

    fun getCows(): List<MushroomCow> =
        bukkitWorld()?.getNearbyEntities(box)?.filterIsInstance<MushroomCow>() ?: emptyList()

    fun getSilverfish(): List<Silverfish> =
        bukkitWorld()?.getNearbyEntities(box)?.filterIsInstance<Silverfish>() ?: emptyList()

    private fun getElement(x: Int, z: Int): MapElement
    {
        val location = Location(bukkitWorld(), x.toDouble(), 0.0, z.toDouble())

        val isTarget = location.block.type == Material.GOLD_BLOCK
        val isLeftFlag = location.clone().add(0.0, 2.0, 0.0).block.type == Material.RED_BANNER
        val isRightFlag = location.clone().add(0.0, 2.0, 0.0).block.type == Material.BLUE_BANNER

        return when
        {
            isLeftFlag && isTarget  -> MapElement.LEFT_TARGET_WITH_FLAG
            isLeftFlag              -> MapElement.LEFT_FLAG
            isRightFlag && isTarget -> MapElement.RIGHT_TARGET_WITH_FLAG
            isRightFlag             -> MapElement.RIGHT_FLAG
            isTarget                -> if (x < 0) MapElement.LEFT_TARGET else MapElement.RIGHT_TARGET
            else                    ->
            {
                if (!location.clone().add(0.0, 1.0, 0.0).block.isPassable) MapElement.OBSTACLE
                else if (!location.clone().add(0.0, 2.0, 0.0).block.isPassable) MapElement.OBSTACLE
                else MapElement.BLANK
            }
        }
    }
}