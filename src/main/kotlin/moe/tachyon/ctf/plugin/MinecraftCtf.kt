package moe.tachyon.ctf.plugin

import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.platform.BukkitPlugin

object MinecraftCtfPlugin: Plugin()
{
    var leftGroup: Int = 1
        set(value)
        {
            field = value
            updateTeam()
        }
    var rightGroup: Int = 2
        set(value)
        {
            field = value
            updateTeam()
        }

    var gameStatus = GameStatus.NONE
        set(value)
        {
            field = value
            if (value == GameStatus.IN_GAME)
            {
                warning("游戏已开始，玩家分组将被冻结!")
            }
            else
            {
                info("游戏已结束，玩家分组已解冻!")
                updateTeam()
            }
        }

    var randomMap = false
    var playerCountPerGroup = 1

    val recordsFolder by lazy { BukkitPlugin.getInstance().dataFolder.resolve("records") }

    override fun onEnable()
    {
        info("Successfully running Minecraft-CTF-Plugin!")
    }
}

enum class GameStatus
{
    NONE,
    PREPARING,
    IN_GAME,
    ;

    fun inGame() = this == IN_GAME
}