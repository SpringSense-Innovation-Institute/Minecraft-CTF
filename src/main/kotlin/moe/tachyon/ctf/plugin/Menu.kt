package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.mainWorld
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.recordsFolder
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.HumanEntity
import taboolib.module.chat.colored
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Anvil
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem
import taboolib.platform.util.submit

fun HumanEntity.openMenu()
{
    if (world == mainWorld) openMainMenu()
    else openWorldMenu()
}

private fun HumanEntity.openMainMenu(): Unit = openMenu<Chest>()
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

    if (isOp) set('B', buildItem(Material.RED_WOOL) { name = "游戏管理" })
    {
        context(GameSettingData())
        {
            clicker.openGameSettingsMenu()
        }
    }
    set('A', buildItem(Material.BLUE_WOOL) { name = "回放管理" })
    {
        context(GameSettingData())
        {
            clicker.openReplaySettingsMenu()
        }
    }

    set('C', buildItem(Material.ORANGE_WOOL) { name = "进入已有世界" })
    {
        clicker.chooseWorldMenu()
    }
}

private fun HumanEntity.openWorldMenu()
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

private fun HumanEntity.chooseWorldMenu(): Unit = openMenu<PageableChest<GameWorld>>()
{
    title = "选择世界"
    rows(6)
    map(
        "<#######X",
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
    set('<', buildItem(Material.ARROW) { name = "返回" }) { clicker.openMainMenu() }

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
            lore += "红队: ${element.leftGroup.name}"
            lore += "蓝队: ${element.rightGroup.name}"
        }
    }


    onClick()
    { event, element ->
        event.clicker.closeInventory()
        event.clicker.teleport(LocationStatus.IN_FIELD.location(element))
    }
}

context(data: GameSettingData)
private fun HumanEntity.openGameSettingsMenu(): Unit = openMenu<Chest>()
{
    title = "游戏设置"
    rows(5)
    map(
        "<#######X",
        "#       #",
        "#A B C D#",
        "#       #",
        "#########"
    )

    set('#', buildItem(Material.GRAY_STAINED_GLASS_PANE) { name = " " }) { }
    set('X', buildItem(Material.BARRIER) { name = "关闭" }) { clicker.closeInventory() }
    set('<', buildItem(Material.ARROW) { name = "返回" }) { clicker.openMainMenu() }
    set('A', buildItem(Material.PLAYER_HEAD)
    {
        name = "玩家分组"
        lore += "&c当前红队组号: ${data.leftGroup}".colored()
        lore += "&9当前蓝队组号: ${data.rightGroup}".colored()
        lore += "每组玩家人数: ${data.playerCountPerGroup}".colored()
    }) { clicker.openPlayerGroupingMenu() }
    set('B', buildItem(Material.MAP) { name = "地图设置" }) { clicker.openMapSettingsMenu() }
    set('C', buildItem(Material.COMPASS) { name = "游戏控制" }) { clicker.openGameControlMenu() }
    set('D', buildItem(Material.OAK_SIGN) { name = "练习赛设置" })
    {
        context(practiceMode ?: PracticeMode())
        {
            clicker.openPracticeSettingMenu()
        }
    }
}

context(data: GameSettingData)
private fun HumanEntity.openPlayerGroupingMenu()
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
            lore += "当前组号: ${data.leftGroup}"
        }) { clicker.openGroupInputMenu(PlayerTeam.LEFT) }
        set('B', buildItem(Material.BLUE_WOOL)
        {
            name = "蓝队"
            lore += "当前组号: ${data.rightGroup}"
        }) { clicker.openGroupInputMenu(PlayerTeam.RIGHT) }
        set('C', buildItem(Material.OMINOUS_TRIAL_KEY)
        {
            name = "每组玩家人数"
            lore += "当前设置: ${data.playerCountPerGroup}"
        }) { clicker.openPlayerCountInputMenu() }
    }
}

context(data: GameSettingData)
private fun HumanEntity.openMapSettingsMenu()
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
private fun HumanEntity.openGroupInputMenu(team: PlayerTeam): Unit = openMenu<Anvil>()
{
    title = if (team == PlayerTeam.LEFT) "输入红队组号" else "输入蓝队组号"
    set(0, buildItem(Material.PAPER) { name = if (team == PlayerTeam.LEFT) "${data.leftGroup}" else "${data.rightGroup}" })

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
            data.leftGroup = Group.PlayerGroup(text.toInt())
        else
            data.rightGroup = Group.PlayerGroup(text.toInt())
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
private fun HumanEntity.openPlayerCountInputMenu(): Unit = openMenu<Anvil>()
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
private fun HumanEntity.openGameControlMenu(): Unit = openMenu<Chest>()
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
        val world = createGame(data, false)
        clicker.teleport(LocationStatus.IN_FIELD.location(world))
    }
}

private fun HumanEntity.openReplaySettingsMenu()
{
    submit(async = true)
    {
        val items = recordsFolder.listFiles { file -> file.extension == "dat" }?.toList() ?: emptyList()

        val allItems = (items.map { it.nameWithoutExtension to it.length() })
            .distinctBy { it.first }
            .sortedByDescending { it.first }

        submit(async = false)
        {
            openMenu<PageableChest<Pair<String, Long>>>()
            {
                title = "回放管理"

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
                    }
                }

                onClick()
                { event, element ->
                    event.clicker.closeInventory()
                    val recordFile = recordsFolder.resolve(element.first + ".dat")
                    if (recordFile.exists())
                        replayRecord(recordFile)
                    else
                        event.clicker.sendMessage("&c回放文件不存在!".colored())
                }

                elements { allItems }
            }
        }
    }
}

context(data: PracticeMode)
private fun HumanEntity.openPracticeSettingMenu(): Unit = openMenu<Chest>()
{
    title = "练习赛设置"
    rows(6)
    map(
        "<#######X",
        "#       #",
        "#A B C D#",
        "#       #",
        "#   E   #",
        "#########"
    )

    set('#', buildItem(Material.GRAY_STAINED_GLASS_PANE) { name = " " }) { }
    set('X', buildItem(Material.BARRIER) { name = "关闭" }) { clicker.closeInventory() }
    set('<', buildItem(Material.ARROW) { name = "返回" }) { clicker.openMainMenu() }

    set('A', buildItem(Material.OAK_SIGN)
    {
        name = "名字设置"
        lore += "当前设置: ${data.name}"
    }) { clicker.openPracticeNameInputMenu() }

    set('B', buildItem(Material.PLAYER_HEAD)
    {
        name = "匹配设置"
        lore += "当前设置: " + when (data.enemy)
        {
            is Enemy.Specific  -> data.enemy.group.name
            Enemy.Any  -> "随机敌人"
            null       -> "未指定"
        }
    }) { clicker.openPracticeEnemySettingMenu() }

    set('C', buildItem(Material.MAP)
    {
        name = "地图设置"
        lore += "当前设置: " + when (data.mapType)
        {
            MapType.FIXED -> "固定地图"
            MapType.RANDOM -> "随机地图"
            MapType.ANY    -> "任意地图"
            null           -> "未指定"
        }
    }) { clicker.openPracticeMapSettingMenu() }

    set('D', buildItem(Material.COMPASS)
    {
        name = "人数设置"
        lore += "当前设置: 每队玩家人数 ${data.playersPerGroup ?: "未指定"}"
    }) { clicker.openPracticePlayerCountMenu() }

    set('E', buildItem(Material.OAK_SIGN)
    {
        val p = practiceMode
        name = if (p == null) "启用练习赛模式" else "禁用练习赛模式"
        if (p != null)
        {
            lore += "当前设置: "
            lore += "名字：${p.name}"
            lore += "匹配模式：" + when (p.enemy)
            {
                is Enemy.Specific -> p.enemy.group.name
                Enemy.Any  -> "随机敌人"
                null       -> "未指定"
            }
            lore += "地图类型：" + when (p.mapType)
            {
                MapType.FIXED -> "固定地图"
                MapType.RANDOM -> "随机地图"
                MapType.ANY    -> "任意地图"
                null           -> "未指定"
            }
            lore += "每队玩家人数：${p.playersPerGroup ?: "未指定"}"
        }
    })
    {
        practiceMode = if (practiceMode == null) data else null
        clicker.openPracticeSettingMenu()
    }
}

context(data: PracticeMode)
private fun HumanEntity.openPracticeNameInputMenu(): Unit = openMenu<Anvil>()
{
    title = "输入练习赛名字"
    set(0, buildItem(Material.PAPER) { name = data.name })

    onRename()
    { _, text, inventory ->
        if (text.isBlank())
        {
            inventory.setItem(2, buildItem(Material.RED_STAINED_GLASS_PANE)
            {
                name = "名字不能为空!"
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
        if (text.isNullOrBlank())
            return@onClick it.inventory.setItem(2, buildItem(Material.RED_STAINED_GLASS_PANE) { name = "名字不能为空!" })

        context(data.copy(name = text))
        {
            it.clicker.openPracticeSettingMenu()
        }
    }
}

context(data: PracticeMode)
private fun HumanEntity.openPracticeEnemySettingMenu(): Unit = openMenu<Chest>()
{
    title = "练习赛匹配设置"
    rows(1)
    map(" A B C D ")

    set('A', buildItem(Material.BARRIER)
    {
        name = "none"
        if (data.enemy is Enemy.Specific && data.enemy.group.isNone) enchants[Enchantment.MENDING] = 1
    })
    {
        context(data.copy(enemy = Enemy.Specific(Group.None)))
        {
            clicker.openPracticeSettingMenu()
        }
    }

    set('B', buildItem(Material.ZOMBIE_HEAD)
    {
        name = "default"
        if (data.enemy is Enemy.Specific && data.enemy.group.isBot) enchants[Enchantment.MENDING] = 1
    })
    {
        context(data.copy(enemy = Enemy.Specific(Group.Bot.DEFAULT)))
        {
            clicker.openPracticeSettingMenu()
        }
    }

    set('C', buildItem(Material.ENDER_PEARL)
    {
        name = "random"
        if (data.enemy is Enemy.Any) enchants[Enchantment.MENDING] = 1
    })
    {
        context(data.copy(enemy = Enemy.Any))
        {
            clicker.openPracticeSettingMenu()
        }
    }

    set('D', buildItem(Material.PLAYER_HEAD)
    {
        name = "不指定"
        if (data.enemy == null) enchants[Enchantment.MENDING] = 1
    })
    {
        context(data.copy(enemy = null))
        {
            clicker.openPracticeSettingMenu()
        }
    }
}

context(data: PracticeMode)
private fun HumanEntity.openPracticeMapSettingMenu(): Unit = openMenu<Chest>()
{
    title = "练习赛地图设置"
    rows(1)
    map(" A B C D ")

    set('A', buildItem(Material.GRASS_BLOCK)
    {
        name = "fixed"
        if (data.mapType == MapType.FIXED) enchants[Enchantment.MENDING] = 1
    })
    {
        context(data.copy(mapType = MapType.FIXED))
        {
            clicker.openPracticeSettingMenu()
        }
    }

    set('B', buildItem(Material.MAP)
    {
        name = "random"
        if (data.mapType == MapType.RANDOM) enchants[Enchantment.MENDING] = 1
    })
    {
        context(data.copy(mapType = MapType.RANDOM))
        {
            clicker.openPracticeSettingMenu()
        }
    }

    set('C', buildItem(Material.ENDER_PEARL)
    {
        name = "unspecified"
        if (data.mapType == MapType.ANY) enchants[Enchantment.MENDING] = 1
    })
    {
        context(data.copy(mapType = MapType.ANY))
        {
            clicker.openPracticeSettingMenu()
        }
    }

    set('D', buildItem(Material.PLAYER_HEAD)
    {
        name = "不指定"
        if (data.mapType == null) enchants[Enchantment.MENDING] = 1
    })
    {
        context(data.copy(mapType = null))
        {
            clicker.openPracticeSettingMenu()
        }
    }
}

context(data: PracticeMode)
private fun HumanEntity.openPracticePlayerCountMenu(): Unit = openMenu<Chest>()
{
    title = "练习赛人数设置"
    rows(1)
    map("1234567 N")

    val counts = listOf(1, 2, 3, 4, 5, 6, 7)

    for (i in counts.indices)
    {
        val count = counts[i]
        set("$count"[0], buildItem(Material.OAK_SIGN)
        {
            name = "$count"
            if (data.playersPerGroup == count) enchants[Enchantment.MENDING] = 1
        })
        {
            context(data.copy(playersPerGroup = count))
            {
                clicker.openPracticeSettingMenu()
            }
        }
    }

    set('N', buildItem(Material.PLAYER_HEAD)
    {
        name = "不指定"
        if (data.playersPerGroup == null) enchants[Enchantment.MENDING] = 1
    })
    {
        context(data.copy(playersPerGroup = null))
        {
            clicker.openPracticeSettingMenu()
        }
    }
}