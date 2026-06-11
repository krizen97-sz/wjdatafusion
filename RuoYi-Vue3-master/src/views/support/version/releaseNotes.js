export const releaseNotes = [
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
