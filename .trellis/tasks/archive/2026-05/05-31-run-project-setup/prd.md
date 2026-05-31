# 跑通当前项目并准备开发环境

## Goal

确认 GTLCore 当前代码库可以在本地开发环境中完成基础 Gradle 验证，并整理后续开发应使用的命令、运行前提和已知风险。此任务只做开发环境与项目可运行性准备，暂不做业务功能开发。

## What I Already Know

- 当前项目是 Java 17 的 Forge 1.20.1 mod，构建工具是 Gradle wrapper，wrapper 版本为 Gradle 8.8。
- `build.gradle` 使用 Architectury Loom、Forge、GTCEu、AE2、KubeJS、JEI、Spotless 等依赖。
- `gradle.properties` 固定 `org.gradle.jvmargs=-Xmx3G`，Java 编译目标为 17。
- `.gitignore` 已忽略 `.gradle/`、`build/`、`run/` 等本地构建/运行产物。
- Trellis backend spec 的预开发清单要求业务代码改动后至少运行 `./gradlew spotlessCheck` 和 `./gradlew compileJava`；涉及数据生成时再运行 `./gradlew runData`。

## Assumptions

- 本轮“跑通”优先定义为命令行可重复验证：Gradle wrapper 可用、格式检查可运行、Java 编译可运行、必要时补充构建/数据生成命令结论。
- 本轮不启动 `runClient`/Minecraft 图形客户端；该命令耗时且偏交互式，不适合作为每次开发前置门禁。
- 本轮不新增或升级业务依赖，除非基础构建无法继续且必须调整配置。

## Requirements

- 识别并记录当前项目的开发前置条件：JDK、Gradle wrapper、关键 Gradle task、缓存/网络注意事项。
- 运行最小有效验证命令，确认项目是否处于可开发状态。
- 如果验证失败，定位阻塞点并优先给出配置/环境层面的修复建议；只有必要时才改项目文件。
- 保持工作区干净或只留下 Trellis 任务记录，不引入业务功能改动。

## Acceptance Criteria

- [x] `java -version` 显示可用的 Java 17。
- [x] `./gradlew --version` 可运行。
- [x] `./gradlew spotlessCheck` 完成。
- [x] `./gradlew compileJava` 完成。
- [x] `./gradlew build` 完成并产出 jar。
- [x] 给出后续开发建议命令和注意事项。
- [x] 没有业务功能代码改动。

## Definition of Done

- 基础验证结果已记录在任务文档或最终回复中。
- 如有环境或配置问题，说明复现命令、失败原因和下一步处理建议。
- Trellis 任务状态可以进入实现/验证阶段并最终收尾。

## Out of Scope

- 新功能、配方、机器、mixin 或资源业务改动。
- 依赖版本升级。
- 发布、上传、推送或远端 CI 配置。
- 默认启动 Minecraft 客户端或执行长时间交互式运行。

## Technical Notes

- 相关文件：`build.gradle`、`gradle.properties`、`settings.gradle`、`gradle/wrapper/gradle-wrapper.properties`、`.trellis/spec/backend/index.md`。
- 后续如果需要业务开发，按 backend spec 读取对应 guideline，并至少执行 `spotlessCheck` 与 `compileJava`。

## Verification Results

### Environment

- Default Java: OpenJDK 17.0.19 at `/usr/lib/jvm/java-17-openjdk`.
- Gradle wrapper: Gradle 8.8.
- Project compile target: Java release 17 via `options.release.set(17)`.
- Local Java 17 package issue: `/usr/lib/jvm/java-17-openjdk/release` is missing. Loom source remap emitted `FileNotFoundException` for that file during the first Gradle attempt.
- Successful workaround: run Gradle with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`. Java 21 has a complete `release` metadata file, while the project still compiles with target release 17.

### Commands Run

```bash
java -version
GRADLE_USER_HOME=/tmp/gtlcore-gradle-home ./gradlew --version
timeout 20m ./gradlew --no-daemon spotlessCheck compileJava --stacktrace
timeout 20m env JAVA_HOME=/usr/lib/jvm/java-21-openjdk PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH ./gradlew --no-daemon spotlessCheck compileJava --stacktrace
timeout 15m env JAVA_HOME=/usr/lib/jvm/java-21-openjdk PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH ./gradlew --no-daemon build --stacktrace
```

### Passing Results

- `spotlessCheck` passed with Java 21 running Gradle.
- `compileJava` passed with Java 21 running Gradle in 8m29s after first-time Forge/Loom setup.
- `build` passed in 17s after caches were warm.
- Build artifacts:
  - `build/libs/gtlcore-1.1.1.jar`
  - `build/libs/gtlcore-1.1.1-sources.jar`
- Captured the Gradle runtime JDK gotcha in `.trellis/spec/backend/quality-guidelines.md`.

### Existing Warnings

- Architectury Loom `1.6.422` reports that the version is outdated.
- `EnergyContainerListMixin.java` reports a Mixin `@Overwrite` target warning during compile.
- `GTLBlocks.java` has several deprecated `BlockBuilder.addLayer(...)` warnings.
- Gradle reports deprecated features that will be incompatible with Gradle 9.0.

## Recommended Development Commands

Use Java 21 to run Gradle in this local environment until the Java 17 JDK package metadata is fixed:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew spotlessCheck compileJava
```

For a full local build:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew build
```

For data generation work:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew runData
```

If Java 17 is preferred for Gradle runtime, fix or reinstall the local Java 17 JDK so `/usr/lib/jvm/java-17-openjdk/release` exists.
