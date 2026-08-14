# Minecraft-CTF

[![Build](https://github.com/SpringSense-Innovation-Institute/Minecraft-CTF/actions/workflows/build.yml/badge.svg)](https://github.com/SpringSense-Innovation-Institute/Minecraft-CTF/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/SpringSense-Innovation-Institute/Minecraft-CTF)](https://github.com/SpringSense-Innovation-Institute/Minecraft-CTF/releases/latest)
[![License](https://img.shields.io/github/license/SpringSense-Innovation-Institute/Minecraft-CTF)](./LICENSE)

基于 Bukkit/Spigot 的 Capture The Flag（夺旗）游戏模式插件，使用 [TabooLib 6.3.0](https://github.com/TabooLib/taboolib) 框架开发，Kotlin 编写，目标 JVM 17，适配 Minecraft 1.21。

## 功能特性

- **夺旗对战**：两支队伍争夺对方旗帜，率先完成 8 次夺旗或在 3 分钟时限内夺旗数领先的一方获胜
- **非战斗对抗**：没有武器，对抗完全依靠抓捕机制、击退与环境威胁（寄生感染、哞菇冲撞、蜂蜜块减速）
- **AI 机器人**：内置 6 种基于 NMS 原生玩家实体的 AI 对手，从基础寻路到规避敌人、主动攻击
- **动态世界**：每局游戏从模板世界复制生成独立维度，结束后自动卸载删除
- **地图随机生成**：旗帜、目标点、树木、蜂蜜块等元素支持固定/随机两种布局模式
- **Web 面板**：内置 HTTP 服务器，提供 Three.js 3D 实时观战、2D 小地图、录像回放、ECharts 统计面板和管理接口
- **逐刻录像**：完整记录每局所有实体与方块变化，支持游戏内与浏览器 3D 回放

## 环境要求

| 项目 | 要求 |
|------|------|
| 服务端 | Bukkit/Spigot/Purpur 1.21.x |
| Java | JDK 17+ |
| 端口 | 预留一个 Web 端口（默认 8001） |
| 模板世界 | 服务器世界目录下需放置 `template_world`（见下文部署说明） |

## 部署安装

1. 从 [Releases](https://github.com/SpringSense-Innovation-Institute/Minecraft-CTF/releases/latest) 下载最新构建的 jar，放入服务器 `plugins/` 目录。
2. 准备模板世界：在服务器世界目录（与主世界同级）创建名为 `template_world` 的世界，需满足：
   - `y=0` 为平坦草方块地面，覆盖整个竞技场范围（x: -24 到 24，z: -36 到 36）；
   - 沿 `x=0` 中线有一条贯穿 z 轴范围的水道（发光鱿鱼的生成依赖该水道，缺失会导致地图生成失败）；
   - 场地区域内无其他建筑、实体或方块。
3. 启动服务器。插件会在 `plugins/Minecraft-CTF-Plugin/` 下生成 `config.json`、`players.json`、`groups.json`。
4. **修改 `config.json` 中的 `adminPassword` 默认值**，并按需调整 `webPort`，然后重启服务器。
5. 玩家通过 Web 端 `http://<服务器地址>:<webPort>/` 注册并加入分组，或在游戏内通过菜单操作。

## 快速开始

1. 玩家在 Web 端 `/register` 注册，并加入一个分组（Group）；
2. 游戏内输入聊天命令发起匹配：

   ```
   match enemy:<enemy> players:<count> map:<mapType>
   ```

   例如 `match enemy:any players:2 map:random` 表示匹配任意对手、每队 2 人、随机地图；`match enemy:trivial players:1 map:fixed` 表示对战最强 AI 机器人。

3. 匹配成功后进入 30 秒准备阶段，在聊天栏输入 `I'm ready!` 确认；
4. 全部确认后游戏开始，3 分钟内率先夺旗 8 次或夺旗数更多的一方获胜。

## 游戏规则

### 队伍与胜利

两支队伍对战：LEFT（红队）和 RIGHT（蓝队）。玩家以 Group（分组）为单位匹配并随机分配到队伍。

夺取对方旗帜并带回己方金块目标点上交即完成一次夺旗。率先完成 8 次夺旗的队伍获胜；每局限时 3 分钟，时间耗尽时夺旗数多的一方获胜，持平则为平局。

### 旗帜机制

旗帜是放置在铜块（Waxed Copper Block）和橡木栅栏杆上的彩色旗帜。红队在左半场放置红色旗帜，蓝队在右半场放置蓝色旗帜。

- **拾取**：走到对方旗帜附近（检测玩家脚下方块及其前后左右各 1 格范围内）自动拾取，旗帜进入头盔槽位（须为空）。原旗帜结构随之移除（铜块变草方块，栅栏和旗帜消失）。
- **上交**：携带对方旗帜站在己方半场的金块目标点上方即完成夺旗（要求金块上方两层为空气）。旗帜重新放置在金块上，头盔槽位清空。
- **掉落**：携带旗帜的玩家死亡或被抓捕时，旗帜以玩家为中心螺旋向外搜索（最大 20 格）一个有效位置（场地内、草方块上方、上方两层空气）并重新生成旗帜结构。

### 抓捕与监狱

双方玩家（均未处于寄生状态）距离小于 0.5 格（x/z 平面）且高度差小于 1 格时触发判定：

- 在己方半场：你抓住入侵者，对方丢弃旗帜并被传送到你方监狱；
- 在敌方半场：你被对方抓住，你丢弃旗帜并被传送到对方监狱。

监狱位于场地边缘（左队 x: -18 到 -14，z: 26 到 30；右队 x: 14 到 18，z: 26 到 30）。处于寄生状态的玩家不会触发抓捕判定，也无法被抓住。

### 死亡与重生

开启 `doImmediateRespawn` 规则，死亡后立即重生在己方监狱区域。死亡时携带的旗帜会掉落。

### 装备

游戏开始时每位玩家获得队伍色皮革装备（胸甲、护腿、靴子，无头盔），全部附带绑定诅咒。游戏不提供任何武器，对抗只能依靠抓捕机制和击退效果。开局会清除药水效果、恢复满血、饱食度设为 9，并关闭物品耐久消耗。

### 移动限制

- **准备阶段（PREPARING）**：限制在己方基地内（左队 x: -23 到 -21，z: -1 到 1；右队 x: 21 到 23，z: -1 到 1）
- **游戏进行中（IN_GAME）**：可在整个场地（x: -24 到 24，z: -36 到 36）内自由移动，越界会被传送回合法位置
- **无队伍玩家**：被强制传送到场地外围高处（y=30）俯瞰，可以飞行
- **全局限制**：所有玩家坐标被限制在 -128 到 127 范围内

## 特殊机制

### 寄生感染

地图上生成的蠹虫（Silverfish）会主动追踪最近的未感染玩家，距离小于 2.5 格时扑向玩家（速度乘数 0.4），距离小于 0.5 格时触发感染。被感染后立即丢弃携带的旗帜。

感染分两个阶段，总计 260 游戏刻（约 13 秒）：

- **第一阶段（0~200 tick，约 10 秒）—— 被控制**：强制装备带绑定诅咒的绿色皮革头盔；移动速度归零；获得等级 200 的跳跃提升（用于禁止起跳）；被施加向最近未感染玩家方向的推力（无其他玩家则随机方向），完全无法自控，只能被推力带着滑行。
- **第二阶段（200~260 tick，约 3 秒）—— 恢复自由**：移动速度与跳跃恢复正常，但仍戴着绿色头盔，可自由移动。
- **结束后**：头盔移除，完全恢复。

**传染性**：感染玩家与健康玩家距离小于 0.5 格时立即传染（两个阶段均可传染），已感染玩家之间不互相触发。

### 哞菇

地图上生成自定义 AI 的哞菇（红色或棕色变种、成年或幼年），追踪附近玩家（不分队伍）：

| 距离 | 行为 |
|------|------|
| > 5 格 | 正常速度（1.0）追踪 |
| < 4 格 | 切换 3 倍速度冲刺 |
| < 1 格 | 冲撞（击退，速度乘数 0.8），进入冷却 |
| 冷却后脱离 > 5 格 | 解除冷却，恢复追踪 |

### 地形元素

- **蜂蜜块**：每半场 8 簇，每簇 9~20 个随机蔓延，减缓移动速度
- **橡树**：每半场 5 棵（1~4 格树干 + 3×3 树冠）
- **瓶子草**：每半场 5 株，蠹虫进入时有 50% 概率被吞噬（仅当蠹虫总数 > 2，防止灭绝）
- **发光鱿鱼**：场地中央水道 5 只，装饰性元素

## 机器人系统

内置 6 种 AI 机器人。它们以 NMS 原生玩家实体（EntityPlayer）形式存在——不是 NPC——通过模拟真实输入移动，触发真正的 `PlayerMoveEvent`，因此所有游戏机制（夺旗、抓捕、寄生等）对机器人完全一致生效。

| 类型 | 匹配 ID | 行为 |
|------|---------|------|
| DEFAULT | `bot` | A* 寻路走向敌方旗帜，基础 AI |
| DEFAULT_JUMP | `jump-bot` | DEFAULT + 自动跳跃 + 冲刺 |
| DEFAULT_SNEAK | `sneak-bot` | DEFAULT + 始终潜行 |
| SIMPLE | `simple` | 寻路时规避附近敌方玩家 |
| SIMPLE_JUMP | `simple-jump` | SIMPLE + 跳跃 + 冲刺 |
| TRIVIAL | `trivial` | 最强 AI：规避敌人、寄生玩家与蠹虫，主动攻击附近敌人 |

实现要点：通过 `EntityPlayer` 创建真实玩家实体（拥有 `GameProfile` 与网络连接）；使用 Embedded Channel 丢弃下行数据包以避免网络开销；每游戏刻调用 NMS `aiStep()` 模拟移动；支持前后左右、跳跃、潜行、冲刺、攻击的完整输入模拟。

## 地图生成

竞技场为 49×73 格（x: -24 到 24，z: -36 到 36），以 x=0 为中线分为左（红）、右（蓝）两个半场。

- **固定地图（Fixed）**：旗帜和目标点按对称固定坐标排列（x = ±22），z 从 6 到 34 每隔 4 格共 8 对；旗帜在 z 负半轴，目标点在 z 正半轴。
- **随机地图（Random）**：旗帜和目标点完全随机放置（x 限制在 ±12 到 ±24）。

每半场固定包含：8 面旗帜、8 个金块目标点、5 棵橡树、5 株瓶子草、与玩家数量相同的哞菇和蠹虫、8 簇蜂蜜块。中央水道有 5 只发光鱿鱼。每局游戏开始时地图都会重新生成。

## 游戏流程

1. **发起匹配**：聊天栏输入 `match` 命令加入等待队列
2. **匹配处理**：系统每 2 秒处理一次队列，将设置兼容的请求配对（指定对手 > 任意对手，指定地图 > 任意地图）
3. **准备阶段**：30 秒，玩家传送到游戏世界（创造模式），输入 `I'm ready!` 确认；双方全部确认后开始，超时则取消
4. **游戏进行**：3 分钟，切换冒险模式，装备队伍色护甲，所有机制生效，地图此时重新生成
5. **游戏结束**：宣布获胜方，展示 10 秒结果，玩家传送回大厅世界，游戏世界卸载并删除

## 命令

### 游戏命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/ctf menu` 或 `/ctfmenu` | 打开 CTF 管理菜单 | 所有玩家 |
| `Shift + F` | 打开 CTF 管理菜单（副手交换键） | 所有玩家 |
| `/ctf watch` | 传送到当前游戏世界观战 | 所有玩家 |
| `/ctf listWorlds` | 列出当前运行中的游戏世界 | OP |
| `/ctf clearWorlds` | 清理可回收的游戏世界 | OP |

### 管理命令

| 命令 | 说明 |
|------|------|
| `/login <password>` | 管理员认证登录 |
| `/whitelist_mode [true/false]` | 切换 IP 白名单模式 |

### 聊天命令

**匹配：**

```
match enemy:<enemy> players:<count> map:<mapType>
```

- `enemy`：对手类型
  - `none`：无对手（练习模式）
  - `any`：任意对手
  - `bot` / `jump-bot` / `sneak-bot` / `simple` / `simple-jump` / `trivial`：对应类型的机器人
  - `<数字>`：指定 Group 编号的对手
- `players`：每队玩家人数（0 到 5）
- `map`：地图类型 `fixed` / `random` / `any`

**准备确认：** `I'm ready!`

## Web 界面

插件内置 HTTP 服务器（默认端口 8001），主要端点：

| 路径 | 功能 |
|------|------|
| `/` | 门户页 |
| `/login` | 玩家登录 |
| `/register` | 玩家注册与分组管理 |
| `/admin` | 管理面板（REST API，管理玩家、分组、白名单，需管理员登录） |
| `/viewer2d` | 2D 俯视小地图观战（SSE 实时推送） |
| `/viewer3d` | 3D 实时观战（Three.js，Bloom + SMAA 后期处理，SSE + Zstd 压缩；需登录，只能观战自己参与的游戏） |
| `/viewer3d/record` | 浏览器 3D 录像回放（支持速度控制与起始位置选择） |
| `/statistics` | 统计面板（ECharts 数据可视化） |
| `/cdn/*` | 前端静态资源 |

## 录像与回放

- **录制**：每局逐刻记录所有实体的位置、朝向、速度、装备，以及所有方块变化。数据使用自定义二进制格式（`.dat`：文件头含起始时间、周期、刻数 + Zstd 压缩数据体），保存在 `plugins/Minecraft-CTF-Plugin/records/`。
- **游戏内回放**：创建新维度，用带自定义名称的缩小僵尸实体代表玩家，逐刻重放方块与实体状态。
- **Web 回放**：通过 `/viewer3d/record` 接口在浏览器中以 3D 回放，支持自定义播放速度。

## 从源码构建

```bash
./gradlew clean build
```

构建产物位于 `build/libs/`。需要 JDK 17；CI（GitHub Actions）会在每次 push 到 main 时构建并发布到 [Releases](https://github.com/SpringSense-Innovation-Institute/Minecraft-CTF/releases)。

## 架构

### 动态世界管理

每场游戏从模板世界（`template_world`）复制创建一个独立的 Minecraft 维度（空 `ChunkGenerator`，不生成结构），游戏结束后自动卸载并删除世界文件。

### 模块化设计

基于 TabooLib 模块系统：

- **BukkitNMS / BukkitNMSEntityAI / BukkitNMSItemTag / BukkitNMSUtil / BukkitFakeOp**：NMS 操作、实体 AI、物品 NBT（用于机器人实体的创建与操控）
- **BukkitNavigation**：实体寻路
- **BukkitUI**：菜单界面
- **BukkitUtil / MinecraftChat**：Bukkit 工具类、聊天组件
- **CommandHelper**：命令系统
- **JavaScript**：脚本支持

### 主要依赖

- `kotlinx-serialization-json`：JSON 序列化（relocate 打包）
- `zstd-jni`：Zstd 数据压缩（录像与 SSE 传输）
- `mojang authlib`：玩家 Profile 处理

## 配置

### config.json

```json
{
  "webPort": 8001,
  "adminPassword": "password123"
}
```

- `webPort`：Web 服务器端口
- `adminPassword`：管理员登录密码，**部署后请立即修改默认值**

### players.json / groups.json

玩家注册信息（ID、名称、可选密码）与分组定义（ID、名称、成员列表）。可通过 Web 端或管理 API 管理，也可通过游戏内菜单操作。

## 许可证

本项目基于 [AGPL-3.0](./LICENSE) 许可证开源。
