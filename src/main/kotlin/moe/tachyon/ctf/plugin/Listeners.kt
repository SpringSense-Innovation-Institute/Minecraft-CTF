package moe.tachyon.ctf.plugin

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
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

@SubscribeEvent
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
        val location = targetStatus.location(world)
        val targetLocation = Location(location.world, location.x, location.y, location.z, to.yaw, to.pitch)
        event.player.teleport(targetLocation)
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

@SubscribeEvent
private fun onMenuOpen(event: PlayerSwapHandItemsEvent)
{
    if (event.player.isOp && event.player.isSneaking)
    {
        if (event.player.world == Bukkit.getWorld("world"))
            return event.player.openMainMenu()
    }

    if (event.player.world == Bukkit.getWorld("world"))
        event.player.chooseWorldMenu()
    else
        event.player.openWorldMenu()
}

@SubscribeEvent
private fun onDropItem(event: ItemSpawnEvent)
{
    event.isCancelled = true
}

@SubscribeEvent
private fun onPlayerChangeWorld(event: PlayerChangedWorldEvent)
{
    val from = getGameWorld(event.from.name)
    val world = getGameWorld(event.player.world.name)
    from?.onPlayerQuit(event.player.name)
    world?.onPlayerJoin(event.player.name)

    if (event.player.world.name == "world")
    {
        event.player.inventory.clear()
        event.player.gameMode = GameMode.CREATIVE
    }

    Bukkit.getPluginManager().callEvent(PlayerMoveEvent(event.player, event.player.location, event.player.location))
}

private val matchMessageRegex = Regex("with (?<team>(\\d+)|none|random|default) (?<count>\\d+) (?<map>fixed|random|unspecified)")
@SubscribeEvent
private fun onPlayerMatch(event: AsyncPlayerChatEvent)
{
    if (matchMessageRegex.matches(event.message))
    {
        val matchResult = matchMessageRegex.matchEntire(event.message) ?: return
        val team = matchResult.groups["team"]?.value ?: return
        val count = matchResult.groups["count"]?.value?.toIntOrNull() ?: return
        val map = matchResult.groups["map"]?.value ?: return

        @Suppress("unused")
        val group = event.player.name.split("-").getOrNull(1)?.toIntOrNull() ?: return

        val enemyMode = when (team)
        {
            "none"       -> Enemy.None
            "random"     -> Enemy.Random
            "default"    -> Enemy.Default
            else         -> Enemy.Group(team.toIntOrNull() ?: return)
        }

        val mapType = when (map)
        {
            "fixed"       -> MapType.FIXED
            "random"      -> MapType.RANDOM
            "unspecified" -> MapType.UNSPECIFIED
            else          -> return
        }

        addToWaitList(
            playerName = event.player.name,
            setting = MatchSetting(
                enemy = enemyMode,
                playersPerGroup = count,
                mapType = mapType,
            )
        )

        event.isCancelled = true
    }
}