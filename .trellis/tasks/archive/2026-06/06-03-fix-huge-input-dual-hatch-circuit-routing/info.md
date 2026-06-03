# 落地设计：巨型输入总成编程电路路由

## 推荐方案

在现有 `HugeBusPartMachineMixin` 中补齐输入侧物品库存和电路槽的行为，使其与 `ItemBusPartMachineMixin` 对普通输入总线/输入总成的处理一致。

这是推荐方案，因为：

* 修改点仍集中在 GTLCore 对 GTMThings 的兼容 mixin 包 `org.gtlcore.gtlcore.mixin.gtmt`。
* 复用现有 `NotifiableCircuitItemStackHandler`，不新增新的电路处理语义。
* 复用 GTMThings 原本使用的 `UnlimitedItemStackTransfer`，不破坏巨型库存容量。
* 不修改 AE2、ExtendedAE、GTCEu 或 GTMThings jar 源码。

## 具体代码形态

目标文件：

`src/main/java/org/gtlcore/gtlcore/mixin/gtmt/HugeBusPartMachineMixin.java`

新增 imports：

```java
import org.gtlcore.gtlcore.api.machine.trait.NotifiableCircuitItemStackHandler;

import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import com.hepdd.gtmthings.api.misc.UnlimitedItemStackTransfer;
```

新增 `createInventory` 注入，输入侧覆盖返回值：

```java
@Inject(method = "createInventory", at = @At("HEAD"), remap = false, cancellable = true)
protected void createInventory(Object[] args, CallbackInfoReturnable<NotifiableItemStackHandler> cir) {
    if (io == IO.IN) {
        cir.setReturnValue(new NotifiableItemStackHandler(this, getInventorySize(), IO.IN, IO.IN,
                UnlimitedItemStackTransfer::new)
                .setFilter(itemStack -> !IntCircuitBehaviour.isIntegratedCircuit(itemStack)));
    }
}
```

新增 `createCircuitItemHandler` 注入，输入侧覆盖返回值：

```java
@Inject(method = "createCircuitItemHandler", at = @At("HEAD"), remap = false, cancellable = true)
protected void createCircuitItemHandler(Object[] args, CallbackInfoReturnable<NotifiableItemStackHandler> cir) {
    if (args.length > 0 && args[0] instanceof IO io && io == IO.IN) {
        cir.setReturnValue(new NotifiableCircuitItemStackHandler(this));
    }
}
```

## 与现有结构风格的一致性

* `@Inject(..., remap = false, cancellable = true)` 与 `ItemBusPartMachineMixin` 当前写法一致。
* `Object[] args` 参数形态与项目里已有的第三方机器 mixin 一致。
* 直接调用 `IntCircuitBehaviour.isIntegratedCircuit`，与普通输入总线过滤逻辑一致。
* 直接复用 `NotifiableCircuitItemStackHandler`，与普通输入总线、普通机器、输入仓的电路槽行为一致。
* 不引入 helper 类，避免为一次兼容 patch 增加额外抽象。

## 行为路径

修复后 AE 样板供应器推送 `programmed_circuit{Configuration:N}` 到巨型输入总成时：

1. 普通巨型物品库存因 filter 拒收编程电路。
2. 输入侧电路槽 handler 具备 `IO.IN` 外部输入能力。
3. 编程电路被 `NotifiableCircuitItemStackHandler.insertItem` 接收。
4. 电路槽被覆盖为 N 号。
5. GT 配方 `.circuitMeta(N)` 在模拟匹配阶段通过电路槽满足。
6. 编程电路不进入普通库存，也不在配方执行时消耗。

## 影响范围

### 会改变

* 巨型输入总成支持 AE 样板电路切换。
* 如果直接 patch `HugeBusPartMachine`，巨型输入总线也会支持 AE 样板电路切换。
* 旧任务文本中“巨型输入总线/成不能通过外界输入编程电路修改仓室本身的电路设置”将过期。

### 不应改变

* 普通输入总线/普通输入总成行为。
* 巨型输入总成接收非编程电路物品。
* 巨型库存大容量特性。
* 催化剂槽和流体槽的既有能力。
* AE 编程电路磁盘和样板编码逻辑。

## 覆盖范围评估

本补丁按底层机器类生效，而不是按物品显示名称生效。

| 游戏内类型 | 注册/底层路径 | 本补丁是否覆盖 | 说明 |
| --- | --- | --- | --- |
| `gtmthings:*_huge_item_import_bus` 巨型输入总线 | `HugeBusPartMachine`，`io == IO.IN` | 覆盖 | 所有 tier 共用该类；普通库存会拒收编程电路，电路槽会接收样板电路 |
| `gtmthings:*_huge_dual_hatch` 巨型输入总成 | `HugeDualHatchPartMachine extends HugeBusPartMachine` | 覆盖 | 继承 `HugeBusPartMachine` 的物品库存和电路槽创建逻辑，因此会命中本 mixin |
| `gtmthings:*_huge_item_export_bus` 巨型输出总线 | `HugeBusPartMachine`，`io == IO.OUT` | 不改变 | 注入条件只处理输入侧，输出侧继续走 GTMThings 原逻辑 |
| `gtceu:*_huge_input_hatch` 巨型输入仓 | `HugeFluidHatchPartMachine extends FluidHatchPartMachine` | 本补丁不覆盖 | 这是流体输入仓，不走 `HugeBusPartMachine`；项目已有 `FluidHatchPartMachineMixin` 复用 `NotifiableCircuitItemStackHandler` 处理输入侧电路槽 |
| `gtceu:*_huge_output_hatch` 巨型输出仓 | `HugeFluidHatchPartMachine extends FluidHatchPartMachine`，`io == IO.OUT` | 不改变 | 输出侧不需要接收 AE 样板电路 |
| 其他非 `HugeBusPartMachine` 路径的仓室 | 非当前 GTMThings 巨型物品输入路径 | 不保证 | 不属于本任务范围，需要另按底层类确认 |

因此，如果“巨型输入仓室系列”指 FTBQuest 里同一页列出的所有巨型仓室，本补丁不会把所有显示名称都改掉；它只修复会把 AE 编程电路当作普通物品吞进去的 GTMThings 巨型物品输入路径。流体巨型输入仓不属于该堵塞路径，并且已有 `FluidHatchPartMachineMixin` 提供输入侧电路槽 handler。

## 风险与缓解

### 容量退化

风险：误用普通 `NotifiableItemStackHandler(this, getInventorySize(), IO.IN)` 会让底层 storage 退化为普通栈容量。

缓解：实现必须使用 `UnlimitedItemStackTransfer::new`。

### 目标方法签名漂移

风险：GTMThings 版本更新后 `createInventory` 或 `createCircuitItemHandler` 签名变化，mixin 失效。

缓解：保持 `remap = false`；实现后运行 `./gradlew compileJava`，并在版本升级时检查本地 jar 字节码或源码。

### 催化剂槽误接收

风险：如果外部能力聚合顺序变化，普通库存拒收后可能尝试其他可插入槽。

当前评估：GTMThings 的共享催化剂槽构造中 capability 为 `IO.NONE`，主要通过 combined recipe handler 参与配方，不应暴露为外部插入目标。电路槽替换为 `NotifiableCircuitItemStackHandler` 后应成为正确接收者。

缓解：游戏内验证普通库存、电路槽、催化剂槽三个位置。

### 既有存档残留

风险：修复不会自动清理已经进入巨型普通库存的编程电路。

缓解：作为已知限制记录；玩家需要手动弹出或清理一次。

## 可选方案

### 方案 A：Patch `HugeBusPartMachine`

推荐。实现最简单，巨型输入总线和巨型输入总成都获得一致行为。

适合目标：让巨型输入类机器全面对齐普通输入总线/输入总成。

### 方案 B：只 Patch `HugeDualHatchPartMachine`

更窄，但实现复杂度更高。需要新增目标为 `HugeDualHatchPartMachine` 的 mixin，并通过新增同签名方法覆盖继承自 `HugeBusPartMachine` 的 `createInventory` / `createCircuitItemHandler`。

风险：构造期间的虚方法分派、目标类未声明方法时的 mixin 合并语义更容易出错；维护者以后看到 `HugeBusPartMachineMixin` 时也不容易发现巨型输入总成另有特殊 patch。

### 方案 C：只拒收普通库存里的编程电路

不推荐。它能避免堵塞，但不能实现用户要求的 AE 样板电路切换。

## 推荐验收顺序

1. 编译：`./gradlew compileJava`
2. 格式：`./gradlew spotlessCheck`
3. 游戏内验证巨型输入总成：
   * N 号电路样板下单后，普通库存无编程电路。
   * 电路槽变为 N 号。
   * 目标配方可运行。
4. 游戏内验证普通输入总成回归。
5. 游戏内验证巨型输入总线是否按预期同样支持电路切换。

## 实施清单

### 1. 修改范围

只修改现有文件：

`src/main/java/org/gtlcore/gtlcore/mixin/gtmt/HugeBusPartMachineMixin.java`

不新增 mixin 文件，不新增 helper，不新增配置项。该 mixin 已在 `gtlcore.mixin.json` 中注册，因此本修复不需要修改资源配置。

### 2. 代码组织

沿用当前文件结构：

* 保留类继承 `TieredIOPartMachine`，继续通过父类字段 `io` 判断输入/输出侧。
* 新增两个 `@Inject` 方法，与现有 `getInventorySize`、`setDistinct` 同级。
* 注入方法命名直接使用目标方法名：`createInventory`、`createCircuitItemHandler`，与项目当前 mixin 风格一致。
* 不添加 `@Unique` 辅助方法；逻辑足够短，直接内联更易维护。

### 3. 实现顺序

1. 在 `HugeBusPartMachineMixin` 增加必要 import。
2. 增加 `createInventory(Object[] args, CallbackInfoReturnable<NotifiableItemStackHandler> cir)` 注入。
3. 输入侧 `io == IO.IN` 时覆盖普通库存 handler。
4. 确认普通库存 handler 构造使用 `UnlimitedItemStackTransfer::new`。
5. 对普通库存设置 `!IntCircuitBehaviour.isIntegratedCircuit(itemStack)` 过滤。
6. 增加 `createCircuitItemHandler(Object[] args, CallbackInfoReturnable<NotifiableItemStackHandler> cir)` 注入。
7. 当 `args[0]` 是 `IO.IN` 时返回 `new NotifiableCircuitItemStackHandler(this)`。
8. 输出侧和其他情况不设置返回值，继续走 GTMThings 原逻辑。

### 4. 与项目规范对齐点

* mixin 注入使用 `remap = false`，因为目标是第三方 mod 类。
* 使用 `@Inject` 覆盖返回值，不使用 `@Overwrite`。
* import 顺序按项目现有风格保持：`org.gtlcore...`，然后 `com.gregtechceu...`，然后 `com.hepdd...`，最后 Sponge mixin。
* 普通输入总线已有同类逻辑，因此电路识别继续使用 `IntCircuitBehaviour.isIntegratedCircuit`。
* 电路槽处理继续复用 `NotifiableCircuitItemStackHandler`，不复制 insert 行为。

### 5. 验证矩阵

| 场景 | 期望结果 | 风险点 |
| --- | --- | --- |
| 巨型输入总成接收 N 号编程电路样板 | 普通库存无电路，电路槽为 N | 样板供应器可能仍先尝试普通库存 |
| 巨型输入总成连续接收 N/M 号样板 | 电路槽被后续样板覆盖 | 订单并发时以最后推入为准 |
| 巨型输入总成接收普通物品 | 物品进入普通库存 | 过滤器不能误拒非电路物品 |
| 巨型库存接收大数量物品 | 大容量能力保留 | 必须使用 `UnlimitedItemStackTransfer::new` |
| 巨型输入总线接收电路样板 | 若采用方案 A，也应支持电路切换 | 这是范围扩大，需要确认 |
| 普通输入总成接收电路样板 | 行为不变 | 本修复不应触碰普通路径 |
| 催化剂槽相关配方 | 行为不变 | 需确认电路没有落入共享催化剂槽 |

### 6. 后续维护说明

这次修复本质上是把 GTLCore 已经给普通 `ItemBusPartMachine` 做过的 AE 样板电路路由补丁，同步到 GTMThings 的 `HugeBusPartMachine` 路径。以后如果 GTMThings 上游原生支持了等价行为，应优先删除或收窄这个 mixin，避免双重 patch。

版本升级时需要重新核对：

* `HugeBusPartMachine#createInventory(Object[] args)` 是否仍存在。
* `HugeBusPartMachine#createCircuitItemHandler(Object[] args)` 是否仍存在。
* 普通库存是否仍使用 `UnlimitedItemStackTransfer`。
* 催化剂槽是否仍不暴露为外部输入目标。

### 7. 已知限制

* 已经进入巨型普通库存的编程电路不会被自动迁移。
* 该修复不改变 AE 样板的并发语义；多个订单同时切换同一个巨型输入总成电路时，仍可能和普通输入总成一样受推送顺序影响。
* 游戏内任务说明如仍写着巨型输入总线/成不能外部输入电路，后续需要另开文案任务更新。
