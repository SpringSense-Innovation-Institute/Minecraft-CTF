package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.gameStatus
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.leftGroup
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.playerCountPerGroup
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.randomMap
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.recordsFolder
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.rightGroup
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.HumanEntity
import taboolib.module.chat.colored
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Anvil
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem
import taboolib.platform.util.submit

fun HumanEntity.openMainMenu(): Unit = openMenu<Chest>()
{
    title = "CTF 管理菜单"
    rows(5)
    map(
        "########X",
        "#       #",
        "#  A B  #",
        "#       #",
        "#########"
    )

    set('#', buildItem(Material.GRAY_STAINED_GLASS_PANE) { name = " " }) { }
    set('X', buildItem(Material.BARRIER) { name = "关闭" }) { clicker.closeInventory() }

    set('A', buildItem(Material.RED_WOOL) { name = "游戏管理" }) { clicker.openGameSettingsMenu() }
    set('B', buildItem(Material.BLUE_WOOL) { name = "回放管理" }) { clicker.openReplaySettingsMenu() }
}

fun HumanEntity.openGameSettingsMenu(): Unit = openMenu<Chest>()
{
    title = "游戏设置"
    rows(5)
    map(
        "<#######X",
        "#       #",
        "# A B C #",
        "#       #",
        "#########"
    )

    set('#', buildItem(Material.GRAY_STAINED_GLASS_PANE) { name = " " }) { }
    set('X', buildItem(Material.BARRIER) { name = "关闭" }) { clicker.closeInventory() }
    set('<', buildItem(Material.ARROW) { name = "返回" }) { clicker.openMainMenu() }
    set('A', buildItem(Material.PLAYER_HEAD)
    {
        name = "玩家分组"
        lore += "&c当前红队组号: $leftGroup".colored()
        lore += "&9当前蓝队组号: $rightGroup".colored()
        lore += "每组玩家人数: $playerCountPerGroup"
    }) { clicker.openPlayerGroupingMenu() }
    set('B', buildItem(Material.MAP) { name = "地图设置" }) { clicker.openMapSettingsMenu() }
    set('C', buildItem(Material.COMPASS) { name = "游戏控制" }) { clicker.openGameControlMenu() }
}

fun HumanEntity.openPlayerGroupingMenu()
{
    if (gameStatus != GameStatus.NONE)
    {
        sendMessage("当前游戏状态为 ${gameStatus.name}，无法修改玩家分组!")
        return closeInventory()
    }
    openMenu<Chest>()
    {
        title = "玩家分组设置"
        rows(5)
        map(
            "<#######X",
            "#       #",
            "# A C B #",
            "#       #",
            "#########"
        )

        set('#', buildItem(Material.GRAY_STAINED_GLASS_PANE) { name = " " }) { }
        set('X', buildItem(Material.BARRIER) { name = "关闭" }) { clicker.closeInventory() }
        set('<', buildItem(Material.ARROW) { name = "返回" }) { clicker.openGameSettingsMenu() }
        set('A', buildItem(Material.RED_WOOL)
        {
            name = "红队"
            lore += "当前组号: $leftGroup"
        }) { clicker.openGroupInputMenu(PlayerTeam.LEFT) }
        set('B', buildItem(Material.BLUE_WOOL)
        {
            name = "蓝队"
            lore += "当前组号: $rightGroup"
        }) { clicker.openGroupInputMenu(PlayerTeam.RIGHT) }
        set('C', buildItem(Material.OMINOUS_TRIAL_KEY)
        {
            name = "每组玩家人数"
            lore += "当前设置: $playerCountPerGroup"
        }) { clicker.openPlayerCountInputMenu() }
    }
}

fun HumanEntity.openMapSettingsMenu()
{
    if (gameStatus != GameStatus.NONE)
    {
        sendMessage("当前游戏状态为 ${gameStatus.name}，无法修改玩家分组!")
        return closeInventory()
    }

    openMenu<Chest>()
    {
        title = "地图设置"
        rows(1)
        map("  A   B  ")

        set('A', buildItem(Material.GRASS_BLOCK)
        {
            name = if (randomMap) "随机地图 (当前)" else "随机地图"
            if (randomMap) enchants[Enchantment.MENDING] = 1
        })
        {
            randomMap = true
            clicker.openGameSettingsMenu()
        }

        set('B', buildItem(Material.OAK_SIGN)
        {
            name = if (!randomMap) "固定地图 (当前)" else "固定地图"
            if (!randomMap) enchants[Enchantment.MENDING] = 1
        })
        {
            randomMap = false
            clicker.openGameSettingsMenu()
        }
    }
}

fun HumanEntity.openGroupInputMenu(team: PlayerTeam): Unit = openMenu<Anvil>()
{
    title = if (team == PlayerTeam.LEFT) "输入红队组号" else "输入蓝队组号"
    set(0, buildItem(Material.PAPER) { name = if (team == PlayerTeam.LEFT) "$leftGroup" else "$rightGroup" })

    onRename { player, text, inventory ->
        if (text.toIntOrNull() == null)
        {
            inventory.setItem(2, buildItem(Material.RED_STAINED_GLASS_PANE)
            {
                name = "请输入一个有效的整数!"
            })
        }
        else
        {
            inventory.setItem(2, buildItem(Material.GREEN_STAINED_GLASS_PANE)
            {
                name = text
            })
        }
    }

    onClick(2)
    {
        val text = it.inventory.getItem(2)?.itemMeta?.displayName
        if (text?.toIntOrNull() == null)
            return@onClick it.inventory.setItem(2, buildItem(Material.RED_STAINED_GLASS_PANE) { name = "请输入一个有效的整数!" })
        if (team == PlayerTeam.LEFT)
            leftGroup = text.toInt()
        else
            rightGroup = text.toInt()
        it.clicker.openPlayerGroupingMenu()
    }
    
    onClose(true)
    {
        submit(delay = 1)
        {
            it.player.openPlayerGroupingMenu()
        }
    }
}

fun HumanEntity.openPlayerCountInputMenu(): Unit = openMenu<Anvil>()
{
    title = "输入每组玩家人数"
    set(0, buildItem(Material.PAPER) { name = "$playerCountPerGroup" })

    onRename { player, text, inventory ->
        if (text.toIntOrNull() == null)
        {
            inventory.setItem(2, buildItem(Material.RED_STAINED_GLASS_PANE)
            {
                name = "请输入一个有效的整数!"
            })
        }
        else
        {
            inventory.setItem(2, buildItem(Material.GREEN_STAINED_GLASS_PANE)
            {
                name = text
            })
        }
    }

    onClick(2)
    {
        val text = it.inventory.getItem(2)?.itemMeta?.displayName
        if (text?.toIntOrNull() == null)
            return@onClick it.inventory.setItem(2, buildItem(Material.RED_STAINED_GLASS_PANE) { name = "请输入一个有效的整数!" })
        playerCountPerGroup = text.toInt()
        it.clicker.openPlayerGroupingMenu()
    }

    onClose(true)
    {
        submit(delay = 1)
        {
            it.player.openPlayerGroupingMenu()
        }
    }
}

fun HumanEntity.openGameControlMenu(): Unit = openMenu<Chest>()
{
    title = "游戏控制"
    rows(5)
    map(
        "<#######X",
        "#       #",
        "#   A   #",
        "#       #",
        "#########"
    )

    set('#', buildItem(Material.GRAY_STAINED_GLASS_PANE) { name = " " }) { }
    set('X', buildItem(Material.BARRIER) { name = "关闭" }) { clicker.closeInventory() }
    set('<', buildItem(Material.ARROW) { name = "返回" }) { clicker.openGameSettingsMenu() }

    onClick('A')
    {
        when (gameStatus)
        {
            GameStatus.NONE      -> prepareGame()
            GameStatus.PREPARING -> stopGame(PlayerTeam.NONE)
            GameStatus.IN_GAME   -> stopGame()
        }
    }

    onBuild()
    { _, inventory ->
        submit(period = 1)
        {
            if (inventory.viewers.isEmpty()) return@submit cancel()
            val slot = getFirstSlot('A')
            when (gameStatus)
            {
                GameStatus.NONE      ->
                {
                    inventory.setItem(slot, buildItem(Material.GREEN_WOOL) { name = "准备开始" })
                }
                GameStatus.PREPARING ->
                {
                    inventory.setItem(slot, buildItem(Material.RED_WOOL)
                    {
                        name = "等待玩家就绪中...(点击取消)"
                        val status = getPlayersPreparedStatus()
                        lore += "当前状态: ${
                            if (status.leftPlayerCount < playerCountPerGroup)
                                "红队玩家不足(${status.leftPlayerCount}/$playerCountPerGroup)"
                            else if (status.rightPlayerCount < playerCountPerGroup)
                                "蓝队玩家不足(${status.rightPlayerCount}/$playerCountPerGroup)"
                            else if (status.leftPlayerCount > playerCountPerGroup)
                                "红队玩家过多(${status.leftPlayerCount}/$playerCountPerGroup)"
                            else if (status.rightPlayerCount > playerCountPerGroup)
                                "蓝队玩家过多(${status.rightPlayerCount}/$playerCountPerGroup)"
                            else if (status.leftGroupUnprepared.isNotEmpty() || status.rightGroupUnprepared.isNotEmpty())
                                "等待玩家就绪中..."
                            else
                                "所有玩家已就绪，准备就绪!"
                        }"
                        lore += ""
                        lore += "左队玩家情况(${status.leftPlayerCount}/$playerCountPerGroup):"
                        for (player in status.leftGroupPrepared)
                            lore += "  &a$player 已就绪".colored()
                        for (player in status.leftGroupUnprepared)
                            lore += "  &c$player 未就绪".colored()
                        lore += "右队玩家情况(${status.rightPlayerCount}/$playerCountPerGroup):"
                        for (player in status.rightGroupPrepared)
                            lore += "  &a$player 已就绪".colored()
                        for (player in status.rightGroupUnprepared)
                            lore += "  &c$player 未就绪".colored()
                    })
                }
                GameStatus.IN_GAME   ->
                {
                    inventory.setItem(slot, buildItem(Material.RED_WOOL) { name = "立刻结束" })
                }
            }
        }
    }
}

fun HumanEntity.openReplaySettingsMenu()
{
    submit(async = true)
    {
        val items = recordsFolder.listFiles { file -> file.extension == "dat" }?.toList() ?: emptyList()
        val cloudItems = getRecords()

        val allItems = (items.map { Triple(it.nameWithoutExtension, it.length(), false) } + cloudItems.map { Triple(it.first, it.second, true) })
            .distinctBy { it.first }
            .sortedByDescending { it.first }

        submit(async = false)
        {
            openMenu<PageableChest<Triple<String, Long, Boolean>>>()
            {
                rows(6)

                map(
                    "<#######X",
                    "#@@@@@@@#",
                    "#@@@@@@@#",
                    "#@@@@@@@#",
                    "#@@@@@@@#",
                    "##P#R#N##"
                )

                slotsBy('@')

                set('#', buildItem(Material.GRAY_STAINED_GLASS_PANE) { name = " " }) { }
                set('X', buildItem(Material.BARRIER) { name = "关闭" }) { clicker.closeInventory() }
                set('<', buildItem(Material.ARROW) { name = "返回" }) { clicker.openMainMenu() }
                set('R', buildItem(Material.COMPASS) { name = "刷新" }) { clicker.openReplaySettingsMenu() }

                setPreviousPage(47)
                { page, hasPreviousPage ->
                    buildItem(if (hasPreviousPage) Material.GREEN_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE)
                    {
                        name = if (hasPreviousPage) "上一页(${page})" else "没有上一页了"
                    }
                }

                setNextPage(51)
                { page, hasNextPage ->
                    buildItem(if (hasNextPage) Material.GREEN_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE)
                    {
                        name = if (hasNextPage) "下一页(${page + 2})" else "没有下一页了"
                    }
                }

                onGenerate()
                { _, element, _, _ ->
                    buildItem(Material.PAPER)
                    {
                        name = element.first
                        lore += "点击播放回放"
                        lore +=
                            if (element.second < 1024)
                                "文件大小: ${element.second} bytes"
                            else if (element.second < 1024 * 1024)
                                "文件大小: ${"%.2f".format(element.second / 1024.0)} KB"
                            else
                                "文件大小: ${"%.2f".format(element.second / (1024.0 * 1024.0))} MB"
                        lore += if (element.third) "存储位置: 云端" else "存储位置: 本地"
                    }
                }

                onClick()
                { event, element ->
                    event.clicker.closeInventory()
                    if (gameStatus == GameStatus.NONE)
                    {
                        if (element.third)
                        {
                            event.clicker.closeInventory()
                            submit(async = true)
                            {
                                val downloadBar = Bukkit.createBossBar(
                                    "&e正在下载回放...(${element.first})".colored(),
                                    BarColor.BLUE,
                                    BarStyle.SEGMENTED_20
                                )

                                try
                                {
                                    downloadBar.addPlayer(event.clicker)
                                    downloadRecord(element.first)
                                    { cur, tot ->
                                        if (tot == 0L) return@downloadRecord
                                        downloadBar.progress = (cur.toDouble() / tot.toDouble()).coerceIn(0.0, 1.0)
                                    }
                                }
                                finally
                                {
                                    downloadBar.removeAll()
                                }

                                val recordFile = recordsFolder.resolve(element.first + ".dat")
                                if (recordFile.exists())
                                    GameRecord.file(recordFile).replay()
                                else
                                    event.clicker.sendMessage("&c下载出现了问题，回放文件不存在!".colored())
                            }
                        }
                        else
                        {
                            val recordFile = recordsFolder.resolve(element.first + ".dat")
                            if (recordFile.exists())
                                GameRecord.file(recordFile).replay()
                            else
                                event.clicker.sendMessage("&c回放文件不存在!".colored())
                        }
                    }
                    else
                        event.clicker.sendMessage("&c当前场地有游戏正在进行，无法播放回放!".colored())
                }

                elements { allItems }
            }
        }
    }
}