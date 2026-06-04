# fix catalyst capability blocking false positive

## Goal

修复巨型输入总成/仓室在 AE 阻挡模式下把共享催化剂槽内容误判为普通输入的问题。目标是让 AE 样板供应器只依据普通输入槽、普通流体槽和必要的电路状态判断是否可以继续发送材料，不再因为手动维护的催化剂槽中存在样板输入而卡住合成。

## What I already know

* 现场坐标位于虚空维度 `kubejs:void`，范围约为 `(-54,145,-50)` 到 `(-51,146,-50)`。
* 受影响方块为 `gtmthings:iv_huge_dual_hatch`。
* 最新远端 region 快照显示普通物品输入槽和普通流体输入槽为空，AE provider 的 `sendList` / `returnInv` 也为空。
* `(-51,146,-50)` 的 `shareTank` 中存在 `gtceu:iron_iii_chloride` 1000。
* 相邻 ExtendedAE provider `(-51,146,-49)` 开启 `blocking=YES` 和 `smart=1`，其中样板包含 `iron_iii_chloride + chlorobenzene -> iron_ii_chloride`。
* GTCEu `MetaMachine.getItemTransferCap/getFluidTransferCap` 会收集实现 `IItemTransfer/IFluidTransfer` 且 `hasCapability(side)` 为 true 的 trait。
* `MachineTrait` 默认 `capabilityValidator` 为 true。
* LowDrag `ItemTransferList` / `FluidTransferList` 的 `getStackInSlot` / `getFluidInTank` 会枚举所有子 handler，不按 `capabilityIO` 过滤。
* GTCEu `IOItemTransferList` / `IOFluidTransferList` 只在插入/抽出路径限制 IO，不限制枚举已有内容。
* 当前 GTLCore 已有 `CatalystItemStackHandlerMixin` 和 `CatalystFluidStackHandlerMixin`，但它们只优化配方模拟逻辑，没有隐藏外部 capability 暴露。

## Assumptions

* 催化剂槽应保持手动放入/取出语义，不应被 AE、管道或一键退回当作普通输入槽处理。
* 巨型输入总成的普通输入槽、普通流体槽、编程电路槽仍需要对 AE 外部能力保持可见。
* 编程电路阻挡模式过滤已有修复不应被本次改动影响。

## Requirements

* 共享物品催化剂槽不应通过机器外部 item capability 暴露给 AE 阻挡模式扫描。
* 共享流体催化剂槽不应通过机器外部 fluid capability 暴露给 AE 阻挡模式扫描。
* 催化剂槽仍必须参与 GT 配方内部模拟和匹配。
* 催化剂槽仍必须能在机器 GUI 中由玩家手动放入和取出。
* 普通输入槽、普通流体槽和编程电路槽的 AE 样板输入行为保持现状。
* 一键退回逻辑不应误伤催化剂槽，继续只退回普通输入内容。

## Proposed Implementation

1. 在 `CatalystItemStackHandlerMixin` 中为 `CatalystItemStackHandler` 构造完成后设置 `setCapabilityValidator(side -> false)`。
2. 在 `CatalystFluidStackHandlerMixin` 中为 `CatalystFluidStackHandler` 构造完成后设置 `setCapabilityValidator(side -> false)`。
3. 优先使用构造器尾部注入或当前 mixin 继承结构可安全表达的方式实现，避免改写 GTMThings 类的核心方法。
4. 保留现有 `handleRecipeInner` 覆写和缓存逻辑，不改变催化剂参与配方匹配的内部行为。
5. 检查 `gtlcore.mixin.json` 中现有 trait mixin 注册是否已覆盖这两个类；如果已注册，不新增无关配置。

## Acceptance Criteria

* [ ] 开启阻挡模式时，provider 相邻巨型输入仓室的共享催化剂槽中存在样板输入物，也不会阻止普通原料进入空的普通输入槽/普通流体槽。
* [ ] 编程电路通过 AE 样板进入巨型输入仓室时仍表现为状态切换，不作为普通物品堆叠。
* [ ] 普通输入槽和普通流体槽仍能接收 AE 样板发送的材料。
* [ ] 催化剂槽中的固体和流体仍可被 GT 配方识别为催化剂。
* [ ] 一键退回仍不会退回催化剂槽内容。
* [ ] `./gradlew spotlessCheck` 和 `./gradlew compileJava` 通过。
* [ ] 构建出的 jar 在整合包内验证化学反应 provider 不再卡死。

## Out of Scope

* 不修改 AE2 或 ExtendedAE 的阻挡模式语义。
* 不修改样板供应器的智能翻倍逻辑。
* 不调整普通 GT/GTCEu 输入总线的行为。
* 不处理巨型蒸汽输入仓。
* 不重构 GTMThings 机器结构或 GUI。

## Technical Notes

* 主要代码落点：
  * `src/main/java/org/gtlcore/gtlcore/mixin/gtmt/trait/CatalystItemStackHandlerMixin.java`
  * `src/main/java/org/gtlcore/gtlcore/mixin/gtmt/trait/CatalystFluidStackHandlerMixin.java`
* 相关已修复行为：
  * `NotifiableCircuitItemStackHandler` 用于 AE 样板编程电路状态切换。
  * `HugeBusPartMachineMixin` 已处理巨型输入仓普通物品输入和一键退回。
  * `PatternProviderLogicMixin` 已从 AE 阻挡模式输入集合中移除基础编程电路 key。
* 详细根因记录见 `research/capability-blocking-root-cause.md`。

## Definition of Done

* PRD 覆盖根因、修复点、验收标准和不做范围。
* 代码改动最小化，沿用已有 GTMThings trait mixin 位置。
* 本地质量检查通过。
* jar 构建完成并交给整合包实测。
