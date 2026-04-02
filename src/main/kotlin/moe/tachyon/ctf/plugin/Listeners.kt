package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.gameStatus
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityInteractEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.player.*
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.simpleCommand
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
private fun onPlayerJoin(event: PlayerJoinEvent) = updateTeam()

@SubscribeEvent
private fun onPlayerQuit(event: PlayerQuitEvent) = updateTeam()

@SubscribeEvent
private fun onPlayerMove(event: PlayerMoveEvent)
{
    val team = getPlayerTeam(event.player.name)
    val from = event.from
    val to = event.to ?: return

    val targetStatus = when (team)
    {
        PlayerTeam.LEFT if gameStatus == GameStatus.IN_GAME  -> LocationStatus.IN_FIELD
        PlayerTeam.RIGHT if gameStatus == GameStatus.IN_GAME -> LocationStatus.IN_FIELD
        PlayerTeam.LEFT if gameStatus == GameStatus.PREPARING  -> LocationStatus.IN_LEFT_BASE
        PlayerTeam.RIGHT if gameStatus == GameStatus.PREPARING -> LocationStatus.IN_RIGHT_BASE
        PlayerTeam.NONE if (gameStatus == GameStatus.IN_GAME || gameStatus == GameStatus.PREPARING) -> LocationStatus.OUT_OF_FIELD
        else                                                                                        -> return
    }

    if (targetStatus.contains(from) && !targetStatus.contains(to))
    {
        val targetLocation = Location(from.world, from.x, from.y, from.z, to.yaw, to.pitch)
        event.player.teleport(targetLocation)
    }
    else if (!targetStatus.contains(from) && !targetStatus.contains(to))
    {
        val targetLocation = Location(targetStatus.location.world, targetStatus.location.x, targetStatus.location.y, targetStatus.location.z, to.yaw, to.pitch)
        event.player.teleport(targetLocation)
    }
}

@SubscribeEvent
private fun onPlayerInteract(event: PlayerInteractEvent)
{
    if (event.action != Action.PHYSICAL && !event.player.isOp)
        event.isCancelled = true
}

@SubscribeEvent
private fun onEntityDamaged(event: EntityDamageEvent)
{
    if (event.entity.isInvulnerable && GameRecord.isReplaying())
        event.isCancelled = true
}

@SubscribeEvent
private fun onPickupItem(event: EntityPickupItemEvent)
{
    if (event.item.isInvulnerable && GameRecord.isReplaying())
        event.isCancelled = true
}

@SubscribeEvent
private fun onEntityInteract(event: EntityInteractEvent)
{
    if (event.entity.isInvulnerable && GameRecord.isReplaying())
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

// 劫持setblock命令，因为setblock命令不会触发方块事件但改变了方块
@Awake(LifeCycle.ENABLE)
private fun replaceSetBlockCommand()
{
    simpleCommand("setblock")
    { sender, args ->
        sender.performCommand("minecraft:setblock ${args.joinToString(" ")}")
        val location = Location(Bukkit.getWorld(args[0]), args[1].toDouble(), args[2].toDouble(), args[3].toDouble())
        GameRecord.addBlock(location)
    }

    simpleCommand("fill")
    { sender, args ->
        sender.performCommand("minecraft:fill ${args.joinToString(" ")}")
        val world = Bukkit.getWorld(args[0])
        val from = Location(world, args[1].toDouble(), args[2].toDouble(), args[3].toDouble())
        val to = Location(world, args[4].toDouble(), args[5].toDouble(), args[6].toDouble())
        for (x in from.blockX..to.blockX)
            for (y in from.blockY..to.blockY)
                for (z in from.blockZ..to.blockZ)
                    GameRecord.addBlock(Location(world, x.toDouble(), y.toDouble(), z.toDouble()))
    }
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
        event.player.openMainMenu()
}

@SubscribeEvent
private fun onDropItem(event: ItemSpawnEvent)
{
    event.isCancelled = true
}