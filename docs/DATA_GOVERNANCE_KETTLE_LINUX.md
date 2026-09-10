# 原 Kettle worker 的 Linux 容器适配

当前交付为独立适配代码、离线命令计划、mock 测试和待审核的真实 Linux smoke 入口。**本轮没有调用 Docker daemon、没有启动容器、没有修改防火墙，也没有操作 250。不能将 mock 或命令计划写成 Linux 实机验收通过。只有根任务后续审核并完成目标主机验收后，才可用于 250。**

## 入口与目录

`tools/data-governance/kettle_linux_runtime.py` 提供 `LinuxRuntime`，不修改现有 worker 主入口。集成方通过：

```python
config = Config.load(private_config_file)
runtime = LinuxRuntime(config)
plan = runtime.plan(operation_dir, "run", preview_step="", row_limit=20)
handle = runtime.launch(operation_dir, "run", launch_id=launch_nonce, stderr=private_engine_log)
```

`launch_nonce` 与现 worker 协议一致，为 64 位十六进制。`handle` 提供文本 stdin/stdout、`poll()`、`wait(timeout)`、returncode，以及 `container_id`、`identity()`。原 stdout NDJSON 不重新解释成成功状态；容器退出码为 0 也不能代替 Java 的 terminal 事件。

停止既可通过 `handle.stdin.write("STOP\n")`，也可通过 `runtime.request_stop(run_id)` 写入 `STOP:<nonce>`。Java 参数包含 `-Dgovernance.worker.launch.id=<nonce>`，需使用支持 nonce 控制文件的 worker 实现。HALT 可沿原 stdin/nonce 文件协议发送；适配器另保留显式、经身份核对的强制容器终止能力，避免损坏的控制文件阻止管理方终止自己的容器。

**handle.pid 只是宿主上的 docker attach CLI PID，不是 Java 身份。** 后续 broker 接线必须用 containerId、labels、sourceHash 与本适配器的状态/恢复接口，不能复用“宿主 PID 命令包含 Java”的判断。当前主入口仍未自动切换为本适配器。

只有 3 个 host bind mount：

| 宿主目录 | 容器目录 | 权限 |
| --- | --- | --- |
| 已验证 worker/classes | `/opt/rynew/classes` | 只读 |
| 已验证 worker/lib | `/opt/rynew/lib` | 只读 |
| 该次 operation 目录 | `/work/<runId>` | 读写 |

Java 入口始终为原 `KettleWorker`，root 参数保留 `<runId>` 尾段，避免所有容器都用 `/work` 时派生出相同 Kafka 预览组。`HOME`、`KETTLE_HOME`、tmp 和 `${WORK_DIR}` 都在这个根下，`${WORK_DIR}` 为 `/work/<runId>/output`。原 inputFiles、子 KTR/KJB、产物路径保持一致。

控制器 journals 位于单独的 `state_root`，不挂进容器。检查目录重叠、路径分隔注入、符号链接、特殊文件及操作文件硬链接。旧 Mac manifest 的 JAR 顺序按受审文件名、SHA-256 重定位到 Linux 的 worker/lib；不会在 Linux 使用旧 `/Users/...` 绝对路径。

## 镜像与限制

`data-governance/kettle-worker/linux/image-lock.json` 固定官方 `eclipse-temurin` Java 17 JRE 的 **Linux amd64 manifest digest**。本轮通过官方 registry 的匿名只读接口核对 index、platform manifest 和 image config 的 SHA-256，并确认 Java 路径 `/opt/java/openjdk`；未拉取或运行该镜像。

该适配暂只支持锁定的 Linux amd64 镜像。目标 ARM64 或自定义 native library 镜像必须另选固定 digest 并审核，不能退回浮动 tag。

每个 run 独立 `docker create/start`：

- 非 root uid/gid，`--cap-drop=ALL`、`no-new-privileges:true`，只读 rootfs；不共享 host PID、IPC、网络、设备或 Docker socket。
- 默认 512 MiB 内存、等额 memory-swap、1 CPU、256 PID；JVM heap 为容器内存的一半，限制单文件为 64 MiB、nofile 为 1024。
- `/tmp` 是有界 noexec/nosuid/nodev tmpfs；Docker 本地日志有大小与数量限制。
- `--pull=never`，必须先独立准备固定镜像。adapter 不下载镜像、不安装包、不自动 sudo。
- 不继承 DOCKER_HOST/CONTEXT；CLI 固定连接本机 `/var/run/docker.sock`。JVM 注入环境变量显式清空。

单文件 ulimit 不等于整个 operation 目录的总量配额。正式主机应给 operation 存储配置总量配额和留存策略；本适配不更改宿主磁盘配额或全局策略。

## 默认断网及可信端点

默认 `endpoints=[]`，运行容器使用 **`--network none`**，不调用任何 firewall/network 创建命令。CSV→Script→File 不需要放开网络。validate、job-validate、capabilities 也保持断网。

仅受控配置文件中的显式 IPv4 和 TCP 端口能提供网络授权；原 XML、流程变量、previewStep 或命令参数不能增加授权。该文件必须由控制器用户持有，权限 600；`execution_enabled` 默认 false。

配置例如：

```json
"endpoints": [{"host": "192.0.2.10", "port": 5432}]
```

这是文档示例地址。实际主机须填从容器网络可达且经过批准的地址。

**容器中的 127.0.0.1 是容器自身，不是宿主。** 本适配拒绝将 loopback 放入可信端点，不自动改写 XML，也不声称 `host.docker.internal` 自动存在。宿主 PostgreSQL/FTP 等服务需要明确绑定可达地址、配置对应服务防火墙，再把真实 IPv4:port 加入受控策略。Kafka 的所有 advertised broker/ZooKeeper 地址和 FTP 被动端口也必须逐一核对，不开放任意动态目标。

有网络授权的 run 使用独立带标签的 bridge、固定私有 /29 和容器 IP。Docker 会检查与既有网络的地址冲突；工具不修改既有 bridge，不收编已存在的同名接口。

Java 启动顺序：

1. 校验原 JAR/class hash、固定镜像、私有配置；先用独占创建保存运行 journal，重复 runId 不能再创建容器。
2. 创建专属网络和容器。容器入口先运行可信 gate，尚未执行原 Java 或 XML。
3. 在现有 `DOCKER-USER` 中插入一个仅匹配**自有 bridge 接口和该容器源 IP**的跳转。专属链允许指定 IPv4:port 返回既有策略，其余 DROP；不绕过管理员已有策略。
4. 开始 gate，核对 container ID、labels、用户、镜像、挂载、能力、namespace、IP 与启动时间。
5. 核对容器 PID 的 cgroup，打开并固定该容器 netns FD；通过传递的 FD 安装容器内 OUTPUT IPv4 白名单与 IPv6 DROP 链。PID 回收不会把规则装到另一个进程的网络空间。
6. 两层规则都成功后，才原子写入 nonce 放行文件，让 gate exec 原 Java 入口。任何中间失败都不放行 Java。

容器网络命名空间规则用于封住 Docker 内置 DNS `127.0.0.11` 等不会按普通宿主 FORWARD 路径流转的通道。不能仅凭 DOCKER-USER 白名单就声称容器完全没有其他出网方式。[Docker firewall 主源](https://docs.docker.com/engine/network/firewall-iptables/)、[Docker networking 主源](https://docs.docker.com/engine/network/)

网络模式要求同一 Linux 主机上的 rootful Docker iptables backend、已有 DOCKER-USER hook，以及受审控制器具备安装专属 firewall/netns 规则的权限；不适用于 Docker Desktop 的跨 VM namespace 或未验收的 nftables/rootless 模式。缺少这些条件时应失败，不能降级到未隔离执行。

## Dry-run 与受审 Linux smoke

先按 `linux/config.example.json` 生成受控 600 配置，使用正确非 root uid/gid、已验证的 classes/lib 和专属 operations/journals 路径。运行目录和输入文件必须对容器 uid 可读写，原制品必须可读；工具不自动修改别人的 ownership。

纯命令计划，不调用 Docker：

```sh
python3 tools/data-governance/kettle_linux_runtime.py plan \
  --config /private/reviewed-linux.json --run-dir /owned/operations/run-id --operation run
```

输出包含 image inspect、network/container create、宿主及 netns firewall 的完整 argv、Java 参数、release gate 次序；container ID 与固定 netns FD 在运行时核验后代入，不应手工把占位符计划作为脚本执行。

准备本轮合成 CSV 和原 KTR，仅生成计划：

```sh
python3 data-governance/kettle-worker/linux/smoke.py --config /private/reviewed-linux.json
```

此命令只新增专属 smoke 目录和合成文件。**后续根任务审核、目标 Linux 前提满足后**，才能在该主机以配置的非 root 控制器用户显式加 `--execute`。真实 smoke 要求 endpoints 为空，会通过同一原 Java 协议验证 SUCCEEDED 和完整输出字节，并记录 container identity/sourceHash 后清理自己的退出容器。

本轮实际生成了针对现有原 JAR/classes 的离线 plan，`executed=false`；不代表上述 Linux smoke 已执行。

## 停止、清理与恢复

`status/stop/recover/cleanup --config ... --run-id ...` 都从私有 journal 读取原 container ID，并核对标签和容器配置。docker attach CLI 退出不等于 Java/容器退出；Popen-shaped handle 的 wait/poll 以容器状态为准。

清理必须先确认容器退出，且网络没有其他容器、宿主专属链没有未知规则。只移除该容器、逐条删除带本次 comment 的专属规则、删除自己的链与网络；不使用全局 flush、改默认策略、prune 或按名称猜测后批量删除。容器内 namespace 链随其 namespace 销毁。

每个已确认的 cleanup 步骤单独持久化；容器已删除但后续规则清理失败时，可继续专属 cleanup，不重复删除或重新运行。创建结果未确认、标签不符、出现未知规则、外部重启等情形保持 RECOVERY_REQUIRED，要求按精确 journal/labels 人工核查，不能自动重新创建执行。operation 文件与上层业务台账保留。

## 本轮验证边界

```sh
python3 data-governance/kettle-worker/linux/test_kettle_linux_runtime.py -v
sh -n data-governance/kettle-worker/linux/gate.sh
```

已通过 26 项纯 mock 测试：默认无网络、原协议透传、64-hex nonce、逐 run 目录映射、真实制品 hash 计划、配置/镜像/端点/argv/路径注入、无未隔离 fallback、策略失败 gate 不放行、未知容器/挂载/权限拒绝、既有规则保留、清理续做、attach 退出不冒充容器结束、恢复不重投。测试没有执行 Docker、iptables 或 nsenter。真正 Linux 上的内核 firewall 顺序、容器 UID 文件权限、CSV 执行和允许/拒绝端点的网络实测，仍是部署前必做验收。
