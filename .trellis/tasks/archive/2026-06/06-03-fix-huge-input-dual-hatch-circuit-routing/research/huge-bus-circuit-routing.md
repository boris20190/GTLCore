# 巨型输入总成编程电路路由调查

## Scope

调查当前整合包内 `gtmthings-1.3.5.b.jar`、`gtceu-1.20.1-1.4.4.jar` 与 GTLCore mixin 之间的实际调用关系，确认巨型输入总成为什么会接收 AE 样板发送的编程电路为普通物品，以及可复用的修复入口。

## Findings

### 普通输入总成路径

普通输入总成 `DualHatchPartMachine` 继承 GTCEu `ItemBusPartMachine`，因此会命中 GTLCore 的 `ItemBusPartMachineMixin`。

该 mixin 做了两件事：

* 普通物品库存拒收 `IntCircuitBehaviour.isIntegratedCircuit(stack)`。
* 输入侧电路槽替换成 `NotifiableCircuitItemStackHandler`。

`NotifiableCircuitItemStackHandler` 的构造为 `super(machine, 1, IO.IN, IO.IN)`，外部输入能力也是 `IO.IN`。插入 `GTItems.INTEGRATED_CIRCUIT` 时会直接写入槽位并返回 `ItemStack.EMPTY`。

### 巨型输入总成路径

本地 `gtmthings-1.3.5.b.jar` 字节码显示：

* `HugeDualHatchPartMachine extends HugeBusPartMachine`。
* `HugeBusPartMachine` 有 `inventory`、`circuitInventory`、`shareInventory`、`combinedInventory` 字段。
* `HugeBusPartMachine#createInventory(Object[] args)` 使用 `new NotifiableItemStackHandler(..., UnlimitedItemStackTransfer::new)` 创建普通库存。
* `HugeBusPartMachine#createCircuitItemHandler(Object[] args)` 使用普通 `NotifiableItemStackHandler`，输入侧为 `new NotifiableItemStackHandler(machine, 1, IO.IN, IO.NONE).setFilter(IntCircuitBehaviour::isIntegratedCircuit)`。
* `HugeBusPartMachine#createCombinedItemHandler(Object[] args)` 将普通库存、电路库存、催化剂库存组合给配方处理。

因此巨型输入总成有电路槽，但这个槽默认不作为外部 item capability 暴露；AE 样板供应器推送编程电路时，普通库存没有过滤，所以它会进入普通库存。

### 可用构造器

本地 `gtceu-1.20.1-1.4.4.jar` 字节码显示 `NotifiableItemStackHandler` 支持这些构造器：

* `(MetaMachine, int, IO, IO, Function)`
* `(MetaMachine, int, IO, IO)`
* `(MetaMachine, int, IO)`

本地 `gtmthings-1.3.5.b.jar` 字节码显示 `UnlimitedItemStackTransfer` 支持：

* `(int)`
* `(NonNullList)`
* `(ItemStack)`
* `setFilter(Function)`

因此 GTLCore mixin 可以在不修改 GTMThings 源码的情况下复用原巨型库存 storage factory：

```java
new NotifiableItemStackHandler(this, getInventorySize(), IO.IN, IO.IN, UnlimitedItemStackTransfer::new)
```

然后对 handler 调用 `setFilter(stack -> !IntCircuitBehaviour.isIntegratedCircuit(stack))`。

## Design Implication

推荐在 `HugeBusPartMachineMixin` 中复刻普通输入总成的两段行为：

* 输入侧普通库存拒收编程电路。
* 输入侧电路槽改用 `NotifiableCircuitItemStackHandler`。

这样可以保持当前项目对 GTMThings 兼容 patch 的集中位置，也避免创建新的机器类或改动注册链路。

## Open Compatibility Point

`HugeBusPartMachine` 是巨型输入总线和巨型输入总成的共同基类。对它打补丁会让两者都获得 AE 样板编程电路切换能力。若只想改巨型输入总成，需要额外 target `HugeDualHatchPartMachine` 并覆盖继承方法，方案更窄但维护复杂度更高。
