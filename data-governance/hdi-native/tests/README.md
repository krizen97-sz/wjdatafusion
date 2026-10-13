# 原 DETL 执行模块合成验收

这是一组验收工具，不是通用编排后端或生产部署服务。它不改写原 ETL 算法，也不使用本项目另建的 Kettle/Java 数据流引擎。

工具装配原 DTO 和明确的内存元数据接口，调用原 ZookeeperServiceImpl.forcePushFlowDataToZk、DataGeneratorInputServiceImpl、FileOutputServiceImpl 生成流图；原 FlowControlMessageDto.scheduleOnce 生成一次执行消息；原 EtlInstance、原 dataGeneratorInput 和 fileOutput 完成实际执行。

执行前需要：

- 经过原文件摘要校验的 DETL 3.3.0 bundle；instance/ 原引擎/原库及两个插件，task-service/ 原配置生成器 JAR 和配套库。
- 原 ZooKeeper Java 库，默认 bundle/zookeeper/lib；可以 --zookeeper-home 指定现有原库。
- 原 instance/logback.xml；可以 --logback-config 指定原文件。
- 本机 macOS、clang、JDK（本次实际验证为17）和已安装 redis-server。工具不安装软件、不连接10服务器。
- 新的、位于 Git 仓库外的私有 state-dir，以及一段空闲的19000以上测试端口。

从仓库根运行：

    python3 tools/data-governance/hdi_original_execution_proof.py \
      --bundle /absolute/original-detl-bundle \
      --state-dir /absolute/private/new-detl-proof \
      --java-home /absolute/jdk17 \
      --redis-executable /absolute/redis-server \
      --port-base 19180

流程：

1. 校验原件，先验证仅本测试进程加载的 bind 适配库：回环连接成功、本机LAN地址连接失败、未授权端口被拒。
2. 生成只用于本次验收的 JKS、Redis密码、配置与命名空间，启动独立本机 Redis/ZK。
3. 在 Seatbelt 文件/网络限制下启动未修改的原 EtlInstance。mac测试使用进程级 bind 适配，把原代码的指定wildcard监听转到127.0.0.1；不改TLS/许可/JAR，也不改变系统配置。
4. 在同一原引擎进程中运行常量A三条，更新原DTO后运行常量B三条，最后提交 line 输出缺少 customExtension 的配置。
5. 校验两次真实文件内容不同、原状态分别finish；无效配置应由原插件拒绝、状态error且不生成新文件。
6. 只停止当前持有的子进程；不能确认停止时保留证据并失败，不按保存PID杀进程。结果位于state-dir/proof.json。

原Java17插件加载需要 --add-opens=java.base/java.net=ALL-UNNAMED，这是旧URLClassLoader反射的JVM兼容参数。原Java11/Linux发布需单独验收。mac bind适配只用于此合成验收，不能当作Linux发布隔离方案；Linux部署应使用受控网络命名空间等原生隔离方式。

state-dir包含合成输入输出、测试私钥和密码，必须保持私有，不能打入通用交付包或提交Git。Source文件没有生产凭据或原私钥。本工具不证明前端页面已经接通完整任务API、插件覆盖或真实业务迁移完成。
