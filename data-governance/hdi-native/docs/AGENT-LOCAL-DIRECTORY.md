# 原 Agent 的本机注册与地址目录适配

原 Agent 自己注册服务器、报告硬件/心跳并部署原实例。平台不直接写 EtlServer 在线记录，不伪造心跳、ready、许可或认证成功。本目录程序只提供本任务本机部署地址，原 JAR、登记协议和业务检查保持原样。

## 已核验的原启动链

原 bin/agent/dEtlv2-agent.jar 0.1.16，主类 com.hikvision.idatafusion.agent.EtlAgentApplication。原 GlobalEnvironment 按顺序执行：

1. AgentConfigService（MIN_VALUE）：读取 componentPath/conf/config.properties，查询服务目录中的 detl/detl 地址。
2. AgentRegisterServiceImpl（MIN_VALUE+2）：以真实 Agent 的 agentNo、agentIp、installRootPath 调用 POST service/rs/v1/remote/agent/register，使用原返回 serverId。
3. ServerStatusCheck（MIN_VALUE+8）：调用原 globalEnvironmentStatus，未 ready 就等待。
4. 原 ZK、插件、实例初始化与定时心跳继续执行。

所以 Agent 可先完成真实注册，再等管理端 ready，以解除管理端 AgentServiceImpl.initEnvironment 等待 enabled/autoregister EtlServer 的初始化条件。ServerManageController 没有新增服务器的公开 add/install 方法；原 AgentController 才提供 register/install 等实际 Agent API。

agent.auto-register=true 时，getServerId 只读取真实注册返回值；server.id 工厂样例值不参与该分支。建议新环境显式 server.id=0，注册失败绝不能把 0 或工厂 3 当已注册 ID。auto-register=false 不会注册，原后台 register 收到 autoRegister=false 也直接返回未注册值，不能用关闭自动注册绕过初始化。

## 最小目录适配

运行源码工具：

    python3 tools/data-governance/hdi_agent_directory.py \
      --detl-url http://127.0.0.1:14312/detl --listen-port 19910

只绑定 IPv4 127.0.0.1，只有 GET /svrService/v1/service/detl/detl。首行 stdout JSON 报告实际目录监听地址；SIGTERM/SIGINT 正常关闭。不修改系统服务或原程序。

响应严格使用原 ServiceInfoDto 读取的字段：

    {"code":"0","data":{"configs":[{"key":"@context","value":"/detl"}],
      "address":[{"key":"webPort","ip":"127.0.0.1","port":14312,"netprotocol":"http"}]}}

14312 是配置的真实原 DETL 监听端口，不能指向假服务。程序只探测该 TCP 端口是否监听；TCP 可达不代表原环境 ready、许可有效或用户认证成功。原环境状态仍由原 globalEnvironmentStatus 返回，false 不改写。无监听则目录返回 503，不输出虚构地址成功。

该地址目录是本机非敏感部署元数据接口，不承担 BIC 的身份验证。它不生成、校验、转发、回显或记录 SDK Token，也不返回 auth/ready/online/license 字段。原 AgentConfigService.generateToken 使用原 Authentication.identifyApply（失败返回 null）；随后真实注册的 ServerClient 使用原 BicUtils→Authentication.identifyApplyEx。后者保留原 Cloader 和原材料加载，实际 Token 仍必须通过原 DETL 验证。若 SDK 材料/身份中心阻止签发或验签，保留原错误，不将目录查询成功当作认证成功。

本工具拒绝非回环地址、重绑定 Host、浏览器 Origin/Fetch-Metadata 请求、其他路径、查询串、请求体和写方法。它不代理任何任意 URL，也不实现原注册/心跳路由。

## 本任务新安装配置

只补新环境 componentPath/conf/config.properties 的这些键；不要复制源服务器的实值，不覆盖现有本机私有证书配置：

    agent.@instanceList=agent.1
    agent.1.@ip=127.0.0.1
    agent.1.instanceExtVmArgs=
    @agent.agentNo=<本任务持久的新UUID，已有合法同部署值则保留>
    @bic.serviceDirectory.protocol=http
    @bic.serviceDirectory.ip=127.0.0.1
    @bic.serviceDirectory.port=19910
    @bic.serviceDirectory.context=

context 必须显式为空，缺失会被原 StringBuilder 拼成 null。自动模式下原 AgentConfigService 无条件查询上述目录并写内部 detl.endpoint，单独覆盖 etl-server.endpoint 不够。

必要原件为 bin/agent 下原 JAR、lib、ext/normal、config，以及 application.properties；使用独立覆盖将旧工厂默认 endpoint/server.id 换为本机失效占位/实际地址。原 classpath 可以按以下次序启动，不执行原安装 shell：

    <Java11>/bin/java -Dspring.profiles.active=prod \
      -DcomponentPath=<component> -Duser.dir=<component>/data/agent \
      -Dagent.auto-register=true -Dserver.id=0 \
      -Detl-server.endpoint=http://127.0.0.1:14312/detl \
      -Dsystemd.pid.file=<component>/data/agent/agent-owned.pid \
      -Dspring.pid.file=<component>/data/agent/current.pid \
      -cp '<component>/bin/agent/dEtlv2-agent.jar:<component>/bin/agent/:<component>/bin/agent/config/:<component>/bin/agent/lib/*:<component>/bin/agent/ext/normal/*' \
      com.hikvision.idatafusion.agent.EtlAgentApplication

保留原 SDK 的本机证书与 native library 配置。若下一真实错误是 Token 不可用、验签拒绝或缺原依赖，应记录该错误再解决，不能关闭检查。

## 实例部署与停止边界

原 Agent 的 InstanceControlService 会读取 instance.exec.root-path/adapter.properties；需要原 bin/instance 的 core/lib、adapter.properties、conf 和 bin/etl.sh。它同步实际算子、复制原 conf、写实例配置、更新原证书，再以 ETL_USER_DIR 指向独立实例目录调用原 sh bin/etl.sh stop/start。实例记录应由原 instanceManage.addInstance 创建，使用真实注册的 serverId、工作区和实际目录/端口，由原 Agent 真正部署及回报状态。

Ubuntu 的 sh 可能是 dash，而原 etl.sh 使用 Bash 语法。本任务 VM 可以只在 Agent 进程 PATH 中提供私有 sh→/bin/bash 兼容入口，不更改系统 /bin/sh，不修改原脚本。HIK_JDK_PATH/JAVA_HOME 指向已验证 Java11，内存参数来自原实例配置，不使用原脚本默认 8GiB。禁止传 DEBUG，避免原脚本开放无认证的调试/JMX端口。

原 postinstall/pre-start 含全局进程查杀、chmod777、systemctl 操作，不能直接运行。原实例 stopall 也不是平台通用停止入口。验收环境仅操作本任务隔离 VM，停止由持有真实 owned 进程记录的 VM 控制器执行。

## 验证

    python3 -m unittest discover -s tools/data-governance -p 'test_hdi_agent_directory.py' -v

5 项测试通过：真实回环地址契约、目标端口关闭时失败、拒绝远端/歧义地址、精确只读路径及拒绝浏览器/Host 重绑定。测试监听端口是合成地址契约探针，不代表原 Agent 已注册。真实 Agent 注册、原 Token 验证、原心跳、EtlServer ID、原实例启动和最终原 UI 任务运行须在隔离 VM 另行记录。
