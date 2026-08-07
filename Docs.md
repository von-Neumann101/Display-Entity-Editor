# Display Entity Editor 技术文档

## 项目环境

- Mod ID：`display_entity_editor`
- Minecraft：1.20.1
- Forge：47.4.10
- Java：17
- Mappings：Mojang Official

## 主要结构

- `ExampleMod`：注册编辑器物品、客户端配置和网络通道。
- `DisplayEditorItem`：处理方块表面放置，创建 Block、Item 或 Text Display。
- `ClientEvents`：处理选择、过滤组、快捷键、射线命中和 HUD。
- `DisplayEditorScreen`：提供 Numeric、Scale、Rotation 三种右下角编辑面板。
- `TypeSelectionScreen`：选择展示实体类型和具体展示内容。
- `DisplayTransform`：读写 transformation、计算中心补偿、视觉边界和射线交点。
- `ModNetwork`：处理客户端到服务端的编辑请求，以及 Selection Group 的持久化和同步。

## 数据与同步

实体 transformation 修改遵循：

`客户端面板 → C2S 数据包 → 服务端校验 → 服务端修改实体 → 游戏同步到客户端`

Selection Group 使用 Display Entity 的 UUID，不依赖临时 entity id。组数据保存在玩家持久数据中，登录和修改后由服务端同步给客户端。失效或已删除实体的 UUID 不会被直接解引用，因此不会导致崩溃。

## 变换处理

编辑数据统一保存为 Position、Rotation 和 Scale。Rotation 使用角度输入，并在应用时转换为 quaternion。

Display Entity 的原点不一定是视觉中心，因此应用缩放和旋转时会根据展示类型计算中心补偿。Numeric、Scale 和 Rotation 模式都调用同一套变换逻辑，保证视觉中心不随缩放或旋转漂移。

射线选择会先按当前 Selection Group 的 EXCLUDE/ONLY 规则过滤候选实体，再对剩余实体进行变换空间求交并选择最近命中目标。

## 客户端与服务端隔离

Screen、Minecraft 客户端实例、输入事件和渲染代码只位于 `client` 包及客户端事件订阅中。实体创建、变换修改、组数据写入和输入校验由服务端执行，Dedicated Server 不会加载 Screen 类。

## 配置与构建

Selection Group 数量位于：

`config/display_entity_editor-client.toml`

配置项为 `selectionGroupCount`，默认值为 9，允许范围为 1–64。

构建命令：

`.\gradlew.bat build`

输出文件位于 `build/libs/display_entity_editor-1.0.0.jar`。

## 改名兼容性

旧物品 ID `examplemod:display_entity_editor` 不再注册。升级后需要重新获取 `display_entity_editor:display_entity_editor`；已存在的原版 Display Entity 不受物品注册 ID 改名影响。
