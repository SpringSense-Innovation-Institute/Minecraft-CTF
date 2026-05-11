@file:Suppress("PackageDirectoryMismatch")

package moe.tachyon.ctf.plugin.auth

import moe.tachyon.ctf.plugin.MinecraftCtfPlugin
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
import java.util.*

/**
 * 线程安全的未登录玩家集合
 */
private val blockedUsers = Collections.synchronizedSet(mutableSetOf<String>())

/**
 * 监听玩家加入事件，如果玩家是管理员，则将其加入到未登录玩家集合中
 * 
 * @param event 玩家加入事件
 * @see PlayerJoinEvent
 * @see blockedUsers
 */
@SubscribeEvent(ignoreCancelled = true)
private fun onPlayerJoin(event: PlayerJoinEvent)
{
    if (event.player.isOp) blockedUsers += event.player.name
}

/**
 * 拦截未登录玩家的操作
 * 
 * @param player 玩家
 * @param event 事件
 * @see blockedUsers
 */
@Suppress("NOTHING_TO_INLINE")
private inline fun check(player: Player, event: Cancellable)
{
    if (player.name !in blockedUsers)
        return
    player.sendMessage("§c请先登录再进行操作！")
    event.isCancelled = true
}

/**
 * 监听玩家命令，如果命令前缀是 /login，则不拦截
 * 
 * @param event 玩家命令
 * @see PlayerCommandPreprocessEvent
 * @see blockedUsers
 */
@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
private fun onPlayerCommandPreprocess(event: PlayerCommandPreprocessEvent)
{
    if (event.message.removePrefix("/").startsWith("login "))
        return
    check(event.player, event)
}

/**
 * 监听玩家聊天，拦截未登录玩家的聊天
 * 
 * @param event 玩家聊天
 * @see AsyncPlayerChatEvent
 * @see blockedUsers
 */
@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onPlayerChat(event: AsyncPlayerChatEvent) =
    check(event.player, event)

/**
 * 监听玩家交互，拦截未登录玩家的交互
 * 
 * @param event 玩家交互
 * @see PlayerInteractEvent
 * @see blockedUsers
 */
@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onPlayerInteract(event: PlayerInteractEvent) =
    check(event.player, event)

/**
 * 监听实体伤害，拦截未登录玩家造成的伤害
 * 
 * @param event 实体伤害
 * @see EntityDamageByEntityEvent
 * @see blockedUsers
 */
@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onEntityDamageByEntity(event: EntityDamageByEntityEvent)
{
    if (event.damager !is Player) return
    check(event.damager as Player, event)
}

/**
 * 监听受到伤害，拦截未登录玩家受到的伤害
 * 
 * @param event 实体伤害
 * @see EntityDamageEvent
 * @see blockedUsers
 */
@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onEntityDamage(event: EntityDamageEvent): Unit =
    check(event.entity as? Player ?: return, event)

/**
 * 监听玩家传送，拦截未登录玩家非插件传送
 * 
 * @param event 玩家传送
 * @see PlayerTeleportEvent
 * @see blockedUsers
 */
@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onPlayerTeleport(event: PlayerTeleportEvent)
{
    if (event.cause != PlayerTeleportEvent.TeleportCause.PLUGIN)
        check(event.player, event)
}

/**
 * 监听玩家丢弃物品，拦截未登录玩家丢弃物品
 * 
 * @param event 玩家丢弃物品
 * @see PlayerDropItemEvent
 * @see blockedUsers
 */
@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onPlayerDropItem(event: PlayerDropItemEvent) =
    check(event.player, event)

/**
 * 监听实体捡起物品，拦截未登录玩家捡起物品
 * 
 * @param event 实体捡起物品
 * @see EntityPickupItemEvent
 * @see blockedUsers
 */
@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onEntityPickupItem(event: EntityPickupItemEvent): Unit =
    check(event.entity as? Player ?: return, event)

/**
 * 监听玩家移动，拦截未登录玩家的跨方块移动，不拦截视角移动
 * 
 * @param event 玩家移动
 * @see PlayerMoveEvent
 * @see blockedUsers
 */
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

/**
 * 监听玩家打开菜单，拦截未登录玩家打开菜单
 * 
 * @param event 打开菜单
 * @see InventoryOpenEvent
 * @see blockedUsers
 */
@SubscribeEvent(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onPlayerOpenMenu(event: InventoryOpenEvent): Unit =
    check(event.player as? Player ?: return, event)


/**
 * 注册登录命令（/login <password>），默认所有玩家都可以使用命令，密码正确则将玩家从未登录玩家集合中移除
 * 
 * @see simpleCommand
 * @see blockedUsers
 */
@Awake(LifeCycle.ENABLE)
private fun registerAuthListeners()
{
    simpleCommand("login", permissionDefault = PermissionDefault.TRUE)
    { sender, arg ->
        if (sender !is ProxyPlayer) return@simpleCommand
        val player = sender.cast<Player>()
        val password = MinecraftCtfPlugin.config.adminPassword
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
