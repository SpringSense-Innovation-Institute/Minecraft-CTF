@file:Suppress("PackageDirectoryMismatch")

package moe.tachyon.ctf.plugin.auth

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.*
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.simpleCommand
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.BukkitPlugin
import java.util.*

private val blockedUsers = Collections.synchronizedSet(mutableSetOf<String>())

@SubscribeEvent(ignoreCancelled = true)
private fun onPlayerJoin(event: PlayerJoinEvent)
{
    if (event.player.isOp) blockedUsers += event.player.name
}

@Suppress("NOTHING_TO_INLINE")
private inline fun check(player: Player, event: Cancellable)
{
    if (player.name !in blockedUsers)
        return
    player.sendMessage("§c请先登录再进行操作！")
    event.isCancelled = true
}

@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
private fun onPlayerCommandPreprocess(event: PlayerCommandPreprocessEvent)
{
    if (event.message.removePrefix("/").startsWith("login "))
        return
    check(event.player, event)
}

@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onPlayerChat(event: AsyncPlayerChatEvent) =
    check(event.player, event)

@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onPlayerInteract(event: PlayerInteractEvent) =
    check(event.player, event)

@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onEntityDamageByEntity(event: EntityDamageByEntityEvent)
{
    if (event.damager !is Player) return
    check(event.damager as Player, event)
}

@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onEntityDamage(event: EntityDamageEvent): Unit =
    check(event.entity as? Player ?: return, event)

@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onPlayerTeleport(event: PlayerTeleportEvent)
{
    if (event.cause != PlayerTeleportEvent.TeleportCause.PLUGIN)
        check(event.player, event)
}

@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onPlayerDropItem(event: PlayerDropItemEvent) =
    check(event.player, event)

@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onEntityPickupItem(event: EntityPickupItemEvent): Unit =
    check(event.entity as? Player ?: return, event)

@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onPlayerMove(event: PlayerMoveEvent)
{
    val from = event.from
    val to = event.to ?: return
    val fx = from.blockX
    val fz = from.blockZ
    val tx = to.blockX
    val tz = to.blockZ
    if (fx == tx && fz == tz) return
    check(event.player, event)
}

@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onPlayerOpenMenu(event: InventoryOpenEvent): Unit =
    check(event.player as? Player ?: return, event)

@Awake(LifeCycle.ENABLE)
private fun registerAuthListeners()
{
    simpleCommand("login", permissionDefault = PermissionDefault.TRUE)
    { sender, arg ->
        if (sender !is ProxyPlayer) return@simpleCommand
        val player = sender.cast<Player>()
        val passwordFile = BukkitPlugin.getInstance().dataFolder.resolve("passwords.txt")
        if (!passwordFile.exists())
        {
            passwordFile.parentFile.mkdirs()
            passwordFile.writeText("password123")
        }
        val password = passwordFile.readText().trim()
        if (arg.any { it == password })
        {
            blockedUsers -= player.name
            player.sendMessage("§a登录成功！")
        }
        else
        {
            player.sendMessage("§c密码错误！")
        }
    }
}
