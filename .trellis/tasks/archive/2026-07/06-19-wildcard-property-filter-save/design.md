# 技术设计

## 边界

本修复只在 GTLCore 内增加 mixin 兼容补丁，不修改 `libs/wildcard_pattern-0.1.2-gtl.jar`。

## 根因

`WildcardFilterFancyConfigurator.save()` 当前流程是：

1. 从 `componentList` 读取组件列表。
2. 调用 `logic.setFilterComponents(components)` 序列化写入样板 NBT。
3. 再对每个组件调用 `onSave()`。
4. 调用 `onSave.accept(stack)` 同步物品栈。

`PropertyFilterComponent.onSave()` 才会从 `SelectorWidget.getValue()` 读取用户刚选的 property。因此原流程会把旧值写入 NBT，新的 UI 值只留在组件对象中，造成用户看到 `blast_alloy`，保存重开后变回旧值或示例材料候选列表第一个值。

`WildcardIOFancyConfigurator.save()` 有同样顺序问题。

## 方案

新增两个 mixin：

- `WildcardFilterFancyConfiguratorMixin`
- `WildcardIOFancyConfiguratorMixin`

两个 mixin 都调用 `WildcardConfiguratorSaveHelper.saveCurrentStateFirst(...)`，集中表达保存顺序契约：

1. 读取组件列表。
2. 先调用每个组件的 `onSave()`，把 UI 当前值同步到组件对象。
3. 调用 `logic.setFilterComponents(...)` 或 `logic.setIOComponents(...)` 写入样板 NBT。
4. 调用原 `onSave.accept(stack)` 触发外层保存回调。
5. `ci.cancel()` 跳过原错误顺序。

## 可选依赖处理

`wildcard_pattern` 是可选依赖。新增独立的 `gtlcore.wildcard.mixin.json`，并只在该配置上挂载 `WildcardMixinPlugin`。该插件检查 `LoadingModList` 或 fallback classpath marker，只有 `wildcard_pattern` 存在时才应用 wildcard mixin。

主 `gtlcore.mixin.json` 不挂载全局 plugin，避免所有现有 mixin 经过额外可选依赖判断。

## 回滚

删除新增 wildcard mixin、`WildcardConfiguratorSaveHelper`、`WildcardMixinPlugin`、`gtlcore.wildcard.mixin.json`，并从 `build.gradle` 的 `mixinConfigs` 移除该配置即可回滚。
