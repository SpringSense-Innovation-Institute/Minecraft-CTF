package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.recordsFolder
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.chat.colored
import taboolib.module.nms.ItemTag
import taboolib.module.nms.getItemTag
import taboolib.module.nms.setItemTag
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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

    private val tmpFile = File(file.absolutePath + ".tmp")

    fun start()
    {
        if (!running.compareAndSet(expectedValue = false, newValue = true) || done.load()) return

        file.parentFile.mkdirs()
        if (tmpFile.exists()) tmpFile.delete()
        val output = DataOutputStream(tmpFile.outputStream())

        output.writeLong(period)
        output.writeLong(System.currentTimeMillis())

        val entityPool = mutableMapOf<UUID, Pair<Short, EntityStatus>>()
        var init = true

        task = submit(period = period)
        {
            if (!running.load() || done.load())
            {
                output.writeShort(-1)
                output.flush()
                output.close()

                file.delete()
                tmpFile.renameTo(file)

                task?.cancel()

                checkGameWorldRecycle(world.name)

                return@submit
            }

            val entities = world.bukkitWorld().getNearbyEntities(box)
            output.writeShort((entities.map { it.uniqueId } + entityPool.keys).distinct().size)
            for (entity in entities)
            {
                val status = EntityStatus(
                    x = entity.location.x,
                    y = entity.location.y,
                    z = entity.location.z,
                    yaw = entity.location.yaw,
                    pitch = entity.location.pitch,
                    vx = entity.velocity.x,
                    vy = entity.velocity.y,
                    vz = entity.velocity.z,
                )

                if (!entityPool.containsKey(entity.uniqueId))
                {
                    val id = entityPool.size.toShort()
                    entityPool[entity.uniqueId] = Pair(id, status)
                    output.writeShort(id.toInt())
                    output.writeUTF(entity.type.name)

                    if (entity.type == EntityType.PLAYER)
                    {
                        val player = entity as Player
                        if (world.getPlayerTeam(player.name) == PlayerTeam.LEFT)
                            output.writeUTF("&c${player.name}".colored())
                        else if (world.getPlayerTeam(player.name) == PlayerTeam.RIGHT)
                            output.writeUTF("&b${player.name}".colored())
                        else
                            output.writeUTF(player.name)
                    }

                    if (entity.type == EntityType.ITEM)
                    {
                        val itemFrame = entity as Item
                        val item = itemFrame.itemStack
                        output.writeUTF(item.type.name)
                        output.writeByte(item.amount)
                        output.writeUTF(item.getItemTag(false).toJson())
                    }
                }
                else if (entityPool[entity.uniqueId]!!.second == status && status.isStationary())
                {
                    output.writeShort(-1)
                    continue
                }
                else
                {
                    output.writeShort(entityPool[entity.uniqueId]!!.first.toInt())
                }

                output.writeFloat(entity.location.x.toFloat())
                output.writeFloat(entity.location.y.toFloat())
                output.writeFloat(entity.location.z.toFloat())
                output.writeFloat(entity.location.yaw)
                output.writeFloat(entity.location.pitch)
                output.writeFloat(entity.velocity.x.toFloat())
                output.writeFloat(entity.velocity.y.toFloat())
                output.writeFloat(entity.velocity.z.toFloat())
            }

            for ((uuid, info) in entityPool)
            {
                if (entities.any { it.uniqueId == uuid }) continue
                val status = EntityStatus(
                    x = 0.0,
                    y = -64.0,
                    z = 0.0,
                    yaw = 0.0f,
                    pitch = 0.0f,
                    vx = 0.0,
                    vy = 0.0,
                    vz = 0.0,
                )
                if (info.second == status)
                {
                    output.writeShort(-1)
                    continue
                }
                output.writeShort(info.first.toInt())
                output.writeFloat(0.0f)
                output.writeFloat(-64.0f)
                output.writeFloat(0.0f)
                output.writeFloat(0.0f)
                output.writeFloat(0.0f)
                output.writeFloat(0.0f)
                output.writeFloat(0.0f)
                output.writeFloat(0.0f)
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
                            if (world.bukkitWorld().getBlockAt(x, y, z).blockData.material != Material.AIR)
                                blocks += Location(world.bukkitWorld(), x.toDouble(), y.toDouble(), z.toDouble())
            }

            output.writeShort(blocks.size)
            for (block in blocks)
            {
                output.writeByte(block.blockX)
                output.writeByte(block.blockY)
                output.writeByte(block.blockZ)
                output.writeUTF(block.block.blockData.asString)
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

        val input = DataInputStream(file.inputStream())

        period = input.readLong()
        input.readLong() // start time, currently unused

        val entityPool = mutableMapOf<Short, Entity>()

        var init = true

        task = submit(period = period)
        {
            if (init)
            {
                world.bukkitWorld().difficulty = Difficulty.HARD
                init = false
                for (x in mapBox.min.blockX..mapBox.max.blockX)
                    for (y in mapBox.min.blockY..mapBox.max.blockY)
                        for (z in mapBox.min.blockZ..mapBox.max.blockZ)
                            world.bukkitWorld().getBlockAt(x, y, z).blockData = Bukkit.createBlockData(Material.AIR)
                world.bukkitWorld().getNearbyEntities(box).forEach { runCatching { it.remove() } } // player会remove失败，忽略它
            }

            if (input.available() == 0)
            {
                input.close()
                task?.cancel()
                entityPool.forEach()
                {
                    it.value.remove()
                }
                world.gameStatus = GameStatus.NONE
                checkGameWorldRecycle(world.name)
                return@submit
            }

            val entityCount = input.readShort()
            if (entityCount.toInt() == -1) return@submit

            repeat(entityCount.toInt())
            {
                val id = input.readShort()
                if (id.toInt() == -1) return@repeat

                if (!entityPool.containsKey(id))
                {
                    val entity = when (val type = EntityType.valueOf(input.readUTF()))
                    {
                        EntityType.PLAYER ->
                        {
                            val name = input.readUTF()
                            val player = world.bukkitWorld().spawnEntity(Location(world.bukkitWorld(), 0.0, -64.0, 0.0), EntityType.CREEPER) as Creeper
                            player.customName = name
                            player.isCustomNameVisible = true
                            player
                        }

                        EntityType.ITEM ->
                        {
                            val material = Material.valueOf(input.readUTF())
                            val amount = input.readUnsignedByte()
                            val itemStack = ItemStack(material, amount)
                            val itemTagJson = input.readUTF()
                            itemStack.setItemTag(ItemTag.fromJson(itemTagJson))
                            val item = world.bukkitWorld().dropItem(Location(world.bukkitWorld(), 0.0, -64.0, 0.0), itemStack)
                            item
                        }

                        else -> world.bukkitWorld().spawnEntity(Location(world.bukkitWorld(), 0.0, -64.0, 0.0), type)
                    }
                    if (entity is LivingEntity)
                    {
                        entity.isCollidable = false
                        entity.setAI(false)
                    }
                    entity.isInvulnerable = true
                    entity.isSilent = true
                    entity.setGravity(false)
                    entityPool[id] = entity
                }
                val entity = entityPool[id]!!

                val x = input.readFloat()
                val y = input.readFloat()
                val z = input.readFloat()
                val yaw = input.readFloat()
                val pitch = input.readFloat()
                val vx = input.readFloat()
                val vy = input.readFloat()
                val vz = input.readFloat()

                entity.teleport(Location(world.bukkitWorld(), x.toDouble(), y.toDouble(), z.toDouble(), yaw, pitch))
                entity.velocity = Vector(vx, vy, vz)
            }

            val blockCount = input.readShort()
            repeat(blockCount.toInt())
            {
                val x = input.readByte().toInt()
                val y = input.readByte().toInt()
                val z = input.readByte().toInt()
                val blockData = input.readUTF()
                world.bukkitWorld().getBlockAt(x, y, z).blockData = Bukkit.createBlockData(blockData)
            }
        }
    }

    companion object
    {
        val box = BoundingBox(-24.0, -4.0, -36.0, 24.0, 10.0, 36.0)
        val mapBox = BoundingBox(-24.0, -4.0, -36.0, 24.0, 10.0, 36.0)

        fun addBlock(location: Location)
        {
            val record = location.world?.name?.let { getGameWorld(it) }?.record ?: return
            if (record.running.load() && mapBox.contains(location.toVector()))
                record.blocks += location
        }

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

    data class EntityStatus(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
        val vx: Double,
        val vy: Double,
        val vz: Double,
    )
    {
        fun isStationary(): Boolean = vx == 0.0 && vy == 0.0 && vz == 0.0
    }
}