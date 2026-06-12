# 合并上游并编译验证

## Goal

验证当前分支 `gtl-1431-skyblock` 是否可以真实合并 `upstream/gtl-1431-skyblock` 的最新代码，并通过项目的编译/测试检查确认合并结果是否可用。

## Requirements

* 刷新 `upstream` 远端引用，确保使用最新上游提交。
* 在当前工作区执行真实合并，保留合并结果用于编译验证。
* 若出现文本冲突，停止并报告冲突文件，不擅自做业务取舍。
* 合并成功后运行项目中最合适的编译/测试命令。
* 不创建提交，不推送远端。

## Acceptance Criteria

* [x] Git 合并完成且无未解决冲突，或明确列出冲突文件。
* [x] 编译/测试命令已运行，并报告通过或失败原因。
* [x] 最终报告当前工作区状态和后续可选操作。

## Definition of Done

* 合并可行性结论明确。
* 构建/测试结果真实、可复核。
* 未提交任何更改，除非用户后续明确要求。

## Out of Scope

* 不修复合并后的编译错误，除非用户另行要求。
* 不解决需要人工业务判断的语义冲突。
* 不提交、不推送、不发布。

## Technical Notes

* 当前分支此前与 `origin/gtl-1431-skyblock` 同步。
* 上游分支此前检查为 `upstream/gtl-1431-skyblock`。
* dry-run 合并曾显示 Git 层面可自动合并，但尚未验证编译。
* 真实合并使用 `git merge --no-commit --no-ff upstream/gtl-1431-skyblock`，没有文本冲突，当前处于待提交合并状态。
* `./gradlew spotlessCheck compileJava` 在默认 Java 17 runtime 下失败，因为 `/usr/lib/jvm/java-17-openjdk` 没有可用 Java compiler；使用完整 JDK 21 临时设置 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` 后通过。
* `./gradlew test` 在 JDK 21 下通过；项目当前测试源为 `NO-SOURCE`。
