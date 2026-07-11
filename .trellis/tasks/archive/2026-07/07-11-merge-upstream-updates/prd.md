# 规划合并上游更新

## Goal

在不改动当前工作区、不执行实际合并的前提下，充分分析 `upstream/gtl-1431-skyblock`
相对本地 `gtl-1431-skyblock` 的更新，形成一份可执行、可验证、可回滚的上游整合计划，
保留本地功能与已完成修复。

## Background

- 当前本地分支：`gtl-1431-skyblock`，`HEAD=05a3817a`。
- 跟踪分支：`origin/gtl-1431-skyblock=5d7aa3af`；本地相对其领先 38 个提交、落后 0 个提交。
- 上游分支：`upstream/gtl-1431-skyblock=4315cf7c`。
- 本地与上游的共同基点为 `a91f863d`；基点后本地独有 37 个提交，上游独有 48 个提交，两者已双向分叉。
- 上游更新规模：133 个文件，8754 行新增，226 行删除；新增 `fix8`—`fix11`
  四个版本标签，`fix11` 后还有 5 个提交。
- 本地已提交改动与上游变更有 10 个路径重叠；包括 `build.gradle`、
  `WildcardPatternCompatImpl.java`、机器注册与语言文件。
- 工作区存在 55 项与本任务无关的未提交改动；已检查到它们与当前上游变更没有直接路径重叠。
- 最近完成的“修复通配符样板属性过滤保存错误”已经服务器实测通过，整合后必须保留其行为。
- 历史会话和 Git 记录确认：项目此前两次同步同一上游分支都使用了 merge commit，
  最近一次为 `2f67d922`，且已通过编译验证；保留合并历史是当前仓库的既有做法。
- `git merge-tree` 对当前 `HEAD` 与上游头部的虚拟合并发现 4 个文本冲突：
  `build.gradle`、`GTLMachines.java`、`assets/gtceu/lang/en_us.json`、
  `assets/gtceu/lang/zh_cn.json`。
- 另有 6 个双方同时修改但可自动合并的文件：`MultiBlockMachineA.java`、
  `MEPatternBufferPartMachine.java`、`MEStockingPatternBufferPartMachine.java`、
  `WildcardPatternCompatImpl.java` 以及 GTLCore 的中英文语言文件。
- 将本地分别与 `fix11` 标签和最新上游头部虚拟合并，冲突集合完全相同。
  `fix11` 后 5 个提交仅改动 4 个文件（`+33/-7`），不额外引入文本冲突。
- 上游没有升级外部依赖、Gradle wrapper 或锁文件；`gradle.properties` 仅将模组版本
  从 `fix7` 更新为 `fix11`。
- 上游新增 `checkMixinPackageClasses` 质量门，禁止在 Mixin package 中放置非 Mixin helper。
  本地 `WildcardConfiguratorSaveHelper` 和 `WildcardMixinPlugin` 会被该检查拒绝，整合时必须移到
  `integration.wildcard` 边界并同步更新引用与 Mixin plugin 类名。
- 最新上游机器 tooltip 变更中存在两处明显重复展示：巨型合金冶炼炉重复
  `perfect_oc`，进阶真空干燥炉重复 `coil_parallel`。

## Requirements

- 本次整合的固定目标为最新上游头部 `4315cf7c`，包含 `fix11` 后 5 个未单独打标签的机器逻辑与 tooltip 提交。
- 整合时删除上游新增的两条重复 tooltip：巨型合金冶炼炉的重复 `perfect_oc` 和进阶真空干燥炉的重复 `coil_parallel`；保留其他上游机器逻辑与新 tooltip。
- 建立上游 48 个提交的功能分类、依赖关系、高风险变更和可选整合边界。
- 盘点共同基点后的本地独有提交，区分产品代码、修复、构建配置、Trellis/工具链以及任务归档记录。
- 对重叠文件和共享调用链进行语义级差异分析，不仅依赖 Git 文本冲突结果。
- 比较合并、变基、拣选以及以上游为新基线重放本地提交等策略，给出推荐方案和放弃其他方案的依据。
- 实施时使用专用整合分支和临时 worktree 创建保留双方历史的 merge commit；在本地检查和服务器验收完成前，不移动当前维护分支。
- 明确事前保护、分批整合、冲突解决顺序、构建/测试验证、服务器验收与回滚点。
- 保留无关工作区改动，不得在未获授权时暂存、提交、重置、合并、变基或推送。
- 本规划阶段仅产出 Trellis 规划文档，不执行上游整合。

## Acceptance Criteria

- [x] `prd.md` 记录目标、范围、约束与可验收标准，且经过收敛检查。
- [x] `design.md` 给出推荐的 Git 整合策略、分批边界、冲突处理原则、验证和回滚设计。
- [x] `implement.md` 给出可按顺序执行的清单、具体检查命令、高风险文件和每个停机检查点。
- [x] 计划覆盖从共同基点到 `4315cf7c` 的所有 48 个上游独有提交，并区分已发布的 `fix8`—`fix11` 与 `fix11` 后未标签提交。
- [x] 计划显式覆盖通配符样板保存修复的保留与回归验证。
- [x] 对代码重叠、构建依赖、Mixin 配置、资源/本地化、AE2 数据流和机器配方逻辑给出专项验证。
- [x] 合并结果不再重复展示巨型合金冶炼炉 `perfect_oc` 和进阶真空干燥炉 `coil_parallel` tooltip。
- [x] 计划使当前维护分支和无关脏工作区在整合结果通过服务器验收前保持不变。
- [x] 用户审阅并明确批准规划后，才能进入实际整合阶段。

## Out of Scope

- 在规划阶段执行 `merge`、`rebase`、`cherry-pick`、`reset`、`stash`、`commit` 或 `push`。
- 顺带修复与上游整合无关的已有代码或工具链问题。
- 替代服务器环境中的最终行为验收。
