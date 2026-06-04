# capability blocking root cause

## 现场快照

远端服务器路径：

* `/home2/wangxuanhao/gt-jdk-server`
* 维度目录：`world/dimensions/kubejs/void`
* 相关 region：`region/r.-1.-1.mca`

最新保存快照中，目标范围内的普通输入均为空：

* `(-51,146,-50) gtmthings:iv_huge_dual_hatch`
  * `inventory=[]`
  * `tank=[]`
  * `circuitInventory=[]`
  * `shareInventory=[zinc_dust, osmium_tetroxide_dust, silver_dust, quicklime_dust, platinum_dust, iron_dust]`
  * `shareTank=[iron_iii_chloride 1000]`
* `(-52,146,-50) gtmthings:iv_huge_dual_hatch`
  * `inventory=[]`
  * `tank=[]`
  * `circuitInventory=[programmed_circuit Configuration:1 Count:1]`
  * `shareInventory=[]`
  * `shareTank=[]`
* 其他相邻目标输入仓普通槽也为空。

相邻 provider：

* `(-51,146,-49) ae2:cable_bus` north part `expatternprovider:ex_pattern_provider_part`
* 名称：`化学反应 带电路 催化剂`
* `blocking=YES`
* `smart=1`
* `sendList=[]`
* `returnInv=[]`
* 样板包含 `iron_iii_chloride 2000 + chlorobenzene 1000 -> iron_ii_chloride 2000`

## AE2 行为

`PatternProviderLogic.pushPattern` 对外部目标的关键顺序：

1. 如果 `sendList` 非空、节点无效或样板不属于该 provider，直接拒绝。
2. 如果 provider 开启 blocking，调用目标的 `containsPatternInput(patternInputs)`。
3. 只有 blocking 检查通过后才执行 `adapterAcceptsAll` 的模拟插入。
4. 模拟插入通过后才真正发送输入并写入 `sendList`。

因此现场 `sendList=[]` 且普通输入为空时，卡死更可能发生在发送前的 blocking 检查或模拟插入检查。

## GTCEu/LowDrag capability 链路

`MetaMachine.getItemTransferCap/getFluidTransferCap`：

* 从机器 traits 中收集实现 `IItemTransfer/IFluidTransfer` 的对象。
* 只检查 `MachineTrait.hasCapability(side)`。
* 默认 `MachineTrait.capabilityValidator` 返回 true。
* 收集后包装为 `IOItemTransferList` / `IOFluidTransferList`。

LowDrag `ItemTransferList` / `FluidTransferList`：

* `getSlots/getTanks` 对所有子 transfer 求和。
* `getStackInSlot/getFluidInTank` 直接枚举子 transfer。
* 不检查子 handler 的 `capabilityIO`。

GTCEu `IOItemTransferList` / `IOFluidTransferList`：

* 对 `insertItem/fill` 和 `extractItem/drain` 按 IO 限制。
* 不覆写 `getStackInSlot/getFluidInTank`。

结果：`capabilityIO=IO.NONE` 的 catalyst handler 虽然不能被外部插入/抽出，但仍会被外部 capability 的枚举路径看见。

## 根因

GTMThings 的共享催化剂槽是机器内部配方 handler，但它同时实现了 item/fluid transfer 接口，并且默认 capability validator 允许任意侧访问。

AE 阻挡模式通过外部 capability 枚举目标内容时看到了共享催化剂槽中的内容。只要这些内容和该 provider 任意样板输入重合，`containsPatternInput` 就返回 true，provider 不再发送材料。

这会造成“普通输入仓室明明为空，但阻挡模式仍认为目标容器已有输入”的 false positive。

## 推荐修复

在 GTLCore 已有 GTMThings catalyst trait mixin 中隐藏 catalyst handler 的外部 capability：

* `CatalystItemStackHandlerMixin`
* `CatalystFluidStackHandlerMixin`

实现方向：

* 在目标对象构造完成后调用 `setCapabilityValidator(side -> false)`。
* 保留现有配方匹配和缓存逻辑。
* 不修改 AE2 阻挡模式，也不修改普通输入 handler。

该修复会让 AE、管道和类似外部 capability 消费者看不见催化剂槽，但机器内部配方逻辑仍直接持有这些 handler，因此催化剂仍能参与配方判断。

## 风险

* 如果某些玩家依赖管道或 AE 直接向催化剂槽自动输入催化剂，这种自动化会失效。但当前设计要求催化剂槽完全手动维护，因此这是预期行为。
* 如果 GTMThings 某处通过外部 capability 而不是机器内部 trait 读取 catalyst 内容，可能受影响。现有已知配方路径使用 handler 自身的 `handleRecipeInner`，不依赖外部 capability。
* 对普通输入槽、普通流体槽和电路槽不应产生影响，因为修改只落在 catalyst handler。
