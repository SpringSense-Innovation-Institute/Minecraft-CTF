package moe.tachyon.ctf.plugin

import org.bukkit.Bukkit
import org.bukkit.WorldCreator
import org.bukkit.generator.ChunkGenerator
import taboolib.common.platform.function.warning
import java.io.File

private val worlds = mutableMapOf<String, GameWorld>()

fun getGameWorld(name: String): GameWorld?
    = worlds[name]

fun getGameWorlds(): List<GameWorld>
    = worlds.values.toList()

private const val TEMPLATE_WORLD_NAME = "template_world"
fun generateGameWorld(
    name: String,
    setting: GameSettingData,
    record: GameRecord
): GameWorld
{
    val serverFolder = Bukkit.getWorldContainer()
    val templateDir = File(serverFolder, TEMPLATE_WORLD_NAME)
    val newWorldDir = File(serverFolder, name)

    if (!templateDir.exists()) error("模板世界 $TEMPLATE_WORLD_NAME 不存在！")
    if (Bukkit.getWorld(name) != null || newWorldDir.exists()) error("世界 $name 已存在！")

    for (filename in listOf("uid.dat", "session.lock", "playerdata", "stats", "advancements"))
    {
        val file = File(templateDir, filename)
        if (file.exists()) file.deleteRecursively()
    }

    templateDir.copyRecursively(newWorldDir, overwrite = false) { _, _ -> OnErrorAction.SKIP }

    val creator = WorldCreator(name)
    creator.generateStructures(false)
    creator.generator(object: ChunkGenerator(){})
    val world = Bukkit.createWorld(creator)
    if (world != null)
    {
        world.isAutoSave = false
        println("维度 $name 创建完毕！")
    }

    val gameWorld = GameWorld(
        name = name,
        leftGroup = setting.leftGroup,
        rightGroup = setting.rightGroup,
        record = record,
        randomMap = setting.randomMap,
        playerCountPerGroup = setting.playerCountPerGroup,
        practice = practiceMode?.name,
    )
    worlds[name] = gameWorld
    return gameWorld
}

fun removeGameWorld(name: String)
{
    val world = Bukkit.getWorld(name) ?: return

    world.players.forEach()
    { player ->
        player.teleportToWorld()
    }
    val unloaded = Bukkit.unloadWorld(name, false)
    if (unloaded)
    {
        val worldDir = File(Bukkit.getWorldContainer(), name)
        if (worldDir.exists()) worldDir.deleteRecursively()
        worlds.remove(name)
        println("维度 $name 已卸载！")
    }
    else
    {
        warning("无法卸载世界 $name！")
    }
}

fun checkGameWorldRecycle(name: String)
{
    val world = getGameWorld(name) ?: return
    if (world.canRecycle())
    {
        world.gameStatus = GameStatus.NONE
        removeGameWorld(name)
    }
}
