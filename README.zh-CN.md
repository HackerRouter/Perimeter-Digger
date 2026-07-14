# Perimeter Digger

[English](README.md)

### 因为我真的很懒，所以我让 Codex 帮我写了份 README

------

### 前置 MOD：[Baritone 魔改版](https://github.com/HackerRouter/baritone/releases)

------

Perimeter Digger 是一个基于魔改版 Baritone 的 Fabric 客户端 Mod，用于自动挖掘 XZ 平面形状不规则、Y 方向规则的大型区域。它支持液体封堵、掉落物收集、自动卸货、消耗品补给、耐久替换、跨维度维修和鞘翅导航。

当前目标版本为 Minecraft 26.1.1 Fabric。

## 运行要求

- Minecraft 26.1.1
- Fabric Loader 0.19.3 或更高版本
- Fabric API
- Java 25 或更高版本
- 本项目使用的魔改版 Baritone

本 Mod 依赖自定义的 Baritone API、区域挖掘和鞘翅寻路改动，不能使用未经修改的普通 Baritone JAR 替代。

## 安装

1. 为 Minecraft 26.1.1 安装 Fabric Loader。
2. 将 Fabric API、魔改版 Baritone JAR 和 `perimeter-digger-1.0.0.jar` 放入客户端的 `mods` 目录。
3. 进入世界或服务器，使用 `/perimeterdig` 指令配置挖掘任务。

这是一个纯客户端 Mod。所有指令均为客户端指令，并支持 Tab 补全。

## 主要功能

- 根据同一 Y 高度的边界方块探测不规则封闭 XZ 区域。
- 直接用坐标规划包含边界的矩形 XZ 区域。
- 在探测出的 XZ 列内挖掘规则且包含上下限的 Y 范围。
- 按配置选择避开、替换或封堵液体。
- 根据物品栏空间分批挖掘，之后收集附近掉落物并卸货。
- 导航到竖直卸货通道，在安全边缘潜行、向下瞄准并丢弃挖掘产物。
- 监视所有携带工具以及携带或穿戴的鞘翅耐久。
- 优先在背包中更换低耐久装备，也可通过箱子替换或跨维度前往熔炉维修。
- 自动食用配置食物，并从箱子补充食物和烟花。
- 使用步行或魔改鞘翅寻路，包括原地跳跃并使用烟花起飞。
- 按服务器或单人世界分别持久化配置。

## 快速开始

### 规则矩形区域

```text
/perimeterdig plan rectangle <x0> <z0> <x1> <z1> <minY> <maxY>
/perimeterdig start
```

两个角点和 Y 上下限均包含在挖掘区域内。

### 探测不规则区域

1. 在同一 Y 高度用一种方块围出需要挖掘的区域。
2. 玩家站在外圈内部，不能站在边界方块上。
3. 确保完整边界均位于客户端当前已加载的渲染区块内。
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

正交或对角接触的边界方块视为同一个边界组件。外圈边界本身不会被挖；如果外圈内还有一圈，子圈内部不会被挖，但子圈边界方块所在的 XZ 列仍属于挖掘区域。更多层嵌套会按奇偶层交替处理。

探测面积没有人为设置的上限，但只能读取客户端渲染距离内已经加载的区块。

## 指令

所有指令均支持 Tab 补全。

### 运行控制

| 指令 | 说明 |
| --- | --- |
| `/perimeterdig` | 显示当前自动化状态。 |
| `/perimeterdig start` | 验证挖掘区域并开始自动化。 |
| `/perimeterdig stop` | 停止自动化并释放 Baritone 控制。 |
| `/perimeterdig pause` | 暂停正在进行的挖掘。 |
| `/perimeterdig resume` | 恢复暂停的挖掘。 |
| `/perimeterdig status` | 显示当前状态、目标点和导航重试次数。 |
| `/perimeterdig status clear` | 停止自动化并清除运行时缓存，不删除已保存配置。 |
| `/perimeterdig reload` | 从磁盘重新读取当前世界配置并重置运行状态。 |

### 挖掘区域

| 指令 | 说明 |
| --- | --- |
| `/perimeterdig detect <方块> <Y>` | 在指定 Y 探测不规则封闭区域。 |
| `/perimeterdig plan rectangle <x0> <z0> <x1> <z1> <minY> <maxY>` | 创建包含边界的矩形区域和 Y 范围。 |
| `/perimeterdig config clear detected_area` | 删除已保存的探测或规划区域。 |
| `/perimeterdig config set digging_min_y <Y>` | 设置包含在内的最低挖掘 Y。 |
| `/perimeterdig config set digging_max_y <Y>` | 设置包含在内的最高挖掘 Y。 |

### 坐标点与路线

以下坐标指令均使用 `<x> <y> <z>`：

```text
/perimeterdig config set consumable_supply_point <x> <y> <z>
/perimeterdig config set durability_supply_point <x> <y> <z>
/perimeterdig config set perimeter_portal_overworld <x> <y> <z>
/perimeterdig config set perimeter_portal_nether <x> <y> <z>
/perimeterdig config set repair_portal_overworld <x> <y> <z>
/perimeterdig config set repair_portal_nether <x> <y> <z>
/perimeterdig config set furnace_row_start <x> <y> <z>
/perimeterdig config set furnace_row_end <x> <y> <z>
```

补给点填写箱子方块坐标，导航目标为箱子上方。熔炉排必须沿单个坐标轴排列，只保存首尾两个熔炉坐标。

普通挖掘不强制要求配置这些坐标。缺少相关坐标或路线时，对应补给或维修行为会被跳过；各阶段 debug 指令仍会严格检查该阶段需要的配置。

### 卸货点

```text
/perimeterdig config set unloading_point <名称> <x> <minY> <z>
/perimeterdig config remove unloading_point <名称>
```

卸货点表示指定 XZ 的竖直通道，Y 参数为通道最低 Y。可以配置多个带名称的卸货点。Mod 会选择 XZ 距离最短的安全站位，优先接近玩家当前 Y，并在其他条件相同时选择更高的位置。到达后，玩家会走到方块边缘、潜行、朝配置的通道中心向下看并丢弃物品。

卸货时会保留有耐久的物品、工具、可装备物品、鞘翅、烟花、食物和白名单物品，其他物品均视为挖掘产物。

```text
/perimeterdig config add unloading_whitelist <物品>
/perimeterdig config remove unloading_whitelist <物品>
```

### 液体处理

```text
/perimeterdig config set liquid_policy avoid
/perimeterdig config set liquid_policy replace
/perimeterdig config set liquid_policy seal_boundary
/perimeterdig config add sealing_block <方块>
/perimeterdig config remove sealing_block <方块>
```

默认策略为 `seal_boundary`，默认封堵方块列表包含 `minecraft:netherrack`。即使封堵方块列表为空，也不会阻止挖掘任务启动。

### 食物与耐久策略

```text
/perimeterdig config add food <物品>
/perimeterdig config remove food <物品>
/perimeterdig config set durability_recovery_mode repair_portal
/perimeterdig config set durability_recovery_mode supply_point
```

默认配置食物为 `minecraft:enchanted_golden_apple`。

- `repair_portal`：依次经过 chunk perimeter 和维修机器的两组传送门，使用熔炉产物经验修复带经验修补的装备。
- `supply_point`：把低耐久装备放入耐久补给箱，取出健康替代品并自动装备。

无论选择哪一种远程耐久策略，Mod 都会优先尝试使用背包中同类型的耐久更多的替代品。

### 查看与调试

| 指令 | 说明 |
| --- | --- |
| `/perimeterdig config` | 显示当前配置。 |
| `/perimeterdig config show` | 显示当前配置。 |
| `/perimeterdig debug stage5` | 单独执行卸货流程。 |
| `/perimeterdig debug stage6 consumables` | 测试消耗品补给箱导航和拿取。 |
| `/perimeterdig debug stage6 durability` | 测试耐久补给箱替换。 |
| `/perimeterdig debug stage7` | 测试传送门与熔炉维修流程。 |

## 自动化行为

### 挖掘与收集

每批挖掘上限根据主物品栏的 36 个槽位计算：

```text
(空余槽位数量 - 1) * 64
```

如果只剩一个空槽，本批上限为一个方块。判断背包是否已满时不考虑副手栏。

一批挖掘结束后，Mod 会尝试收集 XZ 水平距离 8 格内的掉落物。当物品栏物品总数约 1.5 秒没有继续增加时停止收集，随后卸货，并优先返回记录的挖掘出发点，再开始下一批挖掘。

### 消耗品

- 饱食度不高于 14 且背包中存在配置食物时自动进食。
- 配置食物总数不高于 1 时触发补给，目标数量为 64。
- 烟花总数不高于 10 时触发补给，目标数量为 128。
- 消耗品补给的优先级高于远程耐久维修。

### 耐久

- 监视主物品栏及副手中的所有可损坏工具。
- 监视携带和已穿戴的鞘翅。
- 工具或鞘翅剩余耐久不高于 32 时触发替换或恢复。
- 紧急前往补给或维修时，可以继续使用剩余耐久高于 5 的鞘翅。
- 所有维修目标完全修满后立即返程。
- 熔炉产物逐个取出；切换维修目标并观察耐久增长，停止增长后才访问下一个熔炉。

### 导航安全策略

- Baritone 的冲刺和跑酷始终启用。
- 所有非挖掘目的地导航均禁止放置方块。
- 前往 perimeter 传送门时允许挖掘方块。
- 远程维修流程的其他位置在离开挖掘区后均禁止挖掘和放置方块。
- 合适时先尝试步行；目标较远或无法向上步行到达时使用鞘翅。
- 导航 watchdog 会重试停滞路线，两次重试后仍无进展则报告错误。

## 配置文件

配置以 JSON 格式保存到：

```text
config/perimeter-digger/worlds/<world-id>.json
```

每个多人服务器地址和单人存档都有独立配置。配置可跨重启保留，但当前任务的运行进度不会生成检查点，也没有实现断线重连后自动恢复任务。

## 构建

Gradle 会从 `build.gradle` 指定的位置加载魔改版 Baritone Fabric JAR，当前路径为：

```text
../baritone/fabric/build/libs/baritone-fabric-1.11.1-17-gb8e1048f-dirty.jar
```

先完成 Baritone 项目构建，再构建本 Mod：

```text
./gradlew build
```

Windows：

```text
gradlew.bat build
```

生成的 JAR 位于 `build/libs/`。

## 许可证

Perimeter Digger 使用 [MIT License](LICENSE)。
