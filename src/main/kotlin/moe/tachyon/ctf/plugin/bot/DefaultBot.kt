package moe.tachyon.ctf.plugin.bot

import moe.tachyon.ctf.plugin.*
import moe.tachyon.ctf.plugin.GameWorld.MapElement
import org.bukkit.Location
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

open class DefaultBot(world: GameWorld): Bot(world)
{
    private var path: List<Pair<Int, Int>>? = null
    override fun tick(): PlayerInput
    {
        val p = player ?: return PlayerInput.EMPTY
        val map = getMap()
        val target = getTarget(map)
        val curLocation = p.location

        var curX = curLocation.blockX + 24
        var curZ = curLocation.blockZ + 36

        // 如果当前处于不可走的格子，说明刚好站在了栅栏/逻辑上不可走的位置（如敌人旁边），尝试移动到附近的可走位置
        if (!map[curX][curZ].passable())
        {
            val adjacentBlocks = mutableListOf<Triple<Int, Int, Double>>()

            for (dx in -5..5)
            {
                for (dz in -5..5)
                {
                    val x = curX + dx
                    val z = curZ + dz
                    if (x !in map.indices || z !in map[x].indices || !map[x][z].passable())
                        continue
                    val loc = Location(curLocation.world, x - 24 + 0.5, curLocation.y, z - 36 + 0.5)
                    val distance = curLocation.distance(loc)
                    adjacentBlocks.add(Triple(x, z, distance))
                }
            }

            val res = adjacentBlocks.minByOrNull { it.third } ?: return PlayerInput.EMPTY

            curX = res.first
            curZ = res.second

            val nextLocation = Location(
                curLocation.world,
                curX - 24 + 0.5,
                curLocation.y,
                curZ - 36 + 0.5
            )
            val location = curLocation.clone()
            location.direction = nextLocation.toVector().subtract(curLocation.toVector())
            return PlayerInput(yaw = location.yaw, pitch = location.pitch, w = true)
        }

        val path = path
            ?.dropWhile { abs(it.first - curX) > 1 && abs(it.second - curZ) > 1 } // 如果路径上有当前坐标之前的点，丢弃它们
            ?.dropWhile { it == (curX to curZ) } // 如果路径上有当前坐标，丢弃它
            ?.takeIf { it.isNotEmpty() } // 如果路径上去掉当前坐标之后还有点，继续使用这个路径
            ?.takeIf { target.any(it::contains) } // 如果路径上还有目标点，继续使用这个路径
            ?.takeIf { it.all { (x, z) -> map[x][z].passable() || (x to z) in target } } // 全部可达
            ?: run()
            {
                findPath(
                    map,
                    curX to curZ,
                    target
                )
            } ?: return PlayerInput.EMPTY

        this.path = path

        val nextLocation = Location(
            curLocation.world,
            path.first().first - 24 + 0.5,
            curLocation.y,
            path.first().second - 36 + 0.5
        )

        val location = curLocation.clone()
        location.direction = nextLocation.toVector().subtract(curLocation.toVector())

        return PlayerInput(yaw = location.yaw, pitch = location.pitch, w = true)
    }

    protected open fun getTarget(map: List<List<MapElement>>): List<Pair<Int, Int>>
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

    private fun findPath(
        map: List<List<MapElement>>,
        start: Pair<Int, Int>,
        targets: List<Pair<Int, Int>>
    ): List<Pair<Int, Int>>?
    {
        if (targets.isEmpty()) return null

        val priorityQueue = PriorityQueue<Triple<Double, Pair<Int, Int>, Pair<Int, Int>?>>(compareBy()
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

        val from = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>>()

        priorityQueue.add(Triple(0.0, start, null))

        while (priorityQueue.isNotEmpty())
        {
            val (cost, current, lastStep) = priorityQueue.poll()
            if (current in from) continue
            if (lastStep != null) from[current] = lastStep
            if (current in targets)
            {
                val path = mutableListOf(current)
                var cur: Pair<Int, Int>? = current
                while (cur != start)
                {
                    cur = from[cur] ?: break
                    if (cur != start) path.add(cur)
                }
                return path.reversed()
            }

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
                priorityQueue.add(Triple(newCost, next, current))
            }
        }
        return null
    }
}

open class SimpleBot(world: GameWorld): DefaultBot(world)
{
    override fun getMap(): List<List<MapElement>>
    {
        val map = super.getMap().map { it.toMutableList() }.toMutableList()
        val enemies = if (world.leftGroup.isBot) world.rightGroupBukkitPlayers() else world.leftGroupBukkitPlayers()
        enemies.forEach()
        { enemy ->
            val x = enemy.location.blockX + 24
            val z = enemy.location.blockZ + 36

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

class TrivialBot(world: GameWorld): DefaultBot(world)
{
    override fun tick(): PlayerInput
    {
        val p = player ?: return PlayerInput.EMPTY
        val enemies = if (world.leftGroup.isBot) world.rightGroupBukkitPlayers() else world.leftGroupBukkitPlayers()
        val res = (enemies + world.getSilverfish()).minByOrNull { it.location.distance(p.location) }
        return super.tick().copy(jump = true, sprint = true, attack = res?.uniqueId)
    }

    override fun getMap(): List<List<MapElement>>
    {
        val map = super.getMap().map { it.toMutableList() }.toMutableList()
        val enemies = (if (world.leftGroup.isBot) world.rightGroupBukkitPlayers() else world.leftGroupBukkitPlayers())
        val teammates = (if (world.leftGroup.isBot) world.leftGroupBukkitPlayers() else world.rightGroupBukkitPlayers()).filter { it != player }
        val bugs = world.getSilverfish()

        // 所有需要躲避的东西
        val avoid = mutableListOf<Triple<Int, Int, Int>>()

        enemies.forEach()
        {
            if (it.equipment?.helmet.isParasiticHelmet())
                avoid += Triple(it.location.blockX + 24, it.location.blockZ + 36, 3)
            else if (world.leftGroup.isBot && LocationStatus.IN_RIGHT_FIELD.contains(it.location))
                avoid += Triple(it.location.blockX + 24, it.location.blockZ + 36, 3)
            else if (world.rightGroup.isBot && LocationStatus.IN_LEFT_FIELD.contains(it.location))
                avoid += Triple(it.location.blockX + 24, it.location.blockZ + 36, 3)
        }

        teammates.forEach()
        {
            if (it.equipment?.helmet.isParasiticHelmet())
                avoid += Triple(it.location.blockX + 24, it.location.blockZ + 36, 3)
        }

        avoid.addAll(bugs.map { Triple(it.location.blockX + 24, it.location.blockZ + 36, 3) })

        avoid.forEach()
        { (x, z, r) ->
            for (dx in -r..r)
            {
                for (dz in -r..r)
                {
                    if (x + dx in map.indices && z + dz in map[x + dx].indices)
                        map[x + dx][z + dz] = MapElement.OBSTACLE
                }
            }
        }
        return map
    }

    override fun getTarget(map: List<List<MapElement>>): List<Pair<Int, Int>>
    {
        val enemies =
            if (world.leftGroup.isBot) world.rightGroupBukkitPlayers()
            else world.leftGroupBukkitPlayers()

        val targets = super.getTarget(map).toMutableList()
        enemies.forEach()
        {
            if (it.equipment?.helmet.isParasiticHelmet())
                return@forEach
            if (world.leftGroup.isBot && LocationStatus.IN_LEFT_FIELD.contains(it.location))
                targets.add(it.location.blockX + 24 to it.location.blockZ + 36)
            else if (world.rightGroup.isBot && LocationStatus.IN_RIGHT_FIELD.contains(it.location))
                targets.add(it.location.blockX + 24 to it.location.blockZ + 36)
        }
        return targets
    }
}