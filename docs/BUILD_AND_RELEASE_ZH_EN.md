# Build And Release / 构建与发布

Baseline artifact: `ME-Placement-Tool-for-gto-2.1.4-for-gtocore-0.5.6-beta.jar`

## 中文

### 1. 工具链

- JDK：Java 21，当前工程 `java.toolchain` 也固定为 21。
- 编译目标：Java 17 字节码。
- Gradle：项目 Wrapper，分发版本由 `gradle/wrapper/gradle-wrapper.properties` 决定。
- ForgeGradle：`6.0.54`。
- 默认允许联网解析 Forge、映射和 Gradle 插件；若网络失败，应停止并处理依赖，不应伪造成功产物。

### 2. 活动工程依赖

活动工程的 `libs` 必须包含：

```text
appliedenergistics2-forge-1.20.1-15.267.4.jar
gtceu-forge-1.20.1-26.7.3.jar
ldlib-forge-1.20.1-1.0.50.jar
```

这些 JAR 是本地构建输入，不属于项目源码，不得复制到 Git 清洁源码镜像或 Release。

### 3. 清洁构建

在活动源码根目录执行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat clean build --no-daemon
```

成功条件：

- `clean`、`compileJava`、`processResources`、`jar`、`reobfJar`、`check` 和 `build` 成功。
- `build/libs` 中存在唯一正式版本 JAR。
- JAR 名为 `ME-Placement-Tool-for-gto-2.1.4-for-gtocore-0.5.6-beta.jar`。
- 处理后的 `META-INF/mods.toml` 含正确版本和 GTOCore/GTCEu/AE2 依赖范围。

### 4. 清洁源码范围

应包含：

- `src`、`gradle`、Gradle Wrapper 和构建脚本。
- 三份 README、`docs`、`CHANGELOG.md`、许可证和第三方声明。
- `libs/README.md`，用于说明本地依赖。

应排除：

- `.gradle`、`build`、`run`、日志和缓存。
- `libs/*.jar` 第三方依赖。
- 反编译输出、实验草稿、IDE 本地状态和临时文件。

### 5. 发布位置

- 活动源码：`E:\program\java\GregTech-Odyssey\ME-Placement-Tool-for-gto-Development-Project\ME Placement Tool for gto`
- 清洁源码目标：`E:\program\java\GregTech-Odyssey\Git\ME-Placement-Tool-for-gto-git\ME-Placement-Tool-for-gto`
- Release：`E:\program\java\GregTech-Odyssey\Git\ME-Placement-Tool-for-gto-git\Release`

同步前必须确认目标目录不包含需要保留的 `.git` 元数据。未经用户明确要求，不初始化 Git、不提交、不推送、不创建标签或 GitHub Release。

### 6. 验证政策

通常每次构建后都应部署到固定 GTO 客户端并启动验证。本次 `2.1.4-for-gtocore-0.5.6-beta` 清洁归档由用户明确要求跳过客户端验证，因此只声明“Java 21 清洁构建和发布结构验证通过”，不得把本次归档描述为经过新的客户端回归测试。

## English

### 1. Toolchain

- Java 21 toolchain, compiling Java 17 bytecode.
- Project Gradle Wrapper and ForgeGradle `6.0.54`.
- Network dependency resolution is permitted. Stop on an actual network failure instead of presenting a partial artifact as successful.

### 2. Local Build Inputs

The active `libs` directory must contain the exact AE2 `15.267.4`, GTCEu `26.7.3`, and LDLib `1.0.50` JARs listed above. They are build inputs, not redistributable project source, and must be excluded from clean Git mirrors and Release directories.

### 3. Clean Build

Run from the active source root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat clean build --no-daemon
```

The build is complete only when the reobfuscated JAR exists at:

```text
build/libs/ME-Placement-Tool-for-gto-2.1.4-for-gtocore-0.5.6-beta.jar
```

### 4. Clean Source Manifest

Include `src`, wrapper/configuration files, READMEs, `docs`, changelog, licenses, notices, and `libs/README.md`. Exclude build caches, run directories, logs, third-party JARs, decompilation output, and experimental files.

### 5. Release Policy

Copy clean source only to the requested Git mirror and the formal JAR only to the requested `Release` directory. Do not initialize or mutate Git history and do not upload a remote release without a separate explicit instruction.

The current archive intentionally skips client validation because the user explicitly requested a direct clean build. Report build verification accurately and do not imply a new runtime test occurred.
