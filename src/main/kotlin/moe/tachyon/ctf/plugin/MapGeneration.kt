package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.gameStatus
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.playerCountPerGroup
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.randomMap
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.Bisected
import org.bukkit.entity.EntityType
import org.bukkit.entity.MushroomCow
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.util.randomDouble
import taboolib.module.ai.*
import taboolib.module.navigation.isWater
import taboolib.platform.util.isNotAir
import taboolib.platform.util.toProxyLocation

fun clearMap()
{
    for (x in -24..24)
        for (z in -36..36)
        {
            val location = Location(Bukkit.getWorld("world"), x.toDouble(), 0.0, z.toDouble())
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

    val box = BoundingBox(-24.0, -64.0, -36.0, 24.0, 20.0, 36.0)
    Bukkit.getWorld("world")?.getNearbyEntities(box)?.forEach()
    {
        if (it.type != EntityType.PLAYER)
            it.remove()
    }
}

fun generateMap()
{
    var count = 0

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
            val list = mutableListOf<Pair<Int, Int>>()
            while (list.size < 8)
            {
                val x = xRange.random()
                val z = zRange.random()
                val location = Location(Bukkit.getWorld("world"), x.toDouble(), 0.0, z.toDouble())
                val location1 = location.clone().add(0.0, 1.0, 0.0)
                if (location.block.type == Material.GRASS_BLOCK && location1.block.type.isAir &&
                    !listOf(list, leftFlags, rightFlags, leftTarget, rightTarget).any { it.contains(Pair(x, z)) })
                    list += Pair(x, z)
            }
            list
        }
        leftFlags += generate((-24..-12), (-36..36))
        rightFlags += generate((12..24), (-36..36))
        leftTarget += generate((-24..-12), (-36..36))
        rightTarget += generate((12..24), (-36..36))
    }

    for (target in leftTarget + rightTarget)
        Location(Bukkit.getWorld("world"), target.first.toDouble(), 0.0, target.second.toDouble()).block.type = Material.GOLD_BLOCK

    for (flag in leftFlags + rightFlags)
    {
        Location(Bukkit.getWorld("world"), flag.first.toDouble(), 0.0, flag.second.toDouble()).block.type = Material.WAXED_COPPER_BLOCK
        Location(Bukkit.getWorld("world"), flag.first.toDouble(), 1.0, flag.second.toDouble()).block.type = Material.OAK_FENCE
    }

    for (flag in leftFlags)
        Location(Bukkit.getWorld("world"), flag.first.toDouble(), 2.0, flag.second.toDouble()).block.type = Material.RED_BANNER

    for (flag in rightFlags)
        Location(Bukkit.getWorld("world"), flag.first.toDouble(), 2.0, flag.second.toDouble()).block.type = Material.BLUE_BANNER

    // 生成树
    count = 0
    generateTree@while (count < 10)
    {
        val x = (1..24).random() * if (count >= 5) -1 else 1
        val z = (-36..36).random()
        val high = (0..3).random()
        val location = Location(Bukkit.getWorld("world"), x.toDouble(), 0.0, z.toDouble())

        for (dx in -1..1)
            for (dz in -1..1)
                if (location.clone().add(dx.toDouble(), 0.0, dz.toDouble()).block.type != Material.GRASS_BLOCK ||
                    location.clone().add(dx.toDouble(), 1.0, dz.toDouble()).block.type.isNotAir())
                    continue@generateTree

        for (dx in -1..1)
            for (dz in -1..1)
                for (y in high + 1 .. high + 3)
                    location.clone().add(dx.toDouble(), y.toDouble(), dz.toDouble()).block.type = Material.OAK_LEAVES

        for (y in 1..high+1)
            location.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.OAK_LOG

        count++
    }

    // 生成pitcher plant
    count = 0
    generatePitcher@while (count < 10)
    {
        val x = (1..24).random() * if (count >= 5) -1 else 1
        val z = (-36..36).random()
        val location = Location(Bukkit.getWorld("world"), x.toDouble(), 0.0, z.toDouble())
        if (location.block.type != Material.GRASS_BLOCK ||
            location.clone().add(0.0, 1.0, 0.0).block.type.isNotAir() ||
            location.clone().add(0.0, 2.0, 0.0).block.type.isNotAir())
            continue@generatePitcher

        location.clone().add(0.0, 1.0, 0.0).block.blockData =
            Bukkit.createBlockData(Material.PITCHER_PLANT).let { it as Bisected }.also { it.half = Bisected.Half.BOTTOM }

        location.clone().add(0.0, 2.0, 0.0).block.blockData =
            Bukkit.createBlockData(Material.PITCHER_PLANT).let { it as Bisected }.also { it.half = Bisected.Half.TOP }

        count++
    }

    // 生成哞菇
    count = 0
    generateMushroom@while (count < playerCountPerGroup * 2)
    {
        val x = (1..24).random() * if (count >= playerCountPerGroup) -1 else 1
        val z = (-36..36).random()
        val location = Location(Bukkit.getWorld("world"), x.toDouble(), 0.0, z.toDouble())
        if (location.block.type != Material.GRASS_BLOCK ||
            location.clone().add(0.0, 1.0, 0.0).block.type.isNotAir() ||
            location.clone().add(0.0, 2.0, 0.0).block.type.isNotAir())
            continue@generateMushroom
        val e = location.world!!.spawnEntity(location.add(0.0, 1.0, 0.0), EntityType.MOOSHROOM) as MushroomCow
        e.variant = if (randomDouble() < 0.5) MushroomCow.Variant.BROWN else MushroomCow.Variant.RED
        e.clearGoalAi()
        e.clearTargetAi()
        e.addTargetAi(CowAI(e), 1)
        count++
    }

    // 生成发光鱿鱼
    count = 0
    generateSquid@while (count < 5)
    {
        val z = (-36..36).random()
        val location = Location(Bukkit.getWorld("world"), 0.5, -1.0, z.toDouble())
        if (!location.block.type.isWater()) continue@generateSquid
        location.world!!.spawnEntity(location, EntityType.GLOW_SQUID)
        count++
    }
}

private class CowAI(
    val cow: MushroomCow,
): SimpleAi()
{
    private var sprinting = false
    private var cooldown = false

    override fun shouldExecute(): Boolean = gameStatus.inGame()

    override fun continueExecute(): Boolean = gameStatus.inGame()

    override fun resetTask() = Unit

    override fun startTask() = Unit

    override fun updateTask()
    {
        val target = onlinePlayers()
            .filter { getPlayerTeam(it.name) != PlayerTeam.NONE }
            .minBy { it.location.distanceSquared(cow.location.toProxyLocation()) }

        if (!sprinting && target.location.distanceSquared(cow.location.toProxyLocation()) < 16.0 && !cooldown)
        {
            sprinting = true
        }
        else if (target.location.distanceSquared(cow.location.toProxyLocation()) >= 25.0)
        {
            sprinting = false
            cooldown = false
        }

        if (sprinting) cow.navigationMove(target.cast<Player>(), 3.0)
        else cow.navigationMove(target.cast<Player>(), 1.0)

        if (target.location.distanceSquared(cow.location.toProxyLocation()) < 1.0 && !cooldown)
        {
            val d = Vector(target.location.x - cow.location.x, 0.0, target.location.z - cow.location.z).normalize().multiply(0.8)
            cow.velocity = d
            cooldown = true
            sprinting = false
        }
    }
}