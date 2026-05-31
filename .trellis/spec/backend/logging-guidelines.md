# Logging Guidelines

> 日志用于定位 mod 兼容、数据损坏和运行时异常。高频 tick、recipe 匹配和 AE2 扫描路径必须控制日志量。

## Logger Selection

沿用调用区域所属生态的 logger：

- GTLCore 自己的代码：`GTLCore.LOGGER`，定义在 `GTLCore`。
- GTCEu recipe/util 相关：`GTCEu.LOGGER`，例如 `GTLUtil.loadItemStack(...)`。
- AE2 集成：`AELog`，例如 `CommonProxy.commonSetup(...)` 和 AE2 storage inventory。
- LDLib transfer/container：`LDLib.LOGGER`，例如 `MutableItemTransferList`。

不要使用 `System.out.println`、`printStackTrace()` 或临时 stdout 调试。

## Levels

- `error`：状态损坏、registry id 缺失、callback 执行失败，但代码选择 fallback 继续运行。例子：`Registries.getItem(...)`、`BlockStateWatcher.notifyWatchersInternal(...)`。
- `warn`：外部数据不一致但可修复或可跳过。例子：AE2 storage cell NBT 数组长度不匹配、LDLib 容器不支持序列化。
- `info`：用户显式触发的调试工具输出或低频诊断。例子：结构导出、样板冲突分析。
- `debug`：噪声较大且通常无须用户看到的兼容性失败。例子：无效 ItemStack NBT 加载失败。

## Message Shape

使用参数化日志，不用字符串拼接：

```java
GTLCore.LOGGER.error("Error in BlockStateWatcher callback at {}: {}",
        pos, e.getMessage(), e);
```

建议包含：

- ResourceLocation、registry id、recipe id 或 BlockPos。
- 目标 namespace，例如 `gtlcore`、`gtceu`、`ae2`、`kubejs`。
- 异常对象作为最后一个参数，保留 stack trace。

内部日志可以使用中文消息，当前代码已有 `未找到ID为{}的物品`、`配方{}没有输出` 等模式。玩家可见文本仍应走 lang 文件和 `Component.translatable(...)`。

## Hot Path Restrictions

以下路径默认不能逐项记录日志：

- server tick、machine tick、AE2 crafting calculation。
- recipe lookup、parallel calculation、slot transfer、cache scan。
- mixin 注入到第三方 mod 的频繁方法。

如果必须诊断热路径：

- 使用配置开关或显式调试工具触发。
- 限流，例如按 `getOffsetTimer() % n == 0`。
- 汇总统计后低频输出，不要每个 item/fluid/recipe 输出一行。

## Sensitive Data

不要记录：

- token、cookie、credential、私有服务器地址。
- 完整玩家个人数据或聊天内容。
- 大型 NBT、完整库存、完整配方树，除非是显式调试工具且输出量可控。

可以记录压缩后的定位信息，例如 id、数量、维度、方块坐标、机器定义名。

## Common Mistakes

- 在 catch 块里只记录 `e.getMessage()` 而丢掉异常对象。
- 用 `info` 输出高频内部状态，导致日志刷屏和 TPS 下降。
- 在外部输入失败时既不记录也不返回安全值。
- 把玩家可见错误文本只写在日志里，而没有 GUI/tooltip/lang 反馈。
