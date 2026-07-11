# 实施计划

## 步骤

1. 新增独立 `gtlcore.wildcard.mixin.json` 和 mixin 配置插件，确保 wildcard 相关 mixin 仅在 `wildcard_pattern` 存在时应用。
2. 新增共享保存 helper，集中保证 `component.onSave()` 先于 NBT 序列化。
3. 新增过滤器配置页保存顺序修复 mixin。
4. 新增 IN/OUT 配置页保存顺序修复 mixin。
5. 将新增 mixin 配置注册到 `build.gradle`。
6. 运行最小编译验证。

## 验证

- `./gradlew compileJava`

## 验证结果

- `./gradlew spotlessCheck`：通过。
- `env JAVA_HOME=/usr/lib/jvm/java-21-openjdk PATH=/usr/lib/jvm/java-21-openjdk/bin:... ./gradlew compileJava`：通过。
- `env JAVA_HOME=/usr/lib/jvm/java-21-openjdk PATH=/usr/lib/jvm/java-21-openjdk/bin:... ./gradlew processResources`：通过。
- `python3 -m json.tool src/main/resources/gtlcore.mixin.json`：通过。
- `python3 -m json.tool src/main/resources/gtlcore.wildcard.mixin.json`：通过。
- `python3 ./.trellis/scripts/task.py validate 06-19-wildcard-property-filter-save`：通过。
- 2026-07-11 服务器实测：用户确认最终 JAR 已部署使用，属性过滤保存修复效果符合预期。

## 风险点

- 目标方法是外部 mod 私有方法，签名变动会导致注入失败；当前依赖版本固定为 `wildcard_pattern-0.1.2-gtl`。
- 新增 mixin 插件只挂在 `gtlcore.wildcard.mixin.json`，不影响主 mixin 配置；仍需保持 `wildcard_pattern` 缺失时返回 `false`。
