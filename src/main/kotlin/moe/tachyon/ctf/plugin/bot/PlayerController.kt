package moe.tachyon.ctf.plugin.bot

import com.mojang.authlib.GameProfile
import io.netty.channel.Channel
import io.netty.channel.embedded.EmbeddedChannel
import net.minecraft.network.NetworkManager
import net.minecraft.network.protocol.EnumProtocolDirection
import net.minecraft.network.protocol.Packet
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.EntityPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.PlayerConnection
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import taboolib.common.util.unsafeLazy
import taboolib.module.nms.nmsProxy
import java.net.InetSocketAddress
import java.util.*

data class PlayerInput(
    val yaw: Float, val pitch: Float,
    val w: Boolean, val a: Boolean, val s: Boolean, val d: Boolean,
    val jump: Boolean, val sneak: Boolean, val sprint: Boolean, val fly: Boolean,
    val attack: UUID?
)
{
    companion object
    {
        val EMPTY = PlayerInput(
            yaw = 0f,
            pitch = 0f,
            w = false,
            a = false,
            s = false,
            d = false,
            jump = false,
            sneak = false,
            sprint = false,
            fly = false,
            attack = null
        )
    }
}

abstract class BotNMSHandler
{
    abstract fun spawnFakePlayer(world: World, name: String, location: Location): Player
    abstract fun tickBot(player: Player, input: PlayerInput)

    companion object
    {
        val instance by unsafeLazy { nmsProxy<BotNMSHandler>() }
    }
}

class BotNMSHandlerImpl: BotNMSHandler()
{
    override fun spawnFakePlayer(world: World, name: String, location: Location): Player
    {
        val nmsServer = (Bukkit.getServer() as org.bukkit.craftbukkit.v1_21_R1.CraftServer).server
        val nmsWorld = (world as org.bukkit.craftbukkit.v1_21_R1.CraftWorld).handle
        val profile = GameProfile(UUID.randomUUID(), name)
        val bot = EntityPlayer(nmsServer, nmsWorld, profile, ClientInformation.createDefault())

        val fakeConnection = NetworkManager(EnumProtocolDirection.SERVERBOUND)

        val channelField = NetworkManager::class.java.declaredFields.firstOrNull { it.type == Channel::class.java }
        channelField?.isAccessible = true
        channelField?.set(fakeConnection, EmbeddedChannel())
        channelField?.isAccessible = false

        val cookie = CommonListenerCookie.createInitial(profile, false)

        fakeConnection.address = InetSocketAddress(0)
        val listener = object: PlayerConnection(nmsServer, fakeConnection, bot, cookie)
        {
            override fun send(packet: Packet<*>) = Unit
            override fun tick() = Unit
        }
        bot.connection = listener

        bot.setPos(location.x, location.y, location.z)
        bot.yRot = location.yaw
        bot.xRot = location.pitch

        nmsServer.playerList.placeNewPlayer(fakeConnection, bot, cookie)
        return org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer(
            Bukkit.getServer() as org.bukkit.craftbukkit.v1_21_R1.CraftServer,
            bot
        )
    }

    override fun tickBot(player: Player, input: PlayerInput)
    {
        val nmsPlayer = (player as org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer).handle
        val oldLocation = player.location.clone()

        nmsPlayer.yRot = input.yaw
        nmsPlayer.xRot = input.pitch
        nmsPlayer.yHeadRot = input.yaw
        nmsPlayer.yBodyRot = input.yaw

        nmsPlayer.isShiftKeyDown = input.sneak
        nmsPlayer.isSprinting = input.sprint && input.w

        nmsPlayer.abilities.flying = input.fly && nmsPlayer.abilities.mayfly

        var forward = 0f
        var strafe = 0f

        if (input.w) forward += 1f
        if (input.s) forward -= 1f
        if (input.a) strafe += 1f
        if (input.d) strafe -= 1f

        if (input.sneak)
        {
            forward *= 0.3f
            strafe *= 0.3f
        }

        nmsPlayer.xxa = strafe
        nmsPlayer.zza = forward
        nmsPlayer.setJumping(input.jump)
        nmsPlayer.aiStep()
        nmsPlayer.detectEquipmentUpdates()

        val newLocation = player.location.clone()

        if (oldLocation != newLocation)
        {
            val event = PlayerMoveEvent(player, oldLocation, newLocation)
            Bukkit.getPluginManager().callEvent(event)
            if (event.isCancelled)
            {
                nmsPlayer.setPos(oldLocation.x, oldLocation.y, oldLocation.z)
                nmsPlayer.yRot = oldLocation.yaw
                nmsPlayer.xRot = oldLocation.pitch
                nmsPlayer.yHeadRot = oldLocation.yaw
                nmsPlayer.yBodyRot = oldLocation.yaw
            }
        }


        if (input.attack != null)
        {
            val targetBukkit = Bukkit.getEntity(input.attack)
            if (targetBukkit != null)
            {
                val targetNMS = (targetBukkit as org.bukkit.craftbukkit.v1_21_R1.entity.CraftEntity).handle
                val reach = if (nmsPlayer.isCreative) 5.0 else 3.0
                val eyePos = nmsPlayer.eyePosition
                val targetBox = targetNMS.boundingBox
                val distanceSq = targetBox.distanceToSqr(eyePos)
                if (distanceSq <= reach * reach)
                {
                    nmsPlayer.attack(targetNMS)
                    nmsPlayer.resetAttackStrengthTicker()
                    nmsPlayer.swing(net.minecraft.world.EnumHand.MAIN_HAND, true)
                }
            }
        }
    }
}