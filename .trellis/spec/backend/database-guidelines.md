# Data and Persistence Guidelines

> 本项目没有关系数据库、ORM 或 migration。这里的“数据”指 Minecraft/Forge 持久化、GTCEu 数据生成、资源 JSON 和注册数据。

## No Database Layer

- 不要引入数据库、JDBC、ORM 或 migration 机制。
- 运行时持久化使用 Minecraft `SavedData`、NBT、LDLib/GTCEu managed fields 或机器自身序列化机制。
- 静态游戏数据优先通过 GTCEu/Registrate datagen 生成，必要时才维护手写 JSON。

## Runtime Persistence

### World-level data

`StorageManager` 是世界级持久化示例：

- 继承 `SavedData`。
- 通过 `save(CompoundTag)` 写入 `ListTag`。
- 通过静态 `readNbt(CompoundTag)` 恢复。
- 修改后调用 `setDirty()`。
- 通过 `server.getLevel(ServerLevel.OVERWORLD).getDataStorage().computeIfAbsent(...)` 获取实例。

适用规则：

- 持久化 key 使用稳定字符串，例如 `disklist`、`diskuuid`、`diskdata`。
- 序列化时跳过明显无效的 `null` entry，避免坏数据阻断存档保存。
- 修改 world-level 数据的方法必须负责 `setDirty()`，不要要求调用方记住。
- 只在 server side 写入权威状态。

### Machine and UI state

复杂机器使用 LDLib/GTCEu managed field 注解。`MEPatternBufferPartMachine` 展示了当前模式：

- `ManagedFieldHolder` 继承父类 holder。
- `@Persisted` 保存机器状态。
- `@DescSynced` 同步展示字段。
- `@LazyManaged` 用于较重数组字段。
- handler、cache、proxy、inventory 分区组织，构造函数集中初始化。

新增机器状态时先判断它是“存档状态”、“客户端展示状态”还是“运行时缓存”。只有存档状态才加 `@Persisted`。

### NBT helpers

`GTLUtil.serializeNBT(GTRecipe)` 和 `GTLUtil.deserializeNBT(Tag)` 是 recipe NBT 转换示例：

- 使用 GTCEu codec 处理 recipe 主体。
- 额外保存 `id`，恢复后 `recipe.setId(id)`。
- 反序列化失败返回 `null`，调用方必须处理。

不要手写 ad hoc 字符串格式来保存 recipe、ItemStack 或 FluidStack；优先使用对应 API 的 NBT、Codec 或 registry key。

## Data Generation

### Recipe generation

配方生成集中在 `data/recipe/`，入口由 `GTLGTAddon.addRecipes(...)` 调用。

局部模式：

```java
COMBUSTION_GENERATOR_FUELS.recipeBuilder("naphtha")
        .inputFluids(Naphtha.getFluid(1))
        .duration(20)
        .EUt(-V[LV])
        .save(provider);
```

使用规则：

- 每个 recipe id 必须稳定、可搜索、lower_snake_case。
- 使用 `Consumer<FinishedRecipe> provider` 传递生成目标。
- GTCEu 机器配方用 `GTRecipeType.recipeBuilder(...)`；原版 crafting 用 `VanillaRecipeHelper.addShapedRecipe(...)`。
- 同一物品同时需要 crafting 和 GTCEu 机器配方时，沿用 `MachineRecipe` 里同一段落相邻声明的模式。
- 移除已有配方使用 `RemoveRecipe.init(Consumer<ResourceLocation>)`，不要用空配方覆盖。

新增 recipe hook 前先搜索 `onRecipeBuild`。`RecipeModify` 已经集中修改 GTCEu 原配方行为，重复注册 hook 会造成难查的生成副作用。

### Boiler-style solid fuel eligibility

新增消耗“固体锅炉可用燃料”的机器时，不要直接调用原版/Forge 熔炉燃料 API 作为运行时真源。当前项目应复用 GTCEu 固体蒸汽锅炉的 item-fuel 语义：

- 先用 `FluidTransferHelper.getFluidContained(stack)` 排除含流体的物品栈；岩浆桶这类流体容器不属于固体 item fuel。
- 再扫描 `GTRecipeTypes.STEAM_BOILER_RECIPES`，只匹配 `ItemRecipeCapability.CAP` 的输入项。
- 匹配时用 `ItemRecipeCapability.CAP.of(content.content).test(stack)`，不要维护单独的燃料白名单。
- 如果机器把燃料热值直接转换成 EU，要把“燃料总值”和“输出速率”拆开：速率可以跟配置或 tier 变化，总 EU 必须由剩余量字段精确结算，最后一 tick 输出 `min(targetEUt, remainingEU)`。

这样可以自动继承 GTCEu/GTLCore 已有燃料配方、配置和流体容器排除规则，避免固体燃料机器与锅炉燃料行为分叉。

### Large boiler equivalent fuel conversion

如果机器语义是“等效大型锅炉 + 基础蒸汽轮机”，不要直接使用 `STEAM_BOILER_RECIPES` 的原始时长计算热值。GTCEu 会在 recipe type 注册阶段把固体蒸汽锅炉 item fuel 派生到 `GTRecipeTypes.LARGE_BOILER_RECIPES`，其基础时长为：

```text
largeBoilerDuration = steamBoilerDuration / 12 / 80
```

而普通固体燃料的 `steamBoilerDuration` 通常来自：

```text
steamBoilerDuration = furnaceBurnTime * 12
```

所以等效大型锅炉机器应扫描 `GTRecipeTypes.LARGE_BOILER_RECIPES` 的 item input recipe，并继续排除含流体容器。不要用 `STEAM_BOILER_RECIPES` 原始时长乘发电倍率，否则会把单燃料总 EU 和燃烧时间放大约 `960x`。

当前已确认的理想黑盒模型：

```text
totalEU = largeBoilerDuration * 6400 / 2
targetEUt = matchingLargeBoilerMaxTemperature / 2
```

这里 `/ 2` 来自基础蒸汽轮机配方 `640 mB Steam / 10t -> 32 EU/t`，即 `0.5 EU/mB`。实现层应保留 `remainingEU`，每 tick 输出 `min(targetEUt, remainingEU)`，让最后一个 tick 入账剩余 EU，避免钢锅炉这类非整数倍率造成热值损失。

如果变更了持久化能量模型，要给机器状态加持久化模型版本，并在旧版本加载时清掉当前燃烧中的旧 `remainingEU`/`operationTotalEU`，防止旧存档继续按过期模型释放已经持久化的超大能量包。机器 `onLoad()` 可能发生在 chunk post-load 过程中；迁移时不要在 `onLoad()` 内直接调用 `markDirty()`，否则可能触发 `Level.blockEntityChanged -> getChunk` 等待当前 chunk 任务，导致旧存档卡在准备区域。需要持久化迁移结果时，参考 `ComputationProviderMachine` 的做法用 `ServerLevel.getServer().tell(new TickTask(...))` 延迟到当前加载任务结束后再标记脏数据。

### Boiler temperature display

GTCEu 大型锅炉的内部 `maxTemperature` 参与配方时长和蒸汽速率计算，不等同于玩家 UI 里显示的 K 值。玩家可见 K 显示应使用严格物理换算：

```text
displayTemperatureK = internalTemperature + 273.15
```

不要把显示用 K 值带回燃料消耗、蒸汽产量或 EU/t 计算。若用 mixin 修正 GTCEu 原始大型锅炉 UI 中的 `274.15` 字面量，`@Constant(doubleValue = 274.15)` 只是匹配上游原始字节码，替换值仍应返回 `273.15`。

### Resource locations and namespaces

- 本项目资源用 `GTLCore.id("path")`。
- GTCEu 资源用 `GTCEu.id("path")`。
- 外部 mod 如 ExtendedAE 使用对应 helper，例如 `ExtendedAE.id("assembler_matrix_frame")`。
- KubeJS 迁移或 remap 场景显式使用 `KubeJS.id(...)` 或 `new ResourceLocation(namespace:path)`，参见 `ForgeCommonEventListener.remapIds(...)`。

### Generated resources

`src/generated/resources/` 当前包含 blockstates、models、loot tables、tags、`en_ud.json` 等 datagen 产物；`src/main/resources/` 包含 mods.toml、mixin config、手写 assets 和少量手写 recipes。

修改规则：

- 能由 Registrate 或 recipe provider 生成的资源，优先改 Java datagen 入口，再运行 `./gradlew runData`。
- 手写资源适用于外部 namespace 覆盖、已有资源补丁或项目已手写的例外，例如 `src/main/resources/data/gtlcore/recipes/assembler_matrix_frame.json`。
- 修改方块、物品、机器注册后检查对应 lang、model、blockstate、loot table 是否需要新增或重新生成。
- `runData` 可能顺手刷新与当前任务无关的陈旧 generated 产物。提交前必须检查 `src/generated/resources/` diff，只保留能追溯到本次 Java datagen/注册改动的文件；无关刷新应丢弃并在任务记录里说明。

## Config Data

配置集中在 `ConfigHolder`：

- `@Config(id = GTLCore.MOD_ID)` 绑定 mod id。
- `Configuration.registerConfig(ConfigHolder.class, ConfigFormats.yaml())` 初始化。
- 字段使用 `@Configurable`、`@Configurable.Range`、`@Configurable.Comment` 和必要的 `@Configurable.Synchronized`。

新增配置项时同步处理：

- 默认值。
- 范围限制。
- `zh_cn.json` 和 `en_us.json` 中的 `config.gtlcore.option.*` 翻译。
- 调用点的 server/client side 和热更新假设。

## Verification

- 只改 Java datagen 或注册：运行 `./gradlew spotlessCheck`、`./gradlew compileJava`。
- 改 recipe、block/item model、loot table、tag：再运行 `./gradlew runData`，检查 `src/generated/resources/` 变化。
- 改手写 resource JSON：至少用编辑器或工具确认 JSON 语法，并搜索对应 id 是否存在。
