export const mascotDialogConfig = {
  enabled: true,
  idleInterval: 12000,
  messages: {
    hoverSelf: '{nickname}，我在左下角值守，菜单收起或移动端会自动让出空间。',
    modelReady: '{nickname}，本地 Live2D 模型已上线，我会继续盯着现场和巡检状态。',
    modelError: '本地模型加载失败，请检查离线包里的 live2d 资源是否完整。',
    bodyTap: '{nickname}，收到，我切到当前页面的操作指引。'
  },
  play: {
    enabled: true,
    enter: '我变大啦。现在是摸摸、戳戳、猜我下一句的时间。',
    exit: '我缩小回角落啦，下次再一起玩。',
    hint: '点点不同位置，我会用不同语气回应你。',
    hover: '你靠近啦，我假装没有紧张。',
    regions: {
      head: [
        '头顶被发现了。给你一朵小云，今天也要轻一点。',
        '摸头加成启动，我现在心情很好。',
        '这里是灵感开关，轻轻一点就会亮。'
      ],
      face: [
        '被你看到了。那我也眨一下眼回应你。',
        '靠这么近说话，会有点不好意思。',
        '我正在认真盯着你，轮到你做个表情。'
      ],
      body: [
        '这里不能乱戳，我会轻轻躲一下。',
        '哎呀，差点被你点得跳起来。',
        '收到一个轻拍，我回你一个小小鞠躬。'
      ],
      hand: [
        '要牵手吗？我可以陪你晃一晃。',
        '碰到手边啦，击掌成功。',
        '我们来玩石头剪刀布，我先偷偷出布。'
      ],
      bubble: [
        '你点到我的话啦，它们会自己蹦一下。',
        '这颗气泡今天很活泼，轻轻一碰就冒出来。',
        '悄悄告诉你，我把下一句话藏在气泡边上。'
      ],
      around: [
        '点偏啦，不过我假装这是一个暗号。',
        '我在这里转个小圈，你再试试。',
        '空气被你点醒了，我也跟着精神起来。'
      ],
      idle: [
        '你在旁边忙，我在这里晃脚等你。',
        '我悄悄探头看一眼，又缩回来了。',
        '你点哪里，我就把注意力跟过去一点点。'
      ]
    }
  },
  greetings: [
    { before: 6, message: '夜间值守中，异常信息优先看巡检记录和服务状态。' },
    { before: 9, message: '早上好，先看今日运行状态和待处理异常。' },
    { before: 12, message: '上午适合核对现场、平台、服务器配置链路。' },
    { before: 18, message: '下午继续盯紧现场融合管理和自动化巡检结果。' },
    { before: 24, message: '今天的变更记得留痕，版本记录中心以后能帮上忙。' }
  ],
  topics: [
    {
      key: 'site',
      name: '现场融合',
      enabled: true,
      messages: [
        '现场融合管理建议按“现场、主平台、子平台、服务器”顺序核对。',
        '进入现场配置前，先确认组织、联系人和现场对接人是否完整。',
        '现场融合关系画布可以看清平台和服务器挂载关系，节点异常先从这里找。',
        '新增主平台后，记得补齐子平台入口和服务器归属。',
        '设备资产台账最好补齐型号、序列号、安装位置和质保到期时间。',
        '画布节点太密时，先重置视图，再切换横向或纵向布局。'
      ]
    },
    {
      key: 'inspection',
      name: '自动巡检',
      enabled: true,
      messages: [
        '自动化巡检先选测试目标，再配置工具和巡检步骤。',
        'HTTP 健康检测适合看服务接口，TCP 端口检测适合看基础连通性。',
        '服务器服务状态检测要对准服务器，不要把现场、平台、服务器层级混在一起。',
        '巡检记录里的调用信息很重要，排错时别只看成功或失败。',
        '模板改动不会改写历史报告，报告快照可以放心回溯。',
        '今日异常要优先处理，再看最近 7 天趋势。'
      ]
    },
    {
      key: 'ops',
      name: '平台运维',
      enabled: true,
      messages: [
        '版本记录中心可以回查菜单、接口、SQL 和前端改动。',
        '如果页面报错，先看网络请求，再看后端日志，最后定位到服务和表。',
        '白名单管理先确认名单状态和车牌规则，再处理导入导出。',
        '表格批量操作前，先确认筛选条件和当前页数据范围。',
        '离线部署时，前端资源必须全部随包发布，不能依赖外网。',
        '变更上线后，记得用真实账号做一次菜单和核心页面烟测。'
      ]
    }
  ],
  guides: [
    {
      key: 'home',
      match: ['/index'],
      title: '首页工作台',
      enabled: true,
      steps: [
        '{nickname}，首页先看待处理异常和快捷入口，确认今天要优先处理的现场或巡检任务。',
        '{nickname}，如果看到最新修改或异常趋势，建议顺手打开版本记录或巡检记录回溯来源。',
        '{nickname}，从首页进入模块时先确认角色权限，避免误把无数据当成接口异常。'
      ]
    },
    {
      key: 'profile',
      match: ['/user/profile'],
      title: '个人中心',
      enabled: true,
      steps: [
        '{nickname}，这里适合先核对头像、昵称和联系方式，后续提示也会优先使用你的昵称称呼。',
        '{nickname}，修改密码前确认新密码规则和当前登录环境，提交后最好重新登录验证一次。',
        '{nickname}，个人信息变更会影响平台展示和消息识别，保存前再扫一眼关键字段。'
      ]
    },
    {
      key: 'autoInspection',
      match: ['/support/autoInspection', '/support/inspection', '/inspection'],
      title: '自动化巡检',
      enabled: true,
      steps: [
        '{nickname}，先确认巡检目标归属现场、平台还是服务器，层级选错会直接影响工具配置。',
        '{nickname}，编辑模板时按步骤补齐工具、阈值和账号，服务器类工具优先绑定服务器目标。',
        '{nickname}，计划启用前跑一次手动验证，再到巡检记录里看失败子项和返回信息。',
        '{nickname}，排查异常时别只看成功失败，调用地址、耗时、错误码和最近变更都要一起看。'
      ]
    },
    {
      key: 'timInspection',
      match: ['/support/timInspection'],
      title: '巡检配置',
      enabled: true,
      steps: [
        '{nickname}，这个页面重点核对巡检模板、执行计划和巡检目标之间的绑定关系。',
        '{nickname}，切换页签时先看当前配置属于哪类巡检，避免把目标、计划和历史记录混在一起改。',
        '{nickname}，涉及定时任务时要确认周期、启停状态和下一次执行时间，保存后回到记录页验证。'
      ]
    },
    {
      key: 'site',
      match: ['/support/site'],
      title: '现场管理',
      enabled: true,
      steps: [
        '{nickname}，现场列表先看所属组织、负责人和最近更新时间，再决定进入配置还是查看关系画布。',
        '{nickname}，新增现场时把现场名称、地址、联系人和网络环境补齐，后续平台和服务器才好挂载。',
        '{nickname}，关系画布里如果出现孤立节点，优先回到现场配置表修正归属关系。'
      ]
    },
    {
      key: 'platform',
      match: ['/support/platform'],
      title: '平台管理',
      enabled: true,
      steps: [
        '{nickname}，主平台和子平台要先分清上下级，新增前确认接口类型、厂家和所属现场。',
        '{nickname}，编辑平台信息时关注连接地址、账号来源和启用状态，它们会影响现场融合链路。',
        '{nickname}，平台变更后建议打开对应现场画布，确认主平台、子平台和服务器挂载方向没有跑偏。'
      ]
    },
    {
      key: 'supportServer',
      match: ['/support/server'],
      title: '服务器管理',
      enabled: true,
      steps: [
        '{nickname}，服务器页面先看归属现场、IP、账号授权和服务状态，定位问题会快很多。',
        '{nickname}，新增或导入服务器时确认账号口径一致，涉及密码明文展示要注意权限和留痕。',
        '{nickname}，服务状态检测失败时先看网络连通，再看端口、服务名和服务器凭据。'
      ]
    },
    {
      key: 'org',
      match: ['/support/org'],
      title: '组织人员',
      enabled: true,
      steps: [
        '{nickname}，组织人员用于串起现场负责人和对接人，先保证组织层级和联系方式准确。',
        '{nickname}，调整人员归属后建议回到现场配置确认负责人字段有没有同步到正确现场。',
        '{nickname}，删除组织或人员前先检查是否被现场、平台或操作记录引用。'
      ]
    },
    {
      key: 'whitelistFilterData',
      match: ['/whitelist/filterData'],
      title: '过滤数据',
      enabled: true,
      steps: [
        '{nickname}，这里先按现场、时间和来源筛选违法图片或过滤记录，别一上来就做批量处理。',
        '{nickname}，查看异常数据时保留车牌、抓拍时间、图片来源和现场线索，方便后续复核。',
        '{nickname}，批量处理前确认当前筛选范围，避免把只是当前页的数据误判成全部数据。'
      ]
    },
    {
      key: 'whitelistPlate',
      match: ['/whitelist/plate'],
      title: '白名单车牌',
      enabled: true,
      steps: [
        '{nickname}，新增车牌前先确认车牌格式、名单状态和生效范围，避免重复规则进入名单。',
        '{nickname}，导入后优先看失败行和重复车牌，确认无误再继续发布或同步。',
        '{nickname}，查询结果里要同时看车牌、状态、所属现场和更新时间，方便判断是否需要调整。'
      ]
    },
    {
      key: 'version',
      match: ['/support/version', '/version'],
      title: '版本记录',
      enabled: true,
      steps: [
        '{nickname}，先用大版本筛选锁定范围，例如只看 v3.6，或者快速定位 v3.4 以下改动。',
        '{nickname}，再用模块标签缩小到自动化巡检、现场融合、白名单或页面显示优化。',
        '{nickname}，上线交接时重点看数据库、脚本和权限说明，纯前端优化也要确认是否已打小版本提交。',
        '{nickname}，查问题时可以按提交时间倒序看最近改动，再结合当前页面模块标签回溯。'
      ]
    },
    {
      key: 'systemUserAuth',
      match: ['/system/user-auth'],
      title: '用户角色分配',
      enabled: true,
      steps: [
        '{nickname}，分配角色前先确认用户所属部门和岗位，别只看账号名称。',
        '{nickname}，勾选角色时重点看角色权限范围，保存后建议让用户重新登录刷新菜单。',
        '{nickname}，如果页面权限没生效，回到用户管理检查状态，再看角色和菜单授权。'
      ]
    },
    {
      key: 'systemRoleAuth',
      match: ['/system/role-auth'],
      title: '角色用户分配',
      enabled: true,
      steps: [
        '{nickname}，给角色分配用户时先确认角色的数据范围，避免把权限扩散给不相关人员。',
        '{nickname}，批量选择用户前先筛选部门和账号状态，减少误授权。',
        '{nickname}，授权完成后回到角色管理核对菜单权限和数据权限是否一致。'
      ]
    },
    {
      key: 'systemDictData',
      match: ['/system/dict-data'],
      title: '字典数据',
      enabled: true,
      steps: [
        '{nickname}，字典数据改动会影响下拉项和状态展示，先确认当前字典类型再新增。',
        '{nickname}，标签样式、排序和值都要一并检查，避免页面显示和接口值不一致。',
        '{nickname}，保存后如果页面仍旧显示旧值，记得返回字典管理刷新缓存。'
      ]
    },
    {
      key: 'systemUser',
      match: ['/system/user'],
      title: '用户管理',
      enabled: true,
      steps: [
        '{nickname}，用户管理先按部门树和账号状态筛选，确认对象后再做重置密码或分配角色。',
        '{nickname}，新增用户时账号、昵称、部门、角色和岗位要一起核对，昵称会用于平台称呼。',
        '{nickname}，禁用用户前先确认是否仍有现场对接或巡检维护职责。'
      ]
    },
    {
      key: 'systemRole',
      match: ['/system/role'],
      title: '角色管理',
      enabled: true,
      steps: [
        '{nickname}，角色管理的重点是菜单权限和数据范围，名称只是第一层识别。',
        '{nickname}，改角色前先看已有用户数量，避免一个小改动影响整组人员。',
        '{nickname}，新增角色后建议用测试账号验证菜单、按钮和数据权限是否都符合预期。'
      ]
    },
    {
      key: 'systemMenu',
      match: ['/system/menu'],
      title: '菜单管理',
      enabled: true,
      steps: [
        '{nickname}，菜单配置先看类型是目录、菜单还是按钮，路由和权限标识要对应当前页面。',
        '{nickname}，新增页面菜单时组件路径、显示状态和排序要一起检查，不然侧栏可能出现错位。',
        '{nickname}，按钮权限改动后要回到对应页面验证新增、编辑、删除等操作是否按权限展示。'
      ]
    },
    {
      key: 'systemDept',
      match: ['/system/dept'],
      title: '部门管理',
      enabled: true,
      steps: [
        '{nickname}，部门树会影响用户、数据范围和组织归属，拖动或调整前先确认父级。',
        '{nickname}，新增部门时负责人、电话和排序尽量补齐，方便现场对接链路继续使用。',
        '{nickname}，停用部门前先看是否还有用户或现场人员挂在下面。'
      ]
    },
    {
      key: 'systemPost',
      match: ['/system/post'],
      title: '岗位管理',
      enabled: true,
      steps: [
        '{nickname}，岗位编码和岗位名称要保持稳定，用户岗位会影响组织和职责识别。',
        '{nickname}，批量处理岗位前先筛选状态，避免误改已停用岗位。',
        '{nickname}，新增岗位后回到用户管理确认是否能正确选择和保存。'
      ]
    },
    {
      key: 'systemDict',
      match: ['/system/dict'],
      title: '字典管理',
      enabled: true,
      steps: [
        '{nickname}，字典管理先确认字典类型编码，很多页面下拉和状态标签都依赖它。',
        '{nickname}，新增或修改字典后记得刷新缓存，再回业务页面确认展示是否更新。',
        '{nickname}，删除字典前先确认没有页面、接口或历史数据还在引用对应值。'
      ]
    },
    {
      key: 'systemConfig',
      match: ['/system/config'],
      title: '参数设置',
      enabled: true,
      steps: [
        '{nickname}，参数设置改的是全局开关，先确认参数键名和影响范围再保存。',
        '{nickname}，涉及验证码、登录、默认配置等参数时，最好记录修改原因和回滚值。',
        '{nickname}，保存后如果没有立刻生效，先刷新缓存，再重新打开相关页面验证。'
      ]
    },
    {
      key: 'systemNotice',
      match: ['/system/notice'],
      title: '通知公告',
      enabled: true,
      steps: [
        '{nickname}，公告发布前先确认类型、状态和可见内容，避免草稿误发布。',
        '{nickname}，编辑通知时把标题写清楚，正文里尽量给出时间、范围和需要执行的动作。',
        '{nickname}，停用公告后刷新首页或通知入口，确认用户侧不再继续展示。'
      ]
    },
    {
      key: 'monitorJobLog',
      match: ['/monitor/job-log'],
      title: '调度日志',
      enabled: true,
      steps: [
        '{nickname}，调度日志先按任务名称、状态和执行时间定位，失败记录要看异常详情。',
        '{nickname}，排查定时任务时把 Cron、调用目标、耗时和异常堆栈一起看。',
        '{nickname}，如果失败集中在同一时间段，建议回查版本记录和服务器状态。'
      ]
    },
    {
      key: 'monitorOperlog',
      match: ['/monitor/operlog'],
      title: '操作日志',
      enabled: true,
      steps: [
        '{nickname}，操作日志先按人员、模块、时间和结果筛选，可以快速定位最近是谁改了什么。',
        '{nickname}，看详情时关注请求地址、参数、返回结果和耗时，必要时再去后端日志交叉验证。',
        '{nickname}，导出日志前确认筛选范围，避免生成过大文件或遗漏关键时间段。'
      ]
    },
    {
      key: 'monitorLogininfor',
      match: ['/monitor/logininfor'],
      title: '登录日志',
      enabled: true,
      steps: [
        '{nickname}，登录日志适合排查账号异常、验证码问题和登录地点变化。',
        '{nickname}，筛选失败记录时重点看登录名、IP、浏览器和失败原因。',
        '{nickname}，发现异常登录后先处理账号状态，再结合操作日志确认是否有后续操作。'
      ]
    },
    {
      key: 'monitorOnline',
      match: ['/monitor/online'],
      title: '在线用户',
      enabled: true,
      steps: [
        '{nickname}，在线用户页面先看账号、部门、登录 IP 和最后访问时间。',
        '{nickname}，强退用户前要确认是否正在做现场配置、巡检计划或白名单发布。',
        '{nickname}，排查会话问题时可以先刷新列表，再结合登录日志确认登录来源。'
      ]
    },
    {
      key: 'monitorJob',
      match: ['/monitor/job'],
      title: '定时任务',
      enabled: true,
      steps: [
        '{nickname}，定时任务先看启停状态、Cron 表达式和调用目标，别只看任务名称。',
        '{nickname}，新增任务前确认调用类或接口可用，再执行一次手动运行验证。',
        '{nickname}，修改周期后记得去调度日志看下一次执行是否按预期触发。'
      ]
    },
    {
      key: 'monitorDruid',
      match: ['/monitor/druid'],
      title: '数据源监控',
      enabled: true,
      steps: [
        '{nickname}，数据源监控重点看连接池、慢 SQL 和活跃连接，页面慢时可以先从这里切入。',
        '{nickname}，如果接口偶发超时，关注峰值连接、执行耗时和异常 SQL。',
        '{nickname}，排查完数据库后再回到对应业务页面复测，确认前端和接口状态恢复。'
      ]
    },
    {
      key: 'monitorServer',
      match: ['/monitor/server'],
      title: '服务监控',
      enabled: true,
      steps: [
        '{nickname}，服务监控先看 CPU、内存、磁盘和 JVM 状态，判断是不是资源压力导致页面异常。',
        '{nickname}，磁盘或内存接近阈值时，先暂停高风险批量操作，再联系后端处理。',
        '{nickname}，服务恢复后回到巡检或现场页面做一次真实操作验证。'
      ]
    },
    {
      key: 'monitorCache',
      match: ['/monitor/cache'],
      title: '缓存监控',
      enabled: true,
      steps: [
        '{nickname}，缓存监控先看 Redis 基础信息和 key 数量，判断是否有异常增长。',
        '{nickname}，字典、参数或验证码异常时，可以结合缓存命中和相关 key 排查。',
        '{nickname}，清理缓存前先确认影响范围，避免让在线用户的会话或配置突然失效。'
      ]
    },
    {
      key: 'toolGenEdit',
      match: ['/tool/gen-edit'],
      title: '生成配置',
      enabled: true,
      steps: [
        '{nickname}，修改生成配置时先确认表名、包路径和模块名，避免生成到错误目录。',
        '{nickname}，字段类型、查询方式和表单控件要逐项检查，后续页面质量全靠这里打底。',
        '{nickname}，保存后再回代码生成列表预览一次，确认生成结果和业务字段一致。'
      ]
    },
    {
      key: 'toolBuild',
      match: ['/tool/build'],
      title: '表单构建',
      enabled: true,
      steps: [
        '{nickname}，表单构建适合快速搭界面，先确定字段顺序和必填规则，再调整布局。',
        '{nickname}，复杂表单不要只看预览效果，还要确认生成代码里的校验和默认值。',
        '{nickname}，复制配置前先清理临时字段，避免把实验控件带到正式页面。'
      ]
    },
    {
      key: 'toolGen',
      match: ['/tool/gen'],
      title: '代码生成',
      enabled: true,
      steps: [
        '{nickname}，代码生成先同步表结构，再检查业务名、模块名、作者和生成路径。',
        '{nickname}，生成前预览 SQL、前端和接口文件，确认没有覆盖手工改过的代码。',
        '{nickname}，生成后记得跑构建或局部页面验证，别只看文件已经落盘。'
      ]
    },
    {
      key: 'toolSwagger',
      match: ['/tool/swagger'],
      title: '接口文档',
      enabled: true,
      steps: [
        '{nickname}，接口文档适合核对请求路径、参数和返回结构，前端报错时先看这里能不能对上。',
        '{nickname}，调试接口前确认登录态和权限，不然 401 或 403 会误导问题定位。',
        '{nickname}，发现文档和实际返回不一致时，记录接口名和版本，方便后端同步修正。'
      ]
    },
    {
      key: 'manageNovel',
      match: ['/manage/novel'],
      title: '内容管理',
      enabled: true,
      steps: [
        '{nickname}，内容管理先按标题、状态和更新时间筛选，确认对象后再编辑。',
        '{nickname}，编辑长内容时注意保存节奏，提交前检查标题、分类和展示状态。',
        '{nickname}，删除内容前确认是否仍被页面引用，避免前台出现空内容。'
      ]
    },
    {
      key: 'supportDefault',
      match: ['/support'],
      title: '现场融合模块',
      enabled: true,
      steps: [
        '{nickname}，现场融合模块建议按现场、平台、服务器、组织人员的顺序检查关系。',
        '{nickname}，遇到配置不一致时先打开关系画布，再回到对应表单修正。',
        '{nickname}，涉及账号、地址和服务器状态的改动，保存后都要做一次真实连通验证。'
      ]
    },
    {
      key: 'whitelistDefault',
      match: ['/whitelist'],
      title: '白名单模块',
      enabled: true,
      steps: [
        '{nickname}，白名单模块先确认数据类型，再看导入、筛选、发布或过滤处理入口。',
        '{nickname}，批量动作前务必确认筛选条件和操作范围，避免误处理跨现场数据。',
        '{nickname}，处理结果要保留来源、时间和状态，后续复核才不会断线。'
      ]
    },
    {
      key: 'systemDefault',
      match: ['/system'],
      title: '系统管理模块',
      enabled: true,
      steps: [
        '{nickname}，系统管理改的是权限、组织和基础参数，保存前先确认影响范围。',
        '{nickname}，用户、角色、菜单三类页面要联动检查，单点修改可能影响登录后的菜单。',
        '{nickname}，参数和字典调整后别忘了刷新缓存，再回业务页面验证。'
      ]
    },
    {
      key: 'monitorDefault',
      match: ['/monitor'],
      title: '系统监控模块',
      enabled: true,
      steps: [
        '{nickname}，监控模块先按现象选入口：页面慢看服务和数据源，账号异常看登录和在线用户。',
        '{nickname}，定位问题时把时间、账号、接口、资源指标和日志串起来看。',
        '{nickname}，执行强退、清理或任务变更前先确认是否影响现场值守。'
      ]
    },
    {
      key: 'toolDefault',
      match: ['/tool'],
      title: '系统工具模块',
      enabled: true,
      steps: [
        '{nickname}，系统工具多用于生成、构建和接口核对，操作前先确认目标表或接口。',
        '{nickname}，生成类操作要预览结果，避免覆盖现有页面逻辑。',
        '{nickname}，调试完成后回到真实业务页面做一次闭环验证。'
      ]
    },
    {
      key: 'default',
      match: ['*'],
      title: '平台操作指引',
      enabled: true,
      steps: [
        '{nickname}，先确认当前页面所属模块，再看顶部筛选、表格状态和右侧操作按钮。',
        '{nickname}，遇到异常页面，优先检查接口请求和权限，再回到版本记录确认最近改动。',
        '{nickname}，涉及现场人员操作时，建议按“确认现场、核对平台、保存配置、查看反馈”的顺序走。'
      ]
    }
  ],
  interactions: [
    {
      selector: '.sidebar-container .el-sub-menu__title',
      enabled: true,
      event: 'both',
      template: '这里是“{text}”目录，展开后再进入具体页面。'
    },
    {
      selector: '.sidebar-container .el-menu-item',
      enabled: true,
      event: 'both',
      template: '准备进入“{text}”页面，我继续在左下角值守。'
    },
    {
      selector: '#hamburger-container',
      enabled: true,
      event: 'both',
      template: '收起左侧菜单后，我会自动隐藏，给工作区让位置。'
    },
    {
      selector: '.tags-view-container',
      enabled: true,
      event: 'hover',
      template: '页签栏可以在已打开页面之间切换，排查问题时很省时间。'
    },
    {
      selector: '.el-table__row',
      enabled: true,
      event: 'hover',
      template: '这行记录可以重点看状态、更新时间和关联现场。'
    },
    {
      selector: '.el-button--primary',
      enabled: true,
      event: 'both',
      template: '即将执行“{text}”操作，先确认当前页面和表单内容。'
    },
    {
      selector: '.el-dialog',
      enabled: true,
      event: 'hover',
      template: '弹窗里的配置通常会影响现场、平台或巡检规则，保存前再扫一遍。'
    }
  ]
}

export function getMascotGreeting(config = mascotDialogConfig) {
  const hour = new Date().getHours()
  const greeting = config.greetings.find((item) => hour < item.before)
  return greeting?.message || config.greetings[config.greetings.length - 1]?.message || ''
}

export function getMascotTopics(config = mascotDialogConfig) {
  return (config.topics || []).filter((topic) => topic.enabled !== false && topic.messages?.length)
}

export function getMascotInteractions(config = mascotDialogConfig) {
  return (config.interactions || []).filter((item) => item.enabled !== false && item.selector && item.template)
}

export function getMascotPlayMessage(region = 'around', config = mascotDialogConfig) {
  const play = config.play || {}
  const messages = play.regions?.[region] || play.regions?.around || []
  if (!messages.length) {
    return play.hint || ''
  }
  return messages[Math.floor(Math.random() * messages.length)]
}

export function getMascotGuide(path = '', config = mascotDialogConfig) {
  const guides = (config.guides || []).filter((guide) => guide.enabled !== false && guide.steps?.length)
  return guides.find((guide) => guide.match?.some((matcher) => matcher !== '*' && path.startsWith(matcher)))
    || guides.find((guide) => guide.match?.includes('*'))
    || null
}

export function renderMascotTemplate(template = '', data = {}) {
  return template.replace(/\{(\w+)\}/g, (_, key) => data[key] || data.fallback || '当前')
}
