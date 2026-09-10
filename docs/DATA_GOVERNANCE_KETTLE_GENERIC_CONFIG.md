# 原生插件通用配置表单

`KettleGenericConfig.vue` 用于没有专用表单的原生插件。它直接修改父组件传入的 XML draft Element，递归显示标量、扩展属性、配置组和重复项；不生成另一份简化模型替换原 XML，不独立保存或执行任务。

## 接入契约

```vue
<KettleGenericConfig
  :element="draft"
  :template-xml="fromBase64(plugin.configurationTemplateXmlBase64 || plugin.defaultXmlBase64 || '')"
  :readonly="readonly"
  @change="changed"
/>
```

| 参数/事件 | 约定 |
| --- | --- |
| `element` | 正在编辑的原生 `Element`。由父编辑器持有草稿，取消/保存仍走原流程。 |
| `templateXml` | 已解码的 XML 字符串或模板 `Element`。支持插件配置片段、`<step>/<entry>` 外壳，以及 `<native>` 外壳；不直接接收 Base64。 |
| `readonly` | 禁止值修改、行增删和可选组新增，仍可展开查看。 |
| `change` | 修改实际 draft 后通知父级，事件不携带独立副本、不自动调用 API。描述/首屏渲染不触发事件。 |

父级可在插件没有专用 schema 时接入；本提交不修改现有 `KettleNodeConfig/Workbench`。名称、插件类型、GUI 位置、复制数等由父编辑器维护的根级字段不重复显示，未知字段仍留在原 DOM。

API Catalog 的 `configurationTemplateXmlBase64` 来自 worker 单独构造的原生配置骨架，`defaultXmlBase64` 保持原默认值。同步返回 `allocationApplied/allocationMethod/allocationArguments/allocationReason/configurationTemplateReadback`。原生 allocate 和 loadXML 回读只能证明模板来源，不能当成完整插件 schema 或执行验收。

## 编辑与保留规则

- `genericConfig.js` 只描述现有 DOM，读操作不增加 XML 节点或内部 ID。
- 标量修改保留原属性、CDATA 类型、注释和嵌套元素；复合内容不会被 `textContent=` 整体覆盖。
- 未映射参数显示原生名，常见字段使用中文标签。命名空间属性仍保持原命名空间。
- 已实际重复的同名元素、常见记录结构及原模板中的集合，使用 `el-table` 编辑；复杂行可以展开递归编辑。
- 空集合使用原生模板新增行；没有原生模板时，可以沿用已有行结构。新行去除身份 ID、秘密槽 ID 和秘密值，保留业务绑定及其他扩展结构。
- 删除只作用于明确选中的、属于当前集合的行；其他行、交错的扩展节点及注释不会随之重建。
- 未添加的原生可选组只有在用户点击添加后才进入 draft；没有模板时不编造字段结构。
- 已知布尔项按原值保留 `Y/N` 或 `true/false`。无法确定类型的参数使用字符串输入，不把字段名、前导零或长整数自动转成 JavaScript 数字。
- 原模板不等于完整校验规则。特殊 presence、枚举、依赖关系等仍优先使用专用表单和原引擎校验。

## 组件复用与验证

复用当前模块 `KettleValueInput`，及 Element Plus 的 Form、Collapse、Table、行展开、Button、Empty、Alert、Text、Space。交互参考现有节点编辑器和治理配置面板，普通表格行操作参考系统岗位页。组件仅增加 `min-width: 0` 的局部布局规则，不创建配色、动效或第二套控件；无 UI 例外项。

可在实际浏览器环境导入 `src/views/governance/kettle/__tests__/genericConfig.browser.js` 并调用 `runGenericConfigTests()`。16 项测试使用浏览器原生 DOM，所有数据均为合成数据，不调用引擎或网络。覆盖未知 XML、CDATA/注释、结构不同的重复行、精确删除、空集合模板、只读、可选组、混合文本、秘密克隆、命名空间、非法模板、字符串精度及 JavaScript 原型名称。

本轮以独立本地 Vue 页面进行了真实增删/输入/开关验证：首屏修改计数为 0，新增两行、编辑第一行、删除第二行后，只保留第一行；XML 中 `ascending=Y`、`date_format_lenient=true` 分别保持原生编码，未知 `future` 结构及 SQL CDATA 保留。只读区可编辑输入数为 0。组件沿用原 Element Plus 主题，不另设深色配色；本次交互在浅色完成，深色随主页面综合验收。

独立工作树只在验证期间临时引用集成树已有的 `KettleValueInput.vue` 和已安装依赖，验证后移除该临时组件链接，不提交或改写其源码。`npm run ui:guard`、`npm run verify:frontend` 和最终构建/Chrome 64 兼容性检查均用于本次交付；完整页面的接入仍由主工作树完成。
