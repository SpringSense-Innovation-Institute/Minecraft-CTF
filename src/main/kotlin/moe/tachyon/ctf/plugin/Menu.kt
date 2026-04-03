package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.recordsFolder
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
        "# A B C #",
        "#       #",
        "#########"
    )

    set('#', buildItem(Material.GRAY_STAINED_GLASS_PANE) { name = " " }) { }
    set('X', buildItem(Material.BARRIER) { name = "关闭" }) { clicker.closeInventory() }

    set('A', buildItem(Material.RED_WOOL) { name = "游戏管理" })
    {
        context(GameSettingData())
        {
            clicker.openGameSettingsMenu()
        }
    }
    set('C', buildItem(Material.BLUE_WOOL) { name = "回放管理" })
    {
        context(GameSettingData())
        {
            clicker.openReplaySettingsMenu()
        }
    }

    set('B', buildItem(Material.ORANGE_WOOL) { name = "进入已有世界" })
    {
        clicker.chooseWorldMenu()
    }
}

fun HumanEntity.openWorldMenu()
{
    val world = getGameWorld(world.name) ?: return openMainMenu()
    openMenu<Chest>()
    {
        title = "世界导航"
        rows(1)
        if (isOp) map("  A   B  ")
        else      map("    A    ")

        set('A', buildItem(Material.GREEN_WOOL) { name = "回到主世界" })
        {
            clicker.closeInventory()
            clicker.teleportToWorld()
        }

        if (!isOp) return@openMenu

        if (world.gameStatus.preparing())
        {
            set('B', buildItem(Material.RED_WOOL)
            {
                name = "游戏准备中"
                lore += "若所有玩家离开，游戏将被自动取消"
            }) { }
        }
        else if (world.gameStatus.inGame())
        {
            set('B', buildItem(Material.RED_WOOL)
            {
                name = "游戏进行中"
                lore += "点击结束游戏"
            })
            {
                clicker.closeInventory()
                world.stopGame()
            }
        }
        else if (world.gameStatus.replaying())
        {
            set('B', buildItem(Material.RED_WOOL)
            {
                name = "回放进行中"
            }) { }
        }
        else /* world.gameStatus == NONE */
        {
            set('B', buildItem(Material.RED_WOOL)
            {
                name = "等待回收"
                lore += "世界已闲置，等待所有玩家离开后将被回收"
            }) { }
        }
    }
}

fun HumanEntity.chooseWorldMenu(): Unit = openMenu<PageableChest<GameWorld>>()
{
    title = "选择世界"
    rows(6)
    map(
        "########X",
        "#@@@@@@@#",
        "#@@@@@@@#",
        "#@@@@@@@#",
        "#@@@@@@@#",
        "##P#R#N##"
    )
    elements { getGameWorlds() }

    set('#', buildItem(Material.GRAY_STAINED_GLASS_PANE) { name = " " }) { }
    set('X', buildItem(Material.BARRIER) { name = "关闭" }) { clicker.closeInventory() }
    set('R', buildItem(Material.COMPASS) { name = "刷新" }) { clicker.chooseWorldMenu() }

    slotsBy('@')

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
            name = element.name
            lore += "世界状态: ${when (element.gameStatus)
            {
                GameStatus.REPLAYING -> "正在回放对局"
                GameStatus.PREPARING -> "正在准备对局"
                GameStatus.IN_GAME -> "对局进行中"
                GameStatus.NONE -> "等待被回收"
            }}"
            lore += "红队组号: ${element.leftGroupNumber}"
            lore += "蓝队组号: ${element.rightGroupNumber}"
        }
    }


    onClick()
    { event, element ->
        event.clicker.closeInventory()
        event.clicker.teleport(LocationStatus.IN_FIELD.location(element))
    }
}

context(data: GameSettingData)
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
        lore += "&c当前红队组号: ${data.leftGroupNumber}".colored()
        lore += "&9当前蓝队组号: ${data.rightGroupNumber}".colored()
        lore += "每组玩家人数: ${data.playerCountPerGroup}".colored()
    }) { clicker.openPlayerGroupingMenu() }
    set('B', buildItem(Material.MAP) { name = "地图设置" }) { clicker.openMapSettingsMenu() }
    set('C', buildItem(Material.COMPASS) { name = "游戏控制" }) { clicker.openGameControlMenu() }
}

context(data: GameSettingData)
fun HumanEntity.openPlayerGroupingMenu()
{
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
            lore += "当前组号: ${data.leftGroupNumber}"
        }) { clicker.openGroupInputMenu(PlayerTeam.LEFT) }
        set('B', buildItem(Material.BLUE_WOOL)
        {
            name = "蓝队"
            lore += "当前组号: ${data.rightGroupNumber}"
        }) { clicker.openGroupInputMenu(PlayerTeam.RIGHT) }
        set('C', buildItem(Material.OMINOUS_TRIAL_KEY)
        {
            name = "每组玩家人数"
            lore += "当前设置: ${data.playerCountPerGroup}"
        }) { clicker.openPlayerCountInputMenu() }
    }
}

context(data: GameSettingData)
fun HumanEntity.openMapSettingsMenu()
{
    openMenu<Chest>()
    {
        title = "地图设置"
        rows(1)
        map("  A   B  ")

        set('A', buildItem(Material.GRASS_BLOCK)
        {
            name = if (data.randomMap) "随机地图 (当前)" else "随机地图"
            if (data.randomMap) enchants[Enchantment.MENDING] = 1
        })
        {
            data.randomMap = true
            clicker.openGameSettingsMenu()
        }

        set('B', buildItem(Material.OAK_SIGN)
        {
            name = if (!data.randomMap) "固定地图 (当前)" else "固定地图"
            if (!data.randomMap) enchants[Enchantment.MENDING] = 1
        })
        {
            data.randomMap = false
            clicker.openGameSettingsMenu()
        }
    }
}

context(data: GameSettingData)
fun HumanEntity.openGroupInputMenu(team: PlayerTeam): Unit = openMenu<Anvil>()
{
    title = if (team == PlayerTeam.LEFT) "输入红队组号" else "输入蓝队组号"
    set(0, buildItem(Material.PAPER) { name = if (team == PlayerTeam.LEFT) "${data.leftGroupNumber}" else "${data.rightGroupNumber}" })

    onRename()
    { player, text, inventory ->
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
            data.leftGroupNumber = text.toInt()
        else
            data.rightGroupNumber = text.toInt()
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

context(data: GameSettingData)
fun HumanEntity.openPlayerCountInputMenu(): Unit = openMenu<Anvil>()
{
    title = "输入每组玩家人数"
    set(0, buildItem(Material.PAPER) { name = "${data.playerCountPerGroup}" })

    onRename()
    { player, text, inventory ->
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
        data.playerCountPerGroup = text.toInt()
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

context(data: GameSettingData)
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

    set('A', buildItem(Material.GREEN_WOOL)
    {
        name = "准备开始"
        lore += "将创建新维度并在新纬度中进行比赛"
    })
    {
        clicker.closeInventory()
        val world = createGame(data)
        clicker.teleport(LocationStatus.IN_FIELD.location(world))
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
                                replayRecord(recordFile)
                            else
                                event.clicker.sendMessage("&c下载出现了问题，回放文件不存在!".colored())
                        }
                    }
                    else
                    {
                        val recordFile = recordsFolder.resolve(element.first + ".dat")
                        if (recordFile.exists())
                            replayRecord(recordFile)
                        else
                            event.clicker.sendMessage("&c回放文件不存在!".colored())
                    }
                }

                elements { allItems }
            }
        }
    }
}