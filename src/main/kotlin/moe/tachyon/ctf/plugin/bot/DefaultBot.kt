package moe.tachyon.ctf.plugin.bot

import moe.tachyon.ctf.plugin.GameWorld
import moe.tachyon.ctf.plugin.GameWorld.MapElement
import moe.tachyon.ctf.plugin.isBanner
import org.bukkit.Bukkit
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
        val target = getTarget(map)
        val curLocation = p.location

        var curX = curLocation.blockX + 24
        var curZ = curLocation.blockZ + 36

        if (!map[curX][curZ].passable())
        {
            val blocks = listOf(
                curLocation.x - curLocation.x.toInt() to  (curX - 1 to curZ),
                curLocation.z - curLocation.z.toInt() to  (curX to curZ - 1),
                1 - (curLocation.x - curLocation.x.toInt()) to  (curX + 1 to curZ),
                1 - (curLocation.z - curLocation.z.toInt()) to  (curX to curZ + 1),
            )

            val res = blocks
                .filter { it.second.first in map.indices && it.second.second in map[it.second.first].indices }
                .filter { map[it.second.first][it.second.second].passable() }
                .minByOrNull { it.first } ?: return PlayerInput.EMPTY

            curX = res.second.first
            curZ = res.second.second
        }

        val step = findPath(
            map,
            curX to curZ,
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

    private fun getTarget(map: List<List<MapElement>>): List<Pair<Int, Int>>
    {
        val isLeft = world.leftGroup.isBot

        if (player?.inventory?.helmet?.type?.isBanner() == true)
        {
            return map.flatMapIndexed()
            { x, row ->
                row.mapIndexedNotNull()
                { z, element ->
                    if (element == if (isLeft) MapElement.LEFT_TARGET else MapElement.RIGHT_TARGET)
                        Pair(x, z)
                    else null
                }
            }
        }

        return map.flatMapIndexed()
        { x, row ->
            row.mapIndexedNotNull()
            { z, element ->
                if (element == if (isLeft) MapElement.RIGHT_FLAG else MapElement.LEFT_FLAG)
                    Pair(x, z)
                else null
            }
        }
    }

    // 使用A*，返回(1,0)/(-1,0)/(0,1)/(0,-1)之一，表示现在的位置应该向哪个方向移动
    private fun findPath(
        map: List<List<MapElement>>,
        start: Pair<Int, Int>,
        targets: List<Pair<Int, Int>>
    ): Pair<Int, Int>
    {
        if (targets.isEmpty()) return 0 to 0

        val priorityQueue = PriorityQueue<Triple<Double, Pair<Int, Int>, Pair<Int, Int>>>(compareBy()
        {
            it.first + targets.minOf()
            { target ->
                abs(it.second.first - target.first) + abs(it.second.second - target.second)
            }
        })

        val passable = { x: Int, z: Int ->
            x in map.indices &&
            z in map[x].indices &&
            (map[x][z].passable() || Pair(x, z) in targets)
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
            if (current in targets) return firstStep

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

open class SimpleBot(world: GameWorld): DefaultBot(world)
{
    override fun getMap(): List<List<MapElement>>
    {
        val map = super.getMap().map { it.toMutableList() }.toMutableList()
        val enemies = if (world.leftGroup.isBot) world.rightGroupPlayers() else world.leftGroupPlayers()
        enemies.forEach()
        { enemy ->
            val location = Bukkit.getPlayer(enemy)?.location ?: return@forEach
            val x = location.blockX + 24
            val z = location.blockZ + 36

            for (dx in -1..1)
            {
                for (dz in -1..1)
                {
                    if (x + dx in map.indices && z + dz in map[x + dx].indices)
                        map[x + dx][z + dz] = MapElement.OBSTACLE
                }
            }
        }
        return map
    }
}

class JumpBot(world: GameWorld): DefaultBot(world)
{
    override fun tick(): PlayerInput = super.tick().copy(jump = true, sprint = true)
}

class JumpSimpleBot(world: GameWorld): SimpleBot(world)
{
    override fun tick(): PlayerInput = super.tick().copy(jump = true, sprint = true)
}

class SneakBot(world: GameWorld): DefaultBot(world)
{
    override fun tick(): PlayerInput = super.tick().copy(sneak = true)
}