package moe.tachyon.ctf.plugin.bot

import moe.tachyon.ctf.plugin.GameWorld
import moe.tachyon.ctf.plugin.isBanner
import org.bukkit.Location
import org.bukkit.util.Vector
import taboolib.common.platform.function.onlinePlayers
import taboolib.module.chat.colored
import java.util.PriorityQueue
import kotlin.math.abs

open class DefaultBot(world: GameWorld): Bot(world)
{
    override fun tick(): PlayerInput
    {
        val p = player ?: return PlayerInput.EMPTY
        val map = getMap()
        val target = getTarget(map) ?: return PlayerInput.EMPTY
        val curLocation = p.location

        val step = findPath(
            map,
            Pair(curLocation.blockX + 24, curLocation.blockZ + 36),
            target
        )

        val nextLocation = Location(
            curLocation.world,
            curLocation.blockX + step.first + 0.5,
            curLocation.y,
            curLocation.blockZ + step.second + 0.5
        )

        val location = curLocation.clone()
        location.direction = nextLocation.toVector().subtract(curLocation.toVector())

        return PlayerInput(
            yaw = location.yaw,
            pitch = location.pitch,
            w = true,
            a = false,
            s = false,
            d = false,
            jump = false,
            sneak = false,
            sprint = false,
            fly = false,
            attack = null,
        )
    }

    private fun getTarget(map: List<List<MapElement>>): Pair<Int, Int>?
    {
        val isLeft = world.leftGroup.isBot

        if (player?.inventory?.helmet?.type?.isBanner() == true)
        {
            for (x in map.indices)
            {
                for (z in map[x].indices)
                {
                    if (map[x][z] == if (isLeft) MapElement.LEFT_TARGET else MapElement.RIGHT_TARGET)
                        return Pair(x, z)
                }
            }
        }

        for (x in map.indices)
        {
            for (z in map[x].indices)
            {
                if (map[x][z] == if (isLeft) MapElement.RIGHT_FLAG else MapElement.LEFT_FLAG)
                    return Pair(x, z)
            }
        }

        return null
    }

    // 使用A*，返回(1,0)/(-1,0)/(0,1)/(0,-1)之一，表示现在的位置应该向哪个方向移动
    private fun findPath(map: List<List<MapElement>>, start: Pair<Int, Int>, end: Pair<Int, Int>): Pair<Int, Int>
    {
        val priorityQueue = PriorityQueue<Triple<Int, Pair<Int, Int>, Pair<Int, Int>>>(compareBy()
        {
            it.first + abs(it.second.first - end.first) + abs(it.second.second - end.second)
        })

        val passable = { x: Int, z: Int ->
            x in map.indices &&
            z in map[x].indices &&
            (map[x][z].passable() || Pair(x, z) == end)
        }

        if (passable(start.first + 1, start.second))
            priorityQueue.add(Triple(1, Pair(start.first + 1, start.second), 1 to 0))
        if (passable(start.first - 1, start.second))
            priorityQueue.add(Triple(1, Pair(start.first - 1, start.second), -1 to 0))
        if (passable(start.first, start.second + 1))
            priorityQueue.add(Triple(1, Pair(start.first, start.second + 1), 0 to 1))
        if (passable(start.first, start.second - 1))
            priorityQueue.add(Triple(1, Pair(start.first, start.second - 1), 0 to -1))

        val visited = mutableSetOf<Pair<Int, Int>>()
        while (priorityQueue.isNotEmpty())
        {
            val (cost, current, firstStep) = priorityQueue.poll()
            if (current in visited) continue
            visited.add(current)
            if (current == end) return firstStep

            if (passable(current.first + 1, current.second))
                priorityQueue.add(Triple(cost + 1, Pair(current.first + 1, current.second), firstStep))
            if (passable(current.first - 1, current.second))
                priorityQueue.add(Triple(cost + 1, Pair(current.first - 1, current.second), firstStep))
            if (passable(current.first, current.second + 1))
                priorityQueue.add(Triple(cost + 1, Pair(current.first, current.second + 1), firstStep))
            if (passable(current.first, current.second - 1))
                priorityQueue.add(Triple(cost + 1, Pair(current.first, current.second - 1), firstStep))
        }

        return 0 to 0
    }
}

class JumpSpringBot(world: GameWorld): DefaultBot(world)
{
    override fun tick(): PlayerInput = super.tick().copy(jump = true, sprint = true)
}