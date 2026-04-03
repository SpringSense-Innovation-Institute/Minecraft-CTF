package moe.tachyon.ctf.plugin

import org.bukkit.Location
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.simpleCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "ctf")
object Commands
{
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

    @CommandBody
    val menu = subCommand()
    {
        execute<Player>()
        { sender, _, _ ->
            sender.openMenu()
        }
    }

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

    @CommandBody
    val clearWorlds = subCommand()
    {
        execute<Player>()
        { sender, _, _ ->
            getGameWorlds().forEach { checkGameWorldRecycle(it.name) }
        }
    }
}

@Awake(LifeCycle.ENABLE)
fun registerMenuCommand()
{
    simpleCommand("ctfmenu")
    { sender, _ ->
        if (sender !is ProxyPlayer) return@simpleCommand
        val player = sender.cast<Player>()
        player.openMenu()
    }
}