package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.mainWorld
import moe.tachyon.ctf.plugin.group.getPlayerGroup
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityInteractEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.player.*
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit

@SubscribeEvent
private fun onPlayerJoin(event: PlayerJoinEvent)
{
    event.player.inventory.clear()
    event.player.gameMode = GameMode.CREATIVE
    event.player.walkSpeed = 0.2f
    event.player.activePotionEffects.forEach { event.player.removePotionEffect(it.type) }
    event.player.teleportToWorld()
}

@SubscribeEvent
private fun onPlayerQuit(event: PlayerQuitEvent)
{
    val playerName = event.player.name
    val world = getGameWorld(event.player.world.name) ?: return
    submit(delay = 1)
    {
        world.onPlayerQuit(playerName)
        removeFromWaitList(playerName)
    }
}

@SubscribeEvent(ignoreCancelled = true)
private fun onPlayerMove(event: PlayerMoveEvent)
{
    event.to?.let()
    {
        if (it.x !in -128.0..127.0 || it.z !in -128.0..127.0)
        {
            val targetLocation = Location(it.world, it.x.coerceIn(-128.0, 127.0), it.y, it.z.coerceIn(-128.0, 127.0), it.yaw, it.pitch)
            event.player.teleport(targetLocation)
            return
        }
    }
    
    val world = getGameWorld(event.player.world.name) ?: return

    val team = world.getPlayerTeam(event.player.name)
    val from = event.from
    val to = event.to ?: return

    val targetStatus = when (team)
    {
        PlayerTeam.LEFT if world.gameStatus.inGame()  -> LocationStatus.IN_FIELD
        PlayerTeam.RIGHT if world.gameStatus.inGame() -> LocationStatus.IN_FIELD
        PlayerTeam.LEFT if world.gameStatus.preparing()  -> LocationStatus.IN_LEFT_BASE
        PlayerTeam.RIGHT if world.gameStatus.preparing() -> LocationStatus.IN_RIGHT_BASE
        PlayerTeam.NONE if (world.gameStatus.inGame() || world.gameStatus.preparing()) -> LocationStatus.OUT_OF_FIELD
        else                                                                                        -> return
    }

    if (targetStatus.contains(from) && !targetStatus.contains(to))
    {
        val targetLocation = Location(from.world, from.x, from.y, from.z, to.yaw, to.pitch)
        event.player.teleport(targetLocation)
    }
    else if (!targetStatus.contains(from) && !targetStatus.contains(to))
    {
        event.player.teleport(world, targetStatus)
    }
}

@SubscribeEvent
private fun onPlayerInteract(event: PlayerInteractEvent)
{
    if (event.action != Action.PHYSICAL)
        event.isCancelled = true
}

@SubscribeEvent
private fun onEntityDamaged(event: EntityDamageEvent)
{
    val world = getGameWorld(event.entity.world.name) ?: return
    if (event.entity.isInvulnerable && world.gameStatus.replaying())
        event.isCancelled = true
}

@SubscribeEvent
private fun onEntityInteract(event: EntityInteractEvent)
{
    event.isCancelled = true
}

@SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
private fun onBlockPlace(event: BlockPlaceEvent)
{
    GameRecord.addBlock(event.block.location)
}

@SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
private fun onBlockBreak(event: BlockBreakEvent)
{
    GameRecord.addBlock(event.block.location)
}

@SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
private fun onBlockPhysics(event: BlockPhysicsEvent)
{
    GameRecord.addBlock(event.block.location)
}

@SubscribeEvent(ignoreCancelled = true)
private fun onMenuOpen(event: PlayerSwapHandItemsEvent)
{
    if (event.player.isSneaking)
        event.player.openMenu()
}

@SubscribeEvent(ignoreCancelled = true)
private fun onDropItem(event: ItemSpawnEvent)
{
    event.isCancelled = true
}

@SubscribeEvent(ignoreCancelled = true)
private fun onPlayerChangeWorld(event: PlayerChangedWorldEvent)
{
    val from = getGameWorld(event.from.name)
    val world = getGameWorld(event.player.world.name)
    from?.onPlayerQuit(event.player.name)
    world?.onPlayerJoin(event.player.name)

    if (event.player.world == mainWorld)
    {
        event.player.inventory.clear()
        event.player.gameMode = GameMode.CREATIVE
    }

    Bukkit.getPluginManager().callEvent(PlayerMoveEvent(event.player, event.player.location, event.player.location))
}

@SubscribeEvent(ignoreCancelled = true)
private fun onPlayerMatch(event: AsyncPlayerChatEvent)
{
    if (event.player.world != mainWorld)
    {
        val gameWorld = getGameWorld(event.player.world.name) ?: return
        if (gameWorld.gameStatus.inGame() || gameWorld.gameStatus.preparing())
            return
    }

    val msg = event.message.lowercase()

    if (!msg.startsWith("match "))
        return

    event.isCancelled = true

    val illegal = { event.player.sendMessage("§c匹配参数错误！正确格式：match enemy:<enemy> players:<count> map:<mapType>") }

    val args = msg.split(" ").filter { ':' in it }.map { it.split(":") }.associate { it[0] to it[1] }
    val group = getPlayerGroup(event.player.name)?.id ?: return event.player.sendMessage("§c你没有加入任何队伍，无法匹配！")
    val enemyTeam = args["enemy"] ?: return illegal()
    val count = args["players"]?.toIntOrNull()?.takeIf { it in 0..5 } ?: return illegal()
    val map = args["map"] ?: return illegal()

    val enemyMode = when (enemyTeam)
    {
        "none"            -> Enemy.Specific(Group.None)
        "any"             -> Enemy.Any
        else              ->
        {
            val bot = Group.Bot.entries.firstOrNull { it.selectId == enemyTeam }?.let(Enemy::Specific)
            bot ?: Enemy.Specific(Group.PlayerGroup(enemyTeam.toIntOrNull()?.takeIf { it != group && it > 0 } ?: return illegal()))
        }
    }

    val mapType = when (map)
    {
        "fixed"       -> MapType.FIXED
        "random"      -> MapType.RANDOM
        "any"         -> MapType.ANY
        else          -> return illegal()
    }

    val p = practiceMode
    if (p != null)
    {
        if (p.enemy != null && p.enemy != enemyMode) return illegal()
        if (p.playersPerGroup != null && p.playersPerGroup != count) return illegal()
        if (p.mapType != null && p.mapType != mapType) return illegal()
    }

    addToWaitList(
        playerName = event.player.name,
        setting = MatchSetting(
            group = group,
            enemy = enemyMode,
            playersPerGroup = count,
            mapType = mapType,
        )
    )
}

@SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
private fun onPlayerChat(event: AsyncPlayerChatEvent)
{
    event.isCancelled = true
    val msg = "<${event.player.name}> ${event.message}"
    if (msg.lowercase().contains("game"))
        return
    event.player.world.players.forEach()
    {
        it.sendMessage(msg)
    }
}

@SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
private fun onPlayerCommand(event: PlayerCommandPreprocessEvent)
{
    if (event.message.lowercase().contains("game"))
        event.isCancelled = true
}

@SubscribeEvent(ignoreCancelled = true)
private fun onPlayerBreakBlock(event: BlockBreakEvent)
{
    event.isCancelled = true
}

@SubscribeEvent(ignoreCancelled = true)
private fun onPlayerPlaceBlock(event: BlockPlaceEvent)
{
    event.isCancelled = true
}

@SubscribeEvent(ignoreCancelled = true)
private fun onKickSpam(event: PlayerKickEvent)
{
    if ("spam" in event.reason.lowercase() || "spam" in event.leaveMessage.lowercase())
        event.isCancelled = true
}

@SubscribeEvent(ignoreCancelled = true)
private fun onCombust(event: EntityCombustEvent)
{
    event.isCancelled = true
}