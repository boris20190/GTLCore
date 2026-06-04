# 落地设计：编程电路电路槽数量归一化

## 结论

异常堆叠的根因是 `NotifiableCircuitItemStackHandler#insertItem` 把外部输入的编程电路 `ItemStack` 原样写入底层 storage。AE 批量下单时，虚拟编程电路会以请求数量进入 `insertItem`，因此槽内显示相同数量。

修复应集中在该共享 handler，而不是各个输入仓室 mixin。

## 推荐代码形态

```java
@NotNull
@Override
public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate, boolean notifyChange) {
    if (GTItems.INTEGRATED_CIRCUIT.isIn(stack)) {
        if (!simulate) {
            ItemStack circuitStack = stack.copy();
            circuitStack.setCount(1);
            storage.setStackInSlot(slot, circuitStack);
        }
        return ItemStack.EMPTY;
    }
    return stack;
}
```

## 为什么返回 `ItemStack.EMPTY`

当前项目把样板中的编程电路作为“切换电路配置”的输入指令，而不是普通库存输入。保留返回空栈可以维持现有语义：AE 认为这份电路输入已经由机器接受，不会留下未插入残余。

如果改成返回 `stack.copyWithCount(stack.getCount() - 1)` 一类行为，AE 可能认为剩余编程电路未被接收，反而造成重试、卡单或回流风险。

## 为什么不能只依赖 `getSlotLimit`

`getSlotLimit` 限制的是常规插入/容量判断路径。当前代码直接调用 `storage.setStackInSlot(slot, stack)`，底层写入不会自动按 handler 的 slot limit 裁剪数量。因此必须在写入前手动 copy 并 `setCount(1)`。

## simulate 处理

`simulate == true` 时不能修改真实槽位。这是 Forge/LDLib item handler 的通用契约，也能避免 AE 或其他外部能力探测插入时提前改写仓室电路配置。

## 影响范围

| 路径 | 影响 |
| --- | --- |
| 普通输入总线/输入总成 | 电路槽数量固定为 1 |
| 流体输入仓/巨型流体输入仓 | 电路槽数量固定为 1 |
| 巨型输入总线/巨型输入总成 | 电路槽数量固定为 1 |
| 单方块机器的电路槽 | 电路槽数量固定为 1 |
| 配方匹配 | 仍按电路编号匹配，不消耗电路 |

## 已知限制

已存在存档中的异常堆叠不会自动被迁移。玩家再次通过 AE 样板写入电路后，该槽位会被新的 `count = 1` 电路覆盖。
