# 修复巨型输入总成的编程电路路由

## Goal

让 GTMThings 巨型输入总成在接收 AE 处理样板输入时，行为与普通输入总成一致：样板中的 `gtceu:programmed_circuit` 应进入电路槽并覆盖当前电路配置，而不是进入普通物品库存造成堵塞。

## What I Already Know

* 普通输入总成继承 `ItemBusPartMachine`，因此会命中 `ItemBusPartMachineMixin`。
* 普通输入总线/总成的普通库存已过滤编程电路：`itemStack -> !IntCircuitBehaviour.isIntegratedCircuit(itemStack)`。
* 普通输入总线/总成的电路槽已替换为 `NotifiableCircuitItemStackHandler`，其外部输入能力为 `IO.IN`，可接收 AE 推送的编程电路并覆盖电路槽。
* 巨型输入总成来自 GTMThings：`HugeDualHatchPartMachine extends HugeBusPartMachine`，没有走普通 `DualHatchPartMachine extends ItemBusPartMachine` 的继承链。
* 当前 GTLCore 已有 `HugeBusPartMachineMixin`，但只调整 `getInventorySize` 和 `setDistinct` 后刷新，没有补齐普通输入总成的编程电路路由行为。
* 本地 `gtmthings-1.3.5.b.jar` 字节码显示 `HugeBusPartMachine` 包含普通库存、电路库存、催化剂库存和组合物品 handler；普通库存使用 `UnlimitedItemStackTransfer`，未看到拒收编程电路的过滤逻辑。
* `gtceu:*_huge_input_hatch` 巨型输入仓来自 GTLCore 的 `HugeFluidHatchPartMachine extends FluidHatchPartMachine`，不走 `HugeBusPartMachine`；它属于流体仓路径，不是本次物品库存堵塞的直接目标。
* 当前项目已有 `FluidHatchPartMachineMixin#createCircuitItemHandler`，输入侧流体仓已复用 `NotifiableCircuitItemStackHandler`。
* 本地整合包任务文本曾提示巨型输入总线/成不能通过外部输入编程电路修改仓室本身的电路设置；本任务将改变这一点，使巨型输入总成至少与普通输入总成一致。

## Requirements

* 巨型输入总成接收 AE 样板供应器发送的编程电路时，不得把编程电路放入普通物品库存。
* 编程电路应被路由到巨型输入总成的电路槽，并覆盖当前电路配置。
* 巨型输入总成的普通物品库存仍应正常接收非编程电路物品。
* 编程电路作为 GT 配方的 non-consumable circuit 条件，不应在机器运行后消耗或堆积。
* 修复应尽量复用普通输入总成已有的 `NotifiableCircuitItemStackHandler` 行为，避免新增一套不同语义。

## Recommended Implementation

在 `src/main/java/org/gtlcore/gtlcore/mixin/gtmt/HugeBusPartMachineMixin.java` 中补齐两类注入：

1. 覆盖 `HugeBusPartMachine#createInventory(Object[] args)` 的返回值。
   * 当 `io == IO.IN` 时，返回一个与原类一致容量和底层 storage factory 的 `NotifiableItemStackHandler`。
   * 给该普通库存设置过滤器：拒收 `IntCircuitBehaviour.isIntegratedCircuit(stack)`。
   * 需要保留巨型总线使用 `UnlimitedItemStackTransfer` 的大容量能力，不能退化成普通 `CustomItemStackHandler`。

2. 覆盖 `HugeBusPartMachine#createCircuitItemHandler(Object[] args)` 的返回值。
   * 当 `args[0] instanceof IO io && io == IO.IN` 时，返回 `new NotifiableCircuitItemStackHandler(this)`。
   * 输出侧保持原逻辑，不暴露电路槽。

这相当于把普通输入总成中已经验证过的两段逻辑迁移到 GTMThings 巨型总线/总成路径。

进一步落地细节见 `info.md`。本任务的技术调查见 `research/huge-bus-circuit-routing.md`。

## Impact Assessment

### Expected Positive Impact

* AE 样板里的编号编程电路会切换巨型输入总成电路槽，和普通输入总成一致。
* 编程电路不再占用巨型输入总成普通库存，不会造成库存堵塞。
* 多个带不同电路号的样板可以通过同一巨型输入总成切换电路配置，前提是订单推送顺序和机器配方搜索时机与普通输入总成相同。

### Behavior Changes

* 巨型输入总成将从“不支持外部电路切换”变为“支持外部电路切换”。
* 如果同一个 `HugeBusPartMachineMixin` 同时覆盖巨型输入总线和巨型输入总成，则巨型输入总线也会获得同样行为。这和“参考普通输入总成”的目标可能一致，但需要用户确认是否接受。
* 本修复不会覆盖 `gtceu:*_huge_input_hatch` 巨型流体输入仓，因为该类不继承 `HugeBusPartMachine`；但流体仓已有独立的电路槽 mixin，且没有巨型物品库存接收编程电路的堵塞路径。
* 整合包任务说明中“巨型输入总线/成不能通过外界输入编程电路修改仓室本身的电路设置”的文字将不再准确，后续可能需要更新任务文本或语言说明。

### Risks

* **底层 storage factory 风险**：如果 `createInventory` 覆盖时没有使用 `UnlimitedItemStackTransfer`，会破坏巨型库存的大容量特性。实现时必须保留原类 storage factory。
* **代理顺序风险**：`HugeBusPartMachine` 的 combined handler 会组合普通库存、电路槽、催化剂槽。普通库存拒收编程电路后，代理才有机会把电路交给电路槽；若只替换电路槽、不改普通库存，堵塞不会消失。
* **范围扩大风险**：mixin 目标是 `HugeBusPartMachine`，巨型输入总线和巨型输入总成都继承/使用该类。若只想改巨型输入总成，需要更窄的注入条件或目标类，但这会复杂化实现。
* **催化剂槽影响**：巨型输入总线/总成有共享催化剂槽。普通库存拒收编程电路后，如果代理尝试顺序包含催化剂槽，理论上还需确认催化剂槽不会接收编程电路；若会接收，也应给催化剂槽或代理路径增加保护。
* **存量污染风险**：已经堵在普通库存里的编程电路不会因为代码修复自动迁移到电路槽。玩家可能需要手动弹出/清理已有残留。
* **文案一致性风险**：游戏内说明、FTBQuest 文本和实际行为会出现差异。

## Acceptance Criteria

* 巨型输入总成旁接 AE 样板供应器，下单含 `programmed_circuit{Configuration:N}` 的处理样板后：
  * 巨型输入总成普通物品库存没有新增编程电路。
  * 巨型输入总成电路槽变为 N 号。
  * 对应 `.circuitMeta(N)` 的 GT 配方可以运行。
* 连续下单 N 号、M 号电路样板时，电路槽能被后续样板覆盖。
* 非编程电路物品仍能进入巨型输入总成普通物品库存。
* 普通输入总线/普通输入总成现有行为不回退。
* 巨型库存容量和现有催化剂槽能力不回退。
* `gtceu:*_huge_input_hatch` 巨型流体输入仓现有电路槽行为不回退。

## Verification Plan

* 编译检查：运行项目可用的最小编译/检查命令，确认 mixin 目标方法签名正确。
* 源码检查：确认 `HugeBusPartMachineMixin` 中 `createInventory` 使用巨型库存原有的大容量 transfer factory。
* 游戏内手测：
  * AE 无限编程电路磁盘提供 N 号电路。
  * 样板供应器推送到巨型输入总成。
  * 观察普通库存、电路槽和配方运行。
* 回归手测：
  * 普通输入总成仍支持 AE 样板电路切换。
  * 巨型输入总成仍能接收大数量普通物品。

## Out of Scope

* 不修改 AE2、ExtendedAE 或 GTCEu 的基础逻辑。
* 不修改编程电路磁盘配方。
* 不自动迁移或清理存档中已经进入普通库存的编程电路。
* 不在本任务中重写 GTMThings 上游源码；使用 GTLCore mixin 修复当前整合包行为。

## Resolved Scope Decision

* 允许巨型输入总线获得 AE 样板电路切换能力。原因是巨型输入总线和巨型输入总成共用 `HugeBusPartMachine`，统一行为实现更简单，也更接近普通输入总线/普通输入总成的行为。
* 不需要考虑巨型蒸汽输入仓；本任务范围收敛为 GTMThings 巨型物品输入总线和巨型输入总成。

## Technical Notes

* `src/main/java/org/gtlcore/gtlcore/mixin/gtm/machine/ItemBusPartMachineMixin.java`
  * 普通输入总线/总成的参考实现。
* `src/main/java/org/gtlcore/gtlcore/api/machine/trait/NotifiableCircuitItemStackHandler.java`
  * 外部输入编程电路并覆盖电路槽的关键 handler。
* `src/main/java/org/gtlcore/gtlcore/mixin/gtmt/HugeBusPartMachineMixin.java`
  * 推荐修改点。
* `gtmthings-1.3.5.b.jar`
  * `HugeDualHatchPartMachine extends HugeBusPartMachine`。
  * `HugeBusPartMachine#createInventory` 当前使用 `UnlimitedItemStackTransfer`，需要保留。
