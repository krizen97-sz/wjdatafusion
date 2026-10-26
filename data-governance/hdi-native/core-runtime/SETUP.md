# 本机安装准备

入口是 `tools/data-governance/hdi_core_setup.py`。它使用已有原程序 bundle 和本仓库控制桥源码，不启动服务、不执行任务、不安装系统软件。部署机需要 Linux x86_64、原 Core 使用的完整 JDK 11、Python 3、`unshare` 与 `ip`，并允许非特权 user/network/PID namespaces。原编译器和连接元数据工具可单独复用平台现有完整 JDK 17。准备和后续平台进程应使用同一个专用系统用户。

先通过已有 `hdi_native_bundle.py` 获得受审原 bundle，包含 Core、原编译器所需管理 JAR 及 ZooKeeper Java 库。本轮完整包选择十个原插件：`dataGeneratorInput`、`fieldMapping`、`fileInput`、`fileOutput`、`postgresqlInput`、`postgresqlOutput`、`kafkaInput`、`kafkaV2Output`、`rabbitmqInput`、`rabbitmqOutput`；SFTP使用原File插件。准备入口为兼容保留八插件最低预检，缺少RabbitMQ时不会在目录中广告其插件，旧三插件包不能用于本版本安装。这里的管理 JAR 只供原编译器调用，不启动原管理服务或 Agent。原包清单不允许混入运行配置、现场证书或许可文件。

MySQL另用 `tools/data-governance/hdi_mysql_adapter.py --bundle /原包路径 --java-home /JDK17路径 --output /尚不存在的平台适配目录` 构建两个已授权的最小方言子类（输出Java11兼容字节码）。原读写JAR不改；平台manifest单独记录源码、公共参数来源、原依赖与构建产物。审核后记录该manifest的SHA-256，不把这个平台包合并进原bundle或冒称厂商插件。

在该用户可写的现有目录下选择一个**尚不存在**的安装目录，并提供平台现有回环 Redis 的私有口令文件。口令文件必须由该用户拥有，权限不向组或其他用户开放；命令行传文件路径，不传口令值。Redis database 由部署者明确指定：

```sh
python3 tools/data-governance/hdi_core_setup.py \
  --install-root /srv/rynew-private/hdi-core \
  --bundle /srv/rynew-programs/original-detl-bundle \
  --java-home /usr/lib/jvm/java-11-openjdk-amd64 \
  --compiler-java-home /usr/lib/jvm/java-17-openjdk-amd64 \
  --platform-adapters /srv/rynew-programs/platform-mysql-adapters \
  --redis-password-file /srv/rynew-private/redis-password \
  --redis-port 6379 \
  --redis-database 2
```

`--java-home` 专供原 Core 和控制桥，仍必须是 Linux x86_64 JDK 11；不能因为平台使用17就把它替换成17。可选 `--compiler-java-home` 专供原任务编译器、原连接表单和元数据工具，接受完整 JDK 11或17（需要 `java`、`javac`）。上例显式采用已验收的 Core11 / Compiler17 分工。省略该选项时保持旧命令的默认行为，编译器也使用 `--java-home` 的11，不会自动寻找或切换系统 `JAVA_HOME`。输出的 `compilerArgv` 绑定独立编译器路径；worker配置及控制桥构建仍绑定Core路径。

`--platform-adapters` 为可选项；不启用MySQL时省略。提供时，入口校验独立包并将同一个`root/manifestSha256`写入`inputs.platformAdapters`和worker配置，生成的compilerArgv也包含`--platform-adapters`与`--platform-adapters-sha256`。平台另配置`platform-adapters-dir`及`platform-adapters-manifest-sha256`为同一目录/摘要。目录由部署管理员提供，首次准备前应对照审核记录确认摘要；自描述manifest不能充当独立信任来源。改变目录或摘要会触发已有安装冲突，不覆盖旧配置。

目录名和 Redis database 是示例，不能照搬为当前机器的既有路径。安装根必须在 Git 仓库之外；命令不创建或修改其上级目录。完整集成源码中原编译器脚本位于入口旁的 `hdi_original_compiler.py`；分开存放源码时可用 `--compiler-launcher /绝对路径/hdi_original_compiler.py` 指定同版本原编译器入口，并保留它相对于仓库根的 `data-governance/hdi-native/compiler/` 布局。该目录必须同时包含 `OriginalTaskCompiler.java` 和 `OriginalConnectionForms.java`；入口校验三份文件均存在、路径不经过符号链接，分别记录启动脚本、任务编译器和原连接表单适配源码的 SHA-256。缺少表单适配源码会在创建安装目录之前拒绝，不能把只复制主编译器的目录认定为完整源码。

成功输出一条 JSON，含 `status:PREPARED`、`workerArgv`、`compilerArgv` 和 `workerConfig`。将两个 **argv 数组**分别交平台 worker/compiler 配置，不拼接成 shell 字符串。安装根内会生成：

| 路径 | 用途 |
| --- | --- |
| `state/` | worker 独占运行状态、内置协调器数据、各次任务结果 |
| `compile/` | 原编译器临时工作目录 |
| `private/worker.json` | worker 配置，只引用原包、JDK 和 Redis 口令文件 |
| `private/setup-receipt.json` | 输入、源码、原包清单、控制桥及配置摘要 |
| `private/control-build.log` | 控制桥编译诊断，不包含 Redis 口令 |
| `control/` | 通过已有 `core-runtime/build.py` 编译的控制桥及构建证据 |

目录权限为 0700，配置和证据文件为 0600。配置默认使用随包 ZooKeeper 和正常 Java 模式；没有外部 ZooKeeper 地址、实验室 SSH 参数、测试身份或 TLS 材料。首次实际运行由 worker 为自身运行创建新证书。

重复原命令只验证既有 receipt、配置、控制桥摘要和目录权限，成功返回 `VERIFIED`，不重建、不覆盖运行状态。也可加 `--verify-only`；目录不存在时它会失败且不创建目录。未知目录、参数变化、配置变化、缺失或变化的控制桥均报冲突/准备失败，必须由部署者核查，入口没有强制覆盖或自动清理选项。编译失败会保留私有诊断和不完整目录，不会发布可用配置，也不会在重试时接管该目录。

独立编译器JDK配置使用 `receiptVersion: 3`。`inputs.javaHome` 绑定Core11，`inputs.compilerJavaHome` 记录有效的编译器JDK路径，即使省略新选项也会记录；`inputs.connectionFormsSourceSha256` 继续绑定 `OriginalConnectionForms.java`。改变任一JDK路径会产生配置冲突，不覆盖现有安装。旧版没有 receiptVersion、显式为1或2的安装不会被新入口自动升级，也不会猜测其未记录的编译器路径并标记 `VERIFIED`，而是返回 `LEGACY_SETUP_RECEIPT_REQUIRES_NEW_INSTALLATION`；未知格式版本返回 `UNSUPPORTED_SETUP_RECEIPT_VERSION`。旧目录、配置和运行状态全部保留。旧安装继续使用其原版本入口验证；新版本应使用完整配套源码在新的安装根准备，经过运行验收后再安排切换。此入口不迁移已有任务状态，也不修改旧 receipt 以绕过版本检查。

`PREPARED/VERIFIED` 仅表示安装准备完成：此入口未连接 Redis、未启动 ZooKeeper/Core，也未验收业务。平台接入后应读取 worker 的 `hello`，再执行真实测试，核对本次原生终态、CSV 内容与产物 SHA。停止和 70 秒共享关闭预算见 [PROTOCOL.md](PROTOCOL.md)。

回滚只需在平台停止对应 worker、确认 owned 子进程退出后撤销该 argv 配置；私有目录保留作为可恢复证据。本入口不会卸载 Redis、修改系统服务或更改开机启动项。


此前基础版本完成14项针对性测试，以及隔离 Linux x86_64 / Java 11 上的真实 `PREPARED → VERIFIED` 验收：原控制桥实际编译成功，重复验证前后所有安装文件的 SHA 和修改时间不变，未启动 Redis、ZooKeeper、Core 或业务任务。旧的不完整 bundle 缺少原编译器参数 schema 时，入口已实际拒绝，未创建安装目录。当前28项安装准备测试覆盖原连接表单源码摘要、Core11/Compiler17参数分离、默认路径兼容、JDK改变不覆盖、旧receipt拒绝、JRE/不支持版本拒绝以及独立平台适配包与编译器摘要绑定；这些测试不替代新版本在部署机上的实际安装与运行验收。
