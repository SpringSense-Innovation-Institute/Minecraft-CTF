package moe.tachyon.ctf.plugin.bot

import moe.tachyon.ctf.plugin.GameWorld
import moe.tachyon.ctf.plugin.Group

fun createBots(
    world: GameWorld,
)
{
    val count = world.playerCountPerGroup
    val group = if (world.leftGroup.isBot) world.leftGroup else world.rightGroup
    if (group !is Group.Bot) return

    val bots: List<Bot> = (1..count).map()
    {
        when (group)
        {
            Group.Bot.DEFAULT -> DefaultBot(world)
            Group.Bot.JUMP_SPRINT -> JumpSpringBot(world)
        }
    }

    bots.forEach()
    {
        it.init()
    }
}