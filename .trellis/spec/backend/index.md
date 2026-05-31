# GTLCore Mod Development Guidelines

> 面向 GTLCore 的 Forge/GTCEu 后端与数据生成开发规范。这里记录当前代码库真实做法，不记录理想化重构目标。

## 项目概况

GTLCore 是 Java 17 的 Forge 1.20.1 mod，使用 Architectury Loom、GTCEu、AE2、KubeJS、Jade、JEI、LDLib、Mixin 和 Registrate。主代码位于 `src/main/java/org/gtlcore/gtlcore/`，运行时资源位于 `src/main/resources/`，数据生成产物位于 `src/generated/resources/`。

核心入口和生命周期：

- `GTLCore` 是 Forge `@Mod` 入口，负责代理初始化和 Forge bus tick 监听。
- `CommonProxy` 连接 GTCEu/Forge mod bus，注册材料、机器、配方类型、配方条件和 AE2 存储单元。
- `GTLGTAddon` 是 GTCEu addon 入口，调用物品、方块、声音、覆盖板、元素、TagPrefix、配方添加和配方移除。
- `gtlcore.mixin.json` 管理所有 mixin，按 `client` 和通用 `mixins` 分组。

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | 包结构、资源位置、注册入口和命名规则 | Filled |
| [Data and Persistence Guidelines](./database-guidelines.md) | NBT/SavedData、机器持久化、配方数据生成、资源 JSON | Filled |
| [Error Handling](./error-handling.md) | 异常、回退值、mixin 失败模式、异步中断处理 | Filled |
| [Logging Guidelines](./logging-guidelines.md) | logger 选择、日志级别、热点路径日志限制 | Filled |
| [Quality Guidelines](./quality-guidelines.md) | Spotless、Mixin 安全、注册一致性、验证命令 | Filled |

## Pre-Development Checklist

1. 判断修改属于哪个区域：
   - 注册或数据定义：读 `directory-structure.md`、`database-guidelines.md`、`quality-guidelines.md`。
   - 配方、数据生成或资源 JSON：读 `database-guidelines.md`、`quality-guidelines.md`。
   - 机器、trait、AE2/GTCEu 运行逻辑：读 `directory-structure.md`、`error-handling.md`、`quality-guidelines.md`。
   - Mixin：读 `error-handling.md`、`quality-guidelines.md`，并同步检查 `gtlcore.mixin.json`。
   - 日志或调试输出：读 `logging-guidelines.md`。
2. 修改常量、配方 ID、注册 ID、翻译 key 或配置项之前，先用 `rg` 搜索现有引用。
3. 优先沿用本项目已有入口：`GTLGTAddon`、`CommonProxy`、`GTLRegistration.REGISTRATE`、`GTLCore.id(...)`、`GTLRecipeTypes`、`GTLMachines`、`GTLItems`、`GTLBlocks`。
4. 业务代码改动后至少运行 `./gradlew spotlessCheck` 和 `./gradlew compileJava`。涉及数据生成时再运行 `./gradlew runData`。

## Source References

代表性文件：

- `src/main/java/org/gtlcore/gtlcore/GTLCore.java`
- `src/main/java/org/gtlcore/gtlcore/common/CommonProxy.java`
- `src/main/java/org/gtlcore/gtlcore/GTLGTAddon.java`
- `src/main/java/org/gtlcore/gtlcore/common/data/GTLBlocks.java`
- `src/main/java/org/gtlcore/gtlcore/common/data/GTLMachines.java`
- `src/main/java/org/gtlcore/gtlcore/common/data/GTLRecipeTypes.java`
- `src/main/java/org/gtlcore/gtlcore/data/recipe/MachineRecipe.java`
- `src/main/java/org/gtlcore/gtlcore/mixin/gtm/registry/GTRecipeBuilderMixin.java`
- `src/main/java/org/gtlcore/gtlcore/integration/ae2/async/AEWriteService.java`
- `build.gradle`
