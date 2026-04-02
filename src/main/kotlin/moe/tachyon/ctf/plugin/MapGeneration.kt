package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.randomMap
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import taboolib.platform.util.isNotAir

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
        leftFlags += generate((-24..-1), (-36..36))
        rightFlags += generate((1..24), (-36..36))
        leftTarget += generate((-24..-1), (-36..36))
        rightTarget += generate((1..24), (-36..36))
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

    // 生成哞菇
    count = 0
    generateMushroom@while (count < 4)
    {
        val x = (1..24).random() * if (count >= 2) -1 else 1
        val z = (-36..36).random()
        val location = Location(Bukkit.getWorld("world"), x.toDouble(), 0.0, z.toDouble())
        if (location.block.type != Material.GRASS_BLOCK ||
            location.clone().add(0.0, 1.0, 0.0).block.type.isNotAir() ||
            location.clone().add(0.0, 2.0, 0.0).block.type.isNotAir())
            continue@generateMushroom
        location.world!!.spawnEntity(location.add(0.0, 1.0, 0.0), EntityType.MOOSHROOM)
        count++
    }
}