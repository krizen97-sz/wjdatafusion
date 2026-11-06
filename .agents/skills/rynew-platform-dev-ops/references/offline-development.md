# Windows x64 离线开发

## 固定范围和工具链

本轮准备平台主Java工程、Vue3主前端、Vue2遗留前端、独立NiFi兼容工程，以及官方文档与本地知识索引。不修改业务依赖版本，不把macOS的node_modules复制到Windows。

| 范围 | 离线输入/工具 | 说明 |
| --- | --- | --- |
| 平台七个子模块及父POM | Maven3.6.3、JDK17、专用本地repository | 从clean开始验证插件、编译、测试依赖和Boot打包，不只查dependency目录 |
| NiFi compatibility独立reactor | 同一repository、JDK21 | nifi-processors声明release21，JDK17不能编译；不改变平台Java17基线 |
| 主前端 | Node24.13.1、npm11.12.1、冻结Vue3锁、npm-cache | 含esbuild、Rollup、Parcel watcher、Sass embedded的Windows x64包 |
| 遗留前端 | 同一Node/npm、独立Vue2锁、同一缓存 | 安装加legacy-peer-deps；Webpack4构建使用openssl-legacy-provider，仅限该进程 |
| Git与源码 | MinGit Windows x64、精确提交的源码快照仓库 | UI Guard和业务测试依赖Git及外层目录，不能只拷frontend一个目录 |
| 技能检查工具 | Windows Python3.14.10嵌入包 | 仅标准库检查/归档工具；不把可选Linux执行器或其pip环境算成已安装 |

Node压缩包内自带的npm是11.8.0，迁移包另带已验证的npm11.12.1，由本会话shim调用；不要无意切回另一npm。JDK/Node/Git按官方SHA-256，Maven分发包按官方SHA-512核对，原始LICENSE保留在各分发包中。

源码随附的厂商SDK演示POM另声明Java6与旧source/javadoc插件；本轮为其补齐解析依赖并保留原SDK坐标。它不属于平台reactor，不在Java17/21的All构建流程中冒充已通过编译；需要维护该参考样例时另选与其POM匹配的旧工具链，不回退平台依赖。

完整包的 `toolchains/verified-toolchains.json`、`locks/`、`logs/` 和总校验清单记录实际版本、来源与结果。原生HDI Core的真实执行仍需要其已授权的Linux x86_64运行包及隔离环境；Windows上的Java/UI编译不等于该运行环境已经具备。MySQL/Redis、OnlyOffice和现场连接也不是Maven/npm依赖，按部署任务另行配置。

## 目录和使用

建议解压到短路径，例如 `C:\rd`，减少Windows工具链的路径长度问题。包内包含：

```text
Initialize-Offline.ps1 / Verify-Offline.ps1
toolchains/          官方Windows工具分发包与校验
maven/repository/    本轮验证的本地Maven库（不含个人settings或服务器口令）
npm-cache/           可迁移的内容寻址缓存（不复制npm登录配置）
locks/              两个前端的package声明和精确锁文件
source.git/         保留确切提交和基线的浅快照仓库
official-docs/      官网公开文档离线HTML/文本/配图及缺项清单
knowledge/          本地源码、组件/API/表关系索引
logs/               依赖审计和本轮离线验证证据
```

先核对交付ZIP的SHA-256，再在PowerShell运行：

```powershell
C:\rd\Verify-Manifest.ps1
. C:\rd\Initialize-Offline.ps1
git clone --config core.autocrlf=false --no-local C:\rd\source.git C:\work\rynew
C:\rd\Verify-Offline.ps1 -SourceRoot C:\work\rynew -Mode All
```

源码目标必须尚不存在。初始化只解压已校验工具并设置当前PowerShell会话环境，不改系统级环境/用户Maven与npm配置。已有不完整工具解压目录、不同锁文件或与锁不匹配的package声明会拒绝覆盖，应先保留并明确选择正确版本。

`-Mode`可选Platform、Frontend、Legacy、Compatibility；默认All。Java默认编译并打包、编译测试但跳过执行，想在Windows实际跑Java测试时增加 `-RunTests`。主前端会运行仓库既有 `verify:frontend`；Vue2运行原有生产构建。所有依赖解析都使用 `mvn -o` / `npm ci --offline`。

## 开发时使用专用本地仓库

```powershell
mvn -o -gs C:\rd\maven\settings-offline.xml -s C:\rd\maven\settings-offline.xml -Dmaven.repo.local=C:\rd\maven\repository -f WDF100.0\pom.xml test
npm ci --offline --cache C:\rd\npm-cache --no-audit --no-fund
```

第一条从仓库根运行；第二条从指定前端目录运行。IDE的Maven home、JDK、user settings和local repository指向同一套离线路径；Node解释器也指向已解压的WindowsNode。不要让IDE偷偷使用原用户目录中另一套工具/仓库来掩盖缺项。

Maven缓存的 `_remote.repositories` 还会记录下载来源ID。迁移副本使用 `prepare_maven_cache.py` 为已有文件增加local-installed标记，并从已存在JAR/POM生成本地版本目录供未固定版本的生命周期插件查找；保留原来源及摘要，不改JAR/POM字节，用户原.m2不修改。这样离线构建不依赖准备机的个人/global镜像配置。复核时同时隔离 `-gs` 和 `-s`，不能只替换user settings后就声称环境独立。

技能的 `assets/offline/vue3-lock.json` 与 `vue2-lock.json`是冻结输入模板，安装时名称为package-lock.json。主前端原锁的npmmirror地址归一到npm官方仓库，版本与SRI不变；已有安装目录不参与补缓存判断。

## 复核与更新

1. Maven检查JAR/POM、父/BOM、构建插件及其依赖、测试依赖、systemPath SDK。当前Artemis SDK在源码lib路径中，不能只带.m2而漏掉它。
2. npm按每个锁文件的integrity检查 `_cacache/content-v2`；缺少或损坏均失败，Windows包不能因本机是Mac而跳过。然后从无node_modules的目录离线安装并构建。
3. 除依赖管理器offline标志，本轮在macOS子进程策略中禁止外部网络验证主工程/前端构建。需要本机测试服务器时仅允许loopback。
4. Windows包做了官方摘要、ZIP完整性、PE/native包及目标布局核验；macOS不能代替Windows执行器。到达目标机器后运行上述PowerShell脚本，结果才构成Windows实机验收。

有网准备机补缓存：

```bash
node <skill-dir>/scripts/npm_offline_cache.mjs --lock package-lock.json --cache <专用缓存> --fetch --report <审计.json>
```

省略 `--fetch` 是只读核验。该脚本按SRI验证所有锁定tarball，包括非当前OS的可选包。`npm cache verify`本身不能证明项目所需版本齐全；同样，dependency:go-offline完成后仍应运行实际Maven生命周期。

可用 `npm_offline_cache.mjs --export-cache <空目录>` 只复制锁定tarball内容，避免把准备机其他项目的npm缓存或HTTP认证元数据带入。Maven专用副本用 `prepare_maven_cache.py --repository <包>/maven/repository --receipt <审计.json>` 处理来源与版本目录；不要对用户原.m2执行。

资料/依赖准备完成、源码提交干净后，可用 `assemble_offline_bundle.py --root <仓库> --prepared <准备目录> --output <新交付目录>` 按白名单组装。脚本只带工具清单中的Windows包，排除准备机node_modules、macOS工具及个人配置，源码保留确切提交与main基线，不复制完整大历史。

本轮审计摘要见迁移包根目录 `offline-audit.json`。未取得的App配图、可选真实设备/引擎测试和Windows实机结果需按实况报告，不将跳过记为通过。首次使用前校验清单；开始构建后缓存可能正常更新，不能再把它视为未使用的原始交付快照。
