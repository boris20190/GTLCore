# solid fuel generator multiblock lifecycle fixes

## Goal

修正固体燃料发电机在暂停、结构失效、结构恢复、控制器被破坏时的生命周期行为，使它更贴近当前项目和 GTCEu 多方块机器的常规实现方式。

## What I Already Know

* 用户实测：GUI 暂停按钮点击后会立刻恢复工作，仍然继续无线发电；控制器外观短暂进入暂停态后又回到工作态。
* 用户实测：破坏非控制器方块后再恢复结构，发电进度会继续。用户希望保留这个想法：只要控制器没有被破坏，多方块工作进度应该被保留并可稳定恢复。
* 用户实测：破坏控制器后，结构完整性已经失效，但燃烧室仍显示燃烧；破坏非控制器方块时燃烧室能正常熄灭。
* 当前 `SolidFuelGeneratorMachine` 使用自定义 `ConditionalSubscriptionHandler` 执行无线入账，而不是标准 GTCEu 配方输出。
* 当前 `SolidFuelGeneratorMachine` 没有覆盖 `onStructureInvalid()`，因此结构失效时的状态清理由父类和 GTLCore mixin 间接处理。
* `remainingEU`、`operationTotalEU`、`targetEUt`、`lastEUt` 当前是持久化字段；结构失效后恢复继续工作很可能来自这些字段没有被清空。
* 暂停按钮会调用 GTCEu `IControllable.setWorkingEnabled(false)`，其底层通过 `RecipeLogic.Status.SUSPEND` 表达暂停。
* 当前自定义发电 tick 在 `!isWorkingEnabled()` 分支里又调用了 `recipeLogic.setStatus(IDLE)`，这会让 `RecipeLogic.isWorkingEnabled()` 下一 tick 重新变为 true，是暂停失效的直接原因。
* `WorkableMultiblockMachine.onStructureInvalid()` 会 `updateActiveBlocks(false)` 并 `resetRecipeLogic()`；这是燃烧室熄灭的常规路径。
* `LargeBoilerRenderer` 的燃烧光效只读 `recipeLogic.isWorking()`，实际燃烧室方块熄灭依赖 `ActiveBlock` 状态被 `updateActiveBlocks(false)` 清理。
* `MultiblockStateMixin` 控制器位置变更分支当前用 `lastController.self().getBlockState().getBlock()` 判断控制器是否被破坏；控制器已经变成空气后，这个判断可能拿到空气方块，导致控制器破坏时跳过 `onStructureInvalid()`。

## Assumptions

* 控制器仍存在但结构不完整时，应停止发电和燃烧外观，但保留当前燃料周期的剩余能量与进度。
* 控制器被破坏时，控制器方块实体消失，当前燃料周期不要求保留。
* 修复应优先复用项目现有多方块生命周期、主动方块、part appearance、recipe logic 状态管理方式，而不是为该机器写一套孤立机制。

## Decisions

* 控制器被破坏时视为硬中断：当前燃料周期丢失，不尝试通过掉落物 NBT 或重新放置控制器恢复进度；必须清理形成态、订阅和燃烧外观。
* 控制器破坏时未触发 `onStructureInvalid()` 的问题采用全局修复：修正 `MultiblockStateMixin` 的控制器方块变化判定，而不是只给固体燃料发电机写局部兜底。
* 暂停状态必须跨结构失效/重新成型保留：用户暂停后，即使挖掉并补回非控制器方块，也不能自动恢复发电。

## Open Questions

* 无。

## Requirements

* 暂停按钮必须让机器立即停止无线入账，并保持暂停直到用户再次恢复。
* 暂停不应丢失当前燃料周期进度。
* 暂停状态应作为固体燃料发电机自己的持久化用户意图保存，不依赖会被结构失效重置的 `RecipeLogic.Status`。
* 结构失效但控制器仍存在时，不应继续发电，不应继续显示燃烧态。
* 结构恢复后，如果控制器仍存在，应恢复此前燃料周期进度。
* 如果结构失效前处于暂停状态，结构恢复后仍保持暂停，不自动入账 EU。
* 控制器被破坏后，当前燃料周期丢失，不应留下燃烧室继续燃烧的假外观。

## Acceptance Criteria

* [ ] 点击暂停后，下一 tick 不再入账 EU，GUI/Jade 不再显示工作态。
* [ ] 再次恢复后，继续消耗此前燃料周期的剩余能量。
* [ ] 挖掉非控制器方块后，机器停止发电，燃烧室熄灭；补回方块后继续此前进度。
* [ ] 如果暂停后破坏并恢复非控制器方块，机器仍保持暂停，直到用户手动恢复。
* [ ] 挖掉控制器后，燃烧室不再保持燃烧外观。
* [ ] 行为与项目内已有多方块生命周期处理方式一致，避免特殊状态泄漏。

## Definition of Done

* 最小有效验证通过。
* 关键生命周期行为有代码层面的清晰边界。
* 如发现可复用规范，更新 Trellis spec 或任务技术记录。

## Out of Scope

* 不重新调整固体燃料发电机配方、结构尺寸、发电倍率或材料门槛。
* 不修改大型锅炉本身的运行逻辑。

## Technical Notes

* 重点文件：`src/main/java/org/gtlcore/gtlcore/common/machine/multiblock/generator/SolidFuelGeneratorMachine.java`
* 结构注册：`src/main/java/org/gtlcore/gtlcore/common/data/machines/GeneratorMachine.java`
* 项目公共无能源多方块基类：`src/main/java/org/gtlcore/gtlcore/api/machine/multiblock/NoEnergyMultiblockMachine.java`
* GTLCore 多方块状态 mixin：`src/main/java/org/gtlcore/gtlcore/mixin/gtm/api/machine/MultiblockStateMixin.java`
* GTLCore workable 多方块 mixin：`src/main/java/org/gtlcore/gtlcore/mixin/gtm/api/machine/WorkableMultiblockMachineMixin.java`
* GTCEu `WorkableMultiblockMachine.onStructureInvalid()` 顺序：父控制器失效、`updateActiveBlocks(false)`、清空能力代理/订阅、`recipeLogic.resetRecipeLogic()`。
* 大型锅炉注册也使用 `partAppearance` 将控制器下一层渲染为 firebox 外观；固体燃料发电机目前沿用了同类逻辑。

## Implementation Plan

### Design Boundaries

* 全局结构生命周期问题放在 `MultiblockStateMixin` 修复，不把“控制器被破坏时要触发失效清理”做成固体燃料发电机私有兜底。
* 固体燃料发电机只维护自己特有的无线入账、燃料周期和暂停意图；进度、工作态、燃烧渲染仍复用 GTCEu `RecipeLogic` 和 `WorkableMultiblockMachine.updateActiveBlocks(...)`。
* 不新增泛用 helper 或新基类。当前只有固体燃料发电机需要“无真实 GT recipe 但手动同步 RecipeLogic 进度”的特殊逻辑；过早抽象会扩大 API 面。
* 不修改大型锅炉、发电倍率、燃料匹配、结构 pattern 或 renderer。

### Global Multiblock Lifecycle Fix

File: `src/main/java/org/gtlcore/gtlcore/mixin/gtm/api/machine/MultiblockStateMixin.java`

* 在 `pos.equals(controllerPos)` 分支中，不再用 `lastController.self().getBlockState().getBlock()` 判断控制器是否变化。
* 改为以 `lastController.self().getDefinition().getBlock()` 作为期望控制器方块，与传入的新 `state` 比较。
* 当新 `state` 不再是该控制器定义方块时，调用 `lastController.onStructureInvalid()` 并移除 `MultiblockWorldSavedData` mapping。
* 保持现有行为：控制器已被破坏后不加入 async recheck，因为没有存活控制器可以自动重组。
* 目标效果：控制器被挖掉时也走 GTCEu 标准 `onStructureInvalid()`，从而清理 active firebox、part/controller 关系和 recipe logic 状态。

### Solid Fuel Generator Work Toggle

File: `src/main/java/org/gtlcore/gtlcore/common/machine/multiblock/generator/SolidFuelGeneratorMachine.java`

* 新增持久化字段：
  * `@Persisted @DescSynced private boolean workingAllowed = true;`
  * 含义：用户层面的开关意图，跨结构失效和存档保存保留。
* 覆盖 `isWorkingEnabled()` 返回 `workingAllowed`，让 GUI 按钮、显示文本和外部探针读取稳定的用户开关状态。
* 覆盖 `setWorkingEnabled(boolean)`：
  * 更新 `workingAllowed`。
  * 调用父类/GTCEu 的暂停通知路径，使 part `onPaused(...)`、`RecipeLogic.Status.SUSPEND/IDLE` 和 overlay 行为保持项目一致。
  * 关闭时不清空 `remainingEU`、`operationTotalEU` 或进度；只停止入账、关掉工作态和燃烧态。
  * 打开时，如果结构已成型并存在剩余能量，恢复同步进度并重新订阅发电 tick。

### Manual Tick Subscription

File: `SolidFuelGeneratorMachine.java`

* 将 `generationSubs` 的运行条件从只看 `isFormed()` 调整为 `isFormed() && workingAllowed`，避免暂停后仍长期 tick。
* 在以下入口更新订阅：
  * `onStructureFormed()`：结构成型后初始化/更新订阅。
  * `onStructureInvalid()`：结构失效时取消订阅。
  * `setWorkingEnabled(true/false)`：用户开关变化后立即订阅或取消订阅。
* `generationServerTick()` 保留防御性检查：remote、未成型、未允许工作都直接返回，不依赖订阅条件作为唯一保护。

### RecipeLogic Synchronization

File: `SolidFuelGeneratorMachine.java`

* 保留并整理现有 `updateRecipeLogicProgress()` / `resetRecipeLogicProgress()`，新增小型私有方法表达状态转换：
  * `pauseGenerationDisplay()`：保留 progress/duration/isActive，设置 `RecipeLogic.Status.SUSPEND`，停止 active firebox。
  * `resumeGenerationDisplay()` 或等价同步方法：有剩余能量时同步 progress/duration/isActive，并设置 `WORKING`；没有剩余能量时置 `IDLE`。
  * `stopGenerationDisplay()`：结构失效、无线失败或无燃料时清理工作显示，但不一定清空燃料周期。
* 暂停分支不再调用 `recipeLogic.setStatus(IDLE)`，这是当前按钮失效的根因。
* 结构失效但控制器存在时，允许 `super.onStructureInvalid()` 重置 `RecipeLogic` 显示态，但保留 `remainingEU` 和 `operationTotalEU`；结构恢复时再按剩余能量重新同步进度。
* 控制器被破坏时清空 `remainingEU`、`operationTotalEU`、`targetEUt`、`lastEUt`，符合“硬中断”决策。

### Structure Invalid Handling

File: `SolidFuelGeneratorMachine.java`

* 覆盖 `onStructureInvalid()`：
  * 先判断控制器方块是否仍存在：`getLevel().getBlockState(getPos()).is(getDefinition().getBlock())`。
  * 调用 `super.onStructureInvalid()`，复用 GTCEu/GTLCore 的 active block、part、recipe logic 和能力缓存清理。
  * 调用 `generationSubs.unsubscribe()`，保证结构失效期间不会继续无线入账。
  * 设置 `lastEUt = 0`。
  * 如果控制器不存在，清空当前燃料周期；如果控制器仍存在，只清理运行显示，不清空剩余能量。
* 这个方法不做 part 外观特殊处理；外观熄灭应由标准 active block 清理完成。

### Verification Plan

* 静态验证：
  * `./gradlew spotlessCheck`
  * `./gradlew compileJava`
* 手动游戏验证：
  * 正常成型并燃烧，GUI 进度继续更新，燃烧室显示燃烧。
  * 点击暂停后不再无线入账，燃烧室熄灭；再次恢复后从原进度继续。
  * 暂停后破坏并恢复非控制器方块，机器仍保持暂停。
  * 运行中破坏非控制器方块，停止入账并熄灭；补回后继续此前剩余能量。
  * 运行中破坏控制器，燃烧室不残留燃烧外观；重新放置控制器后不继承旧燃料周期。

## Implementation Notes

* `SolidFuelGeneratorMachine` 新增持久化 `workingAllowed`，暂停/恢复按钮读取该字段，不再依赖会被结构失效重置的 `RecipeLogic.Status`。
* 自定义无线入账订阅条件改为 `isFormed() && workingAllowed`；结构失效和暂停会取消订阅，恢复结构或恢复工作后重新订阅。
* 暂停时同步 `RecipeLogic.Status.SUSPEND`，保留当前 `remainingEU/operationTotalEU` 和进度；不再把暂停状态写成 `IDLE`。
* 结构失效时复用父类 `onStructureInvalid()` 清理 active block 和能力代理。控制器仍存在则只停止显示/订阅并保留燃料周期；控制器被破坏则清空当前燃料周期。
* `MultiblockStateMixin` 控制器位置变化判定改为对比控制器定义方块，避免控制器已变成空气时误判为未变化，从而跳过标准失效清理。
* 新增规格记录：`.trellis/spec/backend/database-guidelines.md` 的 “Manual RecipeLogic lifecycle for custom generators”。

## Verification Result

* 通过：`./gradlew spotlessCheck compileJava -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk`
* 未执行：游戏内手动验证。仍需在整合包内确认暂停按钮、非控制器破坏/恢复、控制器破坏三组行为。
