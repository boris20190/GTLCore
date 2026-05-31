# Update Solid Fuel Generator Display Text

## Goal

精简 HV/EV/IV 固体燃料发电机成形后的机器信息显示，让面板只保留玩家当前需要判断运行状态和剩余能量的信息，并调整两条中文/英文翻译的命名。

## What I Already Know

- 用户要求删除面板中的“运行正常”和“所有者”两行。
- 用户要求把“剩余可入账电量”改为“剩余能量”。
- 用户要求把“等效锅炉最大温度”改为“运行温度”。
- 当前显示逻辑在 `SolidFuelGeneratorMachine.addDisplayText(...)`。
- 当前翻译 key 在 `src/main/resources/assets/gtceu/lang/zh_cn.json` 和 `en_us.json`。

## Requirements

- 固体燃料发电机成形后不要显示 `gtceu.multiblock.running` 对应的“运行正常”行。
- 固体燃料发电机成形后不要显示无线电网所有者行。
- 继续保留进度、暂停、空闲、无线入账失败、运行温度、产能功率、剩余能量等信息。
- 将 `gtceu.machine.solid_fuel_generator.max_temperature` 的中文翻译改为“运行温度：%s K”。
- 将 `gtceu.machine.solid_fuel_generator.remaining_eu` 的中文翻译改为“剩余能量：%s EU”。
- 同步维护英文翻译，避免语言文件语义分叉。
- 不修改燃料逻辑、无线电网入账逻辑、结构定义或 renderer。

## Acceptance Criteria

- [x] 成形且燃烧时不再显示“运行正常”。
- [x] 成形后不再显示“所有者：...”。
- [x] 中文显示为“运行温度：...”。
- [x] 中文显示为“剩余能量：...”。
- [x] `./gradlew spotlessCheck compileJava -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk` 通过。

## Definition of Done

- [x] 代码改动范围聚焦在显示文本。
- [x] 质量检查通过。
- [x] 无需更新 Trellis spec，除非实现中发现新的可复用约定。

## Out of Scope

- 不重新设计机器 UI。
- 不调整燃料消耗、发电功率、无线电网入账或进度计算。
- 不重新构建发布 jar，除非用户另行要求。

## Technical Notes

- `SolidFuelGeneratorMachine.addDisplayText(...)` 当前在 `remainingEU > 0` 时添加 `gtceu.multiblock.running` 和进度；应只删除 running 行，保留进度。
- 所有者行来自 `gtmthings.machine.wireless_energy_monitor.tooltip.0`；删除展示即可，不影响 `userid` 仍作为无线入账目标。
