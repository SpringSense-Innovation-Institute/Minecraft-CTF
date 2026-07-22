package moe.tachyon.ctf.plugin

import org.bukkit.Location
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.simpleCommand
import taboolib.common.platform.command.subCommand

/**
 * 注册CTF命令（/ctf <watch|menu|listWorlds|clearWorlds>）
 * 
 * @see Commands.watch
 * @see Commands.menu
 * @see Commands.listWorlds
 * @see Commands.clearWorlds
 */
@CommandHeader(name = "ctf")
object Commands
{
	/**
	 * 注册CTF观看命令（/ctf watch），传送到当前对战世界的观看位置
	 * 
	 * @see Player
	 * @see Location
	 * @see teleport
	 */
    @CommandBody
    val watch = subCommand()
    {
        execute<Player>()
        { sender, _, _ ->
            sender.teleport(Location(
                sender.world,
                0.5,
                40.0,
                35.0,
                180.0f,
                60.0f
            ))
        }
    }

	/**
	 * 注册CTF菜单命令（/ctf menu），打开CTF菜单
	 * 
	 * @see Player
	 * @see openMenu
	 */
    @CommandBody
    val menu = subCommand()
    {
        execute<Player>()
        { sender, _, _ ->
            sender.openMenu()
        }
    }

	/**
	 * 注册CTF列表命令（/ctf listWorlds），列出当前正在运行的游戏世界
	 * 
	 * @see Player
	 * @see getGameWorlds
	 * @see sendMessage
	 */
    @CommandBody
    val listWorlds = subCommand()
    {
        execute<Player>()
        { sender, _, _ ->
            val gameWorlds = getGameWorlds()
            if (gameWorlds.isEmpty())
            {
                sender.sendMessage("§c当前没有正在运行的游戏世界！")
                return@execute
            }
            sender.sendMessage("§a正在运行的游戏世界：")
            for (world in gameWorlds)
            {
                sender.sendMessage("§e- ${world.name} (${world.gameStatus})")
            }
        }
    }

	/**
	 * 注册CTF清空命令（/ctf clearWorlds），清空当前正在运行的游戏世界
	 * 
	 * @see Player
	 * @see getGameWorlds
	 * @see checkGameWorldRecycle
	 */
    @CommandBody
    val clearWorlds = subCommand()
    {
        execute<Player>()
        { sender, _, _ ->
            getGameWorlds().forEach { checkGameWorldRecycle(it.name) }
        }
    }
}

/**
 * 注册CTF菜单命令（/ctfmenu），打开CTF菜单
 * 
 * @see ProxyPlayer
 * @see Player
 * @see openMenu
 */
@Awake(LifeCycle.ENABLE)
fun registerMenuCommand()
{
    simpleCommand("ctfmenu", permissionDefault = PermissionDefault.TRUE)
    { sender, _ ->
        if (sender !is ProxyPlayer) return@simpleCommand
        val player = sender.cast<Player>()
        player.openMenu()
    }
}

@Awake(LifeCycle.ENABLE)
fun registerPerformanceModeCommand()
{
    simpleCommand("performance_mode", permissionDefault = PermissionDefault.TRUE)
    { sender, args ->
        if (args.isEmpty())
        {
            sender.sendMessage("§a当前表演模式：§e$performanceMode")
            return@simpleCommand
        }
        val mode = args[0].toBooleanStrictOrNull()
        if (mode == null)
        {
            sender.sendMessage("§c参数错误！请使用 true 或 false")
            return@simpleCommand
        }
        performanceMode = mode
        sender.sendMessage("§a已将表演模式设置为：§e$performanceMode")
    }
}
