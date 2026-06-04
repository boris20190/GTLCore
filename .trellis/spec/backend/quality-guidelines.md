# Quality Guidelines

> 质量标准以当前 Gradle/Spotless/Mixin/GTCEu 工作流为准。改动要小、可搜索、可生成、可验证。

## Formatting

项目使用 Spotless 管理 Java 格式：

- Java 17，`options.release.set(17)`。
- `spotless/spotless.eclipseformat.xml` 定义 Eclipse formatter。
- `spotless/spotless.importorder` 定义 import 分组。
- Spotless 会移除未使用 import，并要求文件以换行结束。

Import 分组当前为：

1. `org.gtlcore.gtlcore`
2. `com.gregtechceu`、`com.lowdragmc`、`net`
3. 其他第三方包
4. `java`
5. `javax`
6. static imports

修改 Java 后运行：

```bash
./gradlew spotlessCheck
./gradlew compileJava
```

如需自动格式化，使用：

```bash
./gradlew spotlessApply
```

## Required Patterns

### Search before changing shared values

修改以下内容前必须用 `rg` 搜索：

- 注册 ID、recipe id、translation key、config key。
- tier 数组、GTValues tier index、材料名。
- mixin target 方法名和 descriptor。
- `gtlcore.mixin.json` 中已有 mixin 项。

这些值常同时出现在 Java、resource JSON、generated resources 和 lang 文件中。

### Registration consistency

新增或修改注册项时检查完整链路：

- `common/data/*` 是否有注册字段和 `init()` 触发。
- `CommonProxy` 或 `GTLGTAddon` 是否会调用入口。
- recipe、lang、model、blockstate、loot table、tag 是否需要生成或补充。
- KubeJS/Jade/JEI 等集成是否需要暴露新类型。
- 迁移旧 id 时是否需要 `MissingMappingsEvent` remap。

### Mixin safety

优先使用 `@Inject`、`@Redirect`、Accessor 或接口扩展。只有目标方法必须整体替换时才使用 `@Overwrite`。

Mixin 必须满足：

- 第三方 mod API 通常 `remap = false`。
- `@Overwrite` 带 `@author` 和 `@reason`。
- `@Unique` 字段和方法使用 `gTLCore$` 前缀。
- 新增 mixin 同步加入 `gtlcore.mixin.json`。
- 客户端目标放 `client` 数组，不放通用 `mixins`。

### Server/client side

- server 权威状态写入必须在 server side。
- 客户端 UI、renderer、highlight 等放 `client/` 或 client mixin。
- Forge event 中先检查 side、phase 和实体类型，再修改状态。
- 后台 executor 必须有关闭路径，参考 `ForgeServerEventListener.onServerStopping(...)` 调用 `AEWriteService.INSTANCE.shutDownGracefully()`。

### Performance

项目大量逻辑运行在 server tick、recipe lookup 和 AE2 crafting 热路径上：

- 热路径优先使用 fastutil、缓存、数组或复用对象，参考 `MultipleRecipesLogic`、`MEPatternBufferPartMachine`、`Int128`。
- 避免在循环内创建大量 `Component`、`ResourceLocation`、NBT 或 stream pipeline。
- 用 `getOffsetTimer() % n` 做低频刷新，参考 `PerformanceMonitorMachine` 和 `LightningRodMachine`。
- 避免在 mixin hot path 中记录日志或执行阻塞 IO。

### Item handler contracts

- `insertItem(..., simulate = true, ...)` 必须保持无副作用，不能改写真实槽位或机器状态。
- 直接调用底层 `storage.setStackInSlot(...)` 会绕过 handler 的 `getSlotLimit(...)` 和常规插入裁剪逻辑；写入前必须手动归一化数量或校验栈内容。
- 作为配置/状态使用的物品槽位（例如编程电路槽）应只表达状态本身，不应保留外部输入栈的原始数量。

## Forbidden Patterns

- 不经搜索直接改共享 ID 或配置默认值。
- 新业务逻辑放到 `GTLCore` 入口类。
- 在 common/server 路径直接引用客户端-only 类。
- 新增 mixin 但不更新 `gtlcore.mixin.json`。
- 在 `src/generated/resources/` 手改 datagen 产物后不保留对应 Java 生成逻辑或验证说明。
- 在 catch 块中吞掉异常且没有 fallback、日志或注释。
- 在 tick/recipe/slot 扫描中输出逐项 info 日志。
- 引入数据库、外部服务或新依赖来解决本项目已有 Forge/GTCEu/AE2 API 能处理的问题。

## Testing and Verification

当前仓库没有 `src/test` 测试目录，主要验证依赖 Gradle 编译、Spotless 和 datagen。

### Gradle runtime JDK

项目 Java 编译目标是 17，但 Gradle/Loom 运行时需要一个完整 JDK 安装。Loom source remap 会读取 `$JAVA_HOME/release`；如果本机 Java 17 包缺这个文件，Gradle 可能在项目配置阶段输出 `FileNotFoundException` 并显著拖慢首次构建。

本地验证命令可以用完整的 Java 21 JDK 运行 Gradle，同时保持 `options.release.set(17)` 输出 Java 17 字节码：

```bash
export JAVA_HOME=<complete-jdk-21-path>
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew spotlessCheck compileJava
```

不要把本机绝对 JDK 路径提交进 `build.gradle` 或 `gradle.properties`。需要固定本机 Gradle runtime 时，用 shell 环境、IDE Gradle JVM 设置或未跟踪的本地配置。

按改动类型选择最小验证：

- 纯文档/spec：检查模板残留和链接。
- Java 逻辑：`./gradlew spotlessCheck`、`./gradlew compileJava`。
- Mixin：至少 `./gradlew compileJava`，并人工核对目标方法签名和 `gtlcore.mixin.json`。
- 配方、注册、资源生成：`./gradlew runData`，再检查 generated diff。
- 发布前或大范围改动：`./gradlew build`。

## Code Review Checklist

- 改动是否在正确包边界内，而不是塞进入口类或泛用 `utils/`。
- 注册、配方、资源、翻译、mixin config 是否成套更新。
- 是否保留 server/client side 边界。
- 是否避免热路径日志、阻塞 IO 和不必要分配。
- 异常是否有明确处理策略：fail fast、fallback、event cancel 或 callback isolation。
- 是否运行了与改动范围匹配的最小验证，并记录无法运行的原因。
