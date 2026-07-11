# 技术设计

## 1. 目标与不变量

本任务将本地 `gtl-1431-skyblock` 与 `upstream/gtl-1431-skyblock` 的已审阅头部
`4315cf7c` 整合，但规划阶段不执行合并。实施时必须满足以下不变量：

- 保留本地固体燃料发电机、ME 样板总成主产物视图和通配符样板保存修复。
- 保留已在共同基线中验证过的巨型仓室电路、退回和催化剂 capability 语义。
- 当前维护分支和其工作区在整合结果通过检查前保持不变。
- 不把 `.agents/`、`.codex/` 和 Trellis 工具链的无关未提交改动带入合并提交。
- 不升级依赖、Gradle wrapper 或外部 mod；上游本次也没有这类变更。
- 没有明确授权时不创建合并提交、不快进维护分支、不推送。

## 2. 上游更新分析

| 范围 | 规模 | 最终有效内容 | 风险 |
|---|---:|---|---|
| `fix7` → `fix8` | 4 提交，2 文件 | 修正结构检测锁的获取与 `finally` 释放，避免阻塞服务器主线程 | 低 |
| `fix8` → `fix9` | 9 提交，64 文件 | AE2 自适应合成计算、样板快速上传、库存仓监听更新、本地化 | 高 |
| `fix9` → `fix10` | 25 提交，80 文件 | AE 合成暂停/状态、快速上传 UI 修复、MetaMachine 异步通知修正、库存样板通知、ME 吞吐监视器、标签过滤库存输入仓 | 高 |
| `fix10` → `fix11` | 5 提交，61 文件 | 恢复被错误覆盖的吞吐/库存功能，改善合成状态可见性，移除中间版 `MAX_FAST`，增加分子操纵者核心数量显示 | 高 |
| `fix11` → `4315cf7c` | 5 提交，4 文件 | 修正电力聚爆压缩机减免、中子漩涡重复并行、进阶真空干燥炉耗时/超频，补充机器 tooltip | 中 |

最高风险不在 Git 文本冲突，而在 AE2 合成计算、CPU 调度、存储可见性、网络包和
MetaMachine 线程边界的语义变更。这些部分需要编译检查之外的服务器验收。

## 3. Git 整合策略

### 3.1 选项比较

| 策略 | 评估 |
|---|---|
| 保留历史的 merge commit | 推荐。与前两次上游整合一致，不重写 37 个本地独有提交，上游 48 个提交的来源清晰 |
| rebase 本地历史 | 不采用。会重写已归档任务、既有 merge commit 和已在 fork 上存在的历史，风险与收益不匹配 |
| 逐个 cherry-pick 上游提交 | 不采用。上游包含多次合并、回退和覆盖修正，拣选容易遗漏最终状态的依赖 |
| reset 到上游后重放本地改动 | 不采用。属于破坏性历史重写，也难以保留 Trellis 与已发布本地修复的可追踪性 |

### 3.2 推荐机制

1. 从已审阅的当前 `HEAD` 创建专用整合分支和临时 worktree。
2. 在临时 worktree 中执行 `merge --no-commit --no-ff`，解决冲突并完成所有本地检查。
3. 检查通过后，在获得明确提交授权时创建唯一的 merge commit。
4. 从该提交构建 JAR，记录提交号和 SHA-256，交给测试服务器验收。
5. 验收成功后再将当前维护分支 `--ff-only` 快进到整合分支；推送需单独授权。

这一机制使原工作区的无关改动始终不进入整合索引，并且在服务器验收前不移动维护分支。

## 4. 冲突和语义整合

### 4.1 已确认的文本冲突

| 文件 | 本地意图 | 上游意图 | 解析结果 |
|---|---|---|---|
| `build.gradle` | 注册 `gtlcore.wildcard.mixin.json` | 使用 `mixinConfigNames` 并新增 `checkMixinPackageClasses` | `mixinConfigNames` 同时包含主配置和 wildcard 配置；保留上游检查任务 |
| `GTLMachines.java` | ME 样板总成说明“AE 只追踪第一输出” | 增加样板快速上传 tooltip | 在迷你、扩展、库存和终极总成上保留两条说明，不以整文件 `ours/theirs` 解决 |
| `assets/gtceu/lang/en_us.json` | 主产物追踪文案 | 快速上传和新机器文案 | 保留所有不重复 key，对 JSON 语法和重复 key 做独立检查 |
| `assets/gtceu/lang/zh_cn.json` | 主产物追踪文案 | 快速上传和新机器文案 | 同英文文件，并检查中英文 key 对称 |

### 4.2 Mixin package 结构适配

上游质量门会递归检查 `org.gtlcore.gtlcore.mixin` 下的所有 Java 文件。本地新增的
`WildcardConfiguratorSaveHelper` 和 `WildcardMixinPlugin` 不是 `@Mixin` 类，因此必须：

- 移到 `org.gtlcore.gtlcore.integration.wildcard`。
- 将 helper 暴露范围调整为 wildcard mixin 能调用的最小公开 API。
- 更新两个 wildcard mixin 的 import。
- 更新 `gtlcore.wildcard.mixin.json` 中的 plugin 全限定类名。
- 保持 wildcard 依赖为 `mandatory = false` 且 mixin 配置 `required = false`。

### 4.3 自动合并但需人工复核的文件

- `MultiBlockMachineA.java`：保留本地注册关系，接受上游机器修正；删除重复 `perfect_oc` tooltip。
- `AdditionalMultiBlockMachine.java`：接受进阶真空干燥炉和中子漩涡逻辑；删除重复 `coil_parallel` tooltip。
- `MEPatternBufferPartMachine.java`：接受样板槽下标边界修正，保留原始样板与主产物视图语义。
- `MEStockingPatternBufferPartMachine.java`：接受库存变更后的 recipe handler 通知，保留主产物 tooltip。
- `WildcardPatternCompatImpl.java`：同时保留主产物说明和上游新增样板倍增说明。
- GTLCore 中英文语言文件：保留本地固体燃料发电机/主产物文案，并引入上游新 UI 文案。

## 5. AE2 与本地主产物语义

合并后的数据流必须保持如下分层：

```text
原始 encoded pattern（完整输入/输出）
        │
        ├─→ 快速上传/撤回、编码终端、槽位 tooltip：保留完整输出
        │
        └─→ ME 样板总成 onPatternChange
                 └─→ MEBufferPatternHelper 生成 AE 视图
                          └─→ AE 计划、智能翻倍、waitingFor、状态原因：仅第一非空输出
```

上游快速上传直接操作 `getTerminalPatternInventory()` 中的原始物品栈，而本地主产物逻辑只重建
提供给 AE crafting service 的 `AEProcessingPattern` 视图，两者可以共存。验收时仍需同时验证
“快速上传后原始副产物未丢失”和“AE 完成条件仅等待主产物”。

## 6. 风险与验证设计

| 风险 | 观测点 | 缓解/验证 |
|---|---|---|
| AE2 合成 mixin 大量覆写 | 计算卡住、计数溢出、暂停不生效、等待原因错误 | 构建检查 + 大数量处理样板下单 + 暂停/恢复 + 缺材料/缺能源状态验收 |
| MetaMachine 异步 `onChanged` | 关服卡住、异步线程写世界状态 | 多库存仓存档/关服压测，确认无异步异常 |
| 吞吐监视与存储 mixin | 网络包过多、客户端卡顿、数量过期 | 多玩家打开 JEI/Jade/终端，观察限流和数值更新 |
| 通配符修复移动 package | 有 mod 时 mixin 未应用，无 mod 时启动失败 | `checkMixinPackageClasses`、配置/类名检查、有/无 wildcard 两种启动路径 |
| 固体燃料发电机与共享 RecipeLogic/MetaMachine 变更 | 暂停失效、结构失效仍发电、进度丢失 | 复测暂停、结构破坏/恢复、控制器破坏、4 倍速率与总 EU |
| 机器配方修正 | 倍率、并行或超频再次重复 | 分别验证电力聚爆压缩机、中子漩涡、进阶真空干燥炉，并检查 tooltip 去重 |

## 7. 版本、发布与回滚

- 保留上游 `mod_version=1.2.3.0-fix11`。因头部含 5 个标签后提交，发布记录必须额外标明
  整合目标 `4315cf7c`、本地 merge commit 和 JAR SHA-256，不仅依赖文件名版本。
- 部署前保留已验证的上一版 JAR，并备份世界和会被新字段更新的配置。
- 服务器验收前，回滚是“不快进维护分支，恢复旧 JAR”；不需要重写 Git 历史。
- 如维护分支已快进后才发现严重问题，使用 `git revert -m 1 <merge-commit>` 生成可追踪回退，
  不使用 `reset --hard` 或强制推送。
- 整合分支和临时 worktree 保留到服务器验收完成，之后的删除需在收尾时明确确认范围。

## 8. 任务分解决策

不创建子任务。冲突解析、构建验证和服务器 JAR 验收都依赖同一个未变的合并树与同一个
JAR 校验和，将它们分开会增加版本漂移风险。执行计划以明确的停机检查点分阶段验证。
