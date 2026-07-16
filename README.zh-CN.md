# Perimeter Digger

[English](README.md)

Perimeter Digger 是一个 基于魔改版 Baritone 自动挖掘大型区域内方块的 26.1.1 Fabric Mod。

------

### 前置 MOD：
### [魔改版 Baritone](https://github.com/HackerRouter/baritone/releases)

### Perimeter Digger 使用教程视频: [BV1yFK56cESA](https://www.bilibili.com/video/BV1yFK56cESA)

------

它支持：
- 不规则 XZ 平面区域挖掘
- 挖掘区域边缘的流体封堵，内部的流体处理
- 自动收集掉落物
- 自动前往最近卸货点丢出挖掘产物
- 自动前往补给点补充消耗品（烟花+食物）
- 自动前往补给点补充替换低耐久工具/鞘翅
- 自动跨维度前往经验熔炉点修补工具/鞘翅
- 自动睡觉
- 步行+鞘翅寻路

## 运行要求

- Minecraft 26.1.1
- \>= Fabric Loader 0.19.3
- Fabric API
- Java 25 或更高版本
- **[魔改版 Baritone](https://github.com/HackerRouter/baritone/releases)**

原版 Baritone 不具备 自定义 XZ 平面区域挖掘 和 鞘翅/寻路 API 扩展。

## 安装

1. 为 Minecraft 26.1.1 安装 Fabric Loader。
2. 将 Fabric API、最新的 [魔改版 Baritone](https://github.com/HackerRouter/baritone/releases) 和 [Perimeter Digger](https://github.com/HackerRouter/Perimeter-Digger/releases) 放入客户端的 `mods` 目录。

所有指令均为客户端指令，支持基础的 Tab 补全。
界面翻译自动跟随 游戏当前选择的语言，目前仅包含英文和简体中文。

## 主要功能

- 根据同一Y高度的指定边界方块，探测需要挖掘的不规则封闭 XZ 区域。
- 直接通过坐标规划（包含坐标）需要挖掘的的矩形 XZ 区域。
- 在指定的 Y高度 范围内挖掘所选列。
- 使用可配置方块避开、替换或封堵流体。
- 按剩余物品栏容量进行挖掘，收集附近掉落物。
- 物品栏满后，前往最近的竖直卸货通道，丢弃挖掘产物。
- 监视工具+鞘翅耐久。
- 自动替换低耐久工具，也可自动前往补给处/经验熔炉（默认支持跨维度）进行替换/修复。
- 自动进食、补充食物+烟花，并可选择在夜间自动睡觉。
- 步行+鞘翅寻路。
- 按服务器或单人存档分别保存配置。

## 快速开始

### 挖掘 指令指定的 规则XZ平面 矩形区域

```text
/perimeterdig plan rectangle <x0> <z0> <x1> <z1> <minY> <maxY>
/perimeterdig start
```

两个 XZ 角点以及 Y 上下限均包含在挖掘范围中。

### 挖掘 自动探测的 不规则XZ平面 区域

1. 在同一 Y轴高度用一种方块围住所需区域。
2. 站在外圈内部。
3. 确保划出的区域位于客户端渲染出的的区块范围内。
4. 输入：

```text
/perimeterdig detect <boundaryBlock> <boundaryY>
/perimeterdig config set digging_min_y <Y>
/perimeterdig config set digging_max_y <Y>
/perimeterdig start
```

示例：

```text
/perimeterdig detect minecraft:red_wool 80
/perimeterdig config set digging_min_y -59
/perimeterdig config set digging_max_y 79
/perimeterdig start
```

正交或对角接触的边界方块视为同一个边界组件。最外圈边界列不会被挖。一圈内还有子圈时，子圈内部不会被挖，但子圈边界方块所在的列仍属于挖掘区域；继续嵌套时按奇偶层交替判断。

探测面积没有配置上限，但只能读取客户端渲染距离内已加载的区块。

## 指令

### 运行与状态

| 指令 | 说明 |
| --- | --- |
| `/perimeterdig` | 显示当前状态和详情。 |
| `/perimeterdig start` | 验证区域并启动自动化；未配置的可选设施只会禁用对应行为。 |
| `/perimeterdig stop` | 停止自动化并释放 Baritone 输入。 |
| `/perimeterdig pause` | 暂停正在进行的挖掘。 |
| `/perimeterdig resume` | 恢复暂停的挖掘。 |
| `/perimeterdig status` | 显示当前状态、详情、目标和重试次数。 |
| `/perimeterdig status clear` | 停止并清除运行时缓存，不删除配置。 |
| `/perimeterdig status history` | 显示最新 16 条状态转换。 |
| `/perimeterdig status history all` | 最多显示 64 条状态转换。 |
| `/perimeterdig status history clear` | 清除状态转换历史。 |
| `/perimeterdig reload` | 重新读取当前世界的配置并重置运行状态。 |

### 区域与基础配置

| 指令 | 说明 |
| --- | --- |
| `/perimeterdig detect <block> <Y>` | 探测封闭的不规则区域。 |
| `/perimeterdig plan rectangle <x0> <z0> <x1> <z1> <minY> <maxY>` | 创建包含边界的矩形区域和 Y 范围。 |
| `/perimeterdig config` | 显示当前配置。 |
| `/perimeterdig config show` | 显示当前配置。 |
| `/perimeterdig config clear detected_area` | 删除已保存的区域。 |
| `/perimeterdig config set digging_min_y <Y>` | 设置包含在内的最低挖掘 Y。 |
| `/perimeterdig config set digging_max_y <Y>` | 设置包含在内的最高挖掘 Y。 |

### 设施点位

所有点位均使用方块坐标 `<x> <y> <z>`：

```text
/perimeterdig config set consumable_supply_point <x> <y> <z>
/perimeterdig config set durability_supply_point <x> <y> <z>
/perimeterdig config set bed_point <x> <y> <z>
/perimeterdig config set perimeter_portal_overworld <x> <y> <z>
/perimeterdig config set perimeter_portal_nether <x> <y> <z>
/perimeterdig config set repair_portal_overworld <x> <y> <z>
/perimeterdig config set repair_portal_nether <x> <y> <z>
/perimeterdig config set furnace_row_start <x> <y> <z>
/perimeterdig config set furnace_row_end <x> <y> <z>
```

补给点填写箱子方块坐标，床点填写床方块坐标。熔炉排必须沿一个坐标轴排列，只保存首尾两个熔炉位置。床、补给箱和熔炉统一寻找附近站位，并让玩家眼睛与目标方块中心的距离进入 3 格以内再进行交互。

正常挖掘时所有设施均为可选项。缺少点位或路线只会跳过对应自动行为；调试指令仍要求其测试流程所需的设施已经配置。

### 卸货点

```text
/perimeterdig config set unloading_point <name> <x> <minY> <z>
/perimeterdig config remove unloading_point <name>
/perimeterdig config add unloading_whitelist <item>
/perimeterdig config remove unloading_whitelist <item>
```

卸货点表示给定 XZ 的竖直通道，Y 表示通道最低 Y。可以配置多个具名通道。Mod 会寻找 XZ 距离最短的安全站立方块，并优先选择接近玩家当前 Y 的位置；随后移动到距离方块边缘内缩 0.2 格的位置，潜行并面向通道中心丢出物品。

可损坏物品、工具、可装备物品、鞘翅、烟花、配置的食物和白名单物品会被保留，其他物品均视为挖掘产物。

### 流体处理

```text
/perimeterdig config set liquid_policy avoid
/perimeterdig config set liquid_policy replace
/perimeterdig config set liquid_policy seal_boundary
/perimeterdig config add sealing_block <block>
/perimeterdig config remove sealing_block <block>
```

默认策略为 `seal_boundary`，默认封堵方块为 `minecraft:netherrack`。封堵方块列表为空时仍允许开始挖掘。

### 食物与耐久策略

```text
/perimeterdig config add food <item>
/perimeterdig config remove food <item>
/perimeterdig config set durability_recovery_mode repair_portal
/perimeterdig config set durability_recovery_mode supply_point
```

默认食物为 `minecraft:enchanted_golden_apple`。

- `repair_portal`：依次经过待挖掘区域传送门和经验熔炉传送门，使用经验熔炉修复带经验修补的工具/鞘翅。
- `supply_point`：把低耐久工具/鞘翅放入补给处的箱子，取出耐久更高的替换品并自动装备。

无论采用哪一种远程策略，都会优先尝试背包内已有的同类耐久更高替换品。

### 功能开关

```text
/perimeterdig function
/perimeterdig function enable <function>
/perimeterdig function disable <function>
```

可用功能：

| 功能 | 默认值 | 禁用后的行为 |
| --- | --- | --- |
| `collect_drops` | `true` | 跳过每批挖掘结束后的掉落物收集；背包已满仍可触发卸货。 |
| `unload` | `true` | 不再自动卸货。 |
| `eat` | `true` | 不再自动进食。 |
| `durability_recovery` | `true` | 不前往补给点或经验熔炉；仍允许使用背包内耐久更高的替代品。 |
| `cross_dimension_repair` | `true` | 直接前往当前维度中的经验熔炉，而不是走跨维度传送门。 |
| `resupply` | `true` | 不再补充食物或烟花。 |
| `elytra_navigation` | `true` | 禁用鞘翅导航、烟花不足触发的前往补给点，以及鞘翅耐久替换或恢复；工具耐久处理仍然启用。 |
| `sleep` | `false` | 夜间不自动睡觉。 |

### 高级配置

```text
/perimeterdig config_advanced
/perimeterdig config_advanced show
/perimeterdig config_advanced set <key> <value>
```

| 分类 | 配置键与默认值 |
| --- | --- |
| 耐久 | `tool_durability_threshold=32`、`elytra_durability_threshold=32`、`emergency_flight_durability_threshold=5` |
| 消耗品 | `food_level_threshold=14`、`health_eating_threshold=16`、`food_resupply_trigger=1`、`food_resupply_target=64`、`firework_resupply_trigger=10`、`firework_resupply_target=128` |
| 挖掘与卸货 | `drop_collection_radius=8`、`drop_collection_stable_seconds=1.5`、`inventory_reserved_slots=1`、`mining_blocks_per_empty_slot=64`、`unload_landing_search_radius=16`、`unload_edge_inset=0.2` |
| 寻路 | `elytra_navigation_min_distance=32`、`navigation_stall_timeout_seconds=10`、`navigation_retry_count=2`、`flight_retry_count=2`、`portal_transition_cost=16`、`portal_transition_timeout_seconds=20` |
| 传送门出口 | `portal_exit_timeout_seconds=20`、`portal_exit_min_radius=3`、`portal_exit_max_radius=8`、`portal_exit_vertical_radius=4` |
| 交互 | `repair_experience_stable_seconds=1.5`、`supply_interaction_timeout_seconds=2`、`furnace_interaction_timeout_seconds=2` |

所有值都会进行范围检查，相关的最小/最大值以及触发量/目标量会进行联合校验。
配置键和当前值都支持基础的 Tab 补全。

### 调试流程

| 指令 | 说明 |
| --- | --- |
| `/perimeterdig debug unload` | 单独执行卸货流程。 |
| `/perimeterdig debug supply consumables` | 测试消耗品补给箱拿取流程。 |
| `/perimeterdig debug supply durability` | 测试耐久补给点替换流程。 |
| `/perimeterdig debug repair` | 测试经验熔炉维修流程。 |
| `/perimeterdig debug sleep` | 测试自动睡觉。 |

## 自动化行为

默认每批挖掘上限为 `(主物品栏空槽数 - 1) * 64`。

只剩一个空槽时，本批上限为一个方块。判断背包是否已满时不考虑副手。

每批结束后会收集水平距离 8 格以内的掉落物，直到物品栏物品总数约 1.5 秒没有增加。卸货完成后，会先返回记录的挖掘出发点再继续。

饱食度不高于 14 时会自动进食；生命值不高于 16 且饱食度未满时也会进食。

配置食物总数不高于 1 时补到 64，烟花不高于 10 时补到 128。消耗品补给优先于远程耐久恢复。

主物品栏和副手内的所有可损坏工具都会被监视。只有启用 `elytra_navigation` 时，才会监视已装备或携带的鞘翅。剩余耐久不高于 32 时，先寻找本地耐久更高的替代品，再执行配置的恢复策略。紧急行程中可以继续使用剩余耐久高于 5 的鞘翅。

前去经验熔炉修复时会逐个取出产物。经验仍在吸收时会切换待维修目标；耐久停止增长后才访问下一个熔炉；所有目标修满后立即返回。前往熔炉时会逐个尝试处于交互距离内且视线无遮挡的附近站位，所有候选均失败后才会报告熔炉及最后尝试的站位。禁用 `cross_dimension_repair` 后只要求配置经验熔炉的坐标，按当前维度解释，往返过程不会使用传送门。

Baritone 的疾跑和跑酷始终启用。

所有非挖掘目的地均禁止放置方块；前往待挖掘区域传送门时允许挖掘，但维修行程离开挖掘区域后的其他阶段同时禁止挖掘和放置。

适合时先尝试步行；目标较远或无法直接向上步行时使用鞘翅。寻路系统的 watchdog 会在报告错误前重试停滞路线。

## 配置与翻译

每个世界的 JSON 配置保存在：

```text
config/perimeter-digger/worlds/<world-id>.json
```

每个服务器地址和单人存档使用独立文件。
配置带有 schema 版本；旧的无版本文件会自动迁移，比当前程序更新且不受支持的 schema 会拒绝加载。
运行中的任务进度不会生成检查点，也不支持断线重连后自动恢复任务。

语言资源位于 `assets/perimeter-digger/lang/en_us.json` 和 `zh_cn.json`。状态历史保存翻译键及参数，因此会按客户端当前语言渲染。
构建测试会要求两个语言文件具有相同的有序键和兼容的占位符。

## 构建

构建需要魔改版 Baritone API JAR；未经修改的普通 Baritone 不包含本 Mod 使用的 API。当前兼容的已发布依赖为 [Baritone Fabric 26.1.1 hr.1](https://github.com/HackerRouter/baritone/releases/tag/v26.1.1-hr.1)。

未指定 `baritone_jar` 时，Gradle 会使用 `build.gradle` 中配置的本地开发路径。如果本机没有相邻的 Baritone 源码仓库，请显式指定已发布 JAR 的路径。

Windows：

```text
gradlew.bat clean build -Pbaritone_jar="C:\path\to\baritone-api-fabric-26.1.1-hr.1.jar"
```

Linux：

```text
./gradlew clean build -Pbaritone_jar=/path/to/baritone-api-fabric-26.1.1-hr.1.jar
```

仓库 workflow 会自动下载这个固定版本的 Baritone Release、验证 SHA-256，并通过 `baritone_jar` 参数传给 Gradle。

生成的 Mod JAR 位于 `build/libs/`。

## 许可证

Perimeter Digger 使用 [MIT License](LICENSE)。
