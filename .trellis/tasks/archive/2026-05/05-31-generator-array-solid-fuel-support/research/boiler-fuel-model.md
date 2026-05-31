# Boiler Fuel Model Research

## Scope

Local source inspection for whether the planned solid fuel generator should reuse existing boiler-style fuel eligibility and temperature-duration behavior.

## Findings

* GTLCore already adjusts large boiler recipe duration in `LargeBoilerMachineMixin`.
  * Formula: `recipe.duration * 6400D / largeBoilerMachine.maxTemperature`.
  * If the computed duration is below 1 tick, the mixin converts the excess speed into a content multiplier and clamps duration to 1 tick.
  * Throttle below 100% increases duration by `100 / throttle`.
* GTCEu steam boiler fuel recipes are built from furnace-style burnable items, with additional GT burn-time lookup for items not directly present in the vanilla furnace fuel map.
* `SteamSolidBoilerMachine` does not directly call furnace fuel logic from its item slot filter.
  * It rejects stacks containing fluid via `FluidTransferHelper.getFluidContained(stack)`.
  * It then checks whether the item appears in an item-input recipe for the machine's recipe type, `STEAM_BOILER_RECIPES`.
  * The result is cached per item in `FUEL_CACHE`.
* `FuelRecipes` registers lava for `STEAM_BOILER_RECIPES` as a fluid input recipe, while vanilla lava bucket may appear in furnace-derived item recipe data before the solid boiler item filter rejects fluid containers.
* Boiler fuel generation and boiler runtime are not a direct match for Generator Array behavior:
  * Boiler recipes convert fuel into steam and then downstream EU.
  * Generator recipes emit EU directly.
  * Copying the temperature duration formula into a direct generator changes total EU unless EU/t is also adjusted or another balance rule is defined.
* Steam solid boiler ash output is a byproduct rule for specific fuel materials, not a generic container-return rule.
* Basic steam turbine fuel recipe uses `640 mB Steam`, `duration = 10`, and `EUt = LV`, producing `32 * 10 = 320 EU` from `640 mB` steam at 100% efficiency.
  * Conversion rate: `0.5 EU / mB Steam`.
  * Raw recipe steam consumption: `64 mB/t`.
* GTLCore's `SimpleGeneratorMachineMixin` modifies actual simple generator machines:
  * LV steam turbine `getAmperage(1) = 8`.
  * LV steam turbine efficiency from `getEfficiency(STEAM_TURBINE_FUELS, LV)` is `100%`.
  * Actual LV basic steam turbine full-power consumption is `64 * 8 = 512 mB/t`.
  * Actual LV basic steam turbine full-power output is `32 * 8 = 256 EU/t`.
* Large bronze boiler defaults:
  * `bronzeBoilerMaxTemperature = 800`.
  * `bronzeBoilerHeatSpeed = 1`.
  * `steamPerWater = 160`.
  * `FluidHelper.getBucket() = 1000`.
* GTCEu registers its config holder with `@Config(id = "gtceu")` and `ConfigFormats.yaml()`.
  * The configuration library writes configs under `config/` using the configured filename and format extension, so the runtime file is `config/gtceu.yaml`.
  * `LargeBoilers` fields are `@Configurable`: `steamPerWater`, per-tier `*BoilerMaxTemperature`, and per-tier `*BoilerHeatSpeed`.
  * `steamPerWater` is configurable, but it cancels out of ideal steady steam output because it is used as a divisor for water demand and a multiplier for water-to-steam conversion.
  * Per-tier max temperature is the exposed config that changes full-temperature steam output rate.
* GTLCore's large boiler mixin changes fuel duration to `recipe.duration * 6400 / maxTemperature`.
  * For bronze: `effectiveDuration = recipe.duration * 8` at 100% throttle.
  * Because full-temperature steam output scales with `maxTemperature`, this duration scaling keeps large-boiler total steam per fuel independent of per-tier max temperature in the ideal case.
* Strict current large boiler runtime computes steam every 5 ticks:
  * water request per generation event: `currentTemperature * throttle * 5 * bucket / (steamPerWater * 100000)`.
  * steam generated per event: `waterConsumed * steamPerWater`.
  * At full bronze temperature and 100% throttle: water request is `25 mB / 5 ticks`.
  * If fully supplied and fully accepted, generated steam is `4,000 mB / 5 ticks = 800 mB/t`.
  * Basic turbine equivalent: `800 * 0.5 = 400 EU/t` when using the raw steam turbine recipe conversion rate.
  * Raw steam turbine recipe count: `800 / 64 = 12.5`.
  * Actual GTLCore LV basic steam turbine machine count: `800 / 512 = 1.5625`.
  * With bronze duration scaling: `totalEU = recipe.duration * 8 * 400 = recipe.duration * 3,200` before choosing the raw-vs-actual turbine-machine baseline for direct generator EU/t/duration behavior.
* User-proposed large bronze boiler balancing model:
  * Reference steam generation is `6400 mB/t`.
  * Basic turbine equivalent: `6400 * 0.5 = 3200 EU/t`.
  * Code validation status: not verified as a large bronze boiler output constant or direct formula result.
  * This proposal is superseded unless a code-backed source is found.
* Code-level `6400` findings:
  * `BoilerType.TUNGSTENSTEEL` uses `6400` as its first numeric constructor argument.
  * `ConfigHolder.MachineConfigs.LargeBoilers` defaults `tungstensteelBoilerMaxTemperature = 6400`.
  * `LargeBoilerMachineMixin` uses `6400D` as the duration-scaling reference: `recipe.duration * 6400D / maxTemperature`.
  * These are not direct evidence that large bronze boiler steam output is `6400 mB/t`.
* `SteamBoilerMachine` produces steam every 10 ticks when hot enough.
  * Per production tick: `baseSteamOutput * currentTemperature / maxTemperature / 2`.
  * Normal solid boiler defaults: `baseSteamOutput = 120`, `maxTemperature = 500`, so full-temperature output is `60 mB / 10 ticks`.
  * High-pressure solid boiler defaults: `baseSteamOutput = 300`, `maxTemperature = 1000`, so full-temperature output is `150 mB / 10 ticks`; high pressure also halves recipe duration.

## Design Implication

The solid fuel generator should use the existing solid steam boiler item-fuel path as the source of truth:

* reject fluid-containing item stacks the same way the solid boiler does;
* match item fuel against `STEAM_BOILER_RECIPES` item-input recipes;
* keep lava/fluid fuels on the fluid-input side instead of accepting lava buckets as solid generator item fuel.

The temperature-duration model is a separate balance decision and should not be copied blindly into a direct EU generator without an explicit rule for total EU.

The user clarified that the solid fuel generator converts fuel heat value into EU at 100% efficiency. Therefore, duration changes must preserve total EU:

`duration * EUt = totalEU`

A per-tier `maxTemperature`-style rate parameter is compatible with that rule if it only changes throughput:

`rateFactor = generatorMaxTemperature / baselineMaxTemperature`

`targetEUt = baselineEUt * rateFactor`

`duration = ceil(totalEU / targetEUt)`

The implementation should track `remainingEU` and credit `min(targetEUt, remainingEU)` each tick. The final tick credits the exact leftover EU, so total EU is conserved exactly instead of accepting duration/EUt rounding drift.

The user further clarified that the intended balance model is to bypass the physical boiler and turbine chain:

`solid fuel -> steam boiler total steam -> basic steam turbine at 100% efficiency -> EU`

Conceptually, the solid fuel generator is a self-contained black box with an internal virtual boiler and turbine loop. It takes solid fuel and outputs EU; water, steam, and distilled-water handling are not exposed to external logistics.

The design pivoted from a simple generator stackable inside Generator Array to a standalone multiblock. This is a balance constraint: the machine represents a same-era large boiler plus a turbine group, so allowing it to be inserted and stacked by Generator Array would compress too much infrastructure into one array slot.

The baseline is now the equivalent large boiler plus basic steam turbine. The proposed design steam output of `6400 mB/t` for large bronze was not verified from the inspected code; the code-backed full-temperature large bronze boiler output is `800 mB/t`.

The strict runtime formula is recorded because it explains the apparent `steamPerWater` double use: it appears as a divisor when calculating water demand and as a multiplier when converting consumed water to steam.

With the inspected runtime formula and no water/output bottleneck, a full-temperature 100% throttle large bronze boiler produces `800 mB/t`. The raw steam turbine recipe consumes `64 mB/t`, but an actual GTLCore LV basic steam turbine runs that recipe at 8 parallel and consumes `512 mB/t`, so the same boiler output sustains either `12.5` raw recipe-equivalent turbines or `1.5625` actual LV basic steam turbine machines at full power.

The selected tier range is HV/EV/IV. These tiers align respectively to large steel, large titanium, and large tungstensteel boiler throughput. The generator should read the active GTCEu large boiler config values for those boiler tiers instead of snapshotting hardcoded defaults.

With GTCEu default max temperatures, the aligned throughput maps to `1800/3200/6400 mB/t`. At the raw turbine conversion rate of `0.5 EU/mB Steam`, those imply direct target rates of `900/1600/3200 EU/t`.

The user further clarified that one solid fuel generator is equivalent to one full-load same-era large boiler plus the corresponding maximum number of basic steam turbines it can support. The selected interpretation is fractional turbine-equivalent output using all virtual steam. With default steel/titanium/tungstensteel values, that yields `900/1600/3200 EU/t`. The rejected alternative was integer-only actual GTLCore LV basic steam turbine machine counts (`512 mB/t` each), which would have yielded `768/1536/3072 EU/t` and discarded leftover virtual steam.

The black-box generator intentionally does not simulate boiler heat-up or ramp behavior. Every fuel operation uses the full-temperature, full-load equivalent boiler throughput from the start.

Because the user accepted a wireless-output-only solid fuel generator, these direct target rates bypass cable amperage constraints. The implementation should credit wireless energy directly and avoid exposing a normal cable-output path.

Wireless EU crediting should follow the existing Generator Array wireless generation mode: keep a per-tick generated EU value and call `WirelessEnergyManager.addEUToGlobalEnergyMap(ownerUuid, eut, machine)` while working. The owner does not need to be online; the machine credits the persisted owner UUID.

This tier mapping changes throughput and progression placement, not fuel value. The total-EU conservation rule remains unchanged: higher aligned boiler temperature means higher EU/t and shorter runtime for the same fuel.

As a multiblock, the generator should not depend on Generator Array's internal simple-generator slot or recipe-parallel logic. Generator Array item-bus support is no longer required for the solid fuel generator itself unless a separate use case is confirmed.

The selected structure direction is a new integrated boiler+turbine footprint instead of a direct copy of the aligned large boiler structure. Each tier should use aligned boiler-era materials plus same-era turbine casing and/or gearbox blocks to represent the internal basic steam turbine group.

The black-box boundary also hides ash/fuel byproducts. The solid fuel generator consumes valid fuel cleanly and should not expose an item output bus just for boiler residue in the first implementation.

The selected structure part set is combustion-style and intentionally narrow: exactly one item input bus, exactly one maintenance hatch, exactly one muffler hatch, and no item output bus, fluid hatch, or energy output hatch.

Each runtime operation consumes exactly one valid solid fuel item. The standalone multiblock does not parallelize fuel consumption internally; building multiple multiblocks is the scaling path.
