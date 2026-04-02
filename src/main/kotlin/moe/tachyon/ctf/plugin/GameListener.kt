package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.gameStatus
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerRespawnEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.util.buildItem

@SubscribeEvent
private fun onPlayerPickupFlag(event: PlayerMoveEvent)
{
    if (!gameStatus.isGame()) return
    val player = event.player
    val team = getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) return
    val location = (event.to ?: return).clone().apply { y = 0.0 }
    if (location.block.type != Material.WAXED_COPPER_BLOCK) return
    if (location.clone().add(0.0, 1.0, 0.0).block.type != Material.OAK_FENCE) return
    if (location.clone().add(0.0, 2.0, 0.0).block.type != if (team == PlayerTeam.LEFT) Material.BLUE_BANNER else Material.RED_BANNER) return
    if (player.inventory.helmet?.type == Material.LEATHER_HELMET) return

    location.block.type = Material.GRASS_BLOCK
    location.clone().add(0.0, 2.0, 0.0).block.type = Material.AIR
    location.clone().add(0.0, 1.0, 0.0).block.type = Material.AIR

    val cap = buildItem(Material.LEATHER_HELMET)
    {
        color = if (team == PlayerTeam.LEFT) Color.BLUE else Color.RED
        enchants[Enchantment.BINDING_CURSE] = 1
    }

    player.inventory.helmet = cap
}

@SubscribeEvent
private fun onPlayerSetFlag(event: PlayerMoveEvent)
{
    if (!gameStatus.isGame()) return
    val player = event.player
    val team = getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) return
    val location = (event.to ?: return).clone().apply { y = 0.0 }
    if (location.block.type != Material.GOLD_BLOCK) return
    if (location.clone().add(0.0, 1.0, 0.0).block.type != Material.AIR) return
    if (location.clone().add(0.0, 2.0, 0.0).block.type != Material.AIR) return
    if (player.inventory.helmet?.type != Material.LEATHER_HELMET) return

    val field = if (team == PlayerTeam.LEFT) LocationStatus.IN_LEFT_FIELD else LocationStatus.IN_RIGHT_FIELD
    if (!field.contains(location)) return

    location.clone().add(0.0, 1.0, 0.0).block.type = Material.OAK_FENCE
    location.clone().add(0.0, 2.0, 0.0).block.type = if (team == PlayerTeam.LEFT) Material.BLUE_BANNER else Material.RED_BANNER

    player.inventory.helmet = null
}

@SubscribeEvent
private fun onPlayerCaught(event: PlayerMoveEvent)
{
    if (!gameStatus.isGame()) return
    val player = event.player
    val team = getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) return

    val selfLocationStatus = if (team == PlayerTeam.LEFT) LocationStatus.IN_LEFT_FIELD else LocationStatus.IN_RIGHT_FIELD
    val enemyLocationStatus = if (team == PlayerTeam.LEFT) LocationStatus.IN_RIGHT_FIELD else LocationStatus.IN_LEFT_FIELD
    val selfPrisonStatus = if (team == PlayerTeam.LEFT) LocationStatus.IN_LEFT_PRISON else LocationStatus.IN_RIGHT_PRISON
    val enemyPrisonStatus = if (team == PlayerTeam.LEFT) LocationStatus.IN_RIGHT_PRISON else LocationStatus.IN_LEFT_PRISON

    val entities = player
        .getNearbyEntities(0.5, 1.0, 0.5)
        .filterIsInstance<Player>()
        .filter { player.boundingBox.overlaps(it.boundingBox) }
        .filter { getPlayerTeam(it.name) != team }

    for (entity in entities)
    {
        if (selfLocationStatus.contains(player.location) && selfLocationStatus.contains(entity.location))
        {
            onPlayerDropFlag(entity)
            entity.teleport(enemyPrisonStatus.location)
        }
        else if (enemyLocationStatus.contains(player.location) && enemyLocationStatus.contains(entity.location))
        {
            onPlayerDropFlag(player)
            player.teleport(selfPrisonStatus.location)
        }
    }
}

@SubscribeEvent
private fun onPlayerDeath(event: PlayerRespawnEvent)
{
    if (gameStatus != GameStatus.IN_GAME) return
    val player = event.player
    val team = getPlayerTeam(player.name)
    if (team == PlayerTeam.NONE) return

    onPlayerDropFlag(event.player)

    val prisonLocation = when (team)
    {
        PlayerTeam.LEFT -> LocationStatus.IN_LEFT_PRISON.location
        PlayerTeam.RIGHT -> LocationStatus.IN_RIGHT_PRISON.location
    }
    event.respawnLocation = prisonLocation
}

private fun onPlayerDropFlag(player: Player)
{
    if (player.inventory.helmet?.type != Material.LEATHER_HELMET) return
    val team = getPlayerTeam(player.name)
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