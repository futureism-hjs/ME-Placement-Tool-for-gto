# ME Placement Tool for gto Changelog

## 2.1.4-for-gtocore-0.5.6-beta - 2026-07-30

### 中文

- 从 GTOHJS 中分离为独立 Mod；Forge 生命周期、菜单、网络、五个物品、全部资源和配方 ID 均使用原版 `meplacementtool` 命名空间。
- 移植并保留 ME 放置工具、ME 多方块放置工具、ME 线缆放置工具、棱镜原体和光谱的钥匙。
- 适配 GTO 定制 AE2 的库存匹配、提取、玩家操作来源、放置失败回滚和流体放置。
- 完整打包上游物品模型、动态纹理、GUI、按键、中英文翻译和五条工作台配方。
- 线缆工具配方接受 `ae2:smart_dense_cable` Tag 中的任意颜色致密智能线缆。
- 完整重写中英文 README，并新增双语架构、GTO 适配、构建和发布文档。
- 本次正式归档按用户明确要求仅执行 Java 21 清洁构建和发布结构核验，不进行新的客户端启动验证。

### English

- Split the GTO-compatible port out of GTOHJS as a standalone mod. Forge lifecycle, menus, networking, all five items, all assets, and recipe IDs use the upstream `meplacementtool` namespace.
- Retained the ME Placement Tool, ME Multiblock Placement Tool, ME Cable Placement Tool, Prism Core, and Key of Spectrum.
- Adapted inventory matching, extraction, player action sources, placement rollback, and fluid placement for GTO's customized AE2 runtime.
- Packaged the upstream item models, animated textures, GUI, key bindings, English/Chinese translations, and five crafting recipes.
- The cable-tool recipe accepts every smart dense cable in the `ae2:smart_dense_cable` tag.
- Rewrote the Chinese and English READMEs and added bilingual architecture, GTO adaptation, build, and release documentation.
- At the user's explicit request, this formal archive performs a Java 21 clean build and release-layout verification without a new client launch.
