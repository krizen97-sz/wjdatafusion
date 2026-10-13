# 原任务操作与对象授权契约

本表来自原 `dEtlv2.jar` 的 FlowController/FlowServiceImpl 字节码、原 mapper XML，以及原前端模块 40510/56086 的请求封装。它记录实际的参数位置，不是依据路径名称猜测权限。RYNEW 不改写原业务请求正文，不修改 JAR/算子算法。

## 已接入的现有任务入口

所有任务 ID 都必须是正十进制 Long。客户端先验证操作权限及输入形状，再调用原桥 `POST /detl/_platform/authorize`，成功后才发送下表原请求。查询参数 flowId 不能重复，原只接受 query 的 POST 不允许另带 body，避免表单参数覆盖已校验值。

| 实际方法 | 原路径（省略 /detl） | 主任务 ID 的实际位置 | 平台操作权限 |
| --- | --- | --- | --- |
| GET | /flow/getFlowById/{id} | PathVariable id | list |
| GET | /flow/getFlowVisualById/{id} | PathVariable id | list |
| GET | /mount/getMountFlowVisual | query flowId | list |
| GET | /flow/getRunningStatus | query flowId | list |
| POST | /flow/getFlowVisualState | query flowId；不是 GET | list |
| POST | /flow/findFlowExecRecordPage | query flowId；另有 pageNo/pageSize/filterData | list |
| POST | /flow/findFlowModuleExecRecordPage | query flowId；另有 nodeId/pageNo/pageSize/filterData | list |
| GET | /flow/startFlow/{flowId}、/flow/startFlowV2/{flowId} | PathVariable flowId | list + test |
| GET | /flow/stopFlow/{flowId}、/flow/stopFlowV2/{flowId} | PathVariable flowId | list + test |
| GET | /flow/lockFlow/{flowId}、/flow/unlockFlow/{flowId} | PathVariable flowId | list + edit |
| POST | /flow/debugFlow/{flowId} | PathVariable flowId；原方法名 debugFlowStart | list + test |
| POST | /flow/pushFlowDataToZK/{flowId} | PathVariable flowId | list + edit + test |

模块执行记录的第二个 ID 是 nodeId，不是 recordId。原 FlowServiceImpl.findEtlFlowModuleExecRecordPage 将 flowId 和 nodeId 传给 findRunModule，原 EtlModuleMonitorMapper.listModuleMonitor 的 SQL 最终含 `where e.flow_id = #{flowId}`，且模块监控 join 也关联 flow_id；不能省去 flowId 归属授权。

原 getRunningStatus 自身还调用 workspaceService.checkPermission(flow.groupId, session.userId)。getFlowById/getFlowVisualById 与旁路读取不能只凭“只读”或“数字 ID”视为已授权，平台的固定工作区归属校验不可省略。

## 保存/新建/发布：原 DTO 授权成功后原样转发

原方法是 `POST /flow/saveFlowVisual/save` 或 `/push`，请求体为 FlowVisualVo，更新主 ID 位于 `flowVo.id`，空值/缺省值在原逻辑中表示新建。顶层 `flowId` 也存在，后续授权需要确保实际解码后的 ID 一致，不能只检查可伪造的外层 ID。

原配置 `base64.flow-visual.enable` 默认 true。原前端先调用 `GET /system/environment/getBase64VisualEnable` 读取真实布尔值，再在开启时发送：

    {visualBase64: btoa(encodeURIComponent(JSON.stringify(flowVisualVo)))}

原 FlowController 使用原 ObjectUtils.getDecodeFlowVisual 解码，之后无论新旧任务都执行 `flowVo.setGroupId(Workspace header)`。因此必填 Workspace 必须先匹配可信映射；RYNEW 不应递归替换字段来猜测业务语义。

原 FlowServiceImpl.upsertFlow 对新建记录设置 session.userId/tenantCode，projectCode 仅空时才取 session；更新路径先 BeanUtils.copyProperties 再 updateByPrimaryKeySelective。原保存授权必须验证解码后的实际 flowVo.id、显式 projectCode/userId/tenantCode、顶层 flowId、节点/流/实例等引用的所属范围，以及原参数模式。不能仅调用一次主 flowId 授权就宣称整个复杂 DTO 已安全。

网关对两个明确保存路径调用独立 `POST /detl/_platform/authorize-save`，请求严格为服务端生成的 subject、mode（save/push）和 nativeBodyBase64（原 HTTP JSON body 原字节的标准 Base64）。签名绑定该完整路径与整个 envelope。原适配器按相同原配置调用原 ObjectUtils/DTO 解码，并只读核对主任务和旧记录；只有原返回 `code:"0",data.authorized:true` 才继续发送原 body，不重编码、不递归改写。缺失或不可用的原授权入口会拒绝后续保存。

保存请求限定 UTF-8 application/json、无 Content-Encoding、最大 8MiB，Workspace 必须匹配后端签发工作区，项目头固定为映射主项目。save 需要 list+edit，push 需要 list+edit+test。明确不支持的图/数据源引用返回 422 固定说明，对象/身份越权返回 403，其余授权服务失败不混作成功。

旧 PK 精确位置为：

- streamVoList[*].id（stream）
- streamVoList[*].streamNodeVoList[*].stepId、itemId（step/item）
- streamVoList[*].streamNodeVoList[*].streamNodeModuleVoList[*].moduleId（module）
- nodes.<graphKey>.dstTableVo.id（dstTable）

原实现这些非空 PK 会走 updateByPrimaryKey，必须核验属于目标 flow；新建不接受这些旧 PK。0/负数也没有原“新建占位”语义，不能放行。NodeVo.id、streamId、streamNode.id 是画布自然键，不能混作上述数据库 PK。字段/参数批量 insert 的 SQL 不含客户端 id 列，不进行无依据的全树 ID 禁用。

原适配器的首个验收范围为数据生成→本地文件及空草稿；其他节点/外部源/lookup/SQL 引用须逐项核验后扩展，不伪造可用结果。原实例及算子绑定仍由真实原 DAO 和原业务验证。保存授权测试和网关字节测试不代替实际数据库写入及运行验收。

## 日志及其他暂未开放入口

原前端还使用以下接口，但其完整 DTO/关联对象权限尚未闭合，当前网关全部拒绝：

- POST /flowlog/getTaskLogByAgent：query limit/offset，正文携带日志请求 DTO。
- POST /flowlog/getFlowExecNode、/flowlog/packageTaskLog：正文 DTO。
- GET /flowlog/packageLastestLog：query flowId；POST /flowlog/packageLog：query execId/flowId，打包包含副作用且 execId 也需归属核查。
- saveFlowStartConfig、upsertSchedule、批量运行、复制、删除、历史版本、flowtemplate 和 flownode 的其余操作。

这些接口不会因名称含 get/find，或落在 flow/flowlog 命名空间就放行。原错误响应仍保留原 HTTP 状态与原协议正文，身份/归属桥错误只返回简短错误信息。

## 验证边界

网关和签名客户端测试使用隔离本机合成 HTTP 服务，证明授权请求的原始签名、字符串 ID、禁止重定向、归属拒绝时任务端零调用、重复参数/表单覆盖拒绝和精确操作白名单。原桥另使用真实 SDK Mapper 接口验证只读查询；完整数据库、原控制器、实际运行实例和浏览器闭环需要联合验收，不能将上述单测替代真实运行结果。
