@file:Suppress("PackageDirectoryMismatch")

package moe.tachyon.ctf.plugin.whitelist

import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.simpleCommand
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info

private var whiteListMode = false

@SubscribeEvent
private fun onPlayerJoin(event: PlayerJoinEvent)
{
    val address = event.player.address ?: return
    val host = address.address
    if (
        whiteListMode &&
        !event.player.isOp &&
        host.hostAddress !in listOf("127.0.0.1", "::1", "0:0:0:0:0:0:0:1") &&
        !event.player.name.startsWith("CTF-Bot-"))
    {
        info("玩家 ${event.player.name} 尝试连接，但其 IP 地址 ${host.hostAddress} 不在白名单中，已拒绝连接")
        event.player.kickPlayer("您不在白名单名单中")
    }
}

@Awake(LifeCycle.ENABLE)
fun registerMenuCommand()
{
    simpleCommand("whitelist_mode", permissionDefault = PermissionDefault.TRUE)
    { sender, args ->
        if (args.isEmpty())
        {
            sender.sendMessage("§a当前白名单模式：§e$whiteListMode")
            return@simpleCommand
        }
        val mode = args[0].toBooleanStrictOrNull()
        if (mode == null)
        {
            sender.sendMessage("§c参数错误！请使用 true 或 false")
            return@simpleCommand
        }
        whiteListMode = mode
        sender.sendMessage("§a已将白名单模式设置为：§e$whiteListMode")
    }
}