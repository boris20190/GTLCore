# 修复编程电路进入输入仓室后的异常堆叠

## Goal

修复 AE 样板供应器向输入类仓室发送虚拟编程电路时，电路槽显示 19、64 等异常堆叠数量的问题。编程电路应继续作为仓室/机器的电路状态开关使用，但写入电路槽时数量必须归一化为 1，并且模拟插入不能改变真实槽位。

## What I Already Know

* 普通输入总线/输入总成、流体输入仓/巨型流体输入仓、巨型输入总线/巨型输入总成，以及部分单方块机器，都复用 `NotifiableCircuitItemStackHandler` 作为输入侧电路槽 handler。
* `NotifiableCircuitItemStackHandler#insertItem` 当前在收到 GT 编程电路时直接 `storage.setStackInSlot(slot, stack)`，会把 AE 推来的整组 `ItemStack` 原样写入槽位。
* `getSlotLimit(slot)` 返回 1，但直接写底层 `storage.setStackInSlot` 不会自动裁剪 `ItemStack#getCount()`。
* 当前实现没有判断 `simulate`，模拟插入也会真实改写电路槽。
* 配方处理层会跳过 `NotifiableCircuitItemStackHandler` 的普通物品统计；电路槽本来是 non-consumable circuit 条件，不应作为普通输入消耗。

## Requirements

* 编程电路插入电路槽后，槽内 `ItemStack` 数量必须为 1。
* AE 一次推入多个相同编号编程电路时，应只切换电路配置，不在槽内保留堆叠数量。
* `simulate == true` 时不得修改真实电路槽。
* 非编程电路插入行为保持拒收，返回原始 `stack`。
* 配方匹配语义保持不变：`.circuitMeta(N)` 由电路槽当前配置满足，不消耗电路槽内容。
* 修复应集中在共享 handler，避免分别修改每种输入仓室。

## Recommended Implementation

修改：

`src/main/java/org/gtlcore/gtlcore/api/machine/trait/NotifiableCircuitItemStackHandler.java`

在 `insertItem` 中：

1. 判断 `GTItems.INTEGRATED_CIRCUIT.isIn(stack)`。
2. 非编程电路继续返回原始 `stack`。
3. 编程电路路径中，复制输入栈并 `setCount(1)`。
4. 只有 `!simulate` 时写入 `storage.setStackInSlot(slot, circuitStack)`。
5. 返回 `ItemStack.EMPTY`，保持现有“输入的编程电路作为设置指令被接受”的语义。

## Impact Assessment

### Expected Positive Impact

* 所有复用 `NotifiableCircuitItemStackHandler` 的输入侧电路槽都会停止显示异常堆叠。
* AE 批量下单时，编程电路仍能切换仓室当前电路编号，但槽内数量固定为 1。
* 模拟插入不会提前改变电路槽状态。

### Behavior Changes

* 如果外部实际向电路槽插入一组编程电路，槽内只保留 1 个，返回值仍为空。这与当前“电路输入是设置指令，不是普通库存输入”的语义一致。
* 已经存在于存档里的异常堆叠电路不会自动修复，除非后续再次有电路写入或另行增加迁移逻辑。

### Risks

* 若某些外部代码依赖“电路槽中保留输入栈原始数量”的非预期行为，本修复会改变该显示状态。当前设计上电路槽只应表达配置编号，不应表达数量。
* 如果 `simulate` 路径之前被误用来提前设置电路，本修复会暴露该调用方的问题；但这符合 insert API 的通用契约。

## Acceptance Criteria

* AE 样板向输入仓室发送 19 个同编号编程电路后，电路槽显示数量为 1。
* 电路编号仍正确切换到样板指定编号。
* 对应 `.circuitMeta(N)` 配方仍可匹配运行。
* 普通输入总线/输入总成、流体输入仓、巨型输入总线/巨型输入总成行为一致。
* `spotlessCheck` 和 `compileJava` 通过。

## Out of Scope

* 不修改 AE 无限编程电路磁盘或样板编码逻辑。
* 不修改已存在存档中的异常堆叠数据。
* 不修改蒸汽输入仓室。
* 不改变编程电路作为 non-consumable circuit 条件的配方语义。

## Technical Notes

* `src/main/java/org/gtlcore/gtlcore/api/machine/trait/NotifiableCircuitItemStackHandler.java`
  * 共享修复点。
* `src/main/java/org/gtlcore/gtlcore/mixin/gtm/machine/ItemBusPartMachineMixin.java`
  * 普通输入总线/输入总成复用该 handler。
* `src/main/java/org/gtlcore/gtlcore/mixin/gtm/machine/FluidHatchPartMachineMixin.java`
  * 流体输入仓复用该 handler。
* `src/main/java/org/gtlcore/gtlcore/mixin/gtmt/HugeBusPartMachineMixin.java`
  * 巨型输入总线/巨型输入总成复用该 handler。
