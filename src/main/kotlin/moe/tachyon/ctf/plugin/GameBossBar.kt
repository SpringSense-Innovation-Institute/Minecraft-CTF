package moe.tachyon.ctf.plugin

import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import taboolib.module.chat.colored

/**
 * 比赛倒计时 BossBar（"虚假血条"）：仅展示给该 [GameWorld] 当前世界内的玩家。
 *
 * 生命周期由 [GameWorld.startGame] / [GameWorld.stopGame] 驱动：
 * - [start]：在对局正式开始的第一个 tick（tick == 0）调用一次。
 * - [update]：每 tick 调用一次，按剩余 tick 同步标题、进度、颜色。
 * - [stop]：在 [GameWorld.stopGame] 入口处调用，移除所有玩家的 bar。
 *
 * 玩家进出世界的同步由 [refreshPlayers] 在每次 [update] 时完成，因此无需监听
 * PlayerQuitEvent / PlayerChangedWorldEvent；最坏情况下滞后 1 tick（50ms）。
 */
class GameBossBar(private val gameWorld: GameWorld)
{
    fun start()
    {
        if (bar != null) return
        bar = Bukkit.createBossBar(
            formatTitle(TOTAL_TICKS),
            BarColor.GREEN,
            BarStyle.SEGMENTED_20,
        ).also { it.progress = 1.0 }
        refreshPlayers()
    }

    fun update(remainingTicks: Long)
    {
        val bar = bar ?: return
        val clamped = remainingTicks.coerceIn(0L, TOTAL_TICKS)
        bar.setTitle(formatTitle(clamped))
        bar.progress = (clamped.toDouble() / TOTAL_TICKS).coerceIn(0.0, 1.0)
        bar.setColor(colorFor(clamped))
        refreshPlayers()
    }

    fun stop()
    {
        bar?.removeAll()
        bar = null
        shownPlayers.clear()
    }

    private var bar: BossBar? = null
    private val shownPlayers = mutableSetOf<String>()

    private fun colorFor(remainingTicks: Long): BarColor = when
    {
        remainingTicks > TOTAL_TICKS / 2 -> BarColor.GREEN
        remainingTicks > TOTAL_TICKS / 4 -> BarColor.YELLOW
        else                             -> BarColor.RED
    }

    /**
     * 将 bar 的可见玩家集合同步为"当前世界内的玩家"。
     * 新进入世界的玩家会被 addPlayer；离开的会被 removePlayer。
     */
    private fun refreshPlayers()
    {
        val bar = bar ?: return
        val current = gameWorld.getBukkitPlayers().map { it.name }.toSet()

        (shownPlayers - current).forEach { name ->
            Bukkit.getPlayer(name)?.let { bar.removePlayer(it) }
        }
        (current - shownPlayers).forEach { name ->
            Bukkit.getPlayer(name)?.let { bar.addPlayer(it) }
        }

        shownPlayers.clear()
        shownPlayers.addAll(current)
    }

    private fun formatTitle(remainingTicks: Long): String
    {
        val totalSeconds = (remainingTicks / TPS).coerceAtLeast(0L)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "&6剩余时间: &f${minutes}:${seconds.toString().padStart(2, '0')}".colored()
    }

    companion object
    {
        /** 一局比赛总时长，单位 tick。与 GameWorld.startGame 中的 `3L * 60 * 20` 保持一致。 */
        const val TOTAL_TICKS: Long = 3L * 60 * 20

        private const val TPS: Long = 20L
    }
}
