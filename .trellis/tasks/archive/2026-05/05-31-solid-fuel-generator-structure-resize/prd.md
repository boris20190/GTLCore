# Resize Solid Fuel Generator Structure

## Goal

将新增的 HV/EV/IV 固体燃料发电机多方块结构从当前 5x5x5 缩小到 3x3x4，使其占地与普通大型锅炉一致，降低搭建成本和空间压力，同时保留现有燃料消耗、EU 入账和同阶段锅炉对标逻辑。

## What I Already Know

- 用户明确要求当前 5x5x5 太大，需要进一步调整到 3x3x4。
- 目标尺寸应与普通大型锅炉一致。
- 当前固体燃料发电机结构定义位于 `GeneratorMachine.registerSolidFuelGenerator(...)`。
- 当前固体燃料发电机 runtime 逻辑位于 `SolidFuelGeneratorMachine`，本任务不需要改动燃料燃烧、发电功率或无线电网入账逻辑。
- GTCEu 普通大型锅炉的 pattern 是 3 个 aisle，每个 aisle 4 行、每行 3 字符，即 3x3x4。
- 现有固体燃料发电机需要至少支持输入总线、维护仓和消声仓。

## Requirements

- 将 HV/EV/IV 固体燃料发电机共用的 multiblock pattern 改为 3x3x4。
- 结构尺寸必须通过 `FactoryBlockPattern` 的 aisle 定义体现为 3 个 aisle、每个 aisle 4 行、每行 3 字符。
- 结构排布为普通大型锅炉尺寸，但按固体燃料发电机视觉和仓口需求调整：
  - 最下层使用对应阶段火箱。
  - 只有火箱位置可以被输入总线、维护仓、消声仓等能力方块替换。
  - 上层外壳使用对应阶段涡轮外壳，且不可被能力方块替换。
  - 大型锅炉内部管道位置改用对应阶段齿轮箱。
  - 控制器位置与大型锅炉一致。
- 保留三档机器现有方块材料关系：
  - HV 对标大型钢锅炉相关材料。
  - EV 对标大型钛锅炉相关材料。
  - IV 对标大型钨钢锅炉相关材料。
- 保留输入总线、维护仓、消声仓的结构能力支持，并精确允许 1 个输入总线、1 个维护仓、1 个消声仓。
- 替换火箱的能力方块在结构成型后应显示为对应阶段火箱外观，而不是涡轮外壳外观。
- UI 信息应精简，不显示无线能源总量和上一刻入账。
- 不修改机器配方、燃料识别、燃烧效率、发电倍率或无线电网入账逻辑。
- 固体燃料发电机燃烧时，鼠标对准核心方块应显示类似大型锅炉的 Jade/探针燃烧进度条。

## Acceptance Criteria

- [x] `registerSolidFuelGenerator(...)` 的 pattern 不再包含 5 字符宽度或 5 个 aisle 的 5x5x5 结构。
- [x] 新 pattern 是 3x3x4。
- [x] HV/EV/IV 三档固体燃料发电机继续共享同一个注册 helper。
- [x] 燃烧时 `RecipeLogic` 进度数据能反映当前燃料包的已发电/总发电进度，用于 Jade/探针进度条。
- [x] 火箱替换能力仓口成型后渲染为火箱外观。
- [x] UI 不显示无线能源总量和上一刻入账。
- [x] `./gradlew build -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk` 通过。

## Definition of Done

- [x] 代码改动范围聚焦在结构定义。
- [x] 质量检查通过。
- [x] 补充 Trellis spec 中关于该结构约定的说明。
- [ ] 提交本地 Git。

## Technical Approach

采用与普通大型锅炉一致的 3x3x4 尺寸。最下层谓词承载火箱以及输入总线、维护仓、消声仓的替换能力；涡轮外壳谓词只允许对应阶段涡轮外壳；大型锅炉管道位改为对应阶段齿轮箱。为避免替换仓口显示为涡轮外壳，注册时同时设置大型锅炉式 `partAppearance` 和 `LargeBoilerRenderer`，让火箱层能力部件在成形后按对应阶段火箱贴图烘焙。

## Open Questions

- None.

## Out of Scope

- 不重算发电效率。
- 不调整燃料逻辑。
- 不调整机器配方材料成本，除非结构变小导致必须同步配方。
- 不修改已有存档中已经成型的旧 5x5x5 结构兼容逻辑。

## Technical Notes

- Current file: `src/main/java/org/gtlcore/gtlcore/common/data/machines/GeneratorMachine.java`
- Existing method: `registerSolidFuelGenerator(...)`
- Existing pattern size: 5 aisles x 5 rows x 5 columns.
- GTCEu large boiler pattern source was inspected from `libs/gtceu-1.20.1-1.4.4.jar` via `javap`; its `registerLargeBoiler` pattern has 3 aisles, 4 rows, 3 columns.
- User refined approach B again: fireboxes move back to the bottom layer. Only firebox positions may be replaced by the input bus, maintenance hatch, and muffler hatch; turbine casing positions are fixed. Replacement hatches in the bottom layer must render as fireboxes after the structure forms.
