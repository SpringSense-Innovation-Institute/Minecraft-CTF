package moe.tachyon.ctf.plugin.bot

import moe.tachyon.ctf.plugin.GameWorld
import moe.tachyon.ctf.plugin.Group
import moe.tachyon.ctf.plugin.Group.Bot.*

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
            DEFAULT       -> DefaultBot(world)
            DEFAULT_JUMP  -> JumpBot(world)
            DEFAULT_SNEAK -> SneakBot(world)
            SIMPLE        -> SimpleBot(world)
            SIMPLE_JUMP   -> JumpSimpleBot(world)
        }
    }

    bots.forEach()
    {
        it.init()
    }
}