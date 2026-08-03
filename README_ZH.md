# ME Placement Tool for gto

> [!WARNING]
> 本项目包含由 AI 生成或在 AI 辅助下完成的代码与文档，可能存在错误、安全问题或与上游接口及许可不一致的情况。使用、修改或分发前请自行审查并充分测试。

[English](README_EN.md) | [开发文档](docs/README.md) | [更新日志](CHANGELOG.md) | [本地依赖说明](libs/README.md)

这是 ME Placement Tool `2.1.4` 面向 Minecraft 1.20.1 Forge、GregTech Odyssey `0.5.6-beta` 及其定制 Applied Energistics 2 运行时的独立适配版。项目保持上游 `meplacementtool` 命名空间，不依赖 GTOHJS。

当前版本：`2.1.4-for-gtocore-0.5.6-beta`

## 兼容环境

| 组件 | 版本或范围 |
| --- | --- |
| Minecraft | `1.20.1` |
| Forge | `47.4.20`，运行范围 `[47.4.20, 48)` |
| GTOCore | `[0.5.6-beta, 0.5.7)` |
| GTCEu | `[26.7.3, 26.8)` |
| Applied Energistics 2 | `[15.267.4, 15.268)` |
| 构建 JDK | Java 21 |
| 产物字节码 | Java 17 |

## 新增物品

| 注册名 | 中文名 | 用途 |
| --- | --- | --- |
| `meplacementtool:me_placement_tool` | ME 放置工具 | 从绑定的 ME 网络放置普通方块、AE2 部件、伪装板和源流体 |
| `meplacementtool:multiblock_placement_tool` | ME 多方块放置工具 | 批量放置方块、部件或流体，提供方向、数量、预览和撤销 |
| `meplacementtool:me_cable_placement_tool` | ME 线缆放置工具 | 按直线、平面填充或分支方式铺设 AE2 线缆 |
| `meplacementtool:prism_core` | 棱镜原体 | 光谱的钥匙的合成材料 |
| `meplacementtool:key_of_spectrum` | 光谱的钥匙 | 安装在线缆工具中后免费选择线缆颜色 |

五个物品均有独立模型、纹理、中英文翻译和 GTO 原生工作台配方。线缆工具配方接受 `ae2:smart_dense_cable` Tag 中任意颜色的致密智能线缆。

## 连接和供电

1. 将任一放置工具放入 AE2 的 ME 无线接入点进行绑定；提示信息会显示“已连接”或“未连接”。
2. 工具保存无线接入点的维度和坐标。放置时通过该接入点获取对应 ME Grid，并以玩家和接入点共同构造 AE 操作来源。
3. 三种工具均为 AE 供能物品，可在 AE 充能设备内充电；默认充电速率为 `800 AE/t`。
4. 工具直接继承 AE 供能物品，而不是无线终端物品，避免其他 Mod 将其误识别为无线终端。

## 基本操作

| 工具 | 操作 | 默认输入 |
| --- | --- | --- |
| ME 放置工具 | 打开 18 槽配置界面 | 对空气右键 |
| ME 放置工具 | 快速选择配置槽 | 按 `G` 打开单层轮盘 |
| ME 放置工具 | 放置当前物品、部件或流体 | 对目标方块右键 |
| ME 多方块放置工具 | 打开 18 槽配置界面 | 对空气右键 |
| ME 多方块放置工具 | 选择物品、数量和方向 | 按 `G` 打开双层轮盘 |
| ME 多方块放置工具 | 批量放置并显示预览 | 对目标方块右键 |
| ME 多方块放置工具 | 撤销最近一次可撤销放置 | 按住左 `Ctrl` 并左键附近方块 |
| ME 线缆放置工具 | 打开线缆、模式和颜色界面 | 按 `G` |
| ME 线缆放置工具 | 设置定位点或确认铺设 | 对方块右键；直线模式设置首点后也可对空气右键确认 |
| ME 线缆放置工具 | 清除当前定位点 | 左键 |
| ME 线缆放置工具 | 撤销最近一次线缆铺设 | 按住左 `Ctrl` 并左键附近方块 |

按键可在 Minecraft 控制设置中修改。线缆颜色界面的颜色标记快捷键默认为 `A`。

## ME 放置工具

- 配置容量为 18 槽，分为两页，每页 `3 x 3`。
- 支持普通 `BlockItem`、AE2 `IPartItem`、AE2 伪装板以及能够形成源方块的流体。
- 网络缺少目标物品但目标可以自动合成时，会打开 AE2 合成数量界面。
- 放置事务按“模拟可用量、实际预留、执行放置、失败回滚”处理，避免失败放置吞物品。
- 默认搜索物品时忽略 NBT；`ae2:facade` 默认启用严格 NBT 匹配，确保伪装板外观正确。
- 副手持有已配置的 AE2 内存卡时，会尝试把设置应用到新放置的方块或部件，并预先检查相关升级和样板资源。

## ME 多方块放置工具

- 可选放置数量：`1`、`8`、`64`、`256`、`1024`。
- 可选方向：自动平面、南北、东西、垂直。
- 自动模式使用限制候选数量的 BFS，在目标表面寻找可放置位置；客户端预览与服务端执行使用相同的定位规则。
- 支持方块、AE2 部件、伪装板和源流体批量放置。
- 只保留每名玩家最近一次放置记录。撤销要求持有对应工具、处于同一维度并点击放置区域附近。
- 已应用内存卡配置的操作不会被撤销，避免恢复方块时丢失已经写入的机器配置。

## ME 线缆放置工具

支持五类 AE2 线缆：玻璃线缆、包层线缆、智能线缆、致密包层线缆和致密智能线缆。

支持三种结构模式：

- 直线：首点确定起始位置，第二次操作按视线或目标点生成直线。
- 平面填充：使用两个定位点填充平面区域。
- 分支：使用三个定位点定义主干方向、间隔和区域。

颜色规则：

- 未安装光谱的钥匙且副手没有染料时，铺设透明福鲁伊克斯线缆。
- 未安装光谱的钥匙且副手持染料时，副手染料决定目标颜色；已有同色线缆优先直接使用，需要重染时每 8 根线缆消耗 1 个染料。
- 染料消耗依次从 ME 网络、玩家背包和副手扣除。
- 安装光谱的钥匙后，可免费使用界面选择的颜色；副手染料仍可临时覆盖当前选择，但不会被消耗。
- 撤销时移除新放置的中心线缆部件，并把对应线缆返还 ME 网络；网络无法接收的余量返还玩家背包。

## 配置文件

公共配置：`config/meplacementtool-common.toml`

| 配置项 | 默认值 | 说明 |
| --- | ---: | --- |
| `mePlacementToolEnergyCapacity` | `1600000` | ME 放置工具容量，单位 AE |
| `mePlacementToolEnergyCost` | `50` | 单次放置消耗，单位 AE |
| `multiblockPlacementToolEnergyCapacity` | `3200000` | ME 多方块放置工具容量 |
| `multiblockPlacementToolBaseEnergyCost` | `200` | 每个成功放置目标的基础消耗 |
| `cablePlacementToolEnergyCapacity` | `1600000` | ME 线缆放置工具容量 |
| `cablePlacementToolEnergyCost` | `10` | 每根成功放置线缆的消耗 |
| `nbtWhitelistMods` | `[]` | 强制严格 NBT 匹配的 Mod 命名空间 |
| `nbtWhitelistItems` | `["ae2:facade"]` | 强制严格 NBT 匹配的具体物品，支持 `modid:*` |

客户端配置：`config/meplacementtool-client.toml`

- `hudDisplayDuration = 2000`：切换到本 Mod 物品后 HUD 的显示毫秒数；`0` 禁用，`-1` 永久显示。

## GTO 适配

- Forge 生命周期、菜单、网络、物品、资源和配方 ID 均使用 `meplacementtool` 命名空间。
- 五条工作台配方通过最小 CoreMod 注入到 GTO 的 `RecipeFilter.init()` 之后，避免普通 Forge 配方事件被 GTO 配方加载流程覆盖。
- 启动时校验物品、菜单、无线链接处理器和配方注册状态；专用服务器启动后再校验最终 RecipeManager 中的五条配方。
- AE 网络操作使用绑定的无线接入点作为 action host，并兼容 GTO 定制 AE2 的长数量存储接口。
- 本 Mod 与 GTOHJS 完全独立；运行时需要 GTOCore、GTCEu 和整合包定制 AE2，不需要安装 GTOHJS。

## 构建

第三方 Mod JAR 不随清洁源码发布。构建前按 [本地依赖说明](libs/README.md) 准备活动工程的 `libs`。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat clean build --no-daemon
```

正式 JAR：`build\libs\ME-Placement-Tool-for-gto-2.1.4-for-gtocore-0.5.6-beta.jar`

架构、适配点和发布规则见 [开发文档](docs/README.md)。

源代码使用 [LGPL-3.0-only](LICENSE)。第三方算法、UI 与物品素材保留各自上游许可，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
