package moe.tachyon.ctf.plugin.web

import com.github.luben.zstd.Zstd
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.tachyon.ctf.plugin.*
import moe.tachyon.ctf.plugin.GameRecord.Companion.box
import moe.tachyon.ctf.plugin.GameRecord.Companion.mapBox
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.recordsFolder
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common5.util.encodeBase64
import java.io.File

private val listeners = mutableMapOf<GameWorld, MutableList<(block: Block) -> Unit>>()

private fun GameWorld.addChangeListener(listener: (block: Block) -> Unit) = synchronized(listeners)
{
    listeners.computeIfAbsent(this) { mutableListOf() }.add(listener)
}

private fun GameWorld.removeChangeListener(listener: (block: Block) -> Unit) = synchronized(listeners)
{
    listeners[this]?.remove(listener)
    if (listeners[this]?.isEmpty() == true)
        listeners.remove(this)
}

@SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
private fun onBlockPlace(event: BlockPlaceEvent) = synchronized(listeners)
{
    if (event.block.x !in -24..24 || event.block.y !in 0..10 || event.block.z !in -36..36)
        return@synchronized

    val world = getGameWorld(event.block.world.name) ?: return@synchronized
    listeners[world]?.forEach { it(event.block) }
}

@SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
private fun onBlockBreak(event: BlockBreakEvent) = synchronized(listeners)
{
    if (event.block.x !in -24..24 || event.block.y !in 0..10 || event.block.z !in -36..36)
        return@synchronized

    val world = getGameWorld(event.block.world.name) ?: return@synchronized
    listeners[world]?.forEach { it(event.block) }
}

@SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
private fun onBlockPhysics(event: BlockPhysicsEvent) = synchronized(listeners)
{
    if (event.block.x !in -24..24 || event.block.y !in 0..10 || event.block.z !in -36..36)
        return@synchronized

    val world = getGameWorld(event.block.world.name) ?: return@synchronized
    listeners[world]?.forEach { it(event.block) }
}

fun HttpServer.viewer3d()
{

    createContext("/viewer3d")
    { exchange ->
        val html = MinecraftCtfPlugin.javaClass.getResourceAsStream("/web/viewer3d.html")?.readAllBytes() ?: return@createContext exchange.sendResponseHeaders(404, -1)
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        exchange.responseBody.use { it.write(html) }
    }

    createContext("/viewer3d.js")
    { exchange ->
        val js = MinecraftCtfPlugin.javaClass.getResourceAsStream("/web/viewer3d.js")?.readAllBytes() ?: return@createContext exchange.sendResponseHeaders(404, -1)
        exchange.sendResponseHeaders(200, js.size.toLong())
        exchange.responseHeaders.add("Content-Type", "application/javascript; charset=UTF-8")
        exchange.responseBody.use { it.write(js) }
    }

    createContext("/viewer3d.css")
    { exchange ->
        val css = MinecraftCtfPlugin.javaClass.getResourceAsStream("/web/viewer3d.css")?.readAllBytes() ?: return@createContext exchange.sendResponseHeaders(404, -1)
        exchange.sendResponseHeaders(200, css.size.toLong())
        exchange.responseHeaders.add("Content-Type", "text/css; charset=UTF-8")
        exchange.responseBody.use { it.write(css) }
    }

    createContext("/viewer3d/live")
    { exchange ->
        val player = SessionManager.getPlayerIdFromCookie(exchange)
            ?: return@createContext exchange.sendResponseHeaders(401, -1)

        val world = getGameWorlds()
            .firstOrNull { (it.leftGroupPlayers().contains(player) || it.rightGroupPlayers().contains(player)) && !it.gameStatus.none() }
            ?: return@createContext exchange.sendResponseHeaders(404, -1)

        exchange.sseHeaders()

        var init = true
        val updateBlocks = mutableListOf<Block>()
        val listener: (block: Block) -> Unit = { block ->
            synchronized(updateBlocks)
            {
                updateBlocks.add(block)
            }
        }
        submit(period = 1) loop@
        {
            val pack: String
            if (init)
            {
                init = false
                val blocks = (-24..24).map()
                { x ->
                    (0..10).map()
                    { y ->
                        (-36..36).map()
                        { z ->
                            world.bukkitWorld()?.getBlockAt(x, y, z)?.blockData?.asString ?: "minecraft:air"
                        }
                    }
                }
                val data = InitialData(blocks, -24, 0, -36)
                pack = Json.encodeToString(data)
                world.addChangeListener(listener)
            }
            else
            {
                val bukkitWorld = world.bukkitWorld() ?: run()
                {
                    world.removeChangeListener(listener)
                    runCatching { exchange.responseBody.close() }
                    this@loop.cancel()
                    return@loop
                }

                val changes = mutableListOf<TickData.BlockChange>()
                synchronized(updateBlocks)
                {
                    updateBlocks.forEach()
                    { block ->
                        val x = block.x
                        val y = block.y
                        val z = block.z
                        val data = block.blockData.asString
                        changes.add(TickData.BlockChange(x, y, z, data))
                    }
                    updateBlocks.clear()
                }
                val entities = bukkitWorld.getNearbyEntities(box).map()
                {
                    val location = it.location
                    val helmet = if (it is Player) it.equipment?.helmet else null
                    val chestplate = if (it is Player) it.equipment?.chestplate else null
                    val leggings = if (it is Player) it.equipment?.leggings else null
                    val boots = if (it is Player) it.equipment?.boots else null
                    TickData.EntityData(
                        id = it.uniqueId.toString(),
                        type = it.type.name,
                        name = if (it is Player) it.name else null,

                        x = location.x.toFloat(),
                        y = location.y.toFloat(),
                        z = location.z.toFloat(),
                        yaw = location.yaw,
                        pitch = location.pitch,
                        vx = it.velocity.x.toFloat(),
                        vy = it.velocity.y.toFloat(),
                        vz = it.velocity.z.toFloat(),

                        helmet = helmet?.type?.takeUnless(Material::isAir)?.name?.plus(helmet.itemMeta?.asString ?: ""),
                        chestplate = chestplate?.type?.takeUnless(Material::isAir)?.name?.plus(chestplate.itemMeta?.asString ?: ""),
                        leggings = leggings?.type?.takeUnless(Material::isAir)?.name?.plus(leggings.itemMeta?.asString ?: ""),
                        boots = boots?.type?.takeUnless(Material::isAir)?.name?.plus(boots.itemMeta?.asString ?: "")
                    )
                }
                val tickData = TickData(changes, entities)
                pack = Json.encodeToString(tickData)
            }

            submit(async = true)
            {
                try
                {
                    val data = Zstd.compress(pack.toByteArray()).encodeBase64()
                    exchange.responseBody.write("data: $data\n\n".toByteArray())
                    exchange.responseBody.flush()
                }
                catch (_: Throwable)
                {
                    world.removeChangeListener(listener)
                    runCatching { exchange.responseBody.close() }
                    this@loop.cancel()
                }
            }
        }
    }

    createContext("/viewer3d/record")
    { exchange ->
        val args = exchange.args()

        val name = args["name"] ?: return@createContext exchange.sendResponseHeaders(400, -1)
        val start = args["start"]?.toIntOrNull() ?: 0
        val speed = args["speed"]?.toDouble() ?: 1.0

        val file = File(recordsFolder, "$name.dat")
        if (!file.exists())
        {
            exchange.sendResponseHeaders(404, -1)
            return@createContext
        }

        val recordReader = RecordReader(file)

        exchange.responseHeaders.add("X-Record-Length", recordReader.tickCount.toString())
        exchange.sseHeaders()

        val replayer = Replayer(recordReader, speed, start)

        var init = 0

        submit(async = true, period = 1) loop@
        {
            if (init < 20)
            {
                if (init++ != 0) return@loop
                try
                {
                    val initialData = replayer.initialData
                    val data = Zstd.compress(Json.encodeToString(initialData).toByteArray()).encodeBase64()
                    exchange.responseBody.write("data: $data\n\n".toByteArray())
                    exchange.responseBody.flush()
                }
                catch (_: Throwable)
                {
                    runCatching { exchange.responseBody.close() }
                    runCatching { replayer.close() }
                    return@loop this@loop.cancel()
                }
            }
            val tickData = replayer.readTickData() ?: run()
            {
                runCatching { exchange.responseBody.close() }
                runCatching { replayer.close() }
                return@loop this@loop.cancel()
            }

            try
            {
                val json = Json.encodeToString(tickData)
                val data = Zstd.compress(json.toByteArray()).encodeBase64()
                exchange.responseBody.write("data: ${data}\n\n".toByteArray())
                exchange.responseBody.flush()
            }
            catch (_: Throwable)
            {
                runCatching { exchange.responseBody.close() }
                runCatching { replayer.close() }
                return@loop this@loop.cancel()
            }
        }
    }

    createContext("/viewer3d/record_list")
    { exchange ->
        val records = GameRecord.records().map { it.nameWithoutExtension }
        val data = Json.encodeToString(records)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
        exchange.sendResponseHeaders(200, data.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(data.toByteArray()) }
    }
}

@Serializable
private data class TickData(
    val blocks: List<BlockChange>,
    val entities: List<EntityData>,
)
{
    @Serializable
    data class BlockChange(
        val x: Int,
        val y: Int,
        val z: Int,
        val data: String,
    )
    @Serializable
    data class EntityData(
        val id: String,
        val type: String,
        val name: String?,

        val x: Float,
        val y: Float,
        val z: Float,
        val yaw: Float,
        val pitch: Float,
        val vx: Float,
        val vy: Float,
        val vz: Float,

        val helmet: String? = null,
        val chestplate: String? = null,
        val leggings: String? = null,
        val boots: String? = null,
    )
}

@Serializable
private data class InitialData(
    val blocks: List<List<List<String>>>,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
)

private class Replayer(
    val recordReader: RecordReader,
    val speed: Double,
    val offset: Int,
): AutoCloseable
{
    override fun close() = recordReader.close()

    val initialData: InitialData by lazy()
    {
        val blocks = (mapBox.minX.toInt()..mapBox.maxX.toInt()).map()
        {
            (mapBox.minY.toInt()..mapBox.maxY.toInt()).map()
            {
                (mapBox.minZ.toInt()..mapBox.maxZ.toInt()).map()
                {
                    Material.AIR.createBlockData().asString
                }.toMutableList()
            }
        }
        val minX = mapBox.minX.toInt()
        val minY = mapBox.minY.toInt()
        val minZ = mapBox.minZ.toInt()
        repeat(offset + 1)
        {
            val tick = recordReader.readTick() ?: return@repeat
            tick.first.forEach()
            {
                val x = it.x - minX
                val y = it.y - minY
                val z = it.z - minZ
                if (x in blocks.indices && y in blocks[0].indices && z in blocks[0][0].indices)
                {
                    blocks[x][y][z] = it.blockData
                }
            }
        }
        InitialData(blocks, minX, minY, minZ)
    }

    private var speedTest = 0.0
    private var entities = emptyList<TickData.EntityData>()

    fun readTickData(): TickData?
    {
        initialData
        speedTest += speed
        if (speedTest < 1.0)
            return TickData(emptyList(), entities)

        val blocks = mutableMapOf<Triple<Int, Int, Int>, String>()
        while (speedTest >= 1.0)
        {
            speedTest -= 1.0
            val tick = recordReader.readTick() ?: return null
            tick.first.forEach()
            {
                val key = Triple(it.x, it.y, it.z)
                blocks[key] = it.blockData
            }
            if (speedTest < 1.0)
            {
                entities = tick.second.map()
                {
                    TickData.EntityData(
                        id = it.uuid,
                        type = it.type,
                        name = it.name,

                        x = it.x.toFloat(),
                        y = it.y.toFloat(),
                        z = it.z.toFloat(),
                        yaw = it.yaw,
                        pitch = it.pitch,
                        vx = it.vx.toFloat(),
                        vy = it.vy.toFloat(),
                        vz = it.vz.toFloat(),

                        helmet = it.helmet.type.takeUnless(Material::isAir)?.name?.plus(it.helmet.itemMeta?.asString ?: ""),
                        chestplate = it.chestplate.type.takeUnless(Material::isAir)?.name?.plus(it.chestplate.itemMeta?.asString ?: ""),
                        leggings = it.leggings.type.takeUnless(Material::isAir)?.name?.plus(it.leggings.itemMeta?.asString ?: ""),
                        boots = it.boots.type.takeUnless(Material::isAir)?.name?.plus(it.boots.itemMeta?.asString ?: ""),
                    )
                }
            }
        }

        return TickData(
            blocks.map { TickData.BlockChange(it.key.first, it.key.second, it.key.third, it.value) },
            entities
        )
    }
}