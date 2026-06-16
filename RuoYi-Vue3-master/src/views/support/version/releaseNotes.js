export const releaseNotes = [
  {
    version: 'v3.4.1',
    submitTime: '2026-06-16 21:10:24',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '服务器登录信息轻量化配置',
    summary: '将服务器登录信息从重型凭据档案前置为 hik、root 和其他账号三类常用配置，保留原有数据模型的同时降低现场录入和维护成本。',
    changes: [
      '服务器新增、编辑、设备资产清单批量录入和画布右侧快捷编辑统一展示 hik 密码、root 密码和其他账号用户名/密码，不再要求用户额外进入凭据档案维护。',
      '固定账号严格使用小写 hik 和 root；其他账号支持自定义用户名和密码，并校验不能误填为 hik 或 root。',
      '服务器批量导入预览会把旧模板中的系统账号自动映射为 hik、root 或其他账号，兼容已有 xlsx 模板和历史导入习惯。',
      '显示密码和服务器导出改为读取 hik/root/其他账号凭据，旧 sup_server 主表密码仅作为无凭据数据的兼容回退。',
      '服务器新增和修改增加事务保护，账号校验失败时服务器主表和凭据数据一起回滚，避免半保存状态。'
    ],
    scope: ['现场融合管理', '设备资产清单', '服务器管理', '登录凭据', '前端交互', '版本记录'],
    database: '无数据库结构变化；继续复用 v3.4.0 新增的 sup_server_credential 表存储 hik、root 和其他账号凭据。',
    scripts: []
  },
  {
    version: 'v3.4.0',
    submitTime: '2026-06-16 19:41:34',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'warning',
    title: '服务器多凭据与巡检凭据隔离',
    summary: '将现场服务器资产账号与自动化巡检执行账号彻底分离，解决同一服务器需要记录运维、巡检、root等多套凭据时的配置混乱和误用风险。',
    changes: [
      '现场服务器新增“凭据档案”管理入口，同一台服务器可维护多个账号密码档案，并支持新增、修改、停用、删除和按权限查看明文。',
      '自动化巡检的服务器目录、服务器磁盘和大数据服务器爆盘等 SSH 类检测只使用巡检步骤中填写保存的账号密码，不再回退读取现场服务器资产密码。',
      '从现场服务器树状选择巡检目标时仅带出服务器名称、IP、SSH端口和账号提示，密码必须在巡检配置中单独填写，避免 root 或混合账号被误用于巡检执行。',
      '操作记录脱敏范围补充 passwordCipher、secretCipher 等敏感字段，防止凭据档案和巡检目标的加密值进入变更详情。'
    ],
    scope: ['现场融合管理', '服务器管理', '设备资产清单', '自动化巡检', 'SSH凭据', 'SQL脚本', '版本记录'],
    database: '新增 sup_server_credential 服务器多凭据档案表；不迁移、不删除 sup_server 既有账号密码；自动化巡检目标凭据仍保存于 sup_auto_inspection_target。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260616_server_credentials_v3_4_0.sql'
    ]
  },
  {
    version: 'v3.3.7',
    submitTime: '2026-06-16 16:26:00',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '设备资产清单筛选与统一交互优化',
    summary: '重新设计设备资产管理弹窗的信息架构，把筛选条件上移到设备列表上方，并弱化服务器与其他设备在维护体验上的割裂感。',
    changes: [
      '设备资产弹窗取消左侧筛选栏，改为列表上方横向筛选区，支持按关键词、设备类型、网络环境、绑定范围和运行状态统一过滤。',
      '设备类型分类卡片固定展示服务器、解码器、终端、交换机和网闸等类型，点击后与上方筛选条件联动。',
      '弹窗内按钮和空状态统一使用“设备”语义，将批量维护入口调整为“批量录入”，降低用户对服务器和非服务器两套维护模式的感知。',
      '补充窄屏布局保护，筛选条件和批量录入区域在小窗口下自动改为单列显示，避免挤压设备列表。'
    ],
    scope: ['现场融合管理', '设备资产清单', '服务器管理', '前端交互', '版本记录'],
    database: '无数据库结构变化，无需执行 SQL。',
    scripts: []
  },
  {
    version: 'v3.3.6',
    submitTime: '2026-06-16 15:21:38',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '现场设备资产统一维护优化',
    summary: '将现场设备资产清单升级为统一维护入口，补充非服务器设备登录信息、本地设备类型图片和更清晰的设备分类展示。',
    changes: [
      '非服务器硬件资产新增登录账号和登录密码配置，密码按现有敏感信息加密策略保存，并支持按权限查看明文。',
      '新增设备类型选择页为服务器、解码器、终端、交换机、网闸增加本地 SVG 图片，随前端包发布，不依赖外部资源。',
      '设备资产清单增加类型分类卡片，按设备类别展示数量并支持一键筛选，列表行同步展示设备缩略图和登录账号。',
      '统一设备资产弹窗内增加服务器维护模式，直接承接服务器单个添加、批量添加、xlsx 导入、全选、导出、删除和显示密码能力，减少服务器管理与设备资产管理之间的跳转。'
    ],
    scope: ['现场融合管理', '设备资产清单', '服务器管理', '硬件资产', '版本记录'],
    database: '新增 sup_hardware_asset.login_username 和 sup_hardware_asset.login_password_cipher 字段，历史数据默认为空。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260616_equipment_login_unification_v3_3_6.sql'
    ]
  },
  {
    version: 'v3.3.5',
    submitTime: '2026-06-16 12:28:46',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '自动化巡检服务器目录多服务器配置',
    summary: '将服务器目录文件数量检测扩展为一个步骤内可配置多台服务器，支持手动添加和按现场 / 平台 / 服务器树状选择。',
    changes: [
      '服务器目录文件数量检测步骤改为多服务器卡片配置，支持每台服务器单独填写 IP、SSH 端口、检测目录、账号和密码。',
      '复用大数据服务器的树状选择弹窗，按现场、主平台、子平台、服务器分层多选，并自动带出服务器 IP、SSH 端口、系统账号和密码。',
      '手动添加服务器时默认使用 2343 端口和 root 账号，仍可在每张服务器卡片内单独调整。',
      '后端保存时会把多台服务器分别落为巡检目标并绑定到同一个步骤，正式巡检和结果明细按服务器分别记录。'
    ],
    scope: ['自动化巡检', '巡检模板', '服务器目录文件数量检测', '树状选择', '版本记录'],
    database: '不修改业务表结构，仅更新自动化巡检内置工具 SERVER_FILE_COUNT 的参数说明。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260616_server_file_multi_targets_v3_3_5.sql'
    ]
  },
  {
    version: 'v3.3.4',
    submitTime: '2026-06-16 11:06:02',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '自动化巡检 FTP 多目录目标配置',
    summary: '将 FTP 目录文件数量检测从单目录目标扩展为一个步骤内可配置多个 FTP 目录目标，适配同一巡检步骤批量检测多目录的业务场景。',
    changes: [
      '巡检模板添加 FTP 目录文件数量检测步骤时，目标配置改为多卡片列表，支持手动添加多个 FTP 目录目标。',
      '已填写的 FTP 目录目标支持一键复制，复制后自动生成带“副本”的目标名称，便于快速配置同类目录。',
      'FTP 步骤保存时会把多个目录目标分别落库并绑定到同一个步骤，执行巡检后每个 FTP 目录都有独立目标明细。',
      '步骤摘要和详情展示改为显示 FTP 目录目标数量及主机、端口、目录清单，避免多目标配置被误读为单目标。'
    ],
    scope: ['自动化巡检', '巡检模板', 'FTP目录文件数量检测', '多目标配置', '版本记录'],
    database: '无数据库结构变化，无需执行 SQL。',
    scripts: []
  },
  {
    version: 'v3.3.3',
    submitTime: '2026-06-16 10:50:43',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '自动化巡检服务器树形选择修复',
    summary: '修复大数据服务器选择弹窗中服务器平铺展示和穿梭框位置不协调的问题，改为现场 / 平台 / 服务器树形选择。',
    changes: [
      '将大数据服务器选择从 Element Transfer 平铺列表改为自定义树形双栏选择，左侧按现场、主平台、子平台、服务器分层展示。',
      '右侧改为已选服务器清单，展示服务器名称、IP、SSH 端口和默认账号，支持搜索、移除和清空。',
      '重新调整选择弹窗布局比例，中间不再显示拥挤的上下移动按钮，勾选服务器后自动加入已选列表。',
      '保留原有确认逻辑，确认后继续自动带出 IP、端口、账号和密码，不影响手动添加服务器。'
    ],
    scope: ['自动化巡检', '巡检模板', '大数据服务器', '树形选择', '版本记录'],
    database: '无数据库结构变化，无需执行 SQL。',
    scripts: []
  },
  {
    version: 'v3.3.2',
    submitTime: '2026-06-16 10:34:07',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '自动化巡检大数据服务器选择优化',
    summary: '优化大数据服务器爆盘检测的服务器添加方式，支持从现场管理服务器中通过穿梭框多选，也支持继续手动添加。',
    changes: [
      '模板步骤中的大数据服务器添加拆分为“从现场服务器选择”和“手动添加”两种入口。',
      '从现场服务器选择时按现场 / 平台 / 服务器组织可搜索多选，并自动带出 IP、SSH 端口、系统账号和服务器密码。',
      '手动添加大数据服务器时默认 SSH 端口为 2343、默认账号为 root，前后端测试和正式巡检默认值保持一致。',
      '从现场服务器生成的大数据巡检目标保留服务器资产引用，后续编辑模板时可以继续回显资产来源。'
    ],
    scope: ['自动化巡检', '巡检模板', '大数据服务器', '现场服务器资产', '版本记录'],
    database: '无数据库结构变化，无需执行 SQL。',
    scripts: []
  },
  {
    version: 'v3.3.1',
    submitTime: '2026-06-16 10:15:12',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '自动化巡检 SSH 直连修复',
    summary: "修复大数据服务器爆盘检测点击“测试目标”时被 JVM SOCKS 代理配置劫持，导致内网 SSH 连接报 Can't connect to SOCKS proxy 的问题。",
    changes: [
      '自动化巡检 SSH 建连改为使用直连 SocketFactory，强制绕过 JVM 全局 SOCKS 代理配置。',
      '大数据服务器爆盘检测、服务器目录文件数量检测、服务器磁盘使用率检测统一受益，内网服务器检测不再依赖本机代理端口状态。',
      '保留 HTTP/海康接口原有网络调用逻辑，本次仅调整 SSH 类巡检目标的连接方式。'
    ],
    scope: ['自动化巡检', '大数据服务器爆盘检测', 'SSH检测', '目标测试', '版本记录'],
    database: '无数据库结构变化，无需执行 SQL。',
    scripts: []
  },
  {
    version: 'v3.3.0',
    submitTime: '2026-06-16 09:35:42',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'primary',
    title: '自动化巡检大数据服务器爆盘检测',
    summary: '自动化巡检模板新增“大数据服务器爆盘检测”工具，一个步骤可配置多台服务器，逐台读取所有磁盘分区占用并按阈值告警。',
    changes: [
      '新增内置巡检工具 BIG_DATA_SERVER_DISK，默认按磁盘使用率不高于 85% 判断，超时默认 15 秒。',
      '模板步骤配置中新增大数据服务器列表，可为每台服务器单独维护 IP、SSH 端口、账号和密码，不依赖现场服务器资产。',
      '执行时通过 SSH 读取 df -Pk 输出，记录每台服务器每个分区的总量、已用量、剩余量和使用率，步骤代表值取最高使用率。',
      '默认过滤 tmpfs、devtmpfs、proc、sysfs 等临时或伪文件系统，页面提供“包含临时文件系统”开关。',
      '目标测试只验证 SSH 连通和磁盘分区读取能力，正式巡检时才根据阈值判定是否告警。'
    ],
    scope: ['自动化巡检', '巡检模板', '大数据服务器', '磁盘爆盘告警', 'SSH检测', 'SQL脚本', '版本记录'],
    database: '不修改业务表结构；新增一条 sup_auto_inspection_tool 内置工具数据 BIG_DATA_SERVER_DISK。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260616_auto_inspection_bigdata_disk_v3_3_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v3.2.0',
    submitTime: '2026-06-12 23:55:26',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'primary',
    title: '服务器与硬件资产统一管理',
    summary: '将现场里的服务器管理和硬件资产管理整合为统一设备资产清单，保留服务器原有密码、批量导入和自动化巡检配置方式，同时支持网闸等新设备登记。',
    changes: [
      '现场画布中的硬件资产入口升级为设备资产清单，服务器、解码器、终端、交换机、网闸统一展示、筛选、导出和批量删除。',
      '新增设备时先选择设备类型，服务器继续进入原服务器管理流程，非服务器设备继续进入硬件资产表单，避免破坏原有数据和配置习惯。',
      '新增后端 /support/equipment/list 和 /support/equipment/export 聚合接口，统一返回服务器与硬件资产的来源、类型、网络环境、绑定范围和状态。',
      '新增网闸设备类型，补充网闸模式、数据流向、带宽和安全域说明字段，方便现场按网络边界登记安全隔离设备。',
      '统一设备清单导出不包含服务器明文密码，服务器密码仍通过原服务器导出和显示密码能力控制。'
    ],
    scope: ['现场融合管理', '现场画布', '设备资产清单', '服务器管理', '硬件资产', '网闸', '权限', 'SQL脚本', '版本记录'],
    database: '不迁移、不修改 sup_server 现有数据；sup_hardware_asset 新增网闸字段；support_hardware_type 新增 GATEWAY；新增 support:equipment:query/export 按钮权限。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260612_equipment_asset_unification_v3_2_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v3.1.0',
    submitTime: '2026-06-12 23:21:29',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'primary',
    title: '现场硬件资产管理扩展',
    summary: '把现场画布中的服务器视角扩展为硬件资产视角，在不迁移原服务器表的前提下新增解码器、终端、交换机等设备登记和平台绑定能力。',
    changes: [
      '现场画布将服务器层升级为硬件资产层，主平台和子平台节点展示服务器、解码器、终端、交换机等类型汇总数量。',
      '新增硬件资产管理弹窗，支持按网络环境、资产类型、状态和关键词筛选，并可新增、编辑、删除、批量删除和导出非服务器硬件资产。',
      '服务器继续沿用原有 sup_server、密码、批量导入和自动化巡检能力，硬件资产弹窗中提供“管理服务器”入口保持原流程。',
      '新增平台硬件关系，资产可绑定主平台、子平台，也可作为现场公共资产按网络环境归类。',
      '硬件资产新增、修改、删除继续写入现场融合操作记录，便于在画布右侧查看资产变更。'
    ],
    scope: ['现场融合管理', '现场画布', '硬件资产', '网络环境', '操作记录', 'SQL脚本', '版本记录'],
    database: '新增 sup_hardware_asset、sup_platform_asset_rel 两张表；新增 support_hardware_type 字典；新增 support:hardwareAsset:* 菜单权限。不迁移、不修改 sup_server 现有数据。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260612_hardware_asset_v3_1_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v3.0.6',
    submitTime: '2026-06-12 18:11:23',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '版本记录中心独立化',
    summary: '将版本记录从现场融合管理中抽离为独立顶级模块，并按“本次重点、详细说明、影响范围、部署脚本”的结构重新组织展示。',
    changes: [
      '版本记录菜单从现场融合管理子菜单调整为顶级模块，后续现场融合、自动化巡检和首页工作台等变更统一在版本中心查看。',
      '版本页重新设计为左侧版本列表、右侧版本详情的工作台布局，支持按模块筛选和关键词搜索。',
      '每条历史记录统一展示“本次重点”，下方再展示详细修改说明、影响范围、数据库说明和 SQL 脚本，降低长记录阅读成本。',
      '同步更新首页快捷入口、support_v1.sql、support_deploy_all.sql 和独立升级脚本，保证新环境和已部署环境菜单位置一致。'
    ],
    scope: ['版本中心', '菜单权限', '前端展示', 'SQL脚本', '版本记录'],
    database: '不改业务表结构；仅调整 sys_menu 中版本记录菜单的 parent_id、order_num、path、remark 等菜单展示字段。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260612_version_center_top_module_v3_0_6.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v3.0.5',
    submitTime: '2026-06-12 17:54:19',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '自动化巡检模板交互优化',
    summary: '优化自动化巡检模板步骤编辑体验，修复步骤复制后的敏感字段显示问题，并提升长模板配置时的可用性。',
    changes: [
      '复制模板步骤时自动清空密码和密钥等敏感字段，避免复制后的新步骤沿用原步骤脱敏值导致密码显示或保存不正确。',
      '将步骤基础信息和判定规则拆分展示，比较规则、告警阈值、阈值单位、统计窗口和超时秒数集中在“判定规则”区域维护。',
      '服务器目录文件数量检测的“递归查询”增加业务解释，明确开启后统计当前目录及所有子目录，关闭后只统计当前目录第一层文件。',
      '模板步骤较多时，模板编辑弹窗改为内部滚动布局，左侧步骤列表和右侧编辑区独立滚动，避免页面被过长模板撑开。'
    ],
    scope: ['自动化巡检', '巡检模板', '巡检步骤', '敏感字段', '前端交互', '版本记录'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v3.0.4',
    submitTime: '2026-06-11 17:00:36',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'danger',
    title: '自动化巡检动态路由修复',
    summary: '修复自动化巡检一级目录 component 为空导致 Vue 动态路由渲染时报 Cannot read properties of null (reading component) 的问题。',
    changes: [
      '将自动化巡检一级菜单 sys_menu.menu_id=2300 的 component 从空值修正为 Layout，符合若依前后端分离动态路由规范。',
      '新增前端动态路由兜底逻辑：后端返回有子路由但 component 为空的目录时，自动按 Layout 处理，避免空组件进入路由树。',
      '同步修正 v3.0.0 初始化脚本、support_v1.sql、support_deploy_all.sql 和 v3.0.3 独立脚本中的自动化巡检一级目录配置。',
      '新增 v3.0.4 独立升级脚本，线上环境可直接执行以修复已落库的菜单数据。'
    ],
    scope: ['自动化巡检', '若依动态路由', '菜单权限', 'SQL脚本', '版本记录'],
    database: '不改业务表结构，仅更新 sys_menu 中自动化巡检一级菜单 component 字段为 Layout。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260611_auto_inspection_route_layout_v3_0_4.sql',
      'WDF100.0/sql/support_upgrade_20260611_auto_inspection_template_steps_v3_0_3.sql',
      'WDF100.0/sql/support_upgrade_20260611_auto_inspection_v3_0_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v3.0.3',
    submitTime: '2026-06-11 16:44:28',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '自动化巡检模板步骤化配置',
    summary: '将巡检目标从独立配置入口收敛到巡检模板步骤中，用户添加步骤时直接选择巡检工具并完成目标、阈值和参数配置。',
    changes: [
      '巡检配置页仅保留“巡检模板”和“巡检计划”两个主入口，移除主流程中的独立“巡检目标”页签。',
      '新增模板步骤弹窗，先选择 Kafka、HTTP接口、FTP目录、服务器目录或磁盘等巡检工具，再按工具类型展示必要配置项。',
      '模板步骤保存时携带内联目标，后端自动新增或更新巡检目标并绑定到步骤，编辑模板时可完整回显目标配置。',
      '移除选择目标后仍重复展示 Topic、消费组、路径等二次配置的问题，Kafka、FTP、服务器字段统一放到目标配置区。',
      '重新排布 HTTP 接口配置，突出接口 URL、请求方法、认证、结果路径和请求体模板，并补充日期变量的效果示例。',
      '巡检配置菜单默认进入模板页，旧目标独立入口继续隐藏，避免用户进入已废弃的目标管理心智。'
    ],
    scope: ['自动化巡检', '巡检模板', '巡检步骤', 'HTTP接口变量', '菜单权限', 'SQL脚本', '版本记录'],
    database: '不改业务表结构，仅更新 sys_menu 菜单默认入口和备注；业务目标数据仍保存在 sup_auto_inspection_target，用于执行器复用。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260611_auto_inspection_template_steps_v3_0_3.sql',
      'WDF100.0/sql/support_upgrade_20260611_auto_inspection_config_entry_v3_0_2.sql',
      'WDF100.0/sql/support_upgrade_20260611_auto_inspection_v3_0_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v3.0.2',
    submitTime: '2026-06-11 16:00:10',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '自动化巡检配置入口优化',
    summary: '将巡检目标、巡检模板和巡检计划收敛到同一个配置入口，按目标类型精简配置表单，并为HTTP接口参数补充当天日期变量。',
    changes: [
      '一级菜单从“巡检模板、巡检目标、巡检计划、巡检记录”调整为“巡检配置、巡检记录”，配置页内再维护目标、模板和计划。',
      '巡检目标弹窗按 Kafka、HTTP接口、FTP目录、服务器资产分区展示，只保留当前类型必需的连接、路径、认证和默认参数。',
      '新增目标保存和测试前的类型校验，避免服务器目标缺少资产、HTTP目标缺少URL、FTP目标缺少目录等脏配置进入数据库。',
      'HTTP接口目标的URL和请求体模板支持 ${today}、${todayStart}、${todayEnd}、${yyyyMMdd} 等日期变量，并保留原有时间窗口变量。',
      '同步更新自动化巡检菜单SQL，隐藏旧目标和计划独立入口，保留历史菜单和权限数据不删除。'
    ],
    scope: ['自动化巡检', '巡检配置', '巡检目标', 'HTTP接口变量', '菜单权限', 'SQL脚本', '版本记录'],
    database: '不改业务表结构，仅更新 sys_menu 中自动化巡检菜单展示入口和按钮父级关系。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260611_auto_inspection_config_entry_v3_0_2.sql',
      'WDF100.0/sql/support_upgrade_20260611_auto_inspection_v3_0_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v3.0.1',
    submitTime: '2026-06-11 15:29:29',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '自动化巡检菜单参数修复',
    summary: '修复自动化巡检菜单 query 参数格式不符合若依前端 JSON 解析规则，导致已登录用户进入首页后页面空白的问题。',
    changes: [
      '将巡检模板、巡检目标、巡检计划、巡检记录四个菜单的 query 从 tab=xxx 修正为 JSON 字符串。',
      '同步修复 v3.0.0 独立升级脚本、support_v1.sql 和 support_deploy_all.sql，避免后续部署再次写入错误菜单数据。',
      '新增 v3.0.1 独立升级脚本，可直接修复已执行 v3.0.0 脚本的环境。',
      '已同步修复本地 rynew 数据库 sys_menu 中 2301-2304 菜单记录。'
    ],
    scope: ['自动化巡检', '菜单权限', '若依动态路由', 'SQL脚本', '版本记录'],
    database: '不改表结构，仅更新 sys_menu 中 2301、2302、2303、2304 四条自动化巡检菜单的 query 字段为合法 JSON。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260611_auto_inspection_menu_query_v3_0_1.sql',
      'WDF100.0/sql/support_upgrade_20260611_auto_inspection_v3_0_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v3.0.0',
    submitTime: '2026-06-11 15:04:36',
    level: 'major',
    levelLabel: '大版本',
    tagType: 'danger',
    title: '自动化巡检抽象化重构',
    summary: '将原TIM系统巡检升级为独立的自动化巡检一级模块，把固定7项巡检重构为工具、目标、模板、计划和报告的通用巡检体系。',
    changes: [
      '新增“自动化巡检”一级目录，与现场融合管理同级，包含巡检模板、巡检目标、巡检计划和巡检记录四个入口。',
      '将Kafka积压、海康接口数量、FTP目录文件数、服务器目录文件数、服务器磁盘使用率抽象为内置巡检工具。',
      '新增步骤式巡检模板，用户可在同一模板中多次复用同一工具，自由配置目标、参数、阈值、比较规则和展示名称。',
      '巡检计划绑定模板，执行周期改为可视化配置，由前端生成Cron并同步若依sys_job定时任务。',
      '每次手动或定时执行都会保存模板步骤快照、目标明细结果、异常摘要，并支持Excel和Word报告导出。',
      '旧TIM系统巡检表不删除，旧菜单入口隐藏，避免影响历史数据。'
    ],
    scope: ['自动化巡检', '巡检模板', '巡检目标', '巡检计划', '巡检报告', '若依定时任务', '菜单权限', 'SQL脚本', '版本记录'],
    database: '新增 sup_auto_inspection_tool、sup_auto_inspection_target、sup_auto_inspection_template、sup_auto_inspection_template_step、sup_auto_inspection_template_step_target、sup_auto_inspection_plan、sup_auto_inspection_record、sup_auto_inspection_step_result、sup_auto_inspection_target_result 九张表；新增 support:autoInspection:* 菜单按钮权限；隐藏旧TIM系统巡检菜单。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260611_auto_inspection_v3_0_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v2.6.1',
    submitTime: '2026-06-11 12:43:58',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: 'TIM巡检计划弹窗优化',
    summary: '优化巡检计划配置弹窗的信息架构和显示效果，解决7项配置全部展开导致的遮挡、截断和阅读负担问题。',
    changes: [
      '将巡检计划弹窗从7项全部展开改为左侧巡检项导航、右侧单项配置，降低页面密度。',
      '巡检项列表展示启停状态和目标数量，用户可以先扫描计划覆盖范围，再进入具体配置。',
      '右侧配置区突出巡检目标、告警阈值、比较规则、时间窗口和超时时间，避免表单控件互相挤压。',
      '启用巡检项但未选择目标时给出页面内预警，提前暴露配置风险。',
      '优化弹窗高度、滚动区域和底部按钮位置，避免小屏下内容被截断。'
    ],
    scope: ['TIM系统巡检', '巡检计划', '计划弹窗', '前端交互', '版本记录'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v2.6.0',
    submitTime: '2026-06-11 12:23:13',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'primary',
    title: 'TIM巡检计划配置',
    summary: '将TIM巡检计划做成可配置能力，并接入若依自带定时任务，实现计划化自动巡检。',
    changes: [
      '新增巡检计划页签，可维护计划名称、Cron表达式、启停状态和报告样式。',
      '每个计划可单独配置7项巡检的启用状态、告警阈值、比较规则、时间窗口、超时时间和巡检目标。',
      '计划保存后自动同步若依sys_job定时任务，启停和删除计划时同步暂停、恢复或删除对应任务。',
      '定时执行时按照计划快照读取巡检项目和目标，巡检记录保留计划名称、计划ID和报告样式。',
      'Word巡检报告按计划样式生成简要、标准、明细或异常报告。'
    ],
    scope: ['TIM系统巡检', '巡检计划', '若依定时任务', 'Cron配置', '报告样式', 'SQL脚本', '版本记录'],
    database: '新增 sup_tim_inspection_plan、sup_tim_inspection_plan_item、sup_tim_inspection_plan_target 三张表；sup_tim_inspection 增加 plan_id、plan_name、report_style 字段；新增 support:timInspection:plan 权限。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260611_tim_inspection_plan_v2_6_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v2.5.1',
    submitTime: '2026-06-11 10:37:13',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: 'TIM巡检分页与目标测试修复',
    summary: '修复TIM系统巡检记录分页、编辑目标测试旧配置、测试连接被阈值误判失败的问题，提升配置验证的准确性。',
    changes: [
      '巡检记录列表查询不再在分页上下文中初始化默认配置，避免PageHelper分页被内部查询提前消费。',
      '编辑已有巡检目标时，测试连接会使用当前表单中尚未保存的地址、路径、Topic、服务器、请求体等配置。',
      '目标测试接口只验证连接、认证和取数能力，不再因为阈值超限返回失败；正式巡检仍按阈值判断异常。',
      '巡检项配置保存增加加载态和防重复提交，保存失败后自动刷新回后端真实配置。'
    ],
    scope: ['TIM系统巡检', '巡检记录分页', '目标配置测试', '配置保存交互', '版本记录'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v2.5.0',
    submitTime: '2026-06-10 18:44:23',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'primary',
    title: 'TIM系统巡检可配置迁入',
    summary: '新增TIM系统巡检中心，将旧项目中写死的7项巡检改造为可开关、可配置目标、可实时生效的巡检能力。',
    changes: [
      '新增TIM系统巡检页面，包含巡检记录和巡检配置两个页签，可手动执行巡检、查看详情、导出Excel和生成当日Word结果。',
      '7项巡检配置全部数据库化，支持单独启停、配置阈值、比较规则、统计窗口和超时时间。',
      '巡检目标改为可维护清单，支持HTTP计数、FTP目录、SFTP目录、Kafka积压和复用现有服务器的磁盘巡检。',
      '每次巡检保存当时的配置快照和目标明细结果，避免后续配置调整影响历史记录回溯。',
      '新增Quartz任务入口，定时巡检与手动巡检共用同一套最新配置读取和结果落库逻辑。'
    ],
    scope: ['TIM系统巡检', '巡检配置中心', '目标配置', '巡检记录', '定时任务', '菜单权限', 'SQL脚本', '版本记录'],
    database: '新增 sup_tim_inspection、sup_tim_inspection_item、sup_tim_inspection_item_config、sup_tim_inspection_target、sup_tim_inspection_target_result 五张表，并新增 support:timInspection:* 菜单按钮权限。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260610_tim_inspection_v2_5_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v2.4.2',
    submitTime: '2026-06-10 12:31:33',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: 'Datafusion权限字符绑定修复',
    summary: '按若依权限模型修复 datafusion 权限字符分配问题，确保首页依据 permissions 中的 datafusion 展示现场融合工作台。',
    changes: [
      '确认 datafusion 为 sys_menu.perms 权限字符，并将首页判断恢复为只匹配 permissions 中的 datafusion。',
      '新增升级脚本，将 sys_menu.perms=datafusion 的菜单权限自动绑定给 role_key=datafusion 的角色。',
      '同步更新 support_v1.sql 和 support_deploy_all.sql，后续新环境部署时会自动补齐 datafusion 角色权限关系。'
    ],
    scope: ['首页', '权限分流', 'Datafusion权限字符', '角色菜单关系', 'SQL脚本', '版本记录'],
    database: '不修改业务表结构；向 sys_role_menu 补充 role_key=datafusion 与 perms=datafusion 的缺失权限关系。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260610_datafusion_permission_binding_v2_4_2.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v2.4.1',
    submitTime: '2026-06-10 12:20:14',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: 'Datafusion首页权限识别修复',
    summary: '修复 datafusion 账号登录后仍进入通用首页的问题，兼容角色和权限两种授权数据来源。',
    changes: [
      '首页 datafusion 判断从仅匹配 permissions 调整为同时匹配 roles 和 permissions 中的 datafusion 标识。',
      '保持 vehiclealarm 首页优先级不变，同时拥有 vehiclealarm 和 datafusion 的用户仍优先展示车辆告警首页。',
      '修复角色为 datafusion、权限列表为 support:* 的账号无法看到现场融合工作台的问题。'
    ],
    scope: ['首页', '权限分流', 'Datafusion工作台', '版本记录'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v2.4.0',
    submitTime: '2026-06-10 10:56:20',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'primary',
    title: 'Datafusion用户首页',
    summary: '为拥有 datafusion 权限字符的用户新增现场融合工作台首页，聚焦我的现场、全站最新修改和快速配置入口。',
    changes: [
      '首页增加权限分流，vehiclealarm 用户继续优先展示车辆告警首页，datafusion 用户展示现场融合工作台。',
      '新增现场融合首页聚合接口，一次返回我的相关现场、全站最新增删改动态和首页统计数据。',
      '我的相关现场支持按现场名称、编码和地区搜索，卡片展示主平台、子平台、服务器、人员和最近操作摘要。',
      '全站最新修改展示所有用户的新增、修改、删除记录，并支持点击查看操作详情和跳转到现场配置画布。',
      '现场新增和修改时补充写入 createBy / updateBy，提升后续首页归属识别稳定性。'
    ],
    scope: ['首页', '现场融合管理', '权限分流', '现场聚合接口', '操作记录', 'SQL索引', '版本记录'],
    database: '不新增业务表；新增 sup_site 创建/修改人索引和 sup_change_log 首页查询索引。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260610_site_dashboard_v2_4_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v2.3.1',
    submitTime: '2026-06-09 23:29:52',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '现场留言板稳定性修复',
    summary: '按代码审查结果补齐留言板数据隔离、轮询性能、详情刷新、现场删除清理和导入导出遗漏。',
    changes: [
      '留言列表和发布接口统一校验现场ID与现场存在性，避免不带现场查询到全站留言或向不存在现场写入脏数据。',
      '新增留言 latest 轻量接口，前端仅在留言、弹幕或详情场景启动轮询，并改为按 messageId 增量刷新。',
      '留言详情抽屉打开后随轮询同步刷新，切换现场、关闭弹窗、切到后台或离开留言场景时自动停止轮询。',
      '删除现场时同步清理该现场留言，现场完整导出导入新增“留言”工作表并重建到新现场。',
      '补充 sup_site_message 查询索引，匹配按现场、状态和 messageId 的最新留言查询。'
    ],
    scope: ['现场留言板', '留言详情', '弹幕刷新', '现场导入导出', '现场删除', 'SQL索引', '版本记录'],
    database: '不修改业务表结构；新增 idx_sup_site_message_site_status_id(site_id, status, message_id) 索引。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260609_site_message_fixes_v2_3_1.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v2.3.0',
    submitTime: '2026-06-09 21:05:10',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'primary',
    title: '现场留言板',
    summary: '在现场配置画布右侧新增现场留言板，支持用户发布留言、实时刷新查看，并可在画布中打开弹幕展示。',
    changes: [
      '现场配置画布右侧新增留言板卡片，展示当前现场最近留言数量、发布入口和最新留言列表。',
      '新增留言弹幕开关，打开后最近留言会在画布区域轻量飘过，不影响节点点击和右键操作。',
      '新增留言详情抽屉，支持按现场分页查看全部留言、关键词搜索和刷新。',
      '后端新增现场留言表和留言查询、发布接口，留言发布人保存平台用户昵称，内容限制在300字以内。'
    ],
    scope: ['现场画布', '现场留言板', '弹幕展示', '留言详情', '菜单权限', '版本记录'],
    database: '新增 sup_site_message 现场留言板表，并新增 support:message:list、support:message:add 按钮权限。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260609_site_message_board_v2_3_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v2.2.1',
    submitTime: '2026-06-04 10:22:34',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '服务器导入模板格式统一',
    summary: '将服务器批量导入模板从 CSV 调整为 xlsx，并要求导入文件与模板表头完全一致后再进入确认清单。',
    changes: [
      '服务器批量导入弹窗移除 CSV 和文本粘贴入口，改为仅选择 xlsx 模板文件。',
      '下载模板改为后端生成 xlsx 文件，模板表头与导入解析规则共用同一套字段定义。',
      '上传导入时先由后端解析 xlsx 并校验表头，解析成功后继续进入现有服务器清单确认页。',
      '导入文件中的系统密码仍按明文读取，确认保存后沿用现有服务器密码加密落库流程。'
    ],
    scope: ['服务器管理', '批量导入', 'Excel模板', '版本记录'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v2.2.0',
    submitTime: '2026-06-04 09:16:18',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'primary',
    title: '现场完整导入导出',
    summary: '现场管理导出升级为按选中现场生成完整配置数据包，并新增 zip 导入能力，可将导出的现场数据新建为独立现场。',
    changes: [
      '现场管理导出改为按已选现场生成 zip 压缩包，每个现场对应一个独立 xlsx 文件。',
      '每个 xlsx 固定包含现场、主平台、子平台、页面、服务器、组织、人员和关系数据，保留源 ID 用于导入时重建关联。',
      '新增现场数据 zip 导入入口，导入时每个 xlsx 自动新建一个现场，现场名称追加导入副本标识，现场编码重新生成。',
      '服务器密码和页面登录密码按明文写入导出文件，导入后重新使用系统敏感信息加密服务保存。',
      '导入完成后按新现场写入一条操作记录摘要，避免平台、服务器、人员等明细记录刷屏。'
    ],
    scope: ['现场管理', '完整导出', 'zip导入', 'Excel数据包', '菜单权限', '版本记录'],
    database: '不修改业务表结构，仅新增现场导入按钮权限 support:site:import。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260604_site_import_permission_v2_2_0.sql',
      'WDF100.0/sql/support_v1.sql',
      'WDF100.0/sql/support_deploy_all.sql'
    ]
  },
  {
    version: 'v2.1.7',
    submitTime: '2026-06-04 00:06:54',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '画布动态连线重构',
    summary: '将现场融合关系画布的连线方式从固定 CSS 伪元素改为 SVG 动态路径，提升横向树和纵向树下的连接连续性。',
    changes: [
      '新增画布 SVG 连接线层，根据现场节点和主平台节点的真实位置动态生成主干、汇聚线和分支线。',
      '横向树改为现场右侧出线、竖向主干汇聚、分支连接各主平台；纵向树改为现场底部出线、横向主干汇聚、分支下接各主平台。',
      '停用旧的伪元素拼接线条，避免布局切换、节点宽高变化或主平台增删时出现断线、错位和重叠。',
      '补充窗口尺寸、全屏、缩放、筛选和布局切换后的连线自动刷新机制。'
    ],
    scope: ['现场画布', '横向树布局', '纵向树布局', '动态连线', '版本记录'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v2.1.6',
    submitTime: '2026-06-03 23:49:35',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '纵向树连线层次优化',
    summary: '优化纵向树模式下现场与多个主平台之间的连接线层次，避免总线贴边和分支线重叠。',
    changes: [
      '将纵向树连接线拆分为现场下接线、横向汇聚总线和主平台分支线三段，关系表达更清晰。',
      '增加现场框与横向总线之间的留白距离，避免连接线压在现场信息块底边。',
      '主平台增删后继续由统一的连接线变量驱动，保持纵向树布局的动态适配效果。'
    ],
    scope: ['现场画布', '纵向树布局', '树状连线', '版本记录'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v2.1.5',
    submitTime: '2026-06-03 23:35:35',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '纵向树主平台横向展开',
    summary: '调整纵向树模式下多个主平台的排列关系，让主平台之间横向展开，同时保留主平台内部纵向层级。',
    changes: [
      '纵向树模式下，多个主平台由纵向堆叠改为横向排列，符合现场在上、平台横向展开的树状结构。',
      '保留每个主平台内部的主平台、人员层、子平台层、服务器汇总纵向排布。',
      '优化纵向树连接线，改为顶部横向总线连接各主平台，并保留动态连接点效果。'
    ],
    scope: ['现场画布', '纵向树布局', '主平台排列', '树状连线'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v2.1.4',
    submitTime: '2026-06-03 23:28:28',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '纵向树布局精修',
    summary: '优化画布布局切换开关视觉，并修复纵向树下主平台内部层级仍横向排布的问题。',
    changes: [
      '将“横向树 / 纵向树”切换控件改为自定义分段按钮，增加方向图标、说明文字和更清晰的选中态。',
      '纵向树模式下，主平台、人员层、子平台层、服务器汇总改为自上而下的纵向排布。',
      '纵向树模式下子平台网格最多一行显示两个，减少横向占用并保持画布阅读方向一致。',
      '纵向树内各层分割线改为横向分隔，和纵向阅读流保持一致。'
    ],
    scope: ['现场画布', '布局切换', '纵向树样式', '子平台网格'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v2.1.3',
    submitTime: '2026-06-03 23:01:04',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '画布布局方向切换',
    summary: '现场融合关系画布新增横向树和纵向树切换，兼顾不同用户对现场与主平台关系展示方向的偏好。',
    changes: [
      '画布工具栏新增“横向树 / 纵向树”分段开关，支持现场与主平台关系方向切换。',
      '横向树保留现场在左、主平台纵向排列的既有形态；纵向树调整为现场在上、主平台在下的树状结构。',
      '切换布局时自动关闭右键菜单、重置画布视角并触发重新渲染，减少缩放偏移导致的显示错位。',
      '两种布局共用节点颜色、层级标签、连线动效和响应式工具栏样式，保证视觉风格一致。'
    ],
    scope: ['现场画布', '布局切换', '树状连线', '前端样式'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v2.1.2',
    submitTime: '2026-06-03 22:47:08',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '画布版本号同步',
    summary: '在现场融合关系画布上展示最新功能版本号，并让版本记录和画布共用同一份版本数据。',
    changes: [
      '将版本记录数据抽成共享模块，版本页和现场画布同时读取最新版本。',
      '现场融合关系画布标题区新增版本号标签，自动展示当前最新版本。',
      '版本记录增加提交时间字段，精确到时分秒。'
    ],
    scope: ['现场画布', '版本记录页', '版本数据源'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v2.1.1',
    submitTime: '2026-06-03 22:41:32',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '版本记录页面精简',
    summary: '移除版本规则展示，把每次修改涉及的数据库脚本直接写入对应版本记录。',
    changes: [
      '移除页面中的版本记录规则说明区块，减少非业务信息展示。',
      '每条版本记录增加数据库修改脚本清单，涉及数据库的改动直接列出 SQL 文件路径。',
      '没有数据库变更的版本显示“无数据库脚本”，便于部署时快速判断。'
    ],
    scope: ['版本记录页', '数据库脚本清单', '前端展示'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v2.1.0',
    submitTime: '2026-06-03 22:30:15',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'primary',
    title: '新增功能版本记录页',
    summary: '新增现场融合管理版本记录页面，用于沉淀每次功能修改、版本号和影响范围。',
    changes: [
      '新增“版本记录”页面，展示当前版本、历史变更和影响范围。',
      '补充现场融合管理菜单脚本，部署后可在模块菜单中访问版本记录。'
    ],
    scope: ['前端页面', '菜单脚本', '部署说明'],
    database: '不新增业务表，仅新增 sys_menu 菜单项 support/version/index。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260603_version_page.sql',
      'WDF100.0/sql/support_deploy_all.sql',
      'WDF100.0/sql/support_v1.sql'
    ]
  },
  {
    version: 'v2.0.2',
    submitTime: '2026-06-03 22:10:46',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '服务器管理交互精修',
    summary: '对服务器管理弹窗内的按钮位置、文案和填写区域进行统一优化。',
    changes: [
      '将“批量导入”移动到左侧添加服务器区域，与“单个添加”“批量添加”形成同级入口。',
      '优化三个添加入口的排布和选中态，下方填写区域增加独立面板和统一字段间距。',
      '将“导出明文”改为“导出服务器”，“明文”改为“显示密码”，“删除资产”改为“删除服务器”。'
    ],
    scope: ['服务器管理弹窗', '批量导入入口', '按钮文案', '前端样式'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v2.0.1',
    submitTime: '2026-06-03 21:37:22',
    level: 'patch',
    levelLabel: '修订版本',
    tagType: 'success',
    title: '批量添加确认流程完善',
    summary: '批量添加服务器前增加确认清单，先校验重复和归属，再由用户确认保存。',
    changes: [
      '批量添加后先进入“服务器清单校验”页，展示待新增、已存在、可复用数量。',
      '数据库已存在的服务器会标注已添加到哪个子平台下。',
      '增加“复用已有服务器并绑定到当前子平台”开关，避免用户不清楚跳过还是复用。',
      '确认清单支持编辑、删除行，确认后才保存到数据库。'
    ],
    scope: ['服务器批量添加', '重复校验', '复用绑定', '确认页交互'],
    database: '无新增业务表，依赖已有服务器和平台关系数据做校验。',
    scripts: []
  },
  {
    version: 'v2.0.0',
    submitTime: '2026-06-03 20:48:11',
    level: 'major',
    levelLabel: '大版本',
    tagType: 'danger',
    title: '服务器管理模式重构',
    summary: '服务器从“先建资产再关联”的复杂流程调整为在画布管理入口中直接维护。',
    changes: [
      '点击管理服务器后支持单个添加、批量添加、批量导入、批量导出、全选和批量删除。',
      '批量添加支持单个 IP、分号分隔 IP 列表、IP 段、SSH 端口、系统账号和系统密码。',
      '画布上服务器仅展示数量，点击后进入统一管理弹窗查看和维护明细。',
      '取消前端上的服务器移除关联入口，用户主要维护服务器本身。'
    ],
    scope: ['服务器管理流程', '画布服务器层', '导入导出', '批量维护'],
    database: '涉及 sup_server.ssh_port 字段和 site_id + server_address 索引，部署脚本已包含。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260603_server_ssh_and_scope.sql',
      'WDF100.0/sql/support_deploy_all.sql',
      'WDF100.0/sql/support_v1.sql'
    ]
  },
  {
    version: 'v1.4.0',
    submitTime: '2026-06-03 18:25:40',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'primary',
    title: '画布树状结构与视觉优化',
    summary: '重整现场、主平台、子平台、人员、页面和服务器之间的画布呈现关系。',
    changes: [
      '现场与主平台之间改为横向树状结构，主平台纵向排列并动态调整连接线。',
      '增加全屏画布按钮，全屏状态下保留全部配置操作。',
      '优化人员层、子平台层、服务器层之间的分割线、节点密度和关系线表现。',
      '子平台过多时支持换行排布，减少单个主平台展示过长的问题。'
    ],
    scope: ['现场画布', '树状布局', '全屏工作台', '节点视觉'],
    database: '无数据库结构变化。',
    scripts: []
  },
  {
    version: 'v1.3.0',
    submitTime: '2026-06-03 17:09:18',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'primary',
    title: '用户修改记录上线',
    summary: '新增现场融合管理增删改操作记录，并在现场画布边侧展示最近操作。',
    changes: [
      '记录现场融合管理内新增、修改、删除操作，不记录查询动作。',
      '记录用户昵称、操作对象、摘要和详细内容，支持点击查看详情。',
      '拥有 datafusion 权限字符的用户具备现场融合管理全部权限。'
    ],
    scope: ['操作记录', '权限策略', '画布侧栏'],
    database: '新增 sup_change_log 表，并增加 detail_content 字段记录操作详情。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260602_change_log.sql',
      'WDF100.0/sql/support_upgrade_20260602_change_log_detail.sql',
      'WDF100.0/sql/support_upgrade_20260602_all_safe.sql',
      'WDF100.0/sql/support_deploy_all.sql',
      'WDF100.0/sql/support_v1.sql'
    ]
  },
  {
    version: 'v1.2.0',
    submitTime: '2026-06-03 15:42:03',
    level: 'minor',
    levelLabel: '小版本',
    tagType: 'primary',
    title: '平台和人员基础能力扩展',
    summary: '补充主平台网络环境、联系人角色和所属组织维护能力。',
    changes: [
      '主平台新增网络环境字段，内置公安网、图像网、政务网、二类区、党政军、私网。',
      '不同网络环境使用不同颜色标识，内置网络环境不可删除和修改。',
      '新增联系人时支持角色新增和配置，角色值由系统生成。',
      '新增联系人页面增加所属组织的新增和编辑按钮，画布人员标签展示人员角色。'
    ],
    scope: ['主平台', '联系人', '组织', '字典配置', '画布标签'],
    database: '涉及 sup_platform.network_env、sup_contact.role_type 和相关字典数据。',
    scripts: [
      'WDF100.0/sql/support_upgrade_20260529_platform_network_env.sql',
      'WDF100.0/sql/support_upgrade_20260529_contact_role_dict.sql',
      'WDF100.0/sql/support_upgrade_20260602_all_safe.sql',
      'WDF100.0/sql/support_deploy_all.sql',
      'WDF100.0/sql/support_v1.sql'
    ]
  }
]

export const latestSupportRelease = releaseNotes[0]
