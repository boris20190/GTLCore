# 大型锅炉固体燃料逻辑研究

## 结论

当前“固体燃料发电机”的主要偏差在于：它用 `STEAM_BOILER_RECIPES` 的原始固体燃料时长计算总 EU，而同级大型锅炉实际使用的是从 `STEAM_BOILER_RECIPES` 派生到 `LARGE_BOILER_RECIPES` 后的燃料时长。

GTCEu 在 recipe type 注册阶段会把可用的蒸汽锅炉燃料复制到大型锅炉 recipe map，复制时长为：

```text
largeBoilerBaseDuration = steamBoilerDuration / 12 / 80
```

固体燃料的 `steamBoilerDuration` 又来自原版/Forge 燃烧时间：

```text
steamBoilerDuration = furnaceBurnTime * 12
```

因此固体燃料进入大型锅炉后的基础时长等价于：

```text
largeBoilerBaseDuration = floor(furnaceBurnTime / 80)
```

以煤炭为例，原版燃烧时间通常为 `1600`，蒸汽锅炉 recipe 时长为 `19200`，大型锅炉基础 recipe 时长则是 `20`。当前固体燃料发电机直接使用 `19200`，相当于漏掉了大型锅炉固体燃料桥接时的约 `960x` 缩放。

## 代码证据

GTCEu `FuelRecipes` 会把炉子燃料注册成 `STEAM_BOILER_RECIPES`，并把 duration 设置为燃烧时间乘 `12`。反编译路径：

```text
libs/gtceu-1.20.1-1.4.4.jar
com.gregtechceu.gtceu.data.recipe.misc.FuelRecipes
```

对应行为：

```text
FurnaceBlockEntity.getFuel()
GTRecipeTypes.STEAM_BOILER_RECIPES.recipeBuilder(...)
inputItems(fuelItem)
duration(furnaceBurnTime * 12)
```

GTCEu `GTRecipeTypes` 给 `STEAM_BOILER_RECIPES` 绑定了 `onRecipeBuild`，把满足条件的配方复制进 `LARGE_BOILER_RECIPES`：

```text
private static void lambda$static$0(GTRecipeBuilder builder, Consumer out)
largeDuration = builder.duration / 12 / 80
if (largeDuration > 0) {
    LARGE_BOILER_RECIPES.copyFrom(builder).duration(largeDuration).save(out)
}
```

GTLCore 又用 mixin 修改大型锅炉的实际 recipe duration：

```java
double duration = recipe.duration * 6400D / largeBoilerMachine.maxTemperature;
if (duration < 1) {
    recipe1 = recipe.copy(ContentModifier.multiplier(1 / duration), false);
    recipe1.duration = 1;
} else {
    recipe1.duration = (int) duration;
}
if (largeBoilerMachine.getThrottle() < 100) {
    recipe1.duration = recipe1.duration * 100 / largeBoilerMachine.getThrottle();
}
```

位置：`src/main/java/org/gtlcore/gtlcore/mixin/gtm/machine/LargeBoilerMachineMixin.java`

## 大型锅炉满载蒸汽产出

大型锅炉不是通过燃料 recipe 直接输出蒸汽。燃料 recipe 只决定机器处于 working 状态的时长；蒸汽由 `LargeBoilerMachine.updateCurrentTemperature()` 根据当前温度、节流、水和输出能力生成。

大型锅炉的 `maxTemperature` 来自注册机器时传入的配置值，而不是燃料本身。当前三档对应 `ConfigHolder.INSTANCE.machines.largeBoilers` 下的 `steelBoilerMaxTemperature`、`titaniumBoilerMaxTemperature`、`tungstensteelBoilerMaxTemperature`。`BoilerType` 枚举里也存在 `steamPerTick()` 和 `runtimeBoost(int)`，但已检查到的实际运行路径使用的是 `LargeBoilerMachine.maxTemperature`、`currentTemperature` 和 `steamPerWater` 公式，后续应以真实运行路径为准。

核心公式每 `5` tick 执行一次：

```text
waterRequest =
  currentTemperature * throttle * 5 * 1000
  / (steamPerWater * 100000)

steamGeneratedPer5Ticks = actualWaterConsumed * steamPerWater
displayedSteamPerTick = steamGeneratedPer5Ticks / 5
```

在 `throttle = 100`、水和输出能力充足、温度达到上限时，等价于：

```text
waterPer5Ticks = floor(maxTemperature * 5 / steamPerWater)
steamPerTick = waterPer5Ticks * steamPerWater / 5
```

当前运行配置 `run/config/gtceu.yaml` 中：

```yaml
largeBoilers:
  steamPerWater: 160
  steelBoilerMaxTemperature: 1800
  titaniumBoilerMaxTemperature: 3200
  tungstensteelBoilerMaxTemperature: 6400
```

所以默认满温、100% 节流、无限水和可输出蒸汽时：

```text
钢大型锅炉: floor(1800 * 5 / 160) * 160 / 5 = 1792 mB/t
钛大型锅炉: floor(3200 * 5 / 160) * 160 / 5 = 3200 mB/t
钨钢大型锅炉: floor(6400 * 5 / 160) * 160 / 5 = 6400 mB/t
```

大型钢锅炉 UI 原本显示 `2074K` 并不是另一个配置温度。GTCEu 原始显示代码把内部温度加上 `274.15`，所以 `1800 + 274.15` 显示为 `2074K`。这属于显示换算笔误；项目侧已通过 mixin 把大型锅炉 UI 修正为严格物理值 `273.15`，所以内部 `1800` 应显示为 `2073K`。当前固体燃料发电机显示 `1800K` 是显示单位不一致，而不是它读取了不同的内部上限。

## 基础蒸汽轮机换算

GTCEu `STEAM_TURBINE_FUELS` recipe 为：

```text
input: 640 mB Steam
duration: 10 ticks
EUt: LV voltage = 32 EU/t output
```

因此基础蒸汽轮机的理想换算为：

```text
640 mB / 10t = 64 mB/t
32 EU/t / 64 mB/t = 0.5 EU/mB
```

GTLCore 的 `SimpleGeneratorMachineMixin` 会按机器 tier 并行，并且对 `STEAM_TURBINE_FUELS` 应用 tier 效率：

```java
recipe1.duration = recipe1.duration * GeneratorArrayMachine.getEfficiency(recipeType, tier) / 100;
```

其中 `getEfficiency(STEAM_TURBINE_FUELS, tier)` 是 `125 - 25 * tier`，所以 LV 为 `100%`、MV 为 `75%`、HV 为 `50%`。

本需求明确要求“基础蒸汽轮机 100% 运行效率”，因此固体燃料发电机不应使用 MV/HV/EV/IV 阶段效率；应使用基础蒸汽轮机 recipe 的物理换算，即 `0.5 EU/mB Steam`。

## 推荐的固体燃料发电机模型

后续实现应把每个固体燃料发电机视为：

```text
同级大型锅炉（满温、100% 节流、内部水蒸气闭环）
+ 足量基础蒸汽轮机（100% 换算，允许等效分数台）
+ 无线电网输出
```

不要再按电压 tier 自行设置能量释放效率。

## 青铜基准倍率模型

青铜大型锅炉是第一台大型锅炉，适合作为“燃料热值 -> 蒸汽 -> EU”的基准。以派生后的大型锅炉燃料 recipe 时长 `D` 为变量：

```text
D = matched LARGE_BOILER_RECIPES item recipe duration
```

对普通固体燃料，`D` 来自：

```text
D = floor(furnaceBurnTime / 80)
```

青铜锅炉内部最大温度为 `800`，当前 `steamPerWater = 160`。在满温、100% 节流、供水和输出无限的理想状态下：

```text
青铜燃烧时间 = D * 6400 / 800 = D * 8 ticks/个
青铜蒸汽产出 = 800 mB/t
基础蒸汽轮机换算 = 0.5 EU/mB
青铜 EU/t = 800 / 2 = 400 EU/t
单个燃料总 EU = D * 8 * 400 = D * 3200 EU
```

因此更高等级大型锅炉不应被理解为“更高总效率”，而应被理解为“同一热值更快释放”。以青铜为 `1x`，连续理想倍率为：

```text
temperatureRatio = boilerMaxTemperature / bronzeBoilerMaxTemperature
burnTicks = bronzeBurnTicks / temperatureRatio
steamPerTick = bronzeSteamPerTick * temperatureRatio
EUt = bronzeEUt * temperatureRatio
totalEU = D * 3200
```

当前默认配置下，以煤炭为例。煤炭通常 `furnaceBurnTime = 1600`，所以 `D = 20`，青铜基准为 `160 ticks/个`、`800 mB/t`、`400 EU/t`、`64,000 EU/个`。

| 大型锅炉 | 内部最高温度 | 相对青铜温度 | 理想燃烧时间 tick/个 | 理想蒸汽 mB/t | 等效基础蒸汽轮机 | 理想 EU/t | 单燃料总 EU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 青铜 | 800 | 1.00x | 160 | 800 | 12.5 | 400 | 64,000 |
| 钢 | 1800 | 2.25x | 71.111 | 1800 | 28.125 | 900 | 64,000 |
| 钛 | 3200 | 4.00x | 40 | 3200 | 50 | 1600 | 64,000 |
| 钨钢 | 6400 | 8.00x | 20 | 6400 | 100 | 3200 | 64,000 |

表中的“等效基础蒸汽轮机”允许分数台，用于表达理想黑盒转换。真实摆机器时只能放整数台，但固体燃料发电机本来就是内置锅炉和涡轮的黑盒，所以使用等效分数台更符合“热值完全转换”的目标。

如果严格复刻 `LargeBoilerMachine.updateCurrentTemperature()` 的整数取整，钢锅炉会得到 `1792 mB/t`、`896 EU/t`，并且煤炭燃烧时间被取整为 `71 ticks`，总 EU 为 `63,616`。这是真实运行路径的离散误差；如果目标是平衡上的“理想热值完全转换”，建议使用上表的连续倍率作为固体燃料发电机的目标模型。

推荐计算：

```text
baseDuration = matched LARGE_BOILER_RECIPES item recipe duration
bronzeBurnTicks = baseDuration * 6400 / bronzeBoilerMaxTemperature
bronzeSteamPerTick = bronzeBoilerMaxTemperature
bronzeEUt = bronzeSteamPerTick / 2
totalEU = baseDuration * 3200

temperatureRatio = boilerMaxTemperature / bronzeBoilerMaxTemperature
targetEUt = bronzeEUt * temperatureRatio
operationTicks = totalEU / targetEUt
```

如果实现层需要整数 tick，可以每 tick 按 `targetEUt` 入账，并在最后一个 tick 入账剩余 EU。这样总 EU 严格等于 `baseDuration * 3200`，不会因为钢锅炉的 `71.111` tick 被强行截断而丢热值。

对当前三档默认值，若选择严格复刻大型锅炉整数取整，煤炭示例为：

```text
baseDuration = 20

钢: effectiveBurnTicks = floor(20 * 6400 / 1800) = 71
    targetEUt = 1792 / 2 = 896
    totalEU = 71 * 896 = 63,616 EU

钛: effectiveBurnTicks = floor(20 * 6400 / 3200) = 40
    targetEUt = 3200 / 2 = 1600
    totalEU = 40 * 1600 = 64,000 EU

钨钢: effectiveBurnTicks = floor(20 * 6400 / 6400) = 20
      targetEUt = 6400 / 2 = 3200
      totalEU = 20 * 3200 = 64,000 EU
```

这说明真实代码离散值与连续理想值只有钢锅炉存在小偏差。已确认固体燃料发电机采用“连续理想倍率”而不是“逐项复刻大型锅炉整数取整”。

## 当前实现偏差

当前 `SolidFuelGeneratorMachine` 使用：

```text
rawDuration = matched STEAM_BOILER_RECIPES item recipe duration
EU_PER_RAW_FUEL_TICK = 6400 / 2 = 3200
remainingEU = rawDuration * 3200
targetEUt = boilerMaxTemperature / 2
```

以煤炭为例：

```text
rawDuration = 1600 * 12 = 19200
remainingEU = 19200 * 3200 = 61,440,000 EU
targetEUt(钢) = 900 EU/t
operationTicks = 68,266 ticks
```

同一燃料在同级大型钢锅炉链路中应约为 `71 ticks`、`63,616 EU`。因此当前实现同时存在两类问题：

1. 单个燃料的总 EU 约高出 `960x`。
2. 为释放这个过高总 EU，单个燃料燃烧时间也约高出 `960x`，表现为“燃烧速率显著低于大型锅炉”。

`targetEUt = maxTemperature / 2` 对钛、钨钢恰好接近基础蒸汽轮机换算结果；对钢则因为大型锅炉实际按水量整数取整，真实满载应是 `896 EU/t`，不是 `900 EU/t`。这个差距很小，真正的主因仍是总 EU 使用了错误的 recipe 时长。

## 实现注意事项

* 燃料匹配应改用 `GTRecipeTypes.LARGE_BOILER_RECIPES` 的 item input recipe，而不是 `STEAM_BOILER_RECIPES`。
* 仍应拒绝带流体的物品容器，保持“不接受岩浆桶作为固体燃料”的规则。
* 计算 `targetEUt` 应使用连续理想倍率模型，即 `matchingLargeBoilerMaxTemperature / 2`。这里的 `/ 2` 来自基础蒸汽轮机 `0.5 EU/mB`，不是电压阶段效率。
* UI 中“等效锅炉最大温度”如果继续用 K 表示，应显示 `maxTemperature + 273.15`，与修正后的大型锅炉 UI 一致。
* 旧版本已经持久化的燃烧操作会保留过高的 `remainingEU`；实现时需要持久化模型版本并迁移旧状态，否则测试世界会继续显示旧模型能量包。
* 这次改动会把现有固体燃料发电机从“单燃料千万 EU、长时间释放”改成“同大型锅炉链路一样快速消耗燃料、总 EU 约数万级”。这是对齐大型锅炉的预期结果，不是单纯的 UI 修复。

## 实现结果

已实现：

* 固体燃料发电机扫描 `GTRecipeTypes.LARGE_BOILER_RECIPES` 的 item input recipe。
* 单燃料总 EU 使用 `largeBoilerDuration * 6400 / 2`。
* EU/t 使用对应大型锅炉最高温度 `/ 2`，不再引入电压阶段效率。
* 每 tick 入账 `min(targetEUt, remainingEU)`，最后 tick 保留剩余热值。
* 等效锅炉温度显示改为 `maxTemperature + 273.15 K`。
* 添加能量模型版本迁移，旧存档加载时清掉旧模型燃烧中的过高 `remainingEU`。
* 旧存档加载测试发现，迁移不能在 `onLoad()` 中直接 `markDirty()`。`onLoad()` 可能处于 chunk post-load，立即标记脏数据会经 `Level.blockEntityChanged -> getChunk` 等待当前 chunk 任务，导致准备区域卡住。修正为迁移内只更新内存字段，随后用 server `TickTask` 延迟 `markDirty()`。

验证：

```text
./gradlew spotlessCheck compileJava
BUILD SUCCESSFUL
```
