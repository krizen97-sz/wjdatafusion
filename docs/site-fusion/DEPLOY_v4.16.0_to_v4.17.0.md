# v4.16.0 至 v4.17.0 数据库部署包

整理日期：2026-10-12。适用于数据库已完整升级至 **v4.16.0** 的环境；当前本地已集成应用版本为 **v4.17.0，提交1b085c9**。本包只整理此区间的数据库增量，没有执行生产升级，也不包含应用JAR或前端安装包。

## 唯一需要执行的升级脚本

`support_upgrade_20260929_server_credential_password_pool_v4_17_0.sql`

该文件原样取自应用提交1b085c9，不更改迁移行为。Git基线cd08046包含完整v4.16.0及此前交付文档；到1b085c9之间只有这一份新业务增量，另外两份全量SQL仅增加同一字段。

| 对象 | 本次变化 |
| --- | --- |
| sup_server_scan_credential | 新增 password_cipher VARCHAR(1024) NOT NULL DEFAULT '' |
| 原ROOT_DIRECT条目 | 将原root密文逐字复制到新字段，保留原ID |
| 原HIK_SU_ROOT条目 | 原ID保留root密文；hik密文另建同现场独立条目，名称追加“ / 2”，保留审计信息 |
| 原配对字段 | 保留login_mode、hik_password_cipher、root_password_cipher，用于恢复核对 |
| 原服务器、服务器账号、扫描资料、权限与授权 | 不修改；没有新增业务表或权限 |

新程序只使用password_cipher。密码不解密、不重新加密，因此必须保留原 **support.credential.key / SUPPORT_AES_KEY**。SQL成功只证明数据复制成功，不能代替原密钥的解密验收。

## 包内内容

- 升级SQL：上面唯一一份写入脚本。
- `support_check_20261012_server_credential_v4_16_0_to_v4_17_0.sql`：前后各执行一次的只读核验，仅输出结构、状态和聚合数量，不输出密码或密文值。
- `README.md`：本说明。
- `MANIFEST.json` / `SHA256SUMS`：来源提交及文件校验。
- `upgrade-tests.log` / `deployment-check-tests.log`：隔离MySQL测试结果，不含业务数据。

**不要运行support_v1.sql、support_deploy_all.sql或之前的累计升级包。** 如果只是应用显示v4.16.0而扫描库结构尚未升级，应先补齐正确的v4.16.0前置数据库升级；本包不会初始化缺失的旧表。

## 执行顺序

### 1 停写与备份

停止所有应用实例对密码池的写入，停止扫描并确认任务结束，避免新旧程序混写。备份业务库、当前前后端和有效配置，单独核实原加密密钥已保存。备份包含敏感密文，只放受控目录，不上传到代码仓库或公开附件。

下面命令使用交互密码，不将密码写入命令行。先替换占位值，并在SQL包解压目录操作：

```bash
export DEPLOY_DB_HOST='实际MySQL地址'
export DEPLOY_DB_PORT='3306'
export DEPLOY_DB_NAME='实际平台数据库名'
export DEPLOY_DB_USER='部署DBA账号'
umask 077
mysqldump --host="$DEPLOY_DB_HOST" --port="$DEPLOY_DB_PORT" --user="$DEPLOY_DB_USER" -p \
  --single-transaction --routines --triggers --events --hex-blob \
  --set-gtid-purged=OFF --no-tablespaces "$DEPLOY_DB_NAME" > before_v4_17_0.sql
```

确认退出码为0，并在隔离环境验证备份可恢复。备份期间禁止DDL；非事务表需DBA采用适当的一致性备份方式。

### 2 只读前置核验

```bash
mysql --host="$DEPLOY_DB_HOST" --port="$DEPLOY_DB_PORT" --user="$DEPLOY_DB_USER" -p \
  --default-character-set=utf8mb4 --database="$DEPLOY_DB_NAME" \
  < support_check_20261012_server_credential_v4_16_0_to_v4_17_0.sql > precheck.log 2>&1
```

核验脚本只报告，不会自动阻止后续操作。人工确认退出码为0、目标库正确、没有STOP或SQL错误：

- 相关v4.16.0表、旧列、主键、索引、外键及扫描权限符合检查要求；检查范围是本增量所依赖的对象，不替代整个平台历史升级审计。
- `password_cipher` 首次升级前允许NOT_APPLIED；不允许已有字段定义不兼容。
- `invalid_legacy_rows` 必须为0。保存pool_rows_before、pairs_before和first_upgrade_expected_rows。
- 没有自定义密码池触发器。账号须能查看相关元数据，不能把无权限导致不可见当作通过。
- 若新字段已存在但pending_rows不为0，先核实是否上次DDL成功、数据迁移失败，处理根因后再按原脚本恢复；不要删列重建。

### 3 执行唯一增量

DBA账号需有ALTER、SELECT、INSERT、UPDATE、CREATE ROUTINE、ALTER ROUTINE、EXECUTE及相应元数据访问权限。使用支持DELIMITER的mysql客户端、新的专用连接，整文件执行；不要选段执行、并发执行或使用--force。

```bash
mysql --host="$DEPLOY_DB_HOST" --port="$DEPLOY_DB_PORT" --user="$DEPLOY_DB_USER" -p \
  --default-character-set=utf8mb4 --database="$DEPLOY_DB_NAME" \
  < support_upgrade_20260929_server_credential_password_pool_v4_17_0.sql > upgrade.log 2>&1
```

确认退出码为0、无SQL错误且出现SUCCESS再继续。不能只看到日志里某处SUCCESS就忽略其后的错误。报错后停止部署并核对原因；过程可能残留，修复后重跑原脚本会重建过程。

### 4 后置核验与程序切换

再次执行相同只读检查，改为输出postcheck.log。要求password_cipher为PASS、pending_rows=0、其他检查没有STOP。首次升级且全程停写时：新总数应等于前检first_upgrade_expected_rows，即旧总数加旧HIK_SU_ROOT配对条目数。

已使用v4.17.0的库可能增加、编辑或删除过密码项，不能再拿这个首次升级公式判断是否正确。重跑应保留新密码编辑，不重复拆分，也不恢复已删除的拆分条目。

对照备份，原服务器、账号、扫描资料、菜单和角色授权保持不变；只有数量相同不能证明内容相同，必要时在受控环境比对备份，避免导出密文到普通日志。升级SQL不触发设备扫描。

核验通过后配套部署v4.17.0前端和后端，保持原数据库、文件路径及加密配置。不要让v4.16和v4.17实例同时写密码池。应用恢复后先查看密码池数量、名称和正常解密状态；实际设备扫描由管理员选择授权目标单独验收。

## 回退说明

密码复制在事务内，失败会回滚复制和新增条目；**新增字段属于DDL，可能在数据事务失败后仍然存在**。不要依靠外围事务撤回整个升级。

回退应用时恢复匹配的旧前后端和原配置，保留新字段和数据，暂停扫描及密码池维护。新版本修改只更新password_cipher，旧root/hik列可能已陈旧，旧程序也不认识新增PASSWORD_POOL条目。因此，保留旧列不等于无损回退；恢复扫描功能须基于备份和上线后的修改做定向核对，不能删新字段、新条目或覆盖整个业务库。

## 本轮验证

在全新、关闭TCP监听的MySQL8.4.6临时实例上，以合成数据执行原迁移测试5项和交付核验测试6项，全部通过。覆盖首次迁移、重复执行、保留已编辑/已删除项、旧业务及审计保留、异常结构/旧数据拒绝、DML失败回滚，以及缺表、非InnoDB、自定义触发器拒绝；检查脚本前后不修改数据，异常密文默认值也只显示脱敏状态。

本轮没有修改业务实现，没有执行本机业务库或线上库升级，没有重启应用，不重跑无关前后端构建。其他MySQL版本应先在同版本的备份恢复库演练。
