# Solid Fuel Generator Efficiency Multiplier

## Goal

将固体燃料发电机的实际发电出力提升到当前 4 倍，并让燃料消耗速率同步提升到当前 4 倍，用于提高该多方块作为锅炉-蒸汽轮机黑盒系统的瞬时能量释放能力。

## What I Already Know

* 用户希望修改固体燃料发电机，而不是发电阵列或大型锅炉本体。
* 用户明确提出“发电效率为当前的 4 倍，同时燃料消耗速率也是当前的 4 倍”。
* 当前固体燃料发电机只向无线电网入账，不通过传统导线输出。
* 当前 HV/EV/IV 固体燃料发电机的出力读取对应大型锅炉的 `maxTemperature` 配置。
* 当前实现中 `getTargetEUt()` 返回 `getBoilerMaxTemperature() / 2`。
* 当前实现中单个燃料的总 EU 为 `largeBoilerDuration * 3200`，与 `targetEUt` 分开计算。
* 因为运行 tick 数由 `operationTotalEU / targetEUt` 推导，如果只把 `targetEUt` 乘以 4，那么每个燃料的总 EU 不变，运行时长自然缩短到约 1/4。

## Assumptions

* “发电效率 4 倍”在本任务中更准确地理解为“发电速率 / EU/t 变为当前 4 倍”，而不是“每个燃料总 EU 变为当前 4 倍”。用户已确认。
* “燃料消耗速率 4 倍”意味着单个燃料燃烧时间约为当前 1/4。用户已确认。
* 该改动不改变可用燃料集合，不改变大型锅炉燃料配方，不改变锅炉配置文件语义。
* 4 倍倍率作为固体燃料发电机代码中的固定平衡规则，不新增用户配置项。用户已选择 A。

## Open Questions

* 暂无。

## Requirements

* 固体燃料发电机的无线入账 EU/t 应为当前值的 4 倍。
* 燃料消耗速率应同步提升为当前值的 4 倍。
* 单个燃料对应的总 EU 保持不变。
* 4 倍倍率应通过固体燃料发电机代码中的命名常量表达，不新增配置项。
* UI 显示的“产能功率”应反映新的 4 倍 EU/t。
* 进度显示应继续反映当前燃料操作的剩余进度。

## Acceptance Criteria

* [x] HV 固体燃料发电机在 `steelBoilerMaxTemperature = 12800` 时显示并入账 `25600 EU/t`。
* [x] 同一燃料在新版本中的运行时长约为旧版本的 1/4。
* [x] 同一燃料在一次完整燃烧中的总入账 EU 与旧版本保持一致。
* [x] 不新增或修改公开配置文件。
* [x] 旧存档中已放置的固体燃料发电机不会因字段变更导致崩溃或丢失结构。
* [x] `spotlessCheck` 和 `compileJava` 通过。
* [x] `build` 通过并产出可测试 jar。

## Definition of Done

* 代码变更最小化，优先修改 `SolidFuelGeneratorMachine` 的倍率常量或目标出力计算。
* 如行为语义改变，应同步更新相关 tooltip 或 Trellis spec。
* 构建可用于整合包测试的 jar。

## Out of Scope

* 不调整大型锅炉本体逻辑。
* 不调整发电阵列逻辑。
* 不新增燃料类型。
* 不改变多方块结构。
* 不改变无线电网系统。

## Technical Notes

* 主要目标文件：`src/main/java/org/gtlcore/gtlcore/common/machine/multiblock/generator/SolidFuelGeneratorMachine.java`
* 当前出力逻辑：`getTargetEUt()` 使用 `getBoilerMaxTemperature() * EU_PER_STEAM_MB / STEAM_MB_PER_EU`。
* 当前单燃料总能量逻辑：`remainingEU = largeBoilerDuration * EU_PER_LARGE_BOILER_BASE_TICK`。
* 当前进度逻辑：`getOperationTicks(operationTotalEU, targetEUt)`，因此提高 `targetEUt` 会自然压缩燃烧时长。

## Implementation Plan

* 在 `SolidFuelGeneratorMachine` 中新增命名常量 `ENERGY_RELEASE_MULTIPLIER = 4`。
* 只修改 `getTargetEUt()`，让目标入账速率变为 `getBoilerMaxTemperature() / 2 * ENERGY_RELEASE_MULTIPLIER`。
* 不修改 `remainingEU = largeBoilerDuration * EU_PER_LARGE_BOILER_BASE_TICK`，从而保持单个燃料总 EU 不变。
* 不修改 `ENERGY_MODEL_VERSION`，因为已持久化的 `remainingEU` 仍然表示剩余总 EU；升级后未燃尽燃料只会按新速率继续释放。
* 更新 `zh_cn.json` 与 `en_us.json` 中固体燃料发电机 tooltip，明确“4 倍速率释放、总 EU 不变”。
* 在 Phase 3 spec update 中同步修正 `.trellis/spec/backend/database-guidelines.md` 里固体燃料发电机的出力公式。
