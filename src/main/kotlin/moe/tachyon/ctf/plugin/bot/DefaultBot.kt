package moe.tachyon.ctf.plugin.bot

import moe.tachyon.ctf.plugin.GameWorld
import moe.tachyon.ctf.plugin.isBanner
import org.bukkit.Location
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

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
        val priorityQueue = PriorityQueue<Triple<Double, Pair<Int, Int>, Pair<Int, Int>>>(compareBy()
        {
            it.first + abs(it.second.first - end.first) + abs(it.second.second - end.second)
        })

        val passable = { x: Int, z: Int ->
            x in map.indices &&
            z in map[x].indices &&
            (map[x][z].passable() || Pair(x, z) == end)
        }

        val directions = listOf(
            Pair(1, 0),
            Pair(-1, 0),
            Pair(0, 1),
            Pair(0, -1),

            Pair(-1, -1),
            Pair(-1, 1),
            Pair(1, -1),
            Pair(1, 1),
        )

        priorityQueue.add(Triple(0.0, start, 0 to 0))

        val visited = mutableSetOf<Pair<Int, Int>>()
        while (priorityQueue.isNotEmpty())
        {
            val (cost, current, firstStep) = priorityQueue.poll()
            if (current in visited) continue
            visited.add(current)
            if (current == end) return firstStep

            for (d in directions)
            {
                if (d.first != 0)
                    if (!passable(current.first + d.first, current.second))
                        continue
                if (d.second != 0)
                    if (!passable(current.first, current.second + d.second))
                        continue
                if (!passable(current.first + d.first, current.second + d.second))
                    continue

                val newCost = cost + sqrt((d.first * d.first + d.second * d.second).toDouble())
                val next = Pair(current.first + d.first, current.second + d.second)
                priorityQueue.add(Triple(newCost, next, if (firstStep == Pair(0, 0)) d else firstStep))
            }
        }

        return 0 to 0
    }
}

class JumpSpringBot(world: GameWorld): DefaultBot(world)
{
    override fun tick(): PlayerInput = super.tick().copy(jump = true, sprint = true)
}