# brainstorm: solid fuel generator boiler parity

## Goal

研究大型锅炉的固体燃料消耗逻辑，并据此修正“固体燃料发电机”的平衡模型，使其不再按电压阶段臆造能量释放效率，而是严格对齐同级大型锅炉加基础蒸汽轮机组合的燃料消耗速率和能量转换效率。

## What I Already Know

* 当前“固体燃料发电机”已作为 HV/EV/IV 多方块实装，使用固体蒸汽锅炉 item-fuel 语义，直接向无线电网入账。
* 当前实现使用 `targetEUt = configuredBoilerMaxTemperature / 2`，HV 默认为 `1800 / 2 = 900 EU/t`。
* 用户实测大型钢锅炉 UI 显示最大温度为 `2074K`，而 HV 固体燃料发电机显示等效锅炉最大温度 `1800K`。研究确认大型锅炉 UI 原本会把内部温度加 `274.15` 显示为 K；后续已按严格物理换算修正为 `273.15`，所以内部 `1800` 应显示为 `2073K`。
* 用户进一步观察到固体燃料发电机燃烧速率显著低于同级大型锅炉，意味着能量释放比同级大型锅炉更慢。
* GTLCore 已有 `LargeBoilerMachineMixin` 修改大型锅炉 recipe duration：`recipe.duration * 6400 / largeBoilerMachine.maxTemperature`。
* 研究已确认当前实现的主偏差是使用 `STEAM_BOILER_RECIPES` 原始时长计算总 EU，漏掉了 GTCEu 复制到 `LARGE_BOILER_RECIPES` 时的 `duration / 12 / 80` 缩放，约等于 `960x`。

## Requirements (Evolving)

* 先研究大型锅炉固体燃料消耗逻辑，不直接改实现。
* 后续改进必须让“固体燃料发电机”的燃烧效率与同级大型锅炉对齐。
* 后续改进必须放弃使用电压阶段来思考和设置能量释放效率。
* 后续改进必须把“固体燃料发电机”的能量转换效率对齐到同级大型锅炉加基础蒸汽轮机组合。
* 青铜大型锅炉作为基准，后续钢、钛、钨钢按最高温度倍率推导燃料消耗速率和 EU/t，而不是按电压等级限制发电能力。
* 已确认采用“青铜基准连续倍率模型”：总 EU 按燃料热值固定，EU/t 按对应大型锅炉最高温度倍率放大，最后一个 tick 入账剩余 EU 来避免热值损失。

## Acceptance Criteria (Evolving)

* [x] 明确大型锅炉的固体燃料 recipe 来源、燃料温度/最大温度来源和 duration 修饰链路。
* [x] 明确大型锅炉实际蒸汽产出速率公式，以及它如何受 `maxTemperature`、节流和配置影响。
* [x] 明确基础蒸汽轮机把蒸汽转换成 EU 的运行效率和并行/安培影响。
* [x] 给出固体燃料发电机应采用的 total EU 和 EU/t 计算规则。
* [x] 解释当前实现为什么释放速率偏低。
* [x] 以青铜大型锅炉为基准，推导钢、钛、钨钢的大型锅炉消耗速率和 EU/t 倍率。
* [x] 形成后续实现方案和测试办法。

## Definition of Done

* 研究结论写入 `research/`。
* PRD 更新为可执行需求。
* 固体燃料发电机实现对齐青铜基准连续倍率模型。
* 运行 `spotlessCheck` 和 `compileJava`。

## Out of Scope

* 本研究阶段不直接修改 Java 实现。
* 不重新设计多方块外形或配方成本。
* 不把固体燃料发电机重新塞回发电阵列。

## Technical Notes

* 当前固体燃料发电机实现：`src/main/java/org/gtlcore/gtlcore/common/machine/multiblock/generator/SolidFuelGeneratorMachine.java`
* 大型锅炉 duration mixin：`src/main/java/org/gtlcore/gtlcore/mixin/gtm/machine/LargeBoilerMachineMixin.java`
* 研究文档：`.trellis/tasks/05-31-solid-fuel-generator-boiler-parity/research/large-boiler-solid-fuel-logic.md`
* 规格更新：`.trellis/spec/backend/database-guidelines.md`
* 前序任务归档：`.trellis/tasks/archive/2026-05/05-31-generator-array-solid-fuel-support/`
