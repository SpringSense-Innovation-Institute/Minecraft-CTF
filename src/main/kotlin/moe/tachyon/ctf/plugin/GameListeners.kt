package moe.tachyon.ctf.plugin

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerRespawnEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.util.buildItem

private fun Material?.isBanner(): Boolean =
    this == Material.BLUE_BANNER || this == Material.RED_BANNER

@SubscribeEvent
private fun onPlayerPickupFlag(event: PlayerMoveEvent)
{
    val world = getGameWorld(event.player.world.name) ?: return
    if (!world.gameStatus.inGame()) return
    val player = event.player
    val team = world.getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) return
    if (player.inventory.helmet?.type.isBanner()) return
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

@SubscribeEvent
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

    updateStatisticData()
    {
        if (world.leftGroupNumber == 0 || world.rightGroupNumber == 0) return@updateStatisticData
        val group = getPlayerGroup(player.name) ?: return@updateStatisticData
        getFlagTimes[group] = getFlagTimes.getOrDefault(group, 0) + 1
    }
}

@SubscribeEvent
private fun onPlayerCaught(event: PlayerMoveEvent)
{
    val world = getGameWorld(event.player.world.name) ?: return
    if (!world.gameStatus.inGame()) return
    val player = event.player
    val team = world.getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) return

    val selfLocationStatus = if (team == PlayerTeam.LEFT) LocationStatus.IN_LEFT_FIELD else LocationStatus.IN_RIGHT_FIELD
    val enemyLocationStatus = if (team == PlayerTeam.LEFT) LocationStatus.IN_RIGHT_FIELD else LocationStatus.IN_LEFT_FIELD
    val selfPrisonStatus = if (team == PlayerTeam.LEFT) LocationStatus.IN_LEFT_PRISON else LocationStatus.IN_RIGHT_PRISON
    val enemyPrisonStatus = if (team == PlayerTeam.LEFT) LocationStatus.IN_RIGHT_PRISON else LocationStatus.IN_LEFT_PRISON

    val entities = player
        .getNearbyEntities(0.5, 1.0, 0.5)
        .filterIsInstance<Player>()
        .filter { world.getPlayerTeam(it.name) != team }

    for (entity in entities)
    {
        if (selfLocationStatus.contains(player.location) && selfLocationStatus.contains(entity.location))
        {
            onPlayerDropFlag(entity)
            entity.teleport(world, enemyPrisonStatus)
            updateStatisticData()
            {
                if (world.leftGroupNumber == 0 || world.rightGroupNumber == 0) return@updateStatisticData
                val group = getPlayerGroup(player.name) ?: return@updateStatisticData
                catchTimes[group] = catchTimes.getOrDefault(group, 0) + 1
            }
        }
        else if (enemyLocationStatus.contains(player.location) && enemyLocationStatus.contains(entity.location))
        {
            onPlayerDropFlag(player)
            player.teleport(world, selfPrisonStatus)
            updateStatisticData()
            {
                if (world.leftGroupNumber == 0 || world.rightGroupNumber == 0) return@updateStatisticData
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
    if (event.message == PLAYER_PREPARE_MESSAGE)
    {
        getGameWorlds().forEach { it.playerPrepared(event.player.name) }
        event.isCancelled = true
    }
}

@SubscribeEvent
private fun onFoodChange(event: FoodLevelChangeEvent)
{
    if (event.foodLevel < 7)
        event.foodLevel = 7
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
        {
            event.isCancelled = true
        }
    }
}