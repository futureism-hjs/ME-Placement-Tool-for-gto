# Architecture And GTO Adaptation / 架构与 GTO 适配

Baseline: Minecraft `1.20.1`, Forge `47.4.20`, GTOCore `0.5.6-beta`, GTCEu `26.7.3`, AE2 `15.267.4`, Mod `2.1.4-for-gtocore-0.5.6-beta`.

## 中文

### 1. 模块边界

本项目是独立 Mod，Mod ID、物品、菜单、网络频道、资源和配方均使用 `meplacementtool` 命名空间。它不依赖 GTOHJS，但在 `mods.toml` 中强制要求 GTOCore、GTCEu 和整合包定制 AE2。

主入口为 `com.moakiee.meplacementtool.MEPlacementToolMod`，负责：

- 注册五个物品与两个菜单。
- 注册九个客户端到服务端数据包。
- 注册无线接入点链接处理器。
- 把光谱的钥匙声明为线缆工具的唯一升级。
- 注册公共/客户端配置、客户端界面、预览渲染、HUD 和按键处理器。
- 在加载完成时校验物品、菜单、无线链接器和五条配方的注册状态。

### 2. 物品和菜单

三个工具都继承 `BasePlacementToolItem`。该基类直接继承 AE2 `AEBasePoweredItem`，而不继承 `WirelessTerminalItem`，以避免被其他 Mod 当作无线终端处理。

| 领域 | 主要类 | 职责 |
| --- | --- | --- |
| 单点放置 | `ItemMEPlacementTool` | 方块、AE2 部件、伪装板、源流体放置和失败回滚 |
| 批量放置 | `ItemMultiblockPlacementTool` | BFS 定位、批量事务、方向/数量、内存卡和撤销记录 |
| 线缆放置 | `ItemMECablePlacementTool` | 三种结构模式、五类线缆、颜色/染料、定位点与撤销 |
| 通用配置 UI | `WandMenu`, `WandScreen`, `GhostSlot` | 18 槽、两页、物品/流体目标和选择元数据 |
| 线缆 UI | `CableToolMenu`, `CableToolScreen` | 线缆类型、结构模式、颜色、快捷颜色和升级槽 |
| 快速选择 | `RadialMenuScreen`, `DualLayerRadialMenuScreen` | 单层目标轮盘与目标/数量/方向双层轮盘 |
| 预览 | `MEPartPreviewRenderer`, `MultiblockPreviewRenderer`, `CablePreviewRenderer` | 客户端放置位置预览 |
| 撤销 | `UndoHistory`, `UndoKeyHandler`, `UndoPacket` | 保存每名玩家最近一次操作并在服务端恢复 |

### 3. ME 网络绑定

`BasePlacementToolItem.LINKABLE_HANDLER` 通过 AE2 `GridLinkables` 注册到三个工具。绑定时把无线接入点的 `GlobalPos` 写入工具 NBT 的 `accessPoint`；解绑时删除该键。

每次服务端使用工具时：

1. 从 NBT 读取维度和坐标。
2. 在目标维度获取仍在 ticking 的方块实体。
3. 验证它实现 `IWirelessAccessPoint` 并仍有有效 `IGrid`。
4. 使用 `IActionSource.ofPlayer(player, accessPoint)` 构造存储操作来源。

这种做法避免只保存 Grid 引用造成跨重启失效，也满足定制 AE2 对 action host 和安全上下文的要求。

### 4. 存储事务与 GTO AE2 适配

放置不能直接从网络扣除后再假设世界操作成功。当前实现遵循以下事务：

1. 使用 `Actionable.SIMULATE` 检查目标和完整数量。
2. 使用 `Actionable.MODULATE` 预留确切数量。
3. 临时构造实际物品栈、部件或流体，在服务端执行世界放置。
4. 失败或抛出异常时，把预留内容重新插入 ME 网络；网络无法接收的物品返还玩家。
5. 只按实际成功数量扣除工具能量并写入撤销记录。

所有数量使用 AE2 长整型存储 API，避免大容量 GTO 网络在 `int` 边界截断。

`Config.findMatchingKey` 默认按物品 ID 匹配并忽略 NBT；指定 Mod 或物品可进入严格 NBT 白名单。默认 `ae2:facade` 严格匹配，防止错误使用其他外观的伪装板。

### 5. 放置算法

`PlacementBfs` 提供参数化 BFS 骨架。候选上限为请求数量的 10 倍，避免不可放置表面导致无限搜索。

- `AUTO`：在点击面的平面内搜索，包含对角候选。
- `NORTH_SOUTH`：沿南北扩展。
- `EAST_WEST`：沿东西扩展。
- `VERTICAL`：沿上下扩展。

客户端多方块预览和服务端执行共享方向模式与支撑判断，减少“预览可放但服务端拒绝”的差异。

线缆工具不使用该 BFS，而是根据两个或三个点计算直线、平面或分支位置，并逐点执行相同的模拟、预留、放置与回滚过程。

### 6. 内存卡机制

`MemoryCardHelper` 只读取副手中的已配置 AE2 内存卡。批量放置前会统计卡内所需升级和空白样板，并综合检查玩家与 ME 网络库存；执行时把设置写入新方块或 AE2 部件。

应用内存卡设置的操作标记为不可撤销，因为普通方块快照无法完整恢复外部机器配置、升级内容和样板状态。

### 7. 撤销机制

`UndoHistory` 每名玩家只保留最近一条记录，并在玩家退出时删除。撤销需要：

- 持有与记录类型相符的多方块或线缆工具。
- 位于同一维度。
- 点击记录位置本身或距离任一记录位置小于 3 格的位置。
- 当前世界状态仍与放置快照一致。

线缆记录存储中心部件及返还 key；撤销后优先返回 ME 网络，无法接收的余量进入玩家背包。

### 8. GTO 配方注册

GTO 的配方生命周期会过滤普通注册路径，因此五条工作台配方不能只放静态 JSON。当前实现包含：

- `coremods/meplacementtool_recipe_registration.js`：定位 `com.gtocore.data.Data.commonInit()` 中唯一的 `RecipeFilter.init()` 调用，并在其后注入注册方法。
- `PlacementToolRecipeRegistration.register()`：使用 GTCEu `VanillaRecipeHelper` 注册五条有序配方。
- 加载完成校验：确认注册状态和五个输出物品存在。
- 服务端启动校验：确认最终 RecipeManager 中五个 ID 都是工作台配方且输出正确。

CoreMod 要求目标方法中恰好存在一次匹配调用；数量不是 1 时主动失败，避免 GTO 更新后静默漏配方或重复注入。

### 9. 网络与安全边界

网络频道协议版本为 Mod 内部固定值。服务端数据包在处理前校验玩家、主手或指定背包槽、枚举范围、数量范围和当前工具类型；客户端只负责界面和预览，实际放置、扣物品、扣染料、扣能量和撤销均在服务端完成。

### 10. 资源与许可

`assets/meplacementtool` 内的模型、动态纹理、GUI 和语言文件属于正式源码资源。`META-INF/accesstransformer.cfg` 为预览渲染和动态槽位放宽必要访问；不得当作临时文件删除。

代码遵循 LGPL-3.0-only。Construction Wand、Ars Nouveau、AE2 GUI 和 `_leng` 物品材质的来源及各自许可见 `THIRD_PARTY_NOTICES.md`。

## English

### 1. Runtime Boundary

This is an independent `meplacementtool` Mod. Items, menus, packets, resources, and recipes retain the upstream namespace. GTOHJS is not a dependency; GTOCore, GTCEu, and the pack-specific AE2 runtime are mandatory.

`MEPlacementToolMod` registers five items, two menus, nine client-to-server packets, three `GridLinkables` handlers, common/client configuration, client screens and renderers, and load-complete validation.

### 2. Linked Grid Resolution

All tools extend `AEBasePoweredItem` through `BasePlacementToolItem`, not `WirelessTerminalItem`. Linking stores the wireless access point's `GlobalPos`. Every server action resolves the dimension, ticking block entity, access point, and current grid, then creates `IActionSource.ofPlayer(player, accessPoint)`.

### 3. Storage Transaction

Placement uses a strict sequence: simulate availability, reserve exact long-count storage, perform the world action, refund on rejection or exception, consume energy for successful work, and record undo only after success. Network overflow is returned to the player when possible.

Matching ignores NBT by default. `ae2:facade` is strict by default, and configuration can enable strict matching per namespace or item.

### 4. Placement And Undo

The multiblock tool uses bounded BFS with automatic face-plane, north-south, east-west, and vertical modes. The cable tool calculates line, plane-fill, or three-point branching positions. Client previews share the same selection rules used by server placement.

One undo entry is retained per player. Undo requires the matching tool, dimension, nearby target, and unchanged placed state. Operations that applied memory-card settings are intentionally excluded.

### 5. GTO Recipe Lifecycle

A minimal CoreMod injects `PlacementToolRecipeRegistration.register()` immediately after the single `RecipeFilter.init()` call in `Data.commonInit()`. Recipes are built with GTCEu's `VanillaRecipeHelper`, checked at load complete, and validated again against the final server RecipeManager.

If a future GTOCore changes that call site, the transformer fails loudly instead of silently losing or duplicating recipes.

### 6. Maintenance Rules

- Treat models, UI textures, language files, the CoreMod, access transformer, and notices as production resources.
- Keep third-party build JARs only in the active `libs` directory and out of clean source mirrors.
- Do not change packet validation or the simulate/reserve/rollback sequence without client and server-side regression testing.
- Do not assume upstream behavior where this port differs; check the current classes listed in the Chinese architecture table.
