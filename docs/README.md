# ME Placement Tool for gto Development Documentation

> [!WARNING]
> These documents were produced with AI assistance and must be checked against the current source before changing runtime behavior. / 本组文档由 AI 辅助编写；修改运行时行为前必须以当前源码重新核对。

Current baseline: `2.1.4-for-gtocore-0.5.6-beta`

## Documents

- [Architecture and GTO adaptation / 架构与 GTO 适配](ARCHITECTURE_AND_GTO_ADAPTATION_ZH_EN.md)
- [Build and release / 构建与发布](BUILD_AND_RELEASE_ZH_EN.md)
- [Chinese user README / 中文用户说明](../README_ZH.md)
- [English user README](../README_EN.md)
- [Changelog / 更新日志](../CHANGELOG.md)
- [Local dependency guide / 本地依赖说明](../libs/README.md)

## Source Of Truth

Runtime behavior is defined by `src/main/java` and `src/main/resources`. When documentation differs from code, update the documentation or explicitly change and retest the implementation; do not silently assume upstream ME Placement Tool behavior still applies.

运行时行为以 `src/main/java` 与 `src/main/resources` 为准。若文档和源码不一致，应更新文档，或明确修改并重新测试实现；不得默认上游 ME Placement Tool 的行为仍然适用于 GTO 适配版。
