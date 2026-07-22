@file:OptIn(ExperimentalAtomicApi::class)

package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.GameWorld.MapElement
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.Bisected
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.MushroomCow
import org.bukkit.entity.Silverfish
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import taboolib.common.util.randomDouble
import taboolib.module.ai.SimpleAi
import taboolib.module.ai.addTargetAi
import taboolib.module.ai.clearGoalAi
import taboolib.module.ai.clearTargetAi
import taboolib.module.navigation.isWater
import java.util.*
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

private class MapGenerationException(message: String): IllegalStateException(message)

private fun horizontalVelocity(from: Location, target: Location, speed: Double): Vector?
{
    val dx = target.x - from.x
    val dz = target.z - from.z
    val length = kotlin.math.hypot(dx, dz)
    if (length < 1.0e-6) return null
    return Vector(dx / length * speed, 0.0, dz / length * speed)
}

private fun GameWorld.randomLocations(
    xRange: IntRange,
    zRange: IntRange,
    count: Int,
    label: String,
    predicate: (Location) -> Boolean,
): List<Location>
{
    if (count <= 0) return emptyList()

    val candidates = xRange.flatMap()
    { x ->
        zRange.mapNotNull()
        { z ->
            val location = Location(bukkitWorld(), x.toDouble(), 0.0, z.toDouble())
            if (predicate(location)) location else null
        }
    }.shuffled()

    if (candidates.size < count)
        throw MapGenerationException("$label 可用位置不足，需要 $count 个，实际只有 ${candidates.size} 个")

    return candidates.take(count)
}

fun GameWorld.clearMap()
{
    for (x in -24..24)
        for (z in -36..36)
        {
            val location = Location(bukkitWorld(), x.toDouble(), 0.0, z.toDouble())
            if (location.block.type in listOf(Material.GOLD_BLOCK, Material.WAXED_COPPER_BLOCK, Material.OAK_SIGN, Material.RED_BANNER, Material.BLUE_BANNER))
            {
                location.block.type = Material.GRASS_BLOCK
                location.clone().add(0.0, 3.0, 0.0).block.type = Material.AIR
                location.clone().add(0.0, 2.0, 0.0).block.type = Material.AIR
                location.clone().add(0.0, 1.0, 0.0).block.type = Material.AIR
            }
            else if (location.add(0.0, 1.0, 0.0).block.type == Material.OAK_LOG)
            {
                for (x in -1..1)
                    for (y in 0..6)
                        for (z in -1..1)
                            location.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block.type = Material.AIR
            }
            else if (location.clone().add(0.0, 1.0, 0.0).block.type == Material.PITCHER_PLANT)
            {
                location.clone().add(0.0, 2.0, 0.0).block.type = Material.AIR
                location.clone().add(0.0, 1.0, 0.0).block.type = Material.AIR
            }
        }

    val box = BoundingBox(-24.0, -64.0, -36.0, 24.0, 10.0, 36.0)
    bukkitWorld()?.getNearbyEntities(box)?.forEach()
    {
        if (it.type != EntityType.PLAYER)
            it.remove()
    }
}

private fun Location.isEmpty() =
    block.type == Material.GRASS_BLOCK &&
    clone().add(0.0, 1.0, 0.0).block.type.isAir &&
    clone().add(0.0, 2.0, 0.0).block.type.isAir

fun GameWorld.generateMap()
{
    val leftFlags = mutableListOf<Pair<Int, Int>>()
    val leftTarget = mutableListOf<Pair<Int, Int>>()
    val rightFlags = mutableListOf<Pair<Int, Int>>()
    val rightTarget = mutableListOf<Pair<Int, Int>>()

    if (!randomMap) // 固定地图生成
    {
        for (z in 6..34 step 4)
        {
            leftFlags += Pair(-22, -z)
            rightFlags += Pair(22, -z)
            leftTarget += Pair(-22, z)
            rightTarget += Pair(22, z)
        }
    }
    else // 随机地图生成
    {
        val generate: (IntRange, IntRange) -> List<Pair<Int, Int>> = { xRange, zRange ->
            randomLocations(xRange, zRange, 8, "旗帜/目标点")
            { location ->
                val x = location.blockX
                val z = location.blockZ
                location.isEmpty() && listOf(leftTarget, rightTarget, leftFlags, rightFlags).all()
                { list ->
                    list.none { it.first == x && it.second == z }
                }
            }.map { it.blockX to it.blockZ }
        }
        leftFlags += generate((-24..-12), (-36..36))
        rightFlags += generate((12..24), (-36..36))
        leftTarget += generate((-24..-12), (-36..36))
        rightTarget += generate((12..24), (-36..36))
    }

    for (target in leftTarget + rightTarget)
        Location(bukkitWorld(), target.first.toDouble(), 0.0, target.second.toDouble()).block.type = Material.GOLD_BLOCK

    for (flag in leftFlags + rightFlags)
    {
        Location(bukkitWorld(), flag.first.toDouble(), 0.0, flag.second.toDouble()).block.type = Material.WAXED_COPPER_BLOCK
        Location(bukkitWorld(), flag.first.toDouble(), 1.0, flag.second.toDouble()).block.type = Material.OAK_FENCE
    }

    for (flag in leftFlags)
        Location(bukkitWorld(), flag.first.toDouble(), 2.0, flag.second.toDouble()).block.type = Material.RED_BANNER

    for (flag in rightFlags)
        Location(bukkitWorld(), flag.first.toDouble(), 2.0, flag.second.toDouble()).block.type = Material.BLUE_BANNER

    // 生成树
    fun Location.hasTreeSpace(): Boolean
    {
        for (dx in -1..1)
            for (dz in -1..1)
                if (!clone().add(dx.toDouble(), 0.0, dz.toDouble()).isEmpty())
                    return false
        return true
    }

    val treeLocations = randomLocations(1..24, -36..36, 5, "右半场树") { it.hasTreeSpace() } +
        randomLocations(-24..-1, -36..36, 5, "左半场树") { it.hasTreeSpace() }
    treeLocations.forEach()
    { location ->
        val high = (0..3).random()

        for (dx in -1..1)
            for (dz in -1..1)
                for (y in high + 1 .. high + 3)
                    location.clone().add(dx.toDouble(), y.toDouble(), dz.toDouble()).block.type = Material.OAK_LEAVES

        for (y in 1..high+1)
            location.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.OAK_LOG
    }

    // 生成pitcher plant
    val pitcherLocations = randomLocations(1..24, -36..36, 5, "右半场 pitcher plant") { it.isEmpty() } +
        randomLocations(-24..-1, -36..36, 5, "左半场 pitcher plant") { it.isEmpty() }
    pitcherLocations.forEach()
    { location ->
        location.clone().add(0.0, 1.0, 0.0).block.blockData =
            Bukkit.createBlockData(Material.PITCHER_PLANT).let { it as Bisected }.also { it.half = Bisected.Half.BOTTOM }

        location.clone().add(0.0, 2.0, 0.0).block.blockData =
            Bukkit.createBlockData(Material.PITCHER_PLANT).let { it as Bisected }.also { it.half = Bisected.Half.TOP }
    }

    // 生成哞菇
    val mushroomLocations = randomLocations(1..24, -36..36, playerCountPerGroup, "右半场哞菇") { it.isEmpty() } +
        randomLocations(-24..-1, -36..36, playerCountPerGroup, "左半场哞菇") { it.isEmpty() }
    mushroomLocations.forEach()
    { location ->
        val e = location.world!!.spawnEntity(location.add(0.0, 1.0, 0.0), EntityType.MOOSHROOM) as MushroomCow
        e.variant = if (randomDouble() < 0.5) MushroomCow.Variant.BROWN else MushroomCow.Variant.RED

        if (randomDouble() < 0.5)
            e.setBaby()
        else
            e.setAdult()

        e.clearGoalAi()
        e.clearTargetAi()
        e.addTargetAi(CowAI(this, e), 1)
    }

    // 生成蠹虫
    val bugCount = AtomicInt(0)
    val silverfishLocations = randomLocations(1..24, -36..36, 1, "右半场蠹虫") { it.isEmpty() } +
        randomLocations(-24..-1, -36..36, 1, "左半场蠹虫") { it.isEmpty() }
    silverfishLocations.forEach()
    { location ->
        bugCount.incrementAndFetch()

        val e = location.world!!.spawnEntity(location.add(0.0, 1.0, 0.0), EntityType.SILVERFISH) as Silverfish
        e.clearGoalAi()
        e.clearTargetAi()
        e.addTargetAi(SilverfishAI(this, e, bugCount), 1)
    }

    // 生成发光鱿鱼
    randomLocations(0..0, -36..36, 5, "发光鱿鱼")
    {
        Location(bukkitWorld(), 0.5, -1.0, it.blockZ.toDouble()).block.type.isWater()
    }.forEach()
    {
        val location = Location(bukkitWorld(), 0.5, -1.0, it.blockZ.toDouble())
        location.world!!.spawnEntity(location, EntityType.GLOW_SQUID)
    }

    // 生成蜂蜜块
    val honeyLocations = randomLocations(1..24, -36..36, 8, "右半场蜂蜜块") { it.isEmpty() } +
        randomLocations(-24..-1, -36..36, 8, "左半场蜂蜜块") { it.isEmpty() }
    honeyLocations.forEach()
    { location ->
        location.block.type = Material.HONEY_BLOCK
        // 让蜂蜜块随机蔓延
        val list = mutableListOf(location.clone())
        repeat(Random.nextInt(9, 20))
        {
            val adjacent = list.flatMap()
            {
                listOf(
                    it.clone().add(1.0, 0.0, 0.0),
                    it.clone().add(-1.0, 0.0, 0.0),
                    it.clone().add(0.0, 0.0, 1.0),
                    it.clone().add(0.0, 0.0, -1.0)
                )
            }.filter { it.isEmpty() }

            val loc = adjacent.randomOrNull() ?: return@repeat
            loc.block.type = Material.HONEY_BLOCK
            list += loc
        }
    }
}

private class CowAI(
    world: GameWorld,
    val cow: MushroomCow,
): MapEntity(world, cow)
{
    private var sprinting = false
    private var cooldown = false

    override fun shouldExecute(): Boolean = world.gameStatus.inGame()

    override fun continueExecute(): Boolean = world.gameStatus.inGame()

    override fun resetTask() = Unit

    override fun startTask() = Unit

    override fun updateTask()
    {
        val target = world.getBukkitPlayers()
            .asSequence()
            .filter { world.getPlayerTeam(it.name) != PlayerTeam.NONE }
            .map { it to it.location }
            .filter { it.second.world == cow.world }
            .filter { it.first.world == cow.world }
            .toList()

        val nearest = target.minByOrNull { it.second.distanceSquared(cow.location) } ?: return

        if (!sprinting && nearest.second.distanceSquared(cow.location) < 16.0 && !cooldown)
        {
            sprinting = true
        }
        else if (nearest.second.distanceSquared(cow.location) >= 25.0)
        {
            sprinting = false
            cooldown = false
        }

        if (sprinting) cow.velocity = findPath(target.map { it.second.blockX + 24 to it.second.blockZ + 36 }, 0.66)
        else cow.velocity = findPath(target.map { it.second.blockX + 24 to it.second.blockZ + 36 }, 0.22)
        val tmp = cow.location.clone().setDirection(cow.velocity)
        cow.setRotation(tmp.yaw, tmp.pitch)

        if (nearest.second.distanceSquared(cow.location) < 1.0 && !cooldown)
        {
            horizontalVelocity(cow.location, nearest.second, 0.8)?.let { cow.velocity = it }
            cooldown = true
            sprinting = false
        }
    }
}

private class SilverfishAI(
    world: GameWorld,
    val bug: Silverfish,
    val bugCount: AtomicInt,
): MapEntity(world, bug)
{
    private var inPitcherPlant = false
    override fun shouldExecute(): Boolean = world.gameStatus.inGame() && bug.isValid
    override fun continueExecute(): Boolean = world.gameStatus.inGame() && bug.isValid
    override fun resetTask() = Unit
    override fun startTask() = Unit
    override fun updateTask()
    {
        if (bug.location.block.type != Material.PITCHER_PLANT && inPitcherPlant) inPitcherPlant = false
        if (bug.location.block.type == Material.PITCHER_PLANT && !inPitcherPlant)
        {
            inPitcherPlant = true
            if (randomDouble() < 0.5 && bugCount.load() > 2)
            {
                bugCount.decrementAndFetch()
                bug.remove()
                return
            }
        }

        val target = world.getPlayers()
            .asSequence()
            .filter { world.getPlayerTeam(it) != PlayerTeam.NONE }
            .mapNotNull { Bukkit.getPlayer(it) }
            .map { it to it.location.clone().apply { y = bug.location.y } }
            .filter { it.second.world == bug.world }
            .filterNot { it.first.inventory.helmet.isParasiticHelmet() }
            .filter { it.first.world == bug.world }
            .toList()

        bug.velocity = findPath(target.map { it.second.blockX + 24 to it.second.blockZ + 36 }, 0.15)
        val tmp = bug.location.clone().setDirection(bug.velocity)
        bug.setRotation(tmp.yaw, tmp.pitch)

        target
            .minByOrNull { it.second.distanceSquared(bug.location) }
            ?.let()
            { (player, location) ->
                if (location.distanceSquared(bug.location) < 0.64)
                    onPlayerParasitic(world, player)
                else if (location.distanceSquared(bug.location) < 4)
                    horizontalVelocity(bug.location, location, 0.4)?.let { bug.velocity = it }
            }
    }
}

private abstract class MapEntity(
    protected val world: GameWorld,
    private val entity: LivingEntity
): SimpleAi()
{
    protected fun findPath(target: List<Pair<Int, Int>>, speed: Double): Vector
    {
        val map = world.getMap()
        val curLocation = entity.location

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

            val res = adjacentBlocks.minByOrNull { it.third } ?: return Vector(0.0, 0.0, 0.0)

            curX = res.first
            curZ = res.second

            val nextLocation = Location(
                curLocation.world,
                curX - 24 + 0.5,
                curLocation.y,
                curZ - 36 + 0.5
            )
            return horizontalVelocity(curLocation, nextLocation, speed) ?: Vector(0.0, 0.0, 0.0)
        }

        val next = aStar(
            map,
            curX to curZ,
            target
        ) ?: return Vector(0.0, 0.0, 0.0)

        val nextLocation = Location(
            curLocation.world,
            next.first - 24 + 0.5,
            curLocation.y,
            next.second - 36 + 0.5
        )

        return horizontalVelocity(curLocation, nextLocation, speed) ?: Vector(0.0, 0.0, 0.0)
    }

    private fun aStar(
        map: List<List<MapElement>>,
        start: Pair<Int, Int>,
        targets: List<Pair<Int, Int>>
    ): Pair<Int, Int>?
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

        val vis = mutableSetOf<Pair<Int, Int>>()

        priorityQueue.add(Triple(0.0, start, null))

        while (priorityQueue.isNotEmpty())
        {
            val (cost, current, firstStep) = priorityQueue.poll()
            if (current in vis) continue
            vis.add(current)
            if (current in targets)
                return firstStep

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
                priorityQueue.add(Triple(newCost, next, firstStep ?: next))
            }
        }
        return null
    }
}