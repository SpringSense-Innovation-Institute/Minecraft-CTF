package moe.tachyon.ctf.plugin

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
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
                Bukkit.getWorld("world"),
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
            sender.openMainMenu()
        }
    }
}