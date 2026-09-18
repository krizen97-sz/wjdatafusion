# 内网离线材料清单

本资料包没有包含以下大体积或现场专属制品。已有部署可核对后复用，首次启用需独立准备。

| 材料 | 用途与核对 |
| --- | --- |
| 匹配主线JAR与完整dist | 从完整源码的准确提交构建；记录commit、干净状态、SHA256 |
| Java17 JDK/JRE | 后端运行及Kettle worker编译；不全局替换现有Java |
| `kettle6.1.3.0.zip` | 用户提供的原海康引擎，SHA256：`86b8b5e8ced63e43288f70911b2b3b077f2d2593917a7d6a45e84d42fe2ee902` |
| 可选`postgresql-42.7.7.jar`及`.sha1` | 如旧manifest有该批准扩展，新prepare后需重新安装；JAR SHA256：`157963d60ae66d607e09466e8c0cdf8087e9cb20d0159899ffca96bca2528460` |
| Linux amd64 JRE17 Docker归档 | `eclipse-temurin-17-jre-jammy-linux-amd64.docker.tar.gz`；SHA256：`9142aff884ec5f9c2da43611d48aeba0821e31476f7fb31f25bea35584afc197` |
| Python3.10+与IANA tzdata | Broker/adapter使用标准库，不要求额外pip引擎依赖 |
| NiFi2.11.0、Linux对应JDK21 | 仅NiFi路径需要；仓库artifact-lock中的macOS ARM64 JDK不可拿到Linux用 |
| NAR1.2.3 | 从本包compatibility源码在JDK21/Maven构建机干净构建并校验 |
| MySQL/Redis、业务DB/Kafka/FTP | 沿用批准的服务与连接；本地fixture不是生产安装材料 |

JRE镜像配置ID为：
`sha256:72e36d8dd5e6aab7ca6f3bcc47b9a6b1dde9b4c7a03536ed081a1bfb98616e15`。
导入后核对完整ID与 `linux/amd64`。运行使用固定ID/摘要和 `--pull=never`，不依赖内网访问公网registry。

```bash
docker image inspect sha256:72e36d8dd5e6aab7ca6f3bcc47b9a6b1dde9b4c7a03536ed081a1bfb98616e15 \
  --format '{{.Id}} {{.Os}}/{{.Architecture}}'
```

离线归档无RepoTags，load不保证RepoDigest恢复。受控Linux配置必须使用本次模板中的完整
`image` 配置ID，不要省略image后依赖默认RepoDigest；输出必须与上述完整ID及`linux/amd64`一致。

如需重新准备JRE归档，可在能访问官方源的交付机运行：

```bash
python3 data-governance/kettle-worker/linux/export_image.py --output /实际新建离线材料目录
```

脚本核对固定官方制品；传输前后校验SHA256，现场另行执行 `docker image load --input <归档>`。
该准备过程不启动镜像。原海康包只按现有授权用于该现场，不把用户私有运行目录或商业原包当成公共源码发布。
