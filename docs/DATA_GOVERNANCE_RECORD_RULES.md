# 数据治理结构化字段规则（兼容组件1.2.1）

`com.hm.governance.nifi.JsonRecordTransform`用结构化Operations数组处理JSON对象或对象数组。无需输入脚本、环境表达式、文件路径或网络地址；字符串中的替换目标只作为普通数据。

## 通用契约

- 1至64条规则，规则原文最多32KiB；输入最多256KiB/1000条，平台入口当前进一步限制为100条。
- 结果最多2MiB。深度、值数量、字段长度均有边界；重复JSON键、坏UTF-8和非法规则整批失败。
- 保留输入对象/数组形状及无关行字段，按原次序输出。任一保留记录失败时不产生部分success；全部过滤或空数组走empty。
- 所有路径均为JSON Pointer。`set`和`broadcast`只允许创建最后一个对象字段，不自动补造中间容器或数组元素。
- `broadcast`的`outerIndex:true`将完整路径token`$index`替换为外层数组索引，明确表达原违法脚本的`target[i]`行为；越界不会自动改成`target[0]`。

| op | 用途 |
| --- | --- |
| parse | 将行内JSON字符串解析到临时document，临时对象不进入输出 |
| get | 从行或document提取字段；缺失默认FAIL，也可显式NULL |
| constant / copy / remove | 常量、字段复制、移除明确的根字段 |
| trim / replace / substring | 空白裁剪、FIRST或ALL字面替换、有界子串 |
| set / broadcast | 写回document字段或广播到数组元素 |
| serialize | 将临时document写回JSON字符串列 |
| dateGate | 严格日期解析和大于阈值比较；明确时区与无效值策略 |
| filter | 标量EQ/NE/IS_NULL/IS_NOT_NULL，keep控制保留或剔除 |

例如`[{"op":"trim","input":"/message","output":"message","mode":"BOTH"}]`可用于处理两端空白。过滤不做隐式String/Boolean互转；数字比较按十进制值执行。

## 显式兼容策略

默认`parse.numberMode=EXACT`、`get.type=STRING`、`missing=FAIL`保持严格行为。迁移规则按经过实际原类验证的阶段选择策略，不能用一套数字转换覆盖所有节点。

| 策略 | 行为与范围 |
| --- | --- |
| EXACT | 保持原通用精确数值解析；不会隐式改成JavaScript Double |
| ECMASCRIPT_DOUBLE | 复现所给工具包消息改写的双精度及序列化行为，可能舍入大整数；数字对象键保持该原包实际插入次序 |
| KETTLE_JSON | 复现原JsonInput使用的JsonSmart数值token策略，区分整数、短小数Double、长小数BigDecimal及负零 |
| get.type=KETTLE_STRING | 原生String不变；标量/容器转String，容器保留插入键序并复现斜线与行分隔字符转义 |
| parse.onEmpty=NULL | 仅KETTLE_JSON显式兼容空源、空白/BOM及primitive文档，配合missing=NULL；坏JSON仍失败 |

两种兼容数字模式均限制数字token最多1024字符、指数最多6位，不以无界BigDecimal展开换取兼容。原输入Java null在迁移图中先用非空过滤处理。

原JsonInput虽配置trim BOTH，实际没有将trim类型传给字段转换器，因此提取阶段应使用NONE。原TextFileOutput的5列trim BOTH则实际生效，应作为独立`textOutputTrim`阶段放在空base64常量之后、文本编码之前。

## 已验证与待验证

1.2.1组件78项测试通过。原JsonInput的37个基础及44个边界向量，共81个，状态、行数和所有字段逐字一致；消息改写24个向量的状态及所有成功输出字段、`k_message`原字符串一致。真正新NiFiMock的trim→Writer链与原TextFileOutput的9个文件向量逐字节一致。

日期仍有3类已知差异：原包Date.parse接受溢出日期、缺秒、只有日期，新严格dateGate不接受。不得把严格日期模式作为全部原日期语法的等价替代。

上述为原类与合成数据对照，不是生产全链验收。现场实际类加载、Kafka位点、实时数据库变更、原文件名时钟及接收端协议仍需分别核对。完整原类离线报告和原始规则仅保存在任务私有运行目录，源仓库不存放原连接凭据或内部端点。
