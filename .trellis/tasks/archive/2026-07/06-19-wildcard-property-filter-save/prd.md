# 修复通配符样板属性过滤保存错误

## Goal

修复通配符样板配置页保存顺序错误，确保用户在过滤器或输入输出配置中选择的当前 UI 值会在同一次保存中写入样板 NBT。

## Requirements

- 修复 `wildcard_pattern` 过滤器配置页：选择 `blast_alloy`、`fluid`、`dust` 等 property 后，点击保存并重新打开时必须保持所选 property。
- 同步修复 `wildcard_pattern` IN/OUT 配置页的同类保存顺序问题，避免 tag/simple IO 组件当前 UI 值需要保存两次才生效。
- 修复必须保持 `wildcard_pattern` 仍为可选依赖；没有安装该 mod 时不应因为新增 mixin 导致启动失败。
- 不修改外部 jar，不改变通配符样板展开规则、材料过滤语义或 GTLCore 现有样板缓存逻辑。

## Acceptance Criteria

- [x] 在过滤器配置页选择 `Property: blast_alloy` 并保存后，重新打开显示仍为 `blast_alloy`，不会回退为 `rotor`。
- [x] 同一次保存即可持久化当前 UI 状态，不需要重复点击保存。
- [x] `wildcard_pattern` 未加载时，新增 mixin 不会应用到缺失目标类。
- [x] Java 编译或等价最小验证通过。

## Notes

- 已确认外部 jar 中 `WildcardFilterFancyConfigurator.save()` 和 `WildcardIOFancyConfigurator.save()` 当前顺序为先序列化组件，再调用组件 `onSave()`；这会导致当前 UI 值本次保存未写入。
