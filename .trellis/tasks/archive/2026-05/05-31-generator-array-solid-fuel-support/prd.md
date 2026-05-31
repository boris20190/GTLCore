# brainstorm: solid fuel generator multiblock

## Goal

Design a new standalone solid fuel generator multiblock that consumes existing solid-boiler-valid fuel and outputs wireless EU, equivalent to a same-era full-load large boiler plus basic steam turbine chain.

## What I Already Know

* The user originally wanted to modify the existing Generator Array and discuss the implementation plan before coding.
* The user changed direction: the solid fuel generator should be a new multiblock, not a single-block generator that can be stacked inside the Generator Array.
* The rationale for the pivot is balance: a machine equivalent to a same-era large boiler plus multiple basic steam turbines is too strong if it is also stackable inside Generator Array.
* The current Generator Array multiblock pattern only allows fluid import, energy output, maintenance, and fluid export on `X` positions.
* The current internal machine slot accepts non-multiblock generator machine items only when their recipe type is one of the existing fluid-fuel generator types.
* The current `GeneratorArrayMachine.recipeModifier` computes max parallel from inserted machine count, machine tier, amperage, and base recipe EU/t, then limits actual parallel only through `FluidRecipeCapability`.
* GTLCore already overwrites `ItemRecipeCapability.getMaxParallelRatio(...)` and related parallel logic through `IParallelLogic`, so item-input parallel limiting exists in the codebase.
* GTLCore already changes large boiler fuel duration with `duration * 6400 / maxTemperature` in `LargeBoilerMachineMixin`.
* Existing boiler-style fuel handling is a better reference for solid fuel eligibility than inventing a completely separate fuel whitelist.
* GTCEu `SteamSolidBoilerMachine` rejects item stacks that contain fluid, then checks whether the item appears in an item-input recipe for `STEAM_BOILER_RECIPES`.
* GTCEu fuel datagen creates `STEAM_BOILER_RECIPES` item recipes from furnace fuel data and `GTUtil.getItemBurnTime`, and creates lava as a fluid recipe, not as solid boiler item fuel.

## Assumptions

* The Generator Array should continue to work for existing fluid generators without changing their gameplay behavior.
* The solid fuel generator should be represented as a standalone multiblock machine.
* The solid fuel generator should not be insertable into the Generator Array internal machine slot.

## Open Questions

* Should the task proceed to implementation with the current MVP scope, leaving exact visual footprint/block layout to implementation using the approved structure constraints?

## Requirements (Evolving)

* Include a minimal solid fuel generator multiblock in this task.
* The minimal solid fuel generator supports HV, EV, and IV tiers.
* The minimal solid fuel generator should be implemented as three separate multiblock definitions/controllers, one per tier.
* Each tier should use a new integrated boiler+turbine structure footprint rather than directly mirroring the aligned large boiler structure.
* Each tier's structure should use materials that match the aligned large boiler and additionally require same-era turbine casing and/or gearbox blocks to represent the integrated basic steam turbine group.
* Each solid fuel generator structure should require exactly one item input bus, exactly one maintenance hatch, and exactly one muffler hatch.
* Each solid fuel generator structure should not allow item output buses, fluid input hatches, fluid output hatches, energy output hatches, or other external output logistics for the generated EU or hidden byproducts.
* Implement the solid fuel generator with a dedicated machine/runtime logic path instead of relying on static datagen fuel recipes.
* The solid fuel generator is not a single-block generator and must not be stackable through Generator Array.
* Preserve existing Generator Array behavior for current fluid generators.
* Drop the original Generator Array item-input-bus support from this task; it is no longer needed for the standalone solid fuel generator.
* The solid fuel generator should use the mod's existing solid steam boiler fuel logic instead of directly querying vanilla/Forge furnace fuel logic.
* A candidate item fuel is valid only if it would be accepted by the solid steam boiler item-fuel path.
* Fluid containers follow the solid boiler behavior: they are not item fuels for the solid generator. Lava belongs to fluid boiler/fluid generator-style handling, not the solid generator's item bus.
* Solid fuel is consumed cleanly by the black-box generator; ash and other boiler fuel byproducts are not exposed as item outputs.
* The solid fuel generator converts fuel heat value into EU at 100% generator efficiency.
* The solid fuel generator is a black-box machine equivalent to an internal steam boiler plus steam turbine chain: input solid fuel, output EU, with virtual water and steam cycling internally.
* The solid fuel generator must not require water input, steam output, distilled water handling, or any external fluid loop.
* For a fixed fuel heat value, total generated EU is fixed: `duration * EUt = totalEU`.
* Runtime should track `remainingEU` for the current fuel operation and output `min(targetEUt, remainingEU)` each tick so the final tick credits the exact leftover EU.
* Runtime must not accept rounding drift in total EU because a fuel's total generated EU is part of the balance contract.
* Total EU should be derived from the equivalent large boiler path: `fuel heat value -> large boiler total steam -> basic steam turbine at 100% efficiency -> EU`.
* Solid fuel generator tiers may use a `maxTemperature`-style per-tier rate parameter to change burn duration and EU/t while preserving total EU.
* HV, EV, and IV solid fuel generators respectively target large steel boiler, large titanium boiler, and large tungstensteel boiler throughput.
* The solid fuel generator should read the active GTCEu large steel/titanium/tungstensteel boiler configuration for its tier-aligned throughput, rather than snapshotting hardcoded default temperatures.
* One solid fuel generator is equivalent to one full-load same-era large boiler plus the corresponding maximum number of basic steam turbines it can support.
* The equivalent turbine side uses fractional turbine-equivalent conversion from all virtual steam; no virtual steam is discarded due to integer turbine count limits.
* The black-box generator does not simulate boiler heat-up or ramp behavior; each fuel operation assumes the equivalent large boiler is already at full working temperature.
* `maxTemperature` must be treated as a throughput/rate parameter, not as part of the fuel's total heat value.
* The solid fuel generator is wireless-output-only: it deposits generated EU directly into the wireless energy network and cannot output EU through traditional cables.
* Wireless EU crediting should follow Generator Array's wireless generation pattern: store/compute generated EU per tick and call `WirelessEnergyManager.addEUToGlobalEnergyMap(ownerUuid, eut, machine)` during work.
* The wireless owner does not need to be online for generated EU to be credited; the machine credits the persisted owner UUID through the wireless energy manager.
* The solid fuel generator should not expose a normal cable-output path for generated energy.
* The solid fuel generator should not require or allow item output buses for ash/byproducts in the first implementation.
* Do not use `6400 mB/t` as the large bronze boiler steam generation baseline unless a future code-backed source is found.
* Code-backed large boiler runtime calculation: a full-temperature, 100% throttle large bronze boiler with unlimited water and steam output produces `800 mB/t`.
* The raw steam turbine fuel recipe consumes `640 mB / 10 ticks = 64 mB/t`.
* GTLCore's `SimpleGeneratorMachineMixin` makes an LV steam turbine run that recipe at 8 parallel with 100% steam-turbine efficiency, so an actual LV basic steam turbine consumes `512 mB/t` and outputs `256 EU/t` at full power.
* Therefore the code-backed large bronze boiler output can sustain `12.5` raw basic steam turbine recipes or `1.5625` actual GTLCore LV basic steam turbine machines at full power.
* Higher tier solid fuel generators may output higher EU/t and therefore burn the same fuel for fewer ticks, but must not change total EU for that fuel.
* If the per-tier rate model derives a target EU/t higher than conventional cable-output expectations, the wireless-only output rule allows the multiblock to keep the derived EU/t without cable amperage limits.
* Each fuel operation consumes exactly one valid solid fuel item, matching one virtual same-era large boiler chain.
* The multiblock does not parallelize same-fuel consumption inside one operation.
* When multiple different valid solid fuels are present, a running operation chooses one fuel stack/type for that operation; other fuels remain for later operations.

## Acceptance Criteria (Evolving)

* [x] Existing Generator Array builds still validate with existing fluid-only fuel setup.
* [x] A minimal solid fuel generator multiblock exists.
* [x] HV, EV, and IV solid fuel generators are registered as three separate multiblock definitions/controllers.
* [x] Solid fuel generator structures use integrated boiler+turbine footprints with tier-appropriate boiler, turbine casing, and/or gearbox materials.
* [x] Solid fuel generator structures require exactly one item input bus, one maintenance hatch, and one muffler hatch.
* [x] Solid fuel generator structures do not allow item output buses, fluid hatches, or energy output hatches.
* [x] The solid fuel generator cannot be inserted into or stacked through Generator Array.
* [x] Solid fuel generator tiers match the selected tier range.
* [x] Solid fuel generator runtime logic scans item inputs and synthesizes a fuel operation from one selected solid-boiler-valid fuel.
* [x] A solid-fuel generator runtime operation can consume solid-boiler-valid item fuel from the multiblock's item input bus.
* [x] For a given fuel item, all solid fuel generator tiers produce the same total EU at different EU/t and duration.
* [x] If a fuel's total EU does not divide evenly by target EU/t, the final tick credits only the remaining EU and total EU is exactly conserved.
* [x] Solid fuel generators do not require external water, steam, or distilled-water logistics.
* [x] Solid fuel generators credit generated EU directly to wireless energy storage.
* [x] Solid fuel generators can credit generated EU to an offline owner UUID through the wireless energy manager.
* [x] Standalone solid fuel generators bind wireless output to their owner: placing player first, falling back to the first player who opens the UI if placement ownership is unavailable.
* [x] Solid fuel generators do not output generated EU to cables or normal energy output hatches.
* [x] Solid fuel generators do not output ash or other fuel byproducts.
* [x] Fuel total EU is calculated from the aligned large boiler total steam and the basic steam turbine conversion rate.
* [x] Lava buckets and other fluid containers follow the existing solid boiler item-fuel behavior.
* [x] Each operation consumes exactly one valid solid fuel item.
* [x] Mixed fuel inputs run one selected fuel type per operation; other valid fuel types remain for later operations.
* [x] The multiblock does not expose generated EU through output hatches or cables.

## Definition of Done

* `./gradlew spotlessCheck` passes.
* `./gradlew compileJava` passes.
* Datagen is run if recipe, lang, model, or generated resources change.
* Relevant Trellis spec notes are updated if the implementation reveals a durable convention.

## Verification Notes

* `./gradlew spotlessCheck compileJava -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk` passes.
* `./gradlew runData -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk` reached Registrate provider completion and wrote files, but the Gradle process did not exit cleanly and was stopped after unrelated dev-environment warnings. The generated diff contained only unrelated existing gtlcore item/lang resources and was discarded.
* No generated solid-fuel-generator resource files appeared; the machine registration mirrors existing GTCEu multiblock registration behavior where runtime definitions and hand-written lang entries are sufficient for this change.

## Out of Scope (Draft)

* Advanced balancing beyond the selected heat-to-EU conversion rule.
* Reworking existing fluid fuel generator balance.
* Replacing the Generator Array UI.
* Adding Generator Array item input bus support.
* Extending Generator Array to support solid fuel generator stacking.

## Technical Notes

* Generator Array registration: `src/main/java/org/gtlcore/gtlcore/common/data/machines/GeneratorMachine.java`
* Generator Array runtime: `src/main/java/org/gtlcore/gtlcore/common/machine/multiblock/generator/GeneratorArrayMachine.java`
* Existing multiblock generator registrations live in `src/main/java/org/gtlcore/gtlcore/common/data/machines/GeneratorMachine.java`.
* Existing custom multiblock generator runtime classes live under `src/main/java/org/gtlcore/gtlcore/common/machine/multiblock/generator/`.
* Existing large/mega turbine registrations use `GTBlocks.CASING_STEEL_TURBINE`, `GTBlocks.CASING_TITANIUM_TURBINE`, `GTBlocks.CASING_TUNGSTENSTEEL_TURBINE`, and matching gearbox blocks such as `GTBlocks.CASING_STEEL_GEARBOX`, `GTBlocks.CASING_TITANIUM_GEARBOX`, and `GTBlocks.CASING_TUNGSTENSTEEL_GEARBOX`.
* Simple generator registration mixin: `src/main/java/org/gtlcore/gtlcore/mixin/gtm/registry/GTMachinesMixin.java`
* Simple generator recipe modifier mixin: `src/main/java/org/gtlcore/gtlcore/mixin/gtm/machine/SimpleGeneratorMachineMixin.java`
* Item parallel support: `src/main/java/org/gtlcore/gtlcore/mixin/gtm/api/capability/ItemRecipeCapabilityMixin.java`
* Shared parallel helpers: `src/main/java/org/gtlcore/gtlcore/api/recipe/IParallelLogic.java`
* Large boiler duration scaling: `src/main/java/org/gtlcore/gtlcore/mixin/gtm/machine/LargeBoilerMachineMixin.java`
* Boiler fuel model research note: `.trellis/tasks/05-31-generator-array-solid-fuel-support/research/boiler-fuel-model.md`
* Existing generator fuel recipe types are static GTCEu recipe types with datagen recipes.
* `SteamSolidBoilerMachine` item filter rejects fluid-containing stacks, then checks item-input recipes in `STEAM_BOILER_RECIPES`.
* `FuelRecipes` creates `STEAM_BOILER_RECIPES` item recipes from furnace burnable item sources and `GTUtil.getItemBurnTime`; lava is registered as a fluid recipe.
* Basic steam turbine recipe: `640 mB Steam -> 4 mB Distilled Water`, `duration = 10`, `EUt = LV`, so the 100% conversion rate is `320 EU / 640 mB = 0.5 EU per mB Steam`.
* `SteamBoilerMachine` full-temperature steam generation runs every 10 ticks and uses `baseSteamOutput * currentTemperature / maxTemperature / 2`.
* Normal solid boiler defaults are `baseSteamOutput = 120`, `maxTemperature = 500`, so full-temperature steady output is `60 mB / 10 ticks = 6 mB/t`.
* High-pressure solid boiler defaults are `baseSteamOutput = 300`, `maxTemperature = 1000`; it also halves fuel recipe duration in `recipeModifier`.
* Large bronze boiler config defaults are `maxTemperature = 800`, `heatSpeed = 1`, and `steamPerWater = 160`.
* GTCEu registers these boiler values through `@Config(id = "gtceu")` with YAML format, so a runtime game instance exposes them in `config/gtceu.yaml`.
* The configurable large boiler fields include `steamPerWater`, per-tier `*BoilerMaxTemperature`, and per-tier `*BoilerHeatSpeed`.
* GTLCore modifies large boiler fuel duration with `recipe.duration * 6400 / maxTemperature`, so a large bronze boiler at 100% throttle uses `effectiveDuration = recipe.duration * 8`.
* Superseded user-proposed design baseline: large bronze boiler steam generation is `6400 mB/t`.
* Code validation status for `6400 mB/t`: not verified as large bronze boiler steam output from the inspected GTCEu/GTLCore code, so it is not the current conversion baseline.
* `BoilerType.BRONZE` carries `800` as its first numeric constructor argument; `BoilerType.TUNGSTENSTEEL` carries `6400`.
* `ConfigHolder.MachineConfigs.LargeBoilers` defaults `bronzeBoilerMaxTemperature = 800` and `tungstensteelBoilerMaxTemperature = 6400`.
* GTLCore's `LargeBoilerMachineMixin` uses `6400D` as the max-temperature reference in duration scaling, not as a large bronze boiler steam-output field.
* Runtime steam generation note: current `LargeBoilerMachine` bytecode uses `steamPerWater` as a divisor in water-request calculation, then uses it as a multiplier when converting drained water into steam: `maxDrain = currentTemperature * throttle * 5 * bucket / (steamPerWater * 100000)`, `steamGenerated = drained * steamPerWater`.
* Runtime config note: in the ideal unlimited-water/unlimited-output case, `steamPerWater` cancels out of steady steam output apart from integer truncation. Per-tier `*BoilerMaxTemperature` is the config value that changes full-temperature steam output rate.
* Solid fuel generator tier throughput reads the active GTCEu large boiler config values for steel, titanium, and tungstensteel instead of fixed hardcoded defaults.
* Runtime implementation note: actual LV steam turbine full-power consumption must include GTLCore's simple-generator parallel modifier when comparing physical machine counts; raw recipe count and actual machine count differ by 8x.
* Candidate solid generator rate formula: `rateFactor = generatorMaxTemperature / baselineMaxTemperature`, `targetEUt = baselineEUt * rateFactor`, `duration = ceil(totalEU / targetEUt)`, with runtime `remainingEU` making the final tick output exact leftover EU.
* If GTCEu large boiler defaults are reused as solid generator tier temperatures, HV/EV/IV map to steel/titanium/tungstensteel `1800/3200/6400`; at `0.5 EU per mB Steam`, direct target rates are `900/1600/3200 EU/t`.
* Fractional turbine-equivalent conversion is selected: the black-box generator uses all virtual steam at the raw `0.5 EU/mB` basic steam turbine conversion rate, not only the integer number of fully supportable actual turbine machines.
* Heat-up behavior is intentionally omitted: the black-box generator uses full-temperature large boiler throughput for every fuel operation.
* Existing Generator Array/simple-generator amperage for LV/MV/HV is `8/6/4`, giving output ceilings of `256/768/2048 EU/t`; default boiler-derived LV and MV target rates would exceed those ceilings, but this is acceptable for the solid fuel generator if it is wireless-output-only.
* Existing Generator Array wireless mode stores generated EU/t in `eut`, removes `EURecipeCapability` tick output from the running recipe, and calls `WirelessEnergyManager.addEUToGlobalEnergyMap(userid, eut, this)` during `onWorking()`.
* The solid fuel generator multiblock can reuse the same wireless-crediting pattern but should not depend on Generator Array's internal machine-slot or recipe-parallel logic.
* Superseded: direct `ForgeHooks.getBurnTime(ItemStack, RecipeType.SMELTING)` fuel checks should not be the solid generator's source of truth.
* Superseded: `duration = burnTime`, `EUt = LV voltage (32 EU/t)` is not sufficient once generator tiers must preserve total EU.
* Superseded: solid fuel generator as a normal simple generator machine inserted into Generator Array.
* Superseded: Generator Array item-bus support as a required mechanism for the solid fuel generator.
* Decision: implement solid boiler fuel support by querying/mirroring the existing solid steam boiler item-fuel path rather than maintaining a separate solid generator fuel predicate.
* Decision: solid fuel generator efficiency is 100% for all tiers.
* Decision: total EU for a fuel is conserved across solid fuel generator tiers.
* Decision: runtime tracks remaining operation EU and credits exact leftover EU on the final tick instead of accepting duration/EUt rounding drift.
* Decision: solid fuel generator output is wireless-only and does not support traditional cable EU output.
* Decision: standalone solid fuel generator wireless output binds to the placing player, with first-opener fallback if no placer owner is recorded.
* Decision: wireless EU crediting follows the Generator Array wireless mode pattern and may credit offline owner UUIDs through `WirelessEnergyManager`.
* Decision: solid fuel generator is a self-contained boiler+turbine black box with virtual internal water/steam circulation.
* Decision: derive solid fuel total EU from equivalent same-era large boiler total steam plus basic steam turbine 100% conversion, not from a standalone arbitrary fuel burn-time formula.
* Decision: solid fuel generator throughput follows active GTCEu large boiler config values for its aligned boiler tier.
* Decision: "corresponding maximum number of basic steam turbines" means fractional turbine-equivalent use of all produced steam; the generator does not discard leftover virtual steam because physical turbine count is not modeled.
* Decision: solid fuel generator operations do not simulate boiler heat-up/ramp behavior; they assume full working temperature from the start of each operation.
* Decision: minimal solid fuel generator tier range is HV/EV/IV.
* Decision: implement HV, EV, and IV as three separate solid fuel generator controller blocks/multiblock definitions, not one tier-aware controller.
* Decision: use new integrated boiler+turbine structure footprints, not direct copies of the aligned large boiler structures.
* Decision: tier structures should include aligned boiler materials plus same-era turbine casing and/or gearbox blocks to represent integrated turbines.
* Decision: solid fuel generator does not expose ash/byproduct output and should not require item output buses for fuel residue.
* Decision: solid fuel generator uses the standard combustion-style part set: one item input bus, one maintenance hatch, one muffler hatch, and no output/energy/fluid hatches.
* Decision: HV/EV/IV solid fuel generators respectively align to large steel, large titanium, and large tungstensteel boiler throughput.
* Decision: each solid-fuel operation consumes exactly one valid fuel item and does not parallelize fuel consumption inside one multiblock operation.
* Decision: implement the solid fuel generator as a standalone multiblock runtime, not as a simple generator inserted into Generator Array.
* Decision: Generator Array item-bus support is out of scope for this task.
