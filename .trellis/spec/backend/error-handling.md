# Error Handling

> GTLCore 没有 HTTP/API response 层。错误处理主要发生在 Forge 事件、GTCEu/AE2 runtime、mixin、数据生成和 NBT 解析中。

## Local Error Model

本项目使用四类处理方式：

- **Fail fast**：不变量被破坏时抛 `IllegalStateException`、`ArithmeticException`、`AssertionError` 或 `UnsupportedOperationException`。
- **Recover with fallback**：外部 registry、NBT 或兼容性输入无效时记录日志并返回安全默认值。
- **Cancel event**：Forge 事件中通过 `event.setCanceled(true)` 阻止行为，不把普通规则判断写成异常。
- **Isolate callback or async failure**：回调和后台线程捕获异常，记录上下文，避免拖垮 server tick 或关闭流程。

## Fail Fast

用于内部不变量和“不应发生”的分支：

- `Int128.divide(...)` 在除数为 0 时抛 `ArithmeticException("Division by zero")`。
- `BlockStateWatcher.addWatcher(...)` 在 level、pos、callback 为 null 时抛 `IllegalArgumentException`。
- Mixin `@Shadow`/`@Invoker` stub 通常 `throw new AssertionError()`，表示方法体不应被直接执行。
- `MolecularAssemblerMultiblockMachineBase` 这类结构约束用 `IllegalStateException` 表示必要部件缺失或重复。

新增 fail-fast 异常时要带具体原因，不要抛空 `RuntimeException`。如果错误来自外部输入或可恢复存档数据，优先使用 fallback。

## Recoverable External Input

外部 registry、NBT、mod 兼容输入可能损坏或缺失，当前代码倾向于记录并返回安全值：

- `GTLUtil.loadItemStack(...)` 捕获 `RuntimeException`，用 `GTCEu.LOGGER.debug(...)` 记录无效 NBT，然后返回 `ItemStack.EMPTY`。
- `Registries.getItem/getBlock/getFluid(...)` 在找不到 id 时用 `GTLCore.LOGGER.atError().log(...)` 记录，并分别返回 `Items.BARRIER`、`Blocks.BARRIER`、`Fluids.WATER`。
- `GTLUtil.deserializeNBT(...)` 在 tag 类型、id 或 codec parse 失败时返回 `null`。
- AE2 storage inventory 在 tag 数量不一致时用 `AELog.warn(...)` 记录。

调用这些 helper 的代码必须继续检查空值、空栈或 fallback。不要假定 registry lookup 永远成功。

## Forge Events

Forge 事件处理函数应保持短小、可预测：

- 游戏规则阻止使用 `event.setCanceled(true)`，例如传送、踩踏、末地传送门行为。
- side/phase 检查要在修改状态前完成，例如 `TickEvent.PlayerTickEvent` 同时检查 `Phase.END` 和 `LogicalSide.CLIENT`。
- remap 和迁移逻辑集中在 `MissingMappingsEvent`，每个旧 id 显式 remap 到新注册对象。

不要在事件里做长耗时扫描或阻塞 IO；server tick 中需要延迟执行时使用 server executor 或现有 tick 订阅机制。

## Async and Thread Interruption

异步服务以 `AEWriteService` 为参考：

- executor 使用有界队列和 daemon thread。
- weak reference 失效时直接返回。
- shutdown 时 `awaitTermination`，超时后 `shutdownNow()`。
- 捕获 `InterruptedException` 后必须 `Thread.currentThread().interrupt()`，再执行清理。

任何新增后台任务都必须有明确关闭入口，并在 server stopping 或对应 lifecycle 中调用。

## Callback Isolation

`BlockStateWatcher.notifyWatchersInternal(...)` 遍历 watcher callback 时逐个 try/catch，并记录位置和异常：

```java
GTLCore.LOGGER.error("Error in BlockStateWatcher callback at {}: {}",
        pos, e.getMessage(), e);
```

用于事件广播、UI callback、cache listener 等场景时，单个 callback 失败不应影响其他 callback。

## Mixin Error Handling

Mixin 是高风险区域，必须遵守现有模式：

- 修改第三方 mod 方法时显式 `remap = false`，除非目标是 Minecraft/Mojang mapped 方法。
- `@Overwrite` 必须保留 `@author` 和 `@reason` 注释，参考 `CraftingCalculationMixin` 和 `ChanceLogicOrMixin`。
- 新增私有字段或 helper 用 `@Unique`，名称使用 `gTLCore$` 前缀，参考 `GTRecipeBuilderMixin.gTLCore$eut`。
- `@Inject(..., cancellable = true)` 只在确实会设置返回值或取消目标方法时使用。
- Accessor/Invoker stub 用 `throw new AssertionError()`，不要返回伪默认值掩盖错误。

修改 mixin 后检查 `gtlcore.mixin.json`，并优先运行 `./gradlew compileJava` 捕获签名漂移。

## User-Facing Errors

玩家可见文本使用 `Component.translatable(...)` 和 lang 文件，不要把可见中文或英文硬编码在 GUI 或 tooltip 中。调试工具、日志和内部异常可以使用中文消息，因为代码库已有此模式。

## Common Mistakes

- 捕获 `Exception` 后什么都不做。只有明确的兼容探测才允许忽略，并应尽量缩小 try/catch 范围。
- 在热路径中用异常做普通控制流，例如 recipe 匹配、tick 轮询、slot 扫描。
- registry lookup 失败后继续使用原始空值。
- 异步任务吞掉 interrupt。
- `@Overwrite` 没有说明原因或没有同步检查目标方法签名。
