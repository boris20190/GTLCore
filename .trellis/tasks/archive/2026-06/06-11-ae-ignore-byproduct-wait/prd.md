# brainstorm: AE忽略副产物等待

## Goal

实现“方案二”：ME样板总成保留样板中的副产物信息，但 AE 合成计划/执行默认只把主产物作为必须关注和等待的产物，避免 GT 概率副产物导致 AE 卡单，同时不丢失样板展示和副产物输出信息。

## What I already know

* 当前普通 ME样板总成默认 `keepByProduct = false`，界面表现为“禁用副产物: 是”。
* 当前实现会在样板装入总成后重构 `IPatternDetails`，默认只保留第一个输出。
* AE 执行阶段 `CraftingCpuLogicMixin.executeCrafting` 会把 `expectedOutputs` 写入 job 的 `waitingFor`。
* `AEUtils.extractForProcessingPattern` 当前会把处理样板的所有 `originDetail.getOutputs()` 加入 `expectedOutputs`。
* 项目已有快速合成计算 mixin：`CraftingTreeProcessMixin.onSucceed` 会把 `details.getOutputs()` 全部插入模拟库存。
* `CraftingTreeProcessMixin.getOutputCountTest` 和 `CraftingPlanSummaryMixin` 也直接遍历 `details.getOutputs()`。
* GTCEu 真实配方输出仍由 `RecipeRunner` 和 ME 输出 handler 处理，进入 ME样板总成输出缓冲后推回 ME 网络。
* 样板编码终端写入的是 encoded pattern 物品本身；ME样板总成当前只在装入后生成一个给 AE 合成服务使用的 `IPatternDetails`。
* `MEPatternBufferPartMachine.getTerminalPatternInventory()` 返回原始 `patternInventory`，不是 `getAvailablePatterns()` 中的裁剪后 details。

## Assumptions (temporary)

* 本任务只改变 ME样板总成/相关 AE provider 的行为，不全局改变普通 AE 样板供应器。
* 副产物应保留在样板详情和终端展示中，但不参与 AE 合成完成条件。
* 样板编码终端必须继续显示/写入所有输出，玩家仍可通过调整输出槽顺序把需要的产物设为主产物。

## Decisions

* AE 忽略副产物的范围采用完整语义：计划阶段和执行阶段都只关注主产物。副产物不作为可合成目标、不作为可满足后续需求的确定产物，也不进入执行等待；但样板物品/界面仍应保留副产物信息。
* 主产物严格定义为样板输出列表中的第一个非空输出。玩家通过编码终端调整输出槽顺序来选择哪个产物是 AE 关注的主产物。
* ME样板总成不再提供“让 AE 关注所有副产物”的行为开关。现有“禁用副产物”按钮应移除、改名或改为纯显示相关选项，避免误导。
* 副产物展示采用中等实现：编码终端完整显示/写入所有输出；ME样板总成槽位仍显示主产物，但悬浮提示追加完整实际输出信息。
* 实现路径采用“原始样板完整、AE 暴露 details 裁剪”：encoded pattern 物品保留所有输出，ME样板总成提供给 AE crafting service 的 `IPatternDetails` 只包含主产物输出。
* 旧存档中已有的 `keepByProduct = true` 不再保留旧行为。加载/刷新后统一按新语义处理：AE 只关注主产物；需要关注原副产物时，玩家应在编码终端把该产物调整到第一个输出槽。
* 现有“禁用副产物”按钮直接移除；说明信息放到机器 tooltip 和样板槽位 tooltip 中，避免按钮暗示存在可切换行为。
* 范围只覆盖 ME样板总成系列：普通/小型/扩展/终极/库存/代理/通配符总成。不覆盖 `IMECraftIOPart` / 分子操纵者端口。
* 槽位 tooltip 仅在样板存在多个输出时追加实际输出信息：先说明 AE 只追踪第一个输出，再列出完整实际输出；输出过多时截断并提示剩余数量。
* 通配符总成本次只保证 AE 只关注主产物；不额外实现每个展开样板的实际输出 tooltip。

## Open Questions

* 暂无阻塞问题。

## Requirements (evolving)

* ME样板总成可保留样板副产物信息。
* AE 对 ME样板总成下单时默认只等待主产物。
* GT 机器真实产生的副产物仍能进入 ME 网络。
* 不破坏当前样板电路提取逻辑。
* 不影响普通 AE 样板供应器的副产物行为，除非明确决定扩大范围。
* UI 文案必须明确：AE 只追踪主产物，实际副产物仍会输出回 ME 网络。
* 普通/库存/扩展/终极 ME样板总成槽位 tooltip 应展示完整实际输出，帮助玩家确认样板里的副产物仍保留。
* `getAvailablePatterns()` 暴露给 AE 的 details 应只包含主产物输出，以保证计划阶段和执行阶段语义一致。
* 复制/剪切工具可以兼容读取旧 `keepByProduct` 字段，但粘贴后不得恢复旧版“AE 等待所有副产物”的行为。
* 机器说明应提示：AE 只追踪第一个输出；实际副产物仍会输出回 ME 网络。

## Acceptance Criteria (evolving)

* [x] ME样板总成中含副产物的处理样板仍可在 UI/终端显示副产物信息。
* [x] 普通/库存/扩展/终极 ME样板总成槽位 tooltip 能展示实际输出列表，槽位图标仍代表主产物。
* [x] 样板编码终端中编写处理样板时仍能看到所有输出，并能通过输出槽顺序选择主产物。
* [x] 通过 ME样板总成执行该样板时，AE `waitingFor` 只加入主产物。
* [x] 合成计算阶段不会把 ME样板总成的副产物当作可满足后续需求的确定产物。
* [x] GT 配方实际输出的副产物仍通过机器输出逻辑处理。
* [x] `spotlessCheck compileJava` 通过。

## Definition of Done (team quality bar)

* Tests added/updated where practical, or documented why this behavior is hard to unit-test locally.
* Lint / typecheck / compile green.
* Docs/notes updated if behavior changes.
* Rollout/rollback considered if risky.

## Out of Scope (explicit)

* 不重写 AE2 整套合成算法。
* 不改变 GTCEu 真实配方输出。
* 不改变普通非 ME样板总成 provider 的默认行为。
* 不改变 `IMECraftIOPart` / 分子操纵者端口行为。
* 不为通配符总成新增展开样板 tooltip。
* 不处理与本行为无关的样板倍乘工具 UI。
* 不保留旧版“AE 等待所有副产物”的高级开关。
* 不提供隐藏 NBT/配置方式恢复旧版副产物等待行为。

## Technical Notes

* `MEPatternBufferPartMachine.keepByProduct` 当前默认 `false`，按钮会触发 `refreshAllByProduct()` 重新解码样板。
* `MEBufferPatternHelper.processPatternWithCircuit` 同时负责电路提取、副产物裁剪、样板重构。
* `CraftingCpuLogicMixin.executeCrafting` 可以知道 provider 是否是 `IMEPatternPartMachine` / `IMECraftIOPart`，这里适合过滤执行等待输出。
* 计划阶段的 `CraftingTreeProcessMixin` 只有 `IPatternDetails details`，没有直接 provider 上下文；如果要只针对 ME样板总成过滤，需要设计可识别的 pattern wrapper/marker，或保留当前样板重构方案。
* 编码终端相关 mixin 主要操作 `encodedOutputsInv` 和最终 `encodedPatternSlot`；本任务应避免改动编码终端输出过滤逻辑。
* 总成槽位当前已使用 `AEPatternViewExtendSlotWidget.setOnAddedTooltips` 显示配方缓存状态，可在同一路径追加完整实际输出提示。

## Proposed Implementation Plan

1. 调整 `MEBufferPatternHelper`：移除 `keepByProduct` 对 AE 暴露 outputs 的控制，统一生成“去电路 + 只保留第一个非空输出”的 `IPatternDetails`。
2. 保留原始 encoded pattern 物品不变，编码终端不做输出裁剪。
3. 从 `MEPatternBufferPartMachine` 去掉“禁用副产物”按钮；旧 `keepByProduct` 字段不再读取、不再写出，也不再影响 AE 暴露 details。
4. 为普通/库存 ME样板总成槽位 tooltip 增加多输出提示：AE 追踪第一个输出，实际输出列出主产物和副产物，超过阈值截断。
5. 通配符总成沿用同一“AE 暴露 details 裁剪为主产物”的语义，但本次不新增展开样板 tooltip。
6. 更新 zh_cn/en_us 文案：机器说明和 tooltip 明确“AE 只追踪第一个输出，实际副产物仍输出回网”。
7. 验证 `spotlessCheck compileJava`。

## Implementation Result

* `MEBufferPatternHelper` 现在始终为 ME样板总成生成只含第一个非空输出的 AE 视图，同时继续提取虚拟电路；原始 encoded pattern 物品不被重写。
* `MEPatternBufferPartMachine` 移除了副产物开关和 `keepByProduct` 持久字段，复制/剪切不再写出旧字段，粘贴时旧字段被忽略。
* 普通与库存 ME样板总成槽位在多输出样板上追加实际输出 tooltip，过长输出列表按阈值截断。
* 通配符总成的展开 pattern 在进入 `expandedPatterns` / `getAvailablePatterns()` 前转换为主产物视图。
* 机器 tooltip 与 slot tooltip 的中英文文案已更新。

## Verification

* `./gradlew spotlessCheck compileJava`：`spotlessCheck` 通过；默认 Java 17 路径缺少编译器导致首次 `compileJava` 环境失败，改用 `/usr/lib/jvm/java-21-openjdk` 后 `compileJava` 通过。
* `./gradlew -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk test`：通过，项目测试源为 `NO-SOURCE`。
