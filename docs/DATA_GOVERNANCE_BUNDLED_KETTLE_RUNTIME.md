# 平台随附 Kettle 本机运行包

该模式复用已有原 Kettle 执行桥和原海康 Kettle JAR。平台后台作为父进程启动本机 Python broker，再为每次预览/运行启动隔离的 Java 17 子进程。无需10服务器、HDI、NiFi或新增常驻中间件。它不是 HDI DETL 任务/算子的直接迁移。

第一版执行网络为 deny-all，适用于上传文件/合成输入 → 字段处理/脚本/排序等原节点 → 本次运行目录中的文件输出。数据库、Kafka、FTP 等网络节点仍能出现在原插件目录中，但该模式不授予网络连接；不能把目录中出现的插件全部视为本模式业务支持清单。原独立 worker 的显式网络策略和 Linux Docker 路径保持原行为。

## 打包

需要已安装的 JDK 17 和 Python 3.9+。运行时只需要 Java 17，无需 javac。

```sh
python3 tools/data-governance/package_kettle_runtime.py \
  --source-runtime /absolute/verified-kettle-runtime \
  --java-home /absolute/jdk17 \
  --output /absolute/delivery/kettle-runtime
```

也可将 `--source-runtime` 替换为明确原包 `--archive /absolute/kettle6.1.3.0.zip`。原包先使用已有 `prepare` 提取原 JAR；既有 runtime 按其 manifest 的 libraries/runtimeExtensions 摘要及有序 classpath 验证。随后从当前版本的 Java worker 源码重新编译，防止携带旧 classes。输出目录必须不存在，不覆盖已有交付物。

包内包含 `lib/`、`classes/`、三个运行脚本和 `bundle-manifest.json`。manifest 使用相对路径和 SHA256，保持原 classpath 顺序；不包含原 token、数据库凭据、业务任务、输入、运行记录或源绝对路径。完整目录可离线搬到其他安装位置。发布时应额外记录 manifest SHA256并通过可信交付渠道分发；清单校验用于文件一致性，不能替代发布来源认证。

## 父进程契约

```sh
python3 /absolute/kettle-runtime/bundled_kettle_runtime.py \
  --bundle /absolute/kettle-runtime \
  --runtime /absolute/private/kettle-state \
  --java-home /absolute/java17 \
  --port 0 --timeout 120
```

- 父进程保持 stdin 打开；stdin EOF 或 SIGTERM/SIGINT触发停止接收新任务并受控停止当前 owned 子运行。
- stdout 第一行是 `{"url":"http://127.0.0.1:<随机端口>","engine":"original-kettle","sandbox":"macos-seatbelt"}` 或 `linux-bubblewrap`。诊断写 stderr，token不输出。
- `.broker-token` 在 private runtime 下创建/复用，0600，供父进程读取；HTTP只监听127.0.0.1，拒绝浏览器Origin，保持原 bearer校验与REST协议。
- runtime 为当前用户所有的0700目录，不接受符号链接或未归属的非空目录。允许父进程预先创建0600 `.platform-owner.lock` 和 `runtime.log`；Python另持 `.bundle.lock` 防止双 broker。
- 所有权、运行意图、nonce和结果持久化沿用原worker。关闭只操作当前持有的Popen/控制管道；恢复记录不会按保存PID发信号、不会自动重放未知运行。
- 关闭时先STOP，宽限后HALT，最长约12秒等待；未确认的运行保持RECOVERY_REQUIRED，broker退出码为1并保留 `.shutdown-unconfirmed.json`，阻止自动重启直到完成恢复核查；不能把broker退出视为业务已停止。父进程需要留出至少20秒关闭窗口。
- 安装时在 private runtime 内建立原有绝对路径 manifest；后续每次启动重新核验。不同包摘要需选择新的private runtime目录，不能覆盖旧运行所引用的引擎。升级前停写/停调度、停止旧父进程并保留旧runtime；回退用原包和原runtime启动。平台业务定义与该执行状态分别管理。

## 隔离条件

macOS继续使用原 Seatbelt配置，只允许引擎库、JRE和本次operation文件访问，禁止网络。

Linux使用已安装的 bubblewrap本机进程，不需要Docker daemon。`--unshare-all`隔离网络/PID/用户等命名空间，清空环境、移除capabilities、只读绑定JRE/原库/类/系统动态库，本次operation目录可写；broker token和历史目录不挂载。启动先在同一约束下执行 `java -version`探测。缺bwrap、用户命名空间受限或探测失败时拒绝启动；没有回退到未隔离Java或Docker。

Linux实际可用性取决于目标内核和bubblewrap配置，必须在目标Linux上完成真实文件流程和越界/断网验证再作为该平台已验收能力。本次macOS环境只能验证命令约束与拒绝路径，不能声称Linux实机通过。

## 验证

```sh
python3 -m unittest discover -s tools/data-governance -p test_bundled_kettle_runtime.py -v
KETTLE_BUNDLE=/absolute/delivery/kettle-runtime \
  KETTLE_JAVA_HOME=/absolute/java17 \
  python3 -m unittest discover -s tools/data-governance -p test_bundled_kettle_runtime.py -v
```

不设置KETTLE_BUNDLE时，仅测试清单/路径/摘要/运行所有权/隔离拒绝等，无原引擎执行。设置后额外真实复制并重定位bundle，启动随机端口broker，执行CSV → 原JavaScript → 文件输出、节点预览，并在慢流程中关闭stdin检查STOPPED及持久化结果。全程使用独立合成输入输出。
