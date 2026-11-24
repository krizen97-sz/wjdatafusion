# 数据库版本对照

本次文档编辑优化仅修改程序与部署配置。数据库已完成 v4.17.2～v4.17.4 结构升级的现场可直接使用主包。应用安装和回滚命令均不会执行 SQL。

若现场仍处于此前交付的 v4.8.4 或后续中间版本，包内 `db/previous/` 提供主线原有的累计升级及检查脚本。DBA 先根据版本记录和实际表结构确定已完成的阶段，备份数据库与 `support.credential.key` 等外部配置。按各脚本开头的说明使用有权限的独立 MySQL 连接，在维护窗口暂停相应业务写入。遇到 STOP、结构冲突或 SQL 错误时先排查，再继续下一阶段。

| 起点 → 目标 | 先运行检查 | 需要补齐时执行 | 之后 |
| --- | --- | --- | --- |
| v4.8.4 → v4.15.1 | `support_check_20260929_server_credential_v4_8_4_to_v4_15_1.sql` | `support_upgrade_20260929_server_credential_v4_8_4_to_v4_15_1_all.sql` | 重跑检查并审核输出 |
| v4.15.1 → v4.16.0 | `support_check_20260929_server_credential_v4_15_1_to_v4_16_0.sql` | `support_upgrade_20260929_server_credential_v4_15_1_to_v4_16_0_all.sql` | 重跑检查 |
| v4.16.0 → v4.17.0 | `support_check_20261012_server_credential_v4_16_0_to_v4_17_0.sql` | `support_upgrade_20260929_server_credential_password_pool_v4_17_0.sql` | 重跑检查，核对原加密记录和授权 |
| v4.17.0 → v4.17.2 | `data_governance_check_20261026_v4_17_0_to_v4_17_2.sql` | `data_governance_upgrade_20261026_v4_17_0_to_v4_17_2_all.sql` | 重跑检查，核对治理表和初始技术数据 |
| v4.17.2 → v4.17.5 | 已有完整库且文档 ACL、版本及配额可用 | 无增量 SQL | 使用业务账号测试文档打开和保存 |

包内脚本按源码原样提供，合并范围为既有 RYNEW 平台的升级。更早的现场先按其已安装版本完成前序升级。已有业务库应走增量路径，勿导入新库初始化文件或整库覆盖用户数据。
