package moe.tachyon.ctf.plugin

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.InventoryCreativeEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.util.random
import taboolib.platform.util.buildItem
import taboolib.platform.util.isAir
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private fun horizontalVelocity(from: Location, target: Location, speed: Double): Vector?
{
    val dx = target.x - from.x
    val dz = target.z - from.z
    val length = hypot(dx, dz)
    if (length < 1.0e-6) return null
    return Vector(dx / length * speed, 0.0, dz / length * speed)
}

@SubscribeEvent(ignoreCancelled = true, priority = EventPriority.MONITOR)
private fun onPlayerPickupFlag(event: PlayerMoveEvent)
{
    val world = getGameWorld(event.player.world.name) ?: return
    if (!world.gameStatus.inGame()) return
    val player = event.player
    val team = world.getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) return
    if (!player.inventory.helmet?.type.isAir()) return
    val location = (event.to ?: return).clone().apply { y = 0.0 }

    val locations = listOf(
        location.clone(),
        location.clone().add(1.0, 0.0, 0.0),
        location.clone().add(-1.0, 0.0, 0.0),
        location.clone().add(0.0, 0.0, 1.0),
        location.clone().add(0.0, 0.0, -1.0),
    )

    val rLocation = locations.firstOrNull()
    {
        if (it.block.type != Material.WAXED_COPPER_BLOCK) false
        else if (it.clone().add(0.0, 1.0, 0.0).block.type != Material.OAK_FENCE) false
        else if (it.clone().add(0.0, 2.0, 0.0).block.type != if (team == PlayerTeam.LEFT) Material.BLUE_BANNER else Material.RED_BANNER) false
        else true
    }

    if (rLocation == null) return

    rLocation.block.type = Material.GRASS_BLOCK
    rLocation.clone().add(0.0, 2.0, 0.0).block.type = Material.AIR
    rLocation.clone().add(0.0, 1.0, 0.0).block.type = Material.AIR

    player.inventory.helmet = if (team == PlayerTeam.LEFT) buildItem(Material.BLUE_BANNER) else buildItem(Material.RED_BANNER)
}

@SubscribeEvent(ignoreCancelled = true, priority = EventPriority.MONITOR)
private fun onPlayerSetFlag(event: PlayerMoveEvent)
{
    val world = getGameWorld(event.player.world.name) ?: return
    if (!world.gameStatus.inGame()) return
    val player = event.player
    val team = world.getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) return
    val location = (event.to ?: return).clone().apply { y = 0.0 }
    if (location.block.type != Material.GOLD_BLOCK) return
    if (location.clone().add(0.0, 1.0, 0.0).block.type != Material.AIR) return
    if (location.clone().add(0.0, 2.0, 0.0).block.type != Material.AIR) return
    if (!player.inventory.helmet?.type.isBanner()) return

    val field = if (team == PlayerTeam.LEFT) LocationStatus.IN_LEFT_FIELD else LocationStatus.IN_RIGHT_FIELD
    if (!field.contains(location)) return

    location.clone().add(0.0, 1.0, 0.0).block.type = Material.OAK_FENCE
    location.clone().add(0.0, 2.0, 0.0).block.type = if (team == PlayerTeam.LEFT) Material.BLUE_BANNER else Material.RED_BANNER

    player.inventory.helmet = null

    updateStatisticData(world.practice)
    {
        if (!world.leftGroup.isPlayer || !world.rightGroup.isPlayer) return@updateStatisticData
        val group = getPlayerGroup(player.name) ?: return@updateStatisticData
        getFlagTimes[group] = getFlagTimes.getOrDefault(group, 0) + 1
    }
}

@SubscribeEvent(ignoreCancelled = true, priority = EventPriority.MONITOR)
private fun onPlayerCaught(event: PlayerMoveEvent)
{
    val world = getGameWorld(event.player.world.name) ?: return
    if (!world.gameStatus.inGame()) return
    val player = event.player
    val team = world.getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) return
    if (player.inventory.helmet.isParasiticHelmet()) return

    val selfLocationStatus = if (team == PlayerTeam.LEFT) LocationStatus.IN_LEFT_FIELD else LocationStatus.IN_RIGHT_FIELD
    val enemyLocationStatus = if (team == PlayerTeam.LEFT) LocationStatus.IN_RIGHT_FIELD else LocationStatus.IN_LEFT_FIELD
    val selfPrisonStatus = if (team == PlayerTeam.LEFT) LocationStatus.IN_LEFT_PRISON else LocationStatus.IN_RIGHT_PRISON
    val enemyPrisonStatus = if (team == PlayerTeam.LEFT) LocationStatus.IN_RIGHT_PRISON else LocationStatus.IN_LEFT_PRISON

    val entities = player
        .getNearbyEntities(0.5, 1.0, 0.5)
        .filterIsInstance<Player>()
        .filter { world.getPlayerTeam(it.name) != team && !it.inventory.helmet.isParasiticHelmet() }
        .filter { world.getPlayerTeam(it.name) != PlayerTeam.NONE }

    for (entity in entities)
    {
        if (selfLocationStatus.contains(player.location) && selfLocationStatus.contains(entity.location))
        {
            onPlayerDropFlag(entity)
            entity.teleport(world, enemyPrisonStatus)
            updateStatisticData(world.practice)
            {
                if (!world.leftGroup.isPlayer || !world.rightGroup.isPlayer) return@updateStatisticData
                val group = getPlayerGroup(player.name) ?: return@updateStatisticData
                catchTimes[group] = catchTimes.getOrDefault(group, 0) + 1
            }
        }
        else if (enemyLocationStatus.contains(player.location) && enemyLocationStatus.contains(entity.location))
        {
            onPlayerDropFlag(player)
            player.teleport(world, selfPrisonStatus)
            updateStatisticData(world.practice)
            {
                if (!world.leftGroup.isPlayer || !world.rightGroup.isPlayer) return@updateStatisticData
                val group = getPlayerGroup(entity.name) ?: return@updateStatisticData
                catchTimes[group] = catchTimes.getOrDefault(group, 0) + 1
            }
        }
    }
}

@SubscribeEvent
private fun onPlayerDeath(event: PlayerRespawnEvent)
{
    val world = getGameWorld(event.player.world.name) ?: return
    if (world.gameStatus != GameStatus.IN_GAME) return
    val player = event.player
    val team = world.getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) return

    onPlayerDropFlag(event.player)

    val prisonLocation = when (team)
    {
        PlayerTeam.LEFT -> LocationStatus.IN_LEFT_PRISON.location(world)
        PlayerTeam.RIGHT -> LocationStatus.IN_RIGHT_PRISON.location(world)
    }
    event.respawnLocation = prisonLocation
}

val parasiticHelmet = buildItem(Material.LEATHER_HELMET)
{
    this.color = Color.GREEN
    enchants[Enchantment.BINDING_CURSE] = 1
}
fun ItemStack?.isParasiticHelmet(): Boolean =
    this != null &&
    type == Material.LEATHER_HELMET &&
    itemMeta?.hasEnchant(Enchantment.BINDING_CURSE) == true &&
    (itemMeta as? LeatherArmorMeta)?.color == Color.GREEN

// 玩家被寄生
fun onPlayerParasitic(world: GameWorld, player: Player)
{
    if (player.inventory.helmet.isParasiticHelmet()) return
    onPlayerDropFlag(player)
    val speed = player.walkSpeed
    player.walkSpeed = 0.0f
    var tick = 0

    player.inventory.helmet = parasiticHelmet
    player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, Int.MAX_VALUE, 200, false, false, false))

    submit(period = 1)
    {
        tick++
        if (tick >= 260 || !player.isOnline || !world.gameStatus.inGame() || player.world != world.bukkitWorld())
        {
            player.inventory.helmet = null
            Bukkit.getPluginManager().callEvent(PlayerMoveEvent(player, player.location, player.location))
            player.walkSpeed = speed
            player.removePotionEffect(PotionEffectType.JUMP_BOOST)
            return@submit cancel()
        }
        if (tick >= 200)
        {
            player.walkSpeed = speed
            player.removePotionEffect(PotionEffectType.JUMP_BOOST)
            return@submit
        }
        val nearest = (world.leftGroupBukkitPlayers() + world.rightGroupBukkitPlayers()).asSequence()
            .filter { it.name != player.name }
            .filterNot { it.inventory.helmet.isParasiticHelmet() }
            .minByOrNull { it.location.distanceSquared(player.location) }

        val v =
            nearest?.let { horizontalVelocity(player.location, it.location, 0.3) }
            ?: random(0.0, Math.PI * 2).let { Vector(cos(it), 0.0, sin(it)).multiply(0.3) }

        player.velocity = Vector(v.x, player.velocity.y, v.z)
    }
}

// 传染
@SubscribeEvent(ignoreCancelled = true, priority = EventPriority.MONITOR)
private fun onPlayerParasitic(event: PlayerMoveEvent)
{
    val world = getGameWorld(event.player.world.name) ?: return
    if (!world.gameStatus.inGame()) return
    val player = event.player
    val team = world.getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) return

    val entities = player
        .getNearbyEntities(0.5, 1.0, 0.5)
        .filterIsInstance<Player>()
        .filter { player.inventory.helmet.isParasiticHelmet() != it.inventory.helmet.isParasiticHelmet() }
        .filter { world.getPlayerTeam(it.name) != PlayerTeam.NONE }

    for (entity in entities)
    {
        if (player.inventory.helmet.isParasiticHelmet()) onPlayerParasitic(world, entity)
        else onPlayerParasitic(world, player)
    }
}

private fun onPlayerDropFlag(player: Player)
{
    val world = getGameWorld(player.world.name) ?: return
    if (!player.inventory.helmet?.type.isBanner()) return
    val team = world.getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) return
    for (range in 0..20)
    {
        for (x in -range..range)
        {
            val locations = listOf(
                Location(player.world, player.location.x + x, 0.0, player.location.z),
                Location(player.world, player.location.x, 0.0, player.location.z + x),
                Location(player.world, player.location.x - x, 0.0, player.location.z),
                Location(player.world, player.location.x, 0.0, player.location.z - x),
            )
            val targetLocation = locations.firstOrNull { canDropFlag(it) } ?: continue
            targetLocation.block.type = Material.WAXED_COPPER_BLOCK
            targetLocation.clone().add(0.0, 1.0, 0.0).block.type = Material.OAK_FENCE
            targetLocation.clone().add(0.0, 2.0, 0.0).block.type = if (team == PlayerTeam.LEFT) Material.BLUE_BANNER else Material.RED_BANNER
            player.inventory.helmet = null
            return
        }
    }
}

private fun canDropFlag(location: Location): Boolean
{
    if (locationStatus(location) !in listOf(LocationStatus.IN_LEFT_FIELD, LocationStatus.IN_RIGHT_FIELD)) return false
    if (location.block.type != Material.GRASS_BLOCK) return false
    if (location.clone().add(0.0, 1.0, 0.0).block.type != Material.AIR) return false
    if (location.clone().add(0.0, 2.0, 0.0).block.type != Material.AIR) return false
    return true
}

@SubscribeEvent
private fun onPlayerMessage(event: AsyncPlayerChatEvent)
{
    if (event.message.equals(PLAYER_PREPARE_MESSAGE, ignoreCase = true))
    {
        getGameWorlds().forEach { it.playerPrepared(event.player.name) }
        event.isCancelled = true
    }
}

@SubscribeEvent
private fun onItemDamage(event: PlayerItemDamageEvent)
{
    event.isCancelled = true
}

@SubscribeEvent
private fun onEntityDamageEntity(event: EntityDamageByEntityEvent)
{
    if (event.damager is Player)
    {
        val world = getGameWorld(event.damager.world.name) ?: return
        if (world.getPlayerTeam(event.damager.name) == PlayerTeam.NONE)
            event.isCancelled = true
    }
}

@SubscribeEvent
fun onCreativeClick(e: InventoryCreativeEvent)
{
    val player = e.whoClicked as? Player ?: return
    val world = getGameWorld(player.world.name) ?: return
    val team = world.getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) e.isCancelled = true
}
