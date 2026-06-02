@file:OptIn(ExperimentalUuidApi::class)

package moe.tachyon.ctf.plugin

import com.github.luben.zstd.Zstd
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.recordsFolder
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.common.util.randomDouble
import taboolib.module.nms.ItemTag
import taboolib.module.nms.getItemTag
import taboolib.module.nms.setItemTag
import taboolib.platform.util.isNotAir
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalAtomicApi::class)
class GameRecord(
    world: () -> GameWorld,
    val file: File,
    private var period: Long = 1
)
{
    private val running = AtomicBoolean(false)
    private val done = AtomicBoolean(false)
    private var task: PlatformExecutor.PlatformTask? = null
    // 发生变化的方块
    private val blocks = ConcurrentHashMap.newKeySet<Location>()
    private val world by lazy(LazyThreadSafetyMode.SYNCHRONIZED, world)

    fun start()
    {
        if (!running.compareAndSet(expectedValue = false, newValue = true) || done.load()) return

        val recordWriter = RecordWriter(file, period)
        var init = true

        task = submit(period = period)
        {
            val bukkitWorld = world.bukkitWorld()
            if (!running.load() || done.load() || bukkitWorld == null)
            {
                recordWriter.close()
                this@submit.cancel()
                checkGameWorldRecycle(world.name)
                return@submit
            }

            val entities = bukkitWorld.getNearbyEntities(box).map()
            {
                val helmet = if (it is LivingEntity) it.equipment?.helmet ?: ItemStack(Material.AIR) else ItemStack(Material.AIR)
                val chestplate = if (it is LivingEntity) it.equipment?.chestplate ?: ItemStack(Material.AIR) else ItemStack(Material.AIR)
                val leggings = if (it is LivingEntity) it.equipment?.leggings ?: ItemStack(Material.AIR) else ItemStack(Material.AIR)
                val boots = if (it is LivingEntity) it.equipment?.boots ?: ItemStack(Material.AIR) else ItemStack(Material.AIR)

                EntityStatus(
                    uuid = it.uniqueId.toKotlinUuid().toHexString(),
                    type = it.type.name,
                    x = it.location.x,
                    y = it.location.y,
                    z = it.location.z,
                    yaw = it.location.yaw,
                    pitch = it.location.pitch,
                    vx = it.velocity.x,
                    vy = it.velocity.y,
                    vz = it.velocity.z,
                    name = if (it is Player) it.name else null,
                    helmet = helmet,
                    chestplate = chestplate,
                    leggings = leggings,
                    boots = boots,
                )
            }

            val blocks = blocks.toMutableSet()
            this@GameRecord.blocks.clear()

            if (init)
            {
                init = false
                val s = Location(world.bukkitWorld(), mapBox.min.x, mapBox.min.y, mapBox.min.z)
                val e = Location(world.bukkitWorld(), mapBox.max.x, mapBox.max.y, mapBox.max.z)
                for (x in s.blockX..e.blockX)
                    for (y in s.blockY..e.blockY)
                        for (z in s.blockZ..e.blockZ)
                            if (bukkitWorld.getBlockAt(x, y, z).blockData.material != Material.AIR)
                                blocks += Location(world.bukkitWorld(), x.toDouble(), y.toDouble(), z.toDouble())
            }

            submit(async = true)
            {
                recordWriter.writeTick(
                    blocks.map { BlockStatus(it.blockX, it.blockY, it.blockZ, bukkitWorld.getBlockAt(it).blockData.asString) },
                    entities,
                )
            }
        }
    }

    fun stop()
    {
        if (!done.compareAndSet(expectedValue = false, newValue = true)) return
        running.store(false)
    }

    fun replay()
    {
        if (running.load()) return

        world.gameStatus = GameStatus.REPLAYING

        val recordReader = RecordReader(file)
        val entityPool = mutableMapOf<String, Entity>()
        var init = true

        task = submit(period = recordReader.period)
        {
            if (init)
            {
                world.bukkitWorld()?.difficulty = Difficulty.HARD
                init = false
                for (x in mapBox.min.blockX..mapBox.max.blockX)
                    for (y in mapBox.min.blockY..mapBox.max.blockY)
                        for (z in mapBox.min.blockZ..mapBox.max.blockZ)
                            world.bukkitWorld()?.getBlockAt(x, y, z)?.blockData = Bukkit.createBlockData(Material.AIR)
                world.bukkitWorld()?.getNearbyEntities(box)?.forEach { runCatching { it.remove() } } // player会remove失败，忽略它
            }

            val tick = recordReader.readTick()
            val bukkitWorld = world.bukkitWorld()
            if (tick == null || bukkitWorld == null)
            {
                recordReader.close()
                this@submit.cancel()
                entityPool.forEach { it.value.remove() }
                world.gameStatus = GameStatus.NONE
                checkGameWorldRecycle(world.name)
                return@submit
            }

            tick.first.forEach()
            {
                bukkitWorld.getBlockAt(it.x, it.y, it.z).blockData = Bukkit.createBlockData(it.blockData)
            }

            tick.second.forEach()
            {
                if (!entityPool.containsKey(it.uuid))
                {
                    val entity = when (val type = EntityType.valueOf(it.type))
                    {
                        EntityType.PLAYER ->
                        {
                            val player = bukkitWorld.spawnEntity(Location(bukkitWorld, 0.0, -64.0, 0.0), EntityType.ZOMBIE) as Zombie
                            player.setBaby()
                            player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, Int.MAX_VALUE, 0, false, false))
                            player.customName = it.name ?: "Unknown"
                            player.isCustomNameVisible = true
                            player
                        }

                        EntityType.ITEM -> bukkitWorld.dropItem(Location(bukkitWorld, 0.0, -64.0, 0.0), it.helmet)

                        else -> bukkitWorld.spawnEntity(Location(bukkitWorld, 0.0, -64.0, 0.0), type).also()
                        {
                            if (it is Ageable)
                                if (randomDouble() < 0.5) it.setBaby()
                                else it.setAdult()
                        }
                    }
                    if (entity is LivingEntity)
                    {
                        entity.isCollidable = false
                        entity.setAI(false)
                    }
                    entity.isInvulnerable = true
                    entity.isSilent = true
                    entity.setGravity(false)
                    entityPool[it.uuid] = entity
                }
                val entity = entityPool[it.uuid]!!
                entity.teleport(Location(world.bukkitWorld(), it.x, it.y, it.z, it.yaw, it.pitch))
                entity.velocity = Vector(it.vx, it.vy, it.vz)
                if (entity is LivingEntity)
                {
                    entity.equipment?.helmet = it.helmet
                    entity.equipment?.chestplate = it.chestplate
                    entity.equipment?.leggings = it.leggings
                    entity.equipment?.boots = it.boots
                }
            }
        }
    }

    companion object
    {
        val box = BoundingBox(-24.0, 0.0, -36.0, 24.0, 10.0, 36.0)
        val mapBox = BoundingBox(-24.0, 0.0, -36.0, 24.0, 10.0, 36.0)

        fun addBlock(location: Location)
        {
            val record = location.world?.name?.let { getGameWorld(it) }?.record ?: return
            if (record.running.load() && mapBox.contains(location.toVector()))
                record.blocks += location
        }

        fun records() = recordsFolder.listFiles()?.filter { it.isFile && it.extension == "dat" }?.sortedByDescending { it.name } ?: emptyList()

        fun file(world: () -> GameWorld, name: String, period: Long = 1): GameRecord =
            file(world, File(recordsFolder, "$name.dat"), period)

        fun file(world: () -> GameWorld, file: File, period: Long = 1): GameRecord
        {
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            return GameRecord(
                world = world,
                file = file,
                period = period,
            )
        }
    }

}

data class EntityStatus(
    val uuid: String,
    val type: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val vx: Double,
    val vy: Double,
    val vz: Double,
    val name: String?,
    val helmet: ItemStack,
    val chestplate: ItemStack,
    val leggings: ItemStack,
    val boots: ItemStack,
)

data class BlockStatus(
    val x: Int,
    val y: Int,
    val z: Int,
    val blockData: String,
)

class RecordWriter(
    val file: File,
    val period: Long,
): AutoCloseable, Closeable
{
    companion object
    {
        fun randomName() = Random.nextULong().toString(36).takeLast(4)
    }

    private val tmpFile = File(file.absolutePath + "." + randomName() + ".tmp")
    private val tmpFileOutput by lazy {
        if (tmpFile.exists()) tmpFile.delete()
        DataOutputStream(tmpFile.outputStream())
    }
    private var closed = false

    override fun close(): Unit = synchronized(this)
    {
        if (closed) return
        closed = true

        tmpFileOutput.writeShort(-1)
        tmpFileOutput.flush()
        tmpFileOutput.close()

        file.delete()
        DataOutputStream(file.outputStream()).use()
        {
            it.writeLong(start)
            it.writeLong(period)
            it.writeLong(tickCount)
            it.write(Zstd.compress(tmpFile.readBytes()))
        }
        tmpFile.delete()
    }

    private var tickCount = 0L
    private val start by lazy { System.currentTimeMillis() }

    fun writeTick(blocks: List<BlockStatus>, entities: List<EntityStatus>) = synchronized(this)
    {
        if (closed) return@synchronized

        start
        tickCount++
        tmpFileOutput.writeInt(entities.size)
        for (entity in entities)
        {
            tmpFileOutput.writeUTF(entity.uuid)
            tmpFileOutput.writeUTF(entity.type)
            tmpFileOutput.writeFloat(entity.x.toFloat())
            tmpFileOutput.writeFloat(entity.y.toFloat())
            tmpFileOutput.writeFloat(entity.z.toFloat())
            tmpFileOutput.writeFloat(entity.yaw)
            tmpFileOutput.writeFloat(entity.pitch)
            tmpFileOutput.writeFloat(entity.vx.toFloat())
            tmpFileOutput.writeFloat(entity.vy.toFloat())
            tmpFileOutput.writeFloat(entity.vz.toFloat())
            tmpFileOutput.writeUTF(entity.name ?: "")
            tmpFileOutput.writeUTF(entity.helmet.takeIf { it.type.isNotAir() }?.getItemTag(false)?.toJson() ?: "")
            tmpFileOutput.writeUTF(entity.chestplate.takeIf { it.type.isNotAir() }?.getItemTag(false)?.toJson() ?: "")
            tmpFileOutput.writeUTF(entity.leggings.takeIf { it.type.isNotAir() }?.getItemTag(false)?.toJson() ?: "")
            tmpFileOutput.writeUTF(entity.boots.takeIf { it.type.isNotAir() }?.getItemTag(false)?.toJson() ?: "")
        }
        tmpFileOutput.writeInt(blocks.size)
        for (block in blocks)
        {
            tmpFileOutput.writeInt(block.x)
            tmpFileOutput.writeInt(block.y)
            tmpFileOutput.writeInt(block.z)
            tmpFileOutput.writeUTF(block.blockData)
        }
    }
}

class RecordReader(
    val file: File,
): AutoCloseable, Closeable
{
    val period by lazy()
    {
        DataInputStream(file.inputStream()).use()
        {
            it.readLong() // start time, ignored
            it.readLong()
        }
    }

    val startTime by lazy { DataInputStream(file.inputStream()).use { it.readLong() } }

    val tickCount by lazy()
    {
        DataInputStream(file.inputStream()).use()
        {
            it.readLong() // start time, ignored
            it.readLong() // period, ignored
            it.readLong()
        }
    }

    private val tmpFile = File(file.absolutePath + "." + RecordWriter.randomName() + ".tmp")
    private val tmpFileInput by lazy {
        if (tmpFile.exists()) tmpFile.delete()
        DataInputStream(file.inputStream()).use()
        {
            it.readLong()
            it.readLong()
            it.readLong() // start time, period, tick count
            tmpFile.writeBytes(Zstd.decompress(it.readBytes()))
        }
        DataInputStream(tmpFile.inputStream())
    }

    private var tickIndex = 0L

    override fun close()
    {
        tmpFileInput.close()
        tmpFile.delete()
    }

    fun readTick(): Pair<List<BlockStatus>, List<EntityStatus>>? = synchronized(this)
    {
        if (tickIndex >= tickCount || runCatching { tmpFileInput.available() }.getOrElse { 0 } == 0)
        {
            tickIndex = tickCount
            close()
            return null
        }
        tickIndex++

        val entityCount = tmpFileInput.readInt()
        val entities = mutableListOf<EntityStatus>()
        repeat(entityCount)
        { _ ->
            val uuid = tmpFileInput.readUTF()
            val type = tmpFileInput.readUTF()
            val x = tmpFileInput.readFloat().toDouble()
            val y = tmpFileInput.readFloat().toDouble()
            val z = tmpFileInput.readFloat().toDouble()
            val yaw = tmpFileInput.readFloat()
            val pitch = tmpFileInput.readFloat()
            val vx = tmpFileInput.readFloat().toDouble()
            val vy = tmpFileInput.readFloat().toDouble()
            val vz = tmpFileInput.readFloat().toDouble()
            val name = tmpFileInput.readUTF().ifEmpty { null }
            val helmet = tmpFileInput.readUTF().ifEmpty { null }?.let { ItemStack(Material.STONE).setItemTag(ItemTag.fromJson(it), false) } ?: ItemStack(Material.AIR)
            val chestplate = tmpFileInput.readUTF().ifEmpty { null }?.let { ItemStack(Material.STONE).setItemTag(ItemTag.fromJson(it), false) } ?: ItemStack(Material.AIR)
            val leggings = tmpFileInput.readUTF().ifEmpty { null }?.let { ItemStack(Material.STONE).setItemTag(ItemTag.fromJson(it), false) } ?: ItemStack(Material.AIR)
            val boots = tmpFileInput.readUTF().ifEmpty { null }?.let { ItemStack(Material.STONE).setItemTag(ItemTag.fromJson(it), false) } ?: ItemStack(Material.AIR)

            entities += EntityStatus(
                uuid,
                type,
                x, y, z,
                yaw, pitch,
                vx, vy, vz,
                name,
                helmet, chestplate, leggings, boots,
            )
        }

        val blockCount = tmpFileInput.readInt()
        val blocks = mutableListOf<BlockStatus>()
        repeat(blockCount)
        { _ ->
            val x = tmpFileInput.readInt()
            val y = tmpFileInput.readInt()
            val z = tmpFileInput.readInt()
            val blockData = tmpFileInput.readUTF()

            blocks += BlockStatus(x, y, z, blockData)
        }

        val res = Pair(blocks, entities)
        if (tickIndex >= tickCount)
            close()
        return res
    }
}
