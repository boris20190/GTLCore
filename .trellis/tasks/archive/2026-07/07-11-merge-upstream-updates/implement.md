# 实施计划

## 0. 实施前门槛

- [x] 用户已审阅 `prd.md`、`design.md` 和本文档，并明确批准进入实施。
- [x] 读取 `trellis-before-dev` 和与 Java/Mixin/资源相关的 Trellis spec。
- [x] 实施继续使用 inline 模式，不委派 implement/check 子代理。
- [x] 用户已对“创建临时整合分支/worktree 并让 merge 写入其索引”给予明确授权。
- [x] 提交、快进维护分支和推送分别作为后续停机点；不从实施请求中默认扩大授权。

## 1. 刷新和锁定整合输入

1. 记录当前分支、`HEAD`、跟踪分支、工作区状态和当前活跃 Trellis 任务。
2. 执行 `git fetch --prune upstream`，仅刷新上游跟踪引用。
3. 确认 `upstream/gtl-1431-skyblock` 仍为 `4315cf7c`。
4. 如上游头部变化，立即停止；重新统计提交、差异和虚拟合并，不将未审阅提交自动纳入。
5. 重新计算共同基点、双方独有提交数和工作区脏文件与上游变更的路径交集。

参考检查：

```bash
git rev-parse HEAD upstream/gtl-1431-skyblock
git merge-base HEAD upstream/gtl-1431-skyblock
git rev-list --left-right --count HEAD...upstream/gtl-1431-skyblock
git status --short --branch
```

### 停机点 A

- 目标提交不是 `4315cf7c`、工作区出现新的代码路径重叠、或共同基点不是 `a91f863d` 时，不继续。

## 2. 建立隔离的合并环境

1. 从已锁定的当前 `HEAD` 创建 `integration/upstream-4315cf7c` 分支。
2. 将该分支检出到 `$TMPDIR/gtlcore-upstream-4315cf7c` 类似的临时 worktree。
3. 在临时 worktree 中再确认 `HEAD` 与主工作区起始提交一致，工作区干净。
4. 不把当前未提交的 Trellis 规划文档或工具链文件复制到整合 worktree。

临时 worktree 保留到服务器验收完成，不在构建后立即删除。

## 3. 执行未提交合并并核对冲突集

1. 在整合 worktree 执行：

   ```bash
   git merge --no-commit --no-ff upstream/gtl-1431-skyblock
   ```

2. 预期仅有以下 4 个未解决文件：

   - `build.gradle`
   - `src/main/java/org/gtlcore/gtlcore/common/data/GTLMachines.java`
   - `src/main/resources/assets/gtceu/lang/en_us.json`
   - `src/main/resources/assets/gtceu/lang/zh_cn.json`

3. 如冲突集增加、出现删除/修改或重命名冲突，停止并更新设计，不按旧计划猜测处理。
4. 不对任何冲突文件使用整文件 `--ours` 或 `--theirs`。

### 停机点 B

- 实际冲突必须与虚拟合并证据一致，否则不开始手工解析。

## 4. 按意图解析冲突

### 4.1 `build.gradle`

- 定义统一列表：

  ```groovy
  def mixinConfigNames = [
          "gtlcore.mixin.json",
          "gtlcore.wildcard.mixin.json"
  ]
  ```

- `loom.forge.mixinConfigs` 使用 `mixinConfigNames`。
- 完整保留上游 `checkMixinPackageClasses` 任务及 `check` 依赖。
- 不修改依赖声明，`wildcard_pattern` 仍为 `modCompileOnly`。

### 4.2 wildcard 非 Mixin 类迁移

使用 `apply_patch` 完成文件新增/删除和引用更新，不用覆写式 shell 命令：

- `WildcardConfiguratorSaveHelper` → `integration/wildcard`。
- `WildcardMixinPlugin` → `integration/wildcard`。
- 更新 `WildcardFilterFancyConfiguratorMixin` 和 `WildcardIOFancyConfiguratorMixin` import。
- 更新 `gtlcore.wildcard.mixin.json` 的 `plugin`。
- 确认 `mixin/wildcard` 下只剩两个带 `@Mixin` 的类。

### 4.3 `GTLMachines.java`

- 在迷你、扩展、库存、终极 ME 样板总成上同时保留：
  - `gtceu.machine.me_pattern_buffer.desc.6`
  - `gtlcore.machine.pattern_quick_upload.tooltip`
- 保留库存总成的两条专用说明。
- 不覆盖上游吞吐监视器、标签过滤库存仓或分子操纵者的注册变更。

### 4.4 语言 JSON

- 保留本地 `gtceu.machine.me_pattern_buffer.desc.6`。
- 保留上游 `gtlcore.machine.pattern_quick_upload.tooltip` 和新机器 key。
- 保留本地固体燃料发电机文案。
- 中英文 key 必须成对，不得通过删除一方 key 来解决行冲突。

### 4.5 明确的语义修正

- 在 `MultiBlockMachineA.java` 删除新增的第二条 `gtceu.machine.perfect_oc` tooltip。
- 在 `AdditionalMultiBlockMachine.java` 删除新增的第二条 `gtceu.multiblock.coil_parallel` tooltip。
- 除上述两条用户已批准的去重外，不顺带修改其他上游实现。

## 5. 冲突解析后的静态检查

- [x] `git diff --name-only --diff-filter=U` 无输出。
- [x] `git ls-files -u` 无输出。
- [x] 没有真实冲突标记。
- [x] `git diff --cached --check` 通过。
- [x] 审阅完整暂存差异，确认没有原工作区的无关改动。
- [x] 逐个审阅 10 个双方重叠路径和 `AdditionalMultiBlockMachine.java`。
- [x] `mixinConfigNames` 包含两个配置，配置文件存在。
- [x] Mixin package 下没有非 `@Mixin` Java 类。
- [x] wildcard plugin 新类名、两个 mixin import 和 JSON plugin 字段一致。
- [x] 两处 tooltip 已去重，新增的机器说明仍存在。

JSON 检查至少覆盖：

```bash
python3 -m json.tool src/main/resources/gtlcore.mixin.json
python3 -m json.tool src/main/resources/gtlcore.wildcard.mixin.json
python3 -m json.tool src/main/resources/assets/gtceu/lang/en_us.json
python3 -m json.tool src/main/resources/assets/gtceu/lang/zh_cn.json
python3 -m json.tool src/main/resources/assets/gtlcore/lang/en_us.json
python3 -m json.tool src/main/resources/assets/gtlcore/lang/zh_cn.json
```

另用只读 Python 检查器通过 `object_pairs_hook` 拒绝重复 JSON key；`json.tool`/`jq` 本身不能发现这类问题。

### 停机点 C

- 静态检查失败、重叠文件中任一本地契约丢失，或暂存差异含无关路径时，不进入构建。

## 6. JDK 21 构建和资源验证

1. 使用完整 JDK 21，不改动项目 Java 17 target：

   ```bash
   env JAVA_HOME=<complete-jdk-21-path> \
     PATH="$JAVA_HOME/bin:$PATH" \
     ./gradlew --no-daemon spotlessCheck check build
   ```

2. `check` 必须实际执行上游新增的 `checkMixinPackageClasses`。
3. 单独确认 `compileTestJava` 通过。上游新增的两个 `*Test` 是带 `main` 的断言辅助类，
   不是 JUnit 测试；不得仅因 `test` 任务成功就声称其断言已执行。
4. 在临时 worktree 中尝试 `runData`，它用于验证新注册、模型、配方和语言链路：
   - 运行期间持续观察，不进行超过 60 秒的无消息等待。
   - 上次 `runData` 已知会在 DataGenerator 完成后长时间不退出；若重现，记录最后成功阶段并中断。
   - 检查其生成差异，但不将 cache、时间戳或无关 generated 产物纳入合并提交。
5. 检查最终 JAR 至少包含：
   - `gtlcore.mixin.json`
   - `gtlcore.wildcard.mixin.json`
   - 迁移后的 wildcard plugin/helper 类
   - 新增 AE2 快速上传、吞吐监视和标签库存仓类/资源
6. 记录 JAR 路径、大小和 SHA-256。

### 停机点 D

- `spotlessCheck`、`check` 或 `build` 任一失败时不创建 merge commit。
- `runData` 的已知退出问题可单独记录，但不能掩盖数据生成阶段的真实错误。

## 7. 创建 merge commit（需单独确认）

1. 展示并审阅：
   - 暂存路径清单
   - 差异统计
   - 四个冲突文件的最终内容
   - wildcard package 迁移
   - 两处 tooltip 去重
   - 全部验证结果
2. 向用户请求创建 merge commit 的明确授权。
3. 授权后创建一个合并提交，不将解析分散为多个无意义提交。
4. 从已提交树重新运行最小快速检查，再重新生成/确认 JAR SHA-256。

## 8. 服务器验收矩阵

在备份的测试服务器/存档上使用与 merge commit 校验和对应的 JAR：

2026-07-11，用户确认使用 11.6 记录的项目内最终发布 JAR 完成服务器测试且未发现问题。
以下勾选基于用户提供的外部服务器验收结论，不表示这些运行时场景由本地自动化测试直接观测。

### 8.1 启动与存档

- [x] 服务器启动无 Mixin 目标缺失、类加载或重复注册错误。
- [x] 存档、停服和再启动成功；多库存仓场景不卡在“保存世界中”。
- [x] 新配置 `enableAe2PatternProviderAutoExpand` 被识别，默认值保持上游 `true`。

### 8.2 本地功能回归

- [x] 通配符样板 property 和 IN/OUT 配置一次保存即生效，重开不回退。
- [x] 无 `wildcard_pattern` 的可选依赖启动路径不因 plugin/helper 迁移失败。
- [x] ME 样板总成快速上传多输出样板后，编码物品和 tooltip 仍含完整输出，AE 仅等待第一个非空输出。
- [x] 固体燃料发电机保持 3x3x4 结构、4 倍释放速率/总 EU 不变、暂停/恢复、结构破坏/恢复和控制器破坏语义。
- [x] 巨型输入仓电路归一化、退回、阻挡模式与催化剂隐藏行为不回归。

### 8.3 上游新功能

- [x] AE2 默认 `ULTRA_FAST` 计算可完成大量处理样板下单，数量无溢出/负数。
- [x] 合成 CPU 暂停/恢复正常，缺材料、缺能源、provider 忙等状态原因显示正确。
- [x] 样板快速上传的唯一目标、多目标选择、撤回和满仓失败路径正常。
- [x] 库存样板总成从缺料到补料时会通知 recipe handler 并重新检测配方。
- [x] ME 吞吐监视器、JEI/Jade 库存数量和网络请求限流无明显性能/同步问题。
- [x] 标签过滤 ME 库存输入仓可注册、合成、配置并提供库存。
- [x] 电力聚爆压缩机、中子漩涡、进阶真空干燥炉的减免/并行/耗时/超频符合新逻辑。
- [x] 巨型合金冶炼炉和进阶真空干燥炉不显示重复 tooltip。

### 停机点 E

- 服务器验收失败时，维护分支不快进，不推送。在整合分支上根据复现单独评估修复，不隐藏失败。

## 9. 快进维护分支与推送

1. 服务器验收成功后，回到原工作区重新检查脏文件与快进差异的路径交集。
2. 展示维护分支将前进到的 merge commit、差异范围和无关改动保留证据。
3. 获得明确授权后执行 `git merge --ff-only integration/upstream-4315cf7c`。
4. 再次运行快速状态/包含关系检查，确认原工作区无关改动未被暂存或改写。
5. 推送 `origin/gtl-1431-skyblock` 必须另行获得用户明确授权。
6. 整合分支/worktree 清理、Trellis 收尾与归档按 `trellis-finish-work` 执行，不提前删除回滚点。

## 10. 实施前最终检查

- [x] `prd.md` 已通过收敛检查。
- [x] `design.md` 中的冲突集、数据流、风险与回滚点与当前 Git 证据一致。
- [x] 用户已确认整合最新头部 `4315cf7c` 以及两处 tooltip 去重。
- [x] 用户已审阅并批准本实施计划。
- [x] 在执行 `task.py start` 前运行 Trellis 任务验证。

## 11. 实施记录（2026-07-11）

### 11.1 输入与隔离环境

- 已刷新并锁定 `upstream/gtl-1431-skyblock=4315cf7cd7d8f4155ea741383903b40975cd2190`。
- 共同基点仍为 `a91f863d`，分叉仍为本地独有 37 个提交、上游独有 48 个提交。
- 整合分支为 `integration/upstream-4315cf7c`，历史临时 worktree 为
  `$TMPDIR/gtlcore-upstream-4315cf7c`，起点保持 `05a3817accc9835cfdb4cac602514f05a9a71024`。
- 主工作区 `HEAD` 未移动，仍有原有/任务文档共 56 项未提交状态，暂存区为 0；整合索引未混入
  `.agents/`、`.codex/` 或 `.trellis/` 路径。

### 11.2 合并与冲突解析

- 已执行未提交合并，`MERGE_HEAD=4315cf7cd7d8f4155ea741383903b40975cd2190`。
- 实际冲突严格限于计划中的 4 个文件，未出现额外冲突类型；已按双方语义逐块解析。
- 暂存合并结果为 138 个文件、8763 行新增、233 行删除；未解决路径和 index conflict stage 均为 0，
  `git diff --cached --check` 通过。
- 两个 wildcard 非 Mixin 类已迁移到 `integration.wildcard`，配置、plugin 类名和两个 Mixin import
  已同步；Mixin 源码目录不再含非 `@Mixin` Java 类。
- 四种 ME 样板总成同时保留本地多输出说明和上游快速上传说明；中英文资源 key 对齐。
- 已删除用户批准的两条重复 tooltip，其他上游机器逻辑和说明保持不变。

### 11.3 静态与构建验证

- 11 个暂存 JSON 文件均通过拒绝重复 key 的严格解析；GTCEu 与 GTLCore 的中英文 key 集分别完全一致。
- 两个 Mixin 配置均可解析，所列 main/client/server Mixin 及 plugin 源文件均存在。
- 使用 JDK 21 离线执行
  `./gradlew --offline --no-daemon spotlessCheck check build`，76 秒内成功；12 个任务全部实际执行，
  包括 `spotlessCheck`、`checkMixinPackageClasses`、`compileJava`、`compileTestJava`、`test`、
  `remapJar` 和 `build`。
- 上游两个 `*Test` 是带 `main` 的辅助断言类而非 JUnit 测试；本次确认其编译通过，不声称 Gradle
  `test` 已执行其中的 `main` 断言。
- `runData` 已完成 Registrate Provider 和 DataGenerator/HashCache 阶段（总文件 380、旧文件 377、
  新文件 381、写入 7、未删除陈旧文件），随后无输出且未自行退出，按计划中断，进程退出码为 130；
  因此仅认定数据提供阶段完成，不记作 Gradle 成功退出。
- `runData` 留下 2 个已跟踪生成文件的未暂存修改和 4 个未跟踪生成文件；它们均未进入合并索引，
  后续创建 merge commit 时不得暂存。
- 提交前验证 JAR 为 `build/libs/gtlcore-1.2.3.0-fix11.jar`，大小 9,482,285 字节，SHA-256 为
  `c000767ab53aa667391e4e6b4376f71f0f4bb274fcb0f29b0087c7987cdf4ce8`；包含两个 Mixin 配置、
  迁移后的 wildcard plugin/helper，以及快速上传、吞吐监视和标签库存仓类/资源。

### 11.4 提交与提交后验证

- 用户授权后已创建唯一 merge commit：
  `10835f0bc10d7dee279f9ad415be60c19a8bbef3`（`Merge upstream/gtl-1431-skyblock at 4315cf7c`）。
- 两个父提交依次为本地 `05a3817accc9835cfdb4cac602514f05a9a71024` 和上游
  `4315cf7cd7d8f4155ea741383903b40975cd2190`。
- 为排除原整合 worktree 中未暂存的 `runData` 产物，已从 merge commit 创建干净 detached worktree
  `$TMPDIR/gtlcore-verify-10835f0b`；其 `git status --short` 无输出。
- 在干净提交树上再次使用 JDK 21 离线执行
  `./gradlew --offline --no-daemon --console=plain spotlessCheck check build`，105 秒内成功，
  12 个任务全部实际执行。
- 提交后最终 JAR 为
  `$TMPDIR/gtlcore-verify-10835f0b/build/libs/gtlcore-1.2.3.0-fix11.jar`，大小 9,482,286 字节，
  SHA-256 为 `81129c45edb7a8ff456df7acc379799eb1a2205d2e38bb4f4baf7a00b34ff8dd`。
  其 manifest 同时声明两个 Mixin 配置，且已复核包含 wildcard plugin/helper、快速上传、吞吐监视和
  标签过滤库存仓类。JAR manifest 含工作目录派生的 `Implementation-Title` 和构建时间，因此该哈希
  与提交前构建不同。该文件仅作为历史构建验证记录，已由 11.6 的项目内发布 JAR 取代，
  不再用于服务器验收。
- `trellis-update-spec` 已将纯 Mixin package 质量门和非 JUnit `main` 测试辅助类行为写入
  `backend/quality-guidelines.md`；该规范不属于 merge commit，随后以 `e3cde996` 单独提交。

### 11.5 提交后、服务器验收前状态

- 当时维护分支仍为 `05a3817a`，主工作区暂存区和远端均未改变。
- 临时整合分支和两个验证 worktree 均保留；原整合 worktree 的 6 个 `runData` 生成物仍未提交。
- 随后用户报告服务器验收通过，并授权提交与归档。

### 11.6 项目内纠正构建（最终交付）

- 按用户要求，最终交付物不再位于系统临时目录。构建输入通过 `git archive` 从 merge commit
  `10835f0bc10d7dee279f9ad415be60c19a8bbef3` 精确导出到 Git 忽略的
  `build/release-work/10835f0b/GTLCore/`。
- 已对 `build.gradle`、`gradle.properties`、`GTLMachines.java` 和两个 Mixin 配置抽样比较 Git blob
  哈希，归档快照与提交树完全一致；快照不包含当前维护分支的 57 项未提交改动。
- 在全新快照中使用 JDK 21 执行
  `./gradlew --offline --no-daemon --console=plain clean spotlessCheck check build`，77 秒内成功；
  包含 `clean` 在内的 13 个任务全部实际执行。
- 唯一用于服务器验收的发布 JAR 为
  `build/releases/10835f0b/gtlcore-1.2.3.0-fix11.jar`，大小 9,482,277 字节，SHA-256 为
  `559c3e3f143d1a5049289c53a51b7c58a8baed283557b2ef6ecaa6c35a0b3383`。
- 发布文件与构建输出逐字节一致并通过 ZIP 完整性检查。Manifest 中
  `Implementation-Title=GTLCore`、`Implementation-Version=1.2.3.0-fix11`，且同时声明
  `gtlcore.mixin.json` 与 `gtlcore.wildcard.mixin.json`；主类字节码 major version 为 61（Java 17）。
- 已复核 JAR 包含迁移后的 wildcard plugin/helper、AE2 样板快速上传、吞吐监视器和标签过滤库存仓
  关键类。构建时项目主工作区仍有 57 项未提交状态且暂存区为空，维护分支和远端均未移动。

### 11.7 服务器验收与归档前状态

- 用户确认使用 11.6 的项目内发布 JAR 完成服务器测试且未发现问题，停机点 E 通过。
- 维护分支已使用 `--ff-only` 从 `05a3817a` 前进到已验收 merge commit `10835f0b`；
  随后以 `e3cde996` 提交本任务学习到的 Mixin package 与测试验证规范。
- 快进前已确认全部 138 个合并路径与现有脏文件无交集；当前任务外的既有改动均原样保留。
- `origin/gtl-1431-skyblock` 仍为 `5d7aa3af`，本任务未推送或修改其他远端状态。
- 本任务的规划、实施、构建、服务器验收、规范同步和提交均已完成，可以归档。
