# Directory Structure

> GTLCore 是单模块 Forge mod，不按传统 service/controller/repository 分层。新增代码应放到现有 mod 生命周期、注册和集成边界里。

## Top-Level Layout

```text
src/main/java/org/gtlcore/gtlcore/
  GTLCore.java                 Forge @Mod 入口
  GTLGTAddon.java              GTCEu addon 入口
  api/                         跨模块接口、扩展能力和通用抽象
  client/                      客户端 GUI、渲染器、客户端事件
  common/                      方块、物品、机器、配方运行时和注册数据
  config/                      dev.toma.configuration 配置
  data/recipe/                 GTCEu/Vanilla 配方数据生成
  forge/                       Forge bus 事件监听
  integration/<mod>/           AE2、KubeJS、Jade、JEI、GTMThings 等集成
  mixin/<target>/              按被修改 mod 或 Minecraft 子系统分组的 mixin
  utils/                       跨包复用的小工具和数据结构

src/main/resources/
  META-INF/mods.toml           Forge 元数据
  gtlcore.mixin.json           Mixin 列表
  assets/<namespace>/          lang、model、texture、ui 等静态资源
  data/gtlcore/recipes/        手写配方 JSON 例外

src/generated/resources/       datagen 产物，当前仓库会跟踪这些资源
```

## Runtime Entry Points

- Forge 入口放在 `GTLCore`。只在这里做全局 mod 初始化、代理分发和少量全局 tick 挂接。
- Common/client 分发沿用 `DistExecutor.unsafeRunForDist(() -> ClientProxy::new, () -> CommonProxy::new)`，客户端代码放 `client/` 或 client-only mixin 列表。
- GTCEu 注册入口在 `GTLGTAddon`：`initializeAddon()` 调 `GTLItems.init()`、`GTLBlocks.init()`；`addRecipes()` 调 `data/recipe/*`；`removeRecipes()` 调 `RemoveRecipe.init(...)`。
- Forge mod bus 注册在 `CommonProxy`：材料、配方类型、配方条件、机器等都通过 `eventBus.addListener` 或 `addGenericListener` 挂入。

## Package Ownership

### `api/`

放跨多个 runtime 包复用的接口、recipe 扩展、machine trait 和 GUI 基础组件。例子：

- `api/recipe/IParallelLogic.java` 和 `api/recipe/RecipeRunnerHelper.java` 支撑 recipe 并行逻辑。
- `api/machine/trait/*` 定义机器 trait 合约，具体实现放 `common/machine/trait/` 或机器类中。
- `api/registries/GTLRegistration.java` 提供项目自己的 `GTRegistrate` 实例。

不要把只被单个机器使用的私有实现提前放进 `api/`。

### `common/data/`

放注册定义和 GTCEu 数据定义，通常是 `public static final` 字段加空 `init()` 触发类加载。

- `GTLBlocks`：方块、方块实体、方块模型和 blockstate datagen helper。
- `GTLItems`：物品、AE2 存储单元、覆盖板物品、tooltip 和升级卡。
- `GTLMachines`：简单机器、部件、多方块注册，以及 `GTAEMachines` 等嵌套注册集合。
- `GTLRecipeTypes`：自定义 recipe type 和 UI/sound/data info 配置。
- `common/data/machines/*`：大批量多方块和机器注册拆分文件。

新增注册时优先放进相同职责的现有文件；只有一个文件已经明显不可维护或职责不同，才新建同层文件并从 `GTLMachines.init()` 或对应入口调用。

### `common/machine/` and `common/machine/trait/`

放实际运行时机器和 trait 实现。例子：

- `common/machine/generator/LightningRodMachine.java` 使用 `subscribeServerTick` 和 `NotifiableEnergyContainer`。
- `common/machine/multiblock/electric/WorkableElectricMultipleRecipesMachine.java` 覆盖 `createRecipeLogic(...)` 来接入 `MultipleRecipesLogic`。
- `common/machine/trait/MultipleRecipesLogic.java` 放复杂 recipe 匹配和并行输出逻辑。

保持机器类围绕 holder、tier、recipe logic、UI、tick、NBT/managed field 组织；不要把注册 DSL、资源生成和长配方列表塞进机器运行时类。

### `data/recipe/`

放数据生成配方，不是运行时 recipe logic。每个文件提供 `public static void init(Consumer<FinishedRecipe> provider)` 或等价入口，由 `GTLGTAddon.addRecipes(...)` 调用。

例子：

- `FuelRecipes` 使用 `GTRecipeType.recipeBuilder(id)` 链式设置输入、耗时、EUt，然后 `.save(provider)`。
- `MachineRecipe` 同时使用 `VanillaRecipeHelper.addShapedRecipe(...)` 和 GTCEu recipe builder。
- `RemoveRecipe` 接受 `Consumer<ResourceLocation>`，删除 GTCEu、ExtendedAE 等命名空间下的既有配方。

### `integration/<mod>/`

放对外部 mod API 的适配。按目标 mod 分目录：

- `integration/ae2/`：AE2 cell、crafting、storage、handler、async、widget。
- `integration/kjs/GTLKubejsPlugin.java`：KubeJS class filter 和脚本绑定。
- `integration/jade/GTLJadePlugin.java`：Jade provider 注册。
- `integration/jei/GTJEIPlugin.java`：JEI recipe catalyst 注册。

可选集成要用对应 API 的加载约束或运行时检查。JEI 示例里 `LDLib.isReiLoaded()` / `isEmiLoaded()` 用于避免重复注册。

### `mixin/<target>/`

Mixin 按被修改目标分组，目录名就是目标生态：

- `mixin/ae2/*` 修改 AE2 crafting、gui、service、stack 等。
- `mixin/gtm/*` 修改 GTCEu recipe、machine、registry、gui、cover 等。
- `mixin/extendedae/*`、`mixin/gtmt/*`、`mixin/ldlib/*` 等修改对应外部 mod。

新增 mixin 必须同步加入 `src/main/resources/gtlcore.mixin.json`。客户端专用 mixin 放 `client` 数组，通用逻辑放 `mixins` 数组。

## Naming Conventions

- Java package 全部位于 `org.gtlcore.gtlcore` 下。
- 注册 ID 使用 lower_snake_case，例如 `item_infinity_cell`、`dimensionally_transcendent_casing`。
- 注册常量使用 `UPPER_SNAKE_CASE`，例如 `ITEM_INFINITY_CELL`、`DISASSEMBLY_RECIPES`。
- helper 方法使用项目现有命名风格，例如 `createCasingBlock(...)`、`registerStorageCell(...)`、`registerTieredCover(...)`。
- ResourceLocation 优先用 `GTLCore.id("...")`，外部命名空间用对应项目 helper，例如 `GTCEu.id(...)` 或 `ExtendedAE.id(...)`。
- 翻译 key 跟随现有命名空间：`block.gtlcore.*`、`item.gtlcore.*`、`gtlcore.*`、`gui.gtlcore.*`。

## Common Mistakes

- 修改注册 ID、配方 ID 或翻译 key 前没有全仓搜索。相关引用可能分布在 Java、resource JSON、generated resource 和 mixin 配置中。
- 把客户端类直接引用到 common/server 路径。客户端代码要放 `client/` 或 mixin 的 `client` 列表，并用 Forge/Dist 分发约束。
- 新增 mixin 后忘记更新 `gtlcore.mixin.json`。
- 把 datagen 逻辑写成手工 JSON，而项目已有 Registrate 或 recipe provider 可以生成对应资源。
- 把单用途逻辑放进 `utils/`，导致后续调用方难以判断 side、lifecycle 或依赖边界。
