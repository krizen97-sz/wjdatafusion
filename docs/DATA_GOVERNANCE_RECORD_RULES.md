# 数据治理结构化字段规则（兼容组件1.2.3）

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

1.2.3组件92项测试通过。原JsonInput的37个基础及44个边界向量，共81个，状态、行数和所有字段逐字一致；消息改写24个向量的状态及所有成功输出字段、`k_message`原字符串一致。真正新NiFiMock的trim→Writer链与原TextFileOutput的9个文件向量逐字节一致。

日期新增显式`dateGate.parser=RHINO_DATE`：固定调用官方Rhino的原生Date.parse，不接受或执行用户脚本；参数只为String，Java桥接移除且Scope不暴露。此模式禁止pattern，zone须匹配启动时引擎JVM时区，漂移或不匹配会拒绝。UTC/Asia/Shanghai各44个日期向量、原12个脚本日期向量及并发验证通过，原3类宽松解析差异已在该显式模式覆盖；默认STRICT不变。官方库与附件JAR字节不同，仅所用原生函数和受控向量进行了对照。

上述为原类与合成数据对照，不是生产全链验收。现场实际类加载、Kafka位点、实时数据库变更、原文件名时钟及接收端协议仍需分别核对。完整原类离线报告和原始规则仅保存在任务私有运行目录，源仓库不存放原连接凭据或内部端点。

## 可复现打包

曾定位到旧target目录使多个版本依赖进入同一NAR，metadata版本正确也可能加载旧类。新NAR模块在initialize清理自身生成目录；标准构建工具`build_compatibility.py`执行clean verify后，用`verify_nar.py`核对两个自有依赖的版本及重复类。还实际注入旧JAR到staging后执行普通package，验证旧JAR不会再进入产物。

新建自有节点必须使用当前配套版本；冻结任务仍引用其原精确bundle。已安装的旧包保持原样供既有记录核查，不再作为新建组件的候选。安装必须通过`install_compatibility.py`的包内验证、引擎空闲检查和备份，不能覆盖同一版本号。

固定原生日期依赖的版本与许可证参见[官方Maven POM](https://repo.maven.apache.org/maven2/org/mozilla/rhino/1.7R3/rhino-1.7R3.pom)。
