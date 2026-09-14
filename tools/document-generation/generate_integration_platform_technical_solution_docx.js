const fs = require('fs');
const path = require('path');
const {
  AlignmentType, BorderStyle, Document, Footer, HeadingLevel, PageBreak,
  PageNumber, Paragraph, Packer, Table, TableCell, TableRow, TextRun,
  WidthType, ShadingType
} = require('docx');

const projectRoot = path.resolve(__dirname, '..', '..');
const output = path.join(projectRoot, 'docs', 'plans', '县人民医院与基层卫生院中间接口平台技术方案.docx');
const navy = '17365D';
const blue = '1F4E78';
const cyan = 'DDEBF7';
const pale = 'F3F6F8';
const gray = '666666';
const orange = 'C55A11';
const green = '548235';
const red = 'C00000';
const border = { style: BorderStyle.SINGLE, size: 4, color: 'B7C9D6' };

const run = (text, options = {}) => new TextRun({
  text: String(text), font: 'Microsoft YaHei', size: 20, ...options,
});

const p = (text, options = {}) => new Paragraph({
  children: [run(text, options.run || {})],
  spacing: { before: options.before || 0, after: options.after ?? 110, line: options.line || 320 },
  alignment: options.alignment,
  heading: options.heading,
  pageBreakBefore: options.pageBreakBefore,
  keepNext: Boolean(options.heading),
  keepLines: Boolean(options.heading),
});

const richP = (runs, options = {}) => new Paragraph({
  children: runs.map(({ text, ...rest }) => run(text, rest)),
  spacing: { before: options.before || 0, after: options.after ?? 110, line: options.line || 320 },
  alignment: options.alignment,
  keepNext: options.keepNext,
});

const bullet = (text, level = 0) => new Paragraph({
  children: [run(text)],
  numbering: { reference: 'bullets', level },
  spacing: { after: 70, line: 300 },
});

const h1 = (text) => p(text, { heading: HeadingLevel.HEADING_1 });
const h2 = (text) => p(text, { heading: HeadingLevel.HEADING_2 });
const h3 = (text) => p(text, { heading: HeadingLevel.HEADING_3 });

const cell = (text, width, options = {}) => new TableCell({
  width: { size: width, type: WidthType.DXA },
  shading: options.header
    ? { fill: navy, type: ShadingType.CLEAR, color: 'auto' }
    : options.fill
      ? { fill: options.fill, type: ShadingType.CLEAR, color: 'auto' }
      : undefined,
  margins: { top: 75, bottom: 75, left: 90, right: 90 },
  borders: { top: border, bottom: border, left: border, right: border },
  verticalAlign: 'center',
  children: [new Paragraph({
    children: [run(text, options.header ? { bold: true, color: 'FFFFFF' } : { size: 18 })],
    spacing: { after: 0, line: 260 },
    alignment: options.align,
  })],
});

const table = (headers, rows, widths, options = {}) => new Table({
  width: { size: widths.reduce((sum, width) => sum + width, 0), type: WidthType.DXA },
  columnWidths: widths,
  rows: [
    new TableRow({
      children: headers.map((header, i) => cell(header, widths[i], { header: true })),
      tableHeader: true,
      cantSplit: true,
    }),
    ...rows.map((row, rowIndex) => new TableRow({
      children: row.map((value, i) => cell(value, widths[i], {
        fill: options.zebra && rowIndex % 2 === 1 ? pale : undefined,
      })),
      cantSplit: true,
    })),
  ],
});

const note = (title, text, color = cyan) => new Table({
  width: { size: 9200, type: WidthType.DXA },
  columnWidths: [1700, 7500],
  rows: [new TableRow({ children: [
    cell(title, 1700, { fill: color }),
    cell(text, 7500, { fill: color }),
  ], cantSplit: true })],
});

const pageBreak = () => new Paragraph({ children: [new PageBreak()] });
const children = [];

// Cover
children.push(new Paragraph({
  spacing: { before: 1350, after: 260 }, alignment: AlignmentType.CENTER,
  children: [run('县人民医院与基层卫生院', { bold: true, size: 38, color: navy })],
}));
children.push(new Paragraph({
  spacing: { after: 260 }, alignment: AlignmentType.CENTER,
  children: [run('中间接口平台技术方案', { bold: true, size: 48, color: blue })],
}));
children.push(new Paragraph({
  spacing: { after: 760 }, alignment: AlignmentType.CENTER,
  children: [run('模块化单体 · 统一适配 · 可靠交换 · 可管可查', { size: 23, color: gray })],
}));
children.push(table(['文档属性', '内容'], [
  ['方案版本', 'V1.0（评审稿）'],
  ['编制日期', '2026年9月10日'],
  ['建设形态', 'Java 模块化单体应用，提供接口服务、管理端 Web 和可嵌入页面组件'],
  ['建议基线', 'Java 21、Spring Boot 3.5.x、Spring Framework 6、MyBatis 3、Microsoft SQL Server 2019/2022'],
  ['升级方向', '保持 Spring Boot 4 / Spring Framework 7 迁移兼容；通过专项验证后独立升级'],
  ['适用对象', '医院信息科、基层卫生院、业务科室、平台研发、HIS/EMR/LIS/PACS 厂商、实施运维与测试团队'],
  ['配套材料', '业务协同对接方案、接口矩阵、两套 V1.0 接口规范及后续正式勘误'],
], [2200, 7000], { zebra: true }));
children.push(p('文档状态：用于技术评审和实施范围确认，不替代接口开通批复、正式合同、网络安全审批或生产上线批准。', {
  before: 320, alignment: AlignmentType.CENTER, run: { color: red, bold: true, size: 18 },
}));
children.push(pageBreak());

children.push(h1('1. 建设需求与设计结论'));
children.push(h2('1.1 项目定位'));
children.push(p('建设一套部署在县人民医院侧、服务于县人民医院与所辖基层卫生院的信息化中间接口平台。平台位于双方业务系统之间，向上承接县人民医院 HIS、EMR、LIS、PACS 等系统，向下对接基层卫生院及相关统建系统，统一完成协议适配、身份鉴权、数据转换、可靠传输、状态跟踪、审计追溯和运行管理。'));
children.push(p('业务协同关系始终为“县人民医院 ⇄ 所辖基层卫生院”。平台是技术承载和协同工具，不是第三个业务层级，不替代双方现有诊疗、审核、收费、公共卫生和管理职责，也不允许外部系统绕过平台直连医院核心数据库。'));
children.push(note('核心结论', '首发采用 Java 21 + Spring Boot 3.5.x + MyBatis 3 的模块化单体。Spring Boot 4 作为明确的升级目标，而非与 Boot 3 双版本并行运行。可靠写入先采用数据库任务箱和补偿任务，达到容量或解耦阈值后再引入消息队列。'));

children.push(h2('1.2 要解决的技术问题'));
children.push(table(['问题', '平台能力', '预期结果'], [
  ['各厂商协议、字段和成功码不一致', '标准 API、协议适配器、统一数据模型、版本化映射', '上层业务不再感知 REST、SOAP、JSON 字符串、FHIR/Base64 等差异'],
  ['接口超时后结果不明，重复调用可能产生重复业务', '请求幂等、回执落库、状态查询、重试与人工补偿', '每笔交换可判断、可恢复、可对账'],
  ['账号、验证码、token 和签名散落在客户端或脚本', '服务端凭证托管、按机构隔离、自动刷新与轮换审计', '客户端不持有外部平台密钥'],
  ['问题只能靠电话和日志文件排查', '全链路 traceId、接口运行台账、告警、脱敏报文检索', '按机构、接口、业务单号快速定位责任环节'],
  ['基层工作站需要跨系统操作', '嵌入式页面组件、短效单点票据、统一 BFF 接口', '在原工作入口完成查询、发起与结果查看'],
  ['接口范围大、一次性交付风险高', '接口目录、开关、灰度、分域上线、契约门禁', '按业务域逐步联调和验收'],
], [2500, 3300, 3400], { zebra: true }));

children.push(h2('1.3 设计原则'));
[
  '模块化单体：一个部署单元、一个主数据库，内部按领域模块隔离，禁止跨模块直接访问 Mapper。',
  '契约优先：正式接口文件、勘误、脱敏样例、网络和授权未齐备的接口标记为 CONTRACT_BLOCKED，不猜测开发。',
  '标准内核、边缘兼容：内部模型严格统一，历史协议和字段差异仅在适配器边界容错。',
  '同步查询、异步写入：需要即时展示的查询同步返回；上传、回写和通知通过任务箱可靠执行。',
  '默认可审计：每次调用都有 requestId、traceId、机构、接口版本、业务键、状态和责任来源。',
  '最小依赖：首期不默认引入 Redis、MQ、注册中心和微服务治理组件，按量化阈值演进。',
].forEach((x) => children.push(bullet(x)));

children.push(h1('2. 范围、角色与系统边界'));
children.push(h2('2.1 建设范围'));
children.push(table(['范围内', '范围外'], [
  ['统一接口入口、出站调用、协议转换、字段映射、校验、幂等、重试、补偿、对账', '替代 HIS/EMR/LIS/PACS 或基层业务系统完成诊疗业务'],
  ['机构、应用、接口、凭证引用、路由、限流、字典映射和版本配置', '未经批准新增跨基层卫生院横向数据共享'],
  ['运行监控、审计检索、人工重放、告警、统计报表和发布管理', '直接保存不必要的完整病历副本或建设新的临床数据中心'],
  ['管理端 Web、嵌入式协同组件及其 BFF 服务', '由平台代替业务人员作诊断、审核、收费或转诊决策'],
], [4600, 4600], { zebra: true }));

children.push(h2('2.2 各方职责'));
children.push(table(['参与方', '职责'], [
  ['县人民医院', '项目和安全责任主体；确认所辖机构、业务范围、院内数据来源、访问权限、网络策略、上线窗口和验收人。'],
  ['基层卫生院', '确认实际业务入口、使用人员、业务场景、数据回传需求；参与端到端联调和业务验收。'],
  ['中间接口平台研发方', '完成平台、适配器、管理端和嵌入组件；维护接口目录、追踪、补偿、部署、测试与技术文档。'],
  ['各业务系统厂商', '提供正式契约、服务地址、脱敏样例、错误码、改造入口、联调支持和问题修复。'],
  ['平台主管/网络安全方', '提供接入审批、专网或白名单、证书、授权凭证、安全基线和生产变更审批。'],
], [2300, 6900], { zebra: true }));

children.push(h2('2.3 接口范围证据边界'));
children.push(p('现有接口材料包含健康档案云平台和基层 HIS 两类接口。当前 Excel 按行统计为健康云 21 行、基层 HIS 53 行，共 74 行；通知正文描述为 73 个接口，且存在交易码复用和无独立交易码的页面能力。平台按“业务能力、接口项、唯一交易码、契约版本”四个维度管理，最终范围以批准后的接口矩阵为准。'));
children.push(p('已识别的协议包括 HTTPS REST/JSON、SOAP WebService、JSON 字符串嵌套、FHIR 文档 Base64 和外部页面跳转。成功码、方法名、交易码等现有冲突必须形成正式勘误或以联调回执固化，不能把示例文档中的测试地址、账号或验证码带入生产。'));

children.push(pageBreak());
children.push(h1('3. 总体技术架构'));
children.push(h2('3.1 逻辑架构'));
children.push(table(['层次', '主要组成', '职责'], [
  ['业务入口层', '县医院业务系统、基层工作站、嵌入组件、管理端 Web', '发起业务、查看状态和结果；不保存外部平台密钥'],
  ['边界接入层', 'Nginx/WAF、TLS、IP 白名单、限流、请求大小控制', '统一流量入口和网络安全边界'],
  ['平台接入层', 'REST/SOAP Controller、认证过滤器、参数校验、请求标准化', '接收调用，生成 requestId/traceId，校验调用方和幂等键'],
  ['应用服务层', '接口编排、交换任务、回执处理、对账、组件 BFF、管理 BFF', '控制业务用例、事务和状态迁移'],
  ['领域能力层', '机构与应用、接口目录、映射规则、交换记录、审计与告警', '维护稳定业务规则和领域状态'],
  ['适配器层', '健康云、基层 HIS、HIS/EMR/LIS/PACS、页面 SSO 适配器', '隔离厂商协议、签名、字段、错误码和版本差异'],
  ['数据与运维层', 'SQL Server、加密配置、日志、指标、备份；可选 Redis/MQ/对象存储', '持久化、观测与灾备；可选组件按容量评审引入'],
], [1500, 3100, 4600], { zebra: true }));

children.push(h2('3.2 部署拓扑'));
children.push(p('基层卫生院/县医院业务终端 → 医疗专网或受控 HTTPS → 边界代理/WAF → 中间接口平台 → 县医院 HIS/EMR/LIS/PACS 与获批的外部平台。管理端使用独立访问路径和更严格的来源限制；数据库仅对平台应用开放。'));
children.push(table(['节点', '首期建议', '扩展方式'], [
  ['边界代理', '1 套 Nginx 或现有医院网关；统一证书、白名单和限流', '接入现有 WAF/负载均衡，双节点部署'],
  ['平台应用', '一个可执行部署单元，建议 2 实例无状态部署', '水平扩容；定时任务使用数据库抢占锁避免重复执行'],
  ['数据库', 'SQL Server 2019/2022，优先独立数据库和账号；版本以医院现网支持为准', '可复用医院 SQL Server 运维体系；即使同实例也须隔离资源、权限、备份和故障影响'],
  ['前端', '管理端静态资源独立部署；嵌入组件可同域或独立静态发布', 'CDN 仅在安全制度允许时使用'],
  ['可选基础设施', '首期不作为强依赖', '高频会话/限流引入 Redis；跨系统吞吐达到阈值后引入 MQ；大报文按需引入对象存储'],
], [1900, 4400, 2900], { zebra: true }));

children.push(h2('3.3 高可用约束'));
[
  '应用实例不保存本地会话和业务状态，上传文件不得依赖单机临时目录。',
  '数据库任务箱以状态、计划执行时间和乐观锁抢占任务，支持多实例安全消费。',
  '上游不可用时只影响对应适配器；平台管理、查询历史和其他业务域保持可用。',
  '生产变更采用滚动或蓝绿发布；数据库变更坚持向前兼容，先扩展、后切换、再清理。',
].forEach((x) => children.push(bullet(x)));

children.push(h1('4. 技术选型与版本策略'));
children.push(table(['类别', '首发选型', '选择说明'], [
  ['运行时', 'Java 21 LTS', '统一编译和运行版本，使用容器或固定 JDK 发行版，禁止开发/生产漂移'],
  ['应用框架', 'Spring Boot 3.5.x / Spring Framework 6.x', '生态成熟；锁定补丁版本并维护 SBOM。Boot 4 通过专项分支升级，不双版本运行'],
  ['Web/API', 'Spring MVC、Validation、Jackson、Spring Security、Actuator', '适合传统同步接口、管理端 BFF 和可观测性；统一异常与序列化策略'],
  ['数据访问', 'MyBatis 3 + MyBatis-Spring-Boot-Starter', 'SQL 显式可控；SQL 按 SQL Server 方言编写；Mapper 仅属于本模块，禁止把数据库实体直接作为 API DTO'],
  ['数据库', 'Microsoft SQL Server 2019/2022 + Microsoft JDBC Driver + Flyway SQL Server 支持模块', '承载配置、任务箱、回执、审计索引与幂等键；所有结构变更版本化'],
  ['外部调用', 'Spring RestClient/WebClient；SOAP 使用正式 WSDL 生成客户端或受控 XML 解析', '连接池、超时、TLS、签名和报文上限统一治理；禁用 XML 外部实体'],
  ['可靠性', 'Resilience4j + 数据库任务箱/补偿任务', '熔断、隔离、退避和重放可控；首期无需强制 MQ'],
  ['管理端', 'Vue 3 + TypeScript + Vite + Element Plus', '适合运营管理界面；权限按钮和 API 权限必须同时校验'],
  ['嵌入组件', 'Web Components 优先，受限系统使用 iframe 兼容', '与宿主技术栈解耦；统一短效票据、来源校验和事件协议'],
  ['测试与契约', 'JUnit 5、Testcontainers、WireMock、OpenAPI、契约样例回归', '测试替身只用于自动化测试环境，不作为生产替代连接器'],
], [1700, 3300, 4200], { zebra: true }));

children.push(h2('4.1 Spring Boot 4 升级门禁'));
children.push(p('Spring Boot 4 升级应作为独立版本工作，不与首发交付耦合。代码从首期开始避免依赖已废弃 API，统一使用 jakarta 命名空间，隔离 SOAP、安全、观测和数据库驱动等高风险依赖。满足以下条件后才能切换生产基线：'));
[
  'MyBatis Starter、数据库驱动、Flyway、Resilience4j、SOAP 客户端和监控组件均提供正式兼容版本。',
  '完整单元测试、数据库迁移测试、接口契约回归、嵌入组件回归和性能测试通过。',
  '测试环境连续稳定运行不少于一个完整业务周期，并完成双版本结果比对。',
  '形成升级回退方案，数据库结构在回退窗口内同时兼容 Boot 3 和 Boot 4 应用版本。',
].forEach((x) => children.push(bullet(x)));

children.push(h1('5. 模块化单体设计'));
children.push(h2('5.1 代码模块'));
children.push(table(['模块', '职责', '禁止事项'], [
  ['platform-bootstrap', '应用启动、配置装配、环境校验、统一过滤器', '承载具体接口业务逻辑'],
  ['access-control', '调用方、用户、角色、机构数据权限、短效票据', '直接访问外部业务接口'],
  ['interface-catalog', '接口定义、版本、路由、超时、限流、开关、契约指纹', '保存明文生产密钥'],
  ['master-data', '机构、人员、科室、字典和编码映射', '无审批自动合并冲突主数据'],
  ['exchange-core', '请求、任务箱、状态机、幂等、重试、补偿、回执与对账', '包含厂商特有字段'],
  ['adapter-health-cloud', '健康云 REST、签名、token、字段和成功码适配', '将兼容规则泄漏到核心模块'],
  ['adapter-primary-his', '基层 HIS SOAP、TradeCode、FHIR/Base64 与返回归一', '使用字符串拼接解析 XML'],
  ['adapter-hospital', '县医院 HIS/EMR/LIS/PACS 受控接口或视图访问', '外部系统直连医院数据库'],
  ['operations', '监控、告警、审计检索、人工重放、统计和导出', '绕过权限查看敏感原文'],
  ['web-bff / widget-bff', '管理端与嵌入组件专用聚合 API', '把前端展示模型作为核心领域模型'],
], [2100, 4300, 2800], { zebra: true }));

children.push(h2('5.2 分层和依赖规则'));
children.push(p('每个业务模块内部采用 api/application/domain/infrastructure 分层。Controller 只做协议、权限和参数处理；Application Service 组织用例与事务；Domain 保存状态规则；Infrastructure 实现 MyBatis Mapper、外部客户端和任务调度。模块之间通过应用接口和事件协作，不直接引用对方 Mapper 或数据库表。'));
children.push(p('建议在构建中加入 ArchUnit 规则验证依赖方向，并为每个适配器建立独立契约测试集。即使当前为单体，清晰模块边界也能在未来确有独立扩缩容需求时平滑拆分。'));

children.push(h2('5.3 配置管理'));
[
  '普通配置按环境外置，敏感值只保存密钥引用，实际密钥由医院现有凭证系统或加密配置提供。',
  '接口地址、方法、交易码、成功码、超时、限流、开关和映射版本均可审计变更。',
  '生产配置变更实行申请、复核、发布、验证、回退闭环；禁止直接在数据库手工修改。',
  '启动时校验必需配置，发现测试地址、默认密码、明文密钥或跨环境凭证立即拒绝启动。',
].forEach((x) => children.push(bullet(x)));

children.push(pageBreak());
children.push(h1('6. 接口与数据交换设计'));
children.push(h2('6.1 平台内部标准接口'));
children.push(p('平台对院内系统和嵌入组件提供版本化 REST API，推荐路径 `/api/v1/{domain}/{resource}`。调用方必须传递调用方标识、机构上下文和 requestId；写请求必须提供 idempotencyKey。响应统一包含 code、message、data、requestId、traceId 和 timestamp。HTTP 状态表达传输结果，code 表达平台业务处理结果，两者不得混用。'));
children.push(table(['字段', '规则'], [
  ['requestId', '调用方生成或平台补发；同一业务请求全程保持不变，用于检索和去重辅助'],
  ['traceId', '平台生成并向所有下游传播，用于单次执行链路跟踪'],
  ['idempotencyKey', '写操作必填；建议由机构+源系统+业务类型+源业务主键+版本组成'],
  ['tenantOrgCode', '当前机构编码；必须与登录身份授权范围一致，不接受前端自行越权切换'],
  ['apiVersion', 'URL 主版本 + 接口目录中的契约版本；破坏性变更发布新主版本'],
], [2300, 6900], { zebra: true }));

children.push(h2('6.2 三类处理流程'));
children.push(table(['类型', '处理链路', '返回口径'], [
  ['同步查询', '鉴权 → 校验 → 路由 → 调用上游 → 归一响应 → 审计', '在超时窗口内返回明确结果；只对网络/5xx 做有限重试'],
  ['异步写入', '鉴权 → 幂等校验 → 本地事务写业务请求和任务箱 → 返回已受理 → 后台发送 → 保存回执 → 对账', '受理成功不等于外部业务成功；调用方凭 requestId 查询最终状态'],
  ['外部回调', '边界校验 → 签名/票据校验 → 幂等落库 → 快速应答 → 异步处理 → 通知源系统', '重复回调返回既有结果；原始来源和摘要必须可追溯'],
], [1700, 5000, 2500], { zebra: true }));

children.push(h2('6.3 统一状态模型'));
children.push(p('写交换统一使用：RECEIVED（已接收）→ VALIDATED（已校验）→ DISPATCHING（发送中）→ ACKNOWLEDGED（取得明确业务回执）→ RECONCILED（已对账）。可恢复失败进入 RETRY_WAIT；达到重试上限进入 MANUAL_REVIEW；明确业务拒绝进入 REJECTED；经审批取消进入 CANCELLED。HTTP 200、SOAP 正常返回或网络连接成功均不能单独视为业务成功。'));

children.push(h2('6.4 适配器规范'));
[
  '适配器输入和输出使用平台标准 DTO，不允许外部字段穿透到核心业务模块。',
  'REST 签名、token 刷新、SOAP 方法、TradeCode、FHIR/Base64、大小写和成功码差异集中封装。',
  'XML 使用安全解析器并关闭 DTD/外部实体；JSON 使用结构化映射，禁止拼接报文。',
  '每次适配调用记录契约版本、配置版本、耗时、响应代码、报文摘要和脱敏错误信息。',
  '兼容逻辑必须有失效条件和契约测试，正式勘误发布后及时收敛，避免永久容错。',
].forEach((x) => children.push(bullet(x)));

children.push(h1('7. 数据设计'));
children.push(h2('7.1 核心数据表'));
children.push(table(['数据对象', '关键内容', '保留原则'], [
  ['org / client_app / user_role', '机构、调用应用、角色及数据权限', '按在用和审计要求保留'],
  ['interface_definition / route_config', '接口代码、版本、方向、协议、路由、开关、超时、限流', '配置版本不可覆盖，变更留痕'],
  ['mapping_rule / code_mapping', '机构、人员、科室、诊断、目录及字段映射版本', '每笔交换引用具体版本'],
  ['exchange_request', '业务键、请求状态、来源、目标、requestId、traceId、摘要', '按审计制度归档，线上表按时间分区或归档'],
  ['outbox_task', '待发送任务、计划时间、重试次数、锁版本和失败原因', '完成任务按周期归档'],
  ['exchange_receipt', '外部流水、业务回执、平台 ID、结果码和回执时间', '与请求关联，不允许无痕覆盖'],
  ['idempotency_record', '调用方、幂等键、请求摘要和既有结果引用', '覆盖最大重试与追溯周期'],
  ['audit_event / config_change', '用户操作、数据查看、重放、配置发布和审批信息', '防篡改导出或集中日志留存'],
], [2300, 4500, 2400], { zebra: true }));

children.push(h2('7.2 数据最小化与大报文'));
children.push(p('平台默认保存交换元数据、必要回执和报文摘要，不把完整病历、报告或居民档案长期复制为新的临床数据仓库。确因补偿或审计需要保留原始报文时，应字段加密、访问审批、到期清除并记录查看行为。FHIR/Base64 报告等大报文达到数据库容量阈值后，可迁移到医院批准的对象存储，数据库仅保存加密对象引用和摘要。'));

children.push(h2('7.3 事务与并发'));
[
  '本地状态和 outbox_task 在同一数据库事务提交，避免业务已接收但任务丢失。',
  '任务消费采用 SQL Server 的 UPDLOCK、READPAST、ROWLOCK 与短事务抢占，配合 rowversion/状态条件更新，确保多实例不重复执行。',
  '对同一业务键需要有序的修改类任务按分区键串行处理；跨业务键允许并发。',
  '禁止使用分布式事务绑定外部平台；通过幂等、回执、补偿和对账达成最终一致。',
].forEach((x) => children.push(bullet(x)));

children.push(h2('7.4 SQL Server 专项设计'));
children.push(table(['主题', '设计要求'], [
  ['数据库边界', '优先在独立 SQL Server 实例部署；如复用 HIS 所在实例，必须新建独立数据库、登录账号、备份任务和资源治理策略，禁止在 HIS 业务库建平台表'],
  ['访问边界', '平台账号只访问平台数据库；读取 HIS 数据须通过厂商服务或经审批的专用只读账号/视图，不因数据库同为 SQL Server 而跨库直查业务表'],
  ['数据类型', '统一使用 datetime2、nvarchar、decimal 和 uniqueidentifier/bigint；rowversion 仅用于并发控制；大报文使用 nvarchar(max)/varbinary(max) 前须评估，避免进入普通索引'],
  ['并发隔离', '评估开启 READ_COMMITTED_SNAPSHOT 降低读写阻塞；任务抢占事务必须短小，不在持锁期间调用外部接口'],
  ['索引与归档', '围绕机构、接口、状态、计划执行时间、requestId、业务键和创建时间建立组合/唯一索引；交换记录按时间归档，避免审计大表拖慢在线任务'],
  ['迁移工具', '使用与所选 Flyway 版本匹配的 SQL Server 支持模块；脚本针对目标 SQL Server 版本在空库、升级库和回退窗口分别验证'],
  ['运行治理', '设置独立连接池上限、查询超时和锁等待告警；由 DBA 监控阻塞、死锁、日志文件增长、tempdb、备份时长和磁盘容量'],
], [1900, 7300], { zebra: true }));

children.push(h1('8. 管理端 Web 与嵌入式组件'));
children.push(h2('8.1 管理端功能'));
children.push(table(['功能域', '首期能力'], [
  ['机构与应用', '机构接入状态、调用方、授权范围、密钥引用、IP 范围和有效期'],
  ['接口目录', '业务域、接口代码、版本、方向、协议、路由、开关、限流、责任方和契约附件指纹'],
  ['映射治理', '机构/人员/科室/字典映射、冲突清单、版本发布和影响预览'],
  ['运行中心', '实时概览、成功率、耗时、积压、错误分布、上下游健康状态'],
  ['交换台账', '按机构、接口、业务号、requestId、traceId、状态和时间检索；敏感字段默认脱敏'],
  ['异常处置', '失败原因、重试计划、人工复核、单笔/批量重放、处置备注和审批留痕'],
  ['对账与报表', '按日核对发送、回执和业务状态；导出差异清单和验收证据'],
  ['系统管理', '用户、角色、数据权限、告警规则、配置发布、审计日志和版本信息'],
], [2100, 7100], { zebra: true }));

children.push(h2('8.2 嵌入式页面组件'));
children.push(p('优先提供与宿主技术栈无关的 Web Components，例如患者协同摘要、转诊进度、报告结果和异常提示组件；对于不能加载自定义脚本的老旧 C/S 或 Web 系统，提供受控 iframe 页面作为兼容方案。组件只展示当前业务所需信息，复杂管理功能仍进入独立管理端。'));
children.push(table(['能力', '要求'], [
  ['身份传递', '宿主后端换取一次性短效票据；票据绑定用户、机构、患者/业务上下文和目标组件，禁止在 URL 传长期 token'],
  ['页面隔离', 'CSP frame-ancestors 白名单、SameSite Cookie、CSRF 防护；iframe sandbox 权限最小化'],
  ['事件通信', '通过版本化 postMessage 事件协议通信，严格校验 origin、事件类型和负载 schema'],
  ['样式与兼容', 'Web Components 使用 Shadow DOM 隔离；明确支持浏览器版本和 C/S WebView 内核'],
  ['权限与脱敏', '后端每次请求重新校验数据权限；前端隐藏不等于授权；日志不记录患者明文上下文'],
  ['降级处理', '组件加载失败不得阻断 HIS 主流程，提供重试、独立打开和 requestId 报障入口'],
], [2200, 7000], { zebra: true }));

children.push(pageBreak());
children.push(h1('9. 安全设计'));
children.push(table(['层面', '控制要求'], [
  ['网络', '医疗专网优先；互联网接入须审批并使用 TLS、固定出口/IP 白名单、WAF、端口最小化和管理面隔离'],
  ['身份', '管理用户接入医院统一身份或 MFA；系统调用使用独立 client 身份、双向证书或签名，不共用个人账号'],
  ['授权', 'RBAC + 机构数据范围 + 接口权限；敏感查询可增加患者/业务上下文校验和二次审计'],
  ['凭证', '密钥、验证码、token 按机构和环境隔离，加密保存、可轮换、不可回显；客户端和源码中不得出现'],
  ['数据', '传输加密、字段级加密/脱敏、最小留存、导出水印、下载审批和到期清理'],
  ['应用', '输入校验、SQL 参数化、文件类型/大小限制、XXE/SSRF 防护、CORS/CSP/CSRF、安全响应头'],
  ['供应链', '锁定依赖、生成 SBOM、漏洞扫描、镜像签名、制品校验和第三方组件升级流程'],
  ['审计', '登录、查询、导出、重放、权限和配置变更全记录；日志集中留存且限制删除权限'],
], [1800, 7400], { zebra: true }));
children.push(p('等保等级、密码应用、日志保留期限、数据分类分级和跨机构授权规则必须由医院安全责任部门最终确认。本方案提供技术控制基线，不自行替代合规认定。'));

children.push(h1('10. 可靠性、性能与可观测性'));
children.push(h2('10.1 可靠交换策略'));
children.push(table(['场景', '策略'], [
  ['查询失败', '网络、连接超时和 5xx 可进行最多 2 次短退避重试；鉴权、参数、权限和业务拒绝不重试'],
  ['写入超时未知', '先查询本地回执或外部业务状态；无法判断时进入 MANUAL_REVIEW，禁止直接盲目重发'],
  ['重复写入', '唯一幂等键命中后返回既有 requestId 和结果；请求摘要不同则拒绝复用同一幂等键'],
  ['下游持续故障', '按接口熔断、降低并发、延迟任务；不影响其他适配器和管理台历史查询'],
  ['任务积压', '按业务优先级和机构公平调度；达到告警阈值后停止低优先级批量任务'],
  ['人工重放', '必须具备权限、原因、影响预览和完整审计；收费等高风险业务默认要求复核'],
], [2100, 7100], { zebra: true }));

children.push(h2('10.2 建议的非功能基线'));
children.push(table(['指标', '评审建议值', '说明'], [
  ['平台可用性', '月度不低于 99.9%', '不含已批准维护窗口；外部依赖可用性单独统计'],
  ['平台内部开销', 'P95 ≤ 200 ms', '不含下游接口耗时，以网关进入到发起下游调用前后增量计量'],
  ['同步查询', '默认 15 秒超时', '按接口配置；大病历/报告可放宽到 60 秒或改异步'],
  ['异步受理', 'P95 ≤ 1 秒', '指请求校验并可靠写入本地任务箱，不代表下游处理完成'],
  ['积压告警', '最老待执行任务 > 5 分钟或积压 > 基线 2 倍', '上线压测后按业务域重新校准'],
  ['恢复目标', '建议 RPO ≤ 15 分钟，RTO ≤ 2 小时', '须结合医院数据库备份和灾备能力确认'],
], [2000, 2600, 4600], { zebra: true }));
children.push(note('容量口径', '接口文档中的单项 QPS 或超时不能直接作为平台容量结论。立项阶段需采集机构数、终端数、峰值请求、日交换量、平均/最大报文、批量窗口和保留期限，再完成容量模型与压测。', 'FFF2CC'));

children.push(h2('10.3 监控与告警'));
[
  '技术指标：请求量、成功率、P50/P95/P99、连接池、线程池、JVM、数据库、任务积压、重试和熔断状态。',
  '业务指标：按机构/接口的受理、明确成功、业务拒绝、人工处理中、对账差异和最长未闭环时长。',
  '日志关联：requestId、traceId、机构、接口、源业务键哈希、配置版本和部署版本必须可关联。',
  '告警分级：凭证失效、持续失败、数据越权和对账差异为高优先级；告警需明确责任人、升级路径和恢复确认。',
].forEach((x) => children.push(bullet(x)));

children.push(h1('11. 工程、测试与发布'));
children.push(h2('11.1 环境与流水线'));
children.push(table(['环节', '要求'], [
  ['环境', '开发、测试、联调、生产独立地址、数据库、账号、密钥和日志；生产数据不得进入开发环境'],
  ['构建', '统一 Maven Wrapper、依赖锁定、代码检查、单元测试、SBOM、漏洞和许可证扫描'],
  ['数据库', 'Flyway SQL Server 脚本只增不改；流水线验证从空库和上一生产版本均可升级'],
  ['制品', '后端 JAR/镜像、前端静态包和配置模板统一版本；制品生成校验和并不可变存档'],
  ['发布', '配置预检、数据库备份、灰度验证、健康检查、关键接口烟测、监控观察和回退确认'],
], [1900, 7300], { zebra: true }));

children.push(h2('11.2 测试体系'));
children.push(table(['测试层级', '重点'], [
  ['单元测试', '映射、签名、状态机、幂等、错误码归一、脱敏和权限规则'],
  ['集成测试', '真实 SQL Server 容器/测试实例上的事务、锁、任务抢占、Flyway、加密配置、REST/SOAP/FHIR 解析和大报文限制'],
  ['契约测试', '对每个正式样例和已确认异常样例做回归；记录契约文件指纹和厂商版本'],
  ['端到端测试', '基层发起 → 平台 → 县医院处理 → 状态/报告反馈的完整业务场景'],
  ['可靠性测试', '重复、乱序、超时未知、断网、下游慢、重启恢复、重放、熔断和对账'],
  ['安全测试', '越权、票据重放、注入、XXE、SSRF、CORS/CSP、敏感日志、依赖漏洞和导出控制'],
  ['性能测试', '峰值并发、大报文、批量任务、数据库任务箱、嵌入组件首屏和长稳测试'],
], [1900, 7300], { zebra: true }));

children.push(h2('11.3 上线与回退'));
children.push(p('接口按机构、业务域和版本设置开关，先在少量机构灰度。出现持续业务失败、错误数据写入、积压超过容量、关键安全事件或核心 HIS 性能受影响时，立即关闭对应接口入口/出站任务并保留已接收记录，禁止通过删除记录“回退”。应用版本可回退，数据库采用向前兼容脚本；恢复后对暂停期间任务进行评估和分批补发。'));

children.push(pageBreak());
children.push(h1('12. 分期实施与交付'));
children.push(table(['阶段', '主要工作', '退出条件'], [
  ['P0 需求与准入', '确认双方场景、机构名单、接口矩阵、正式契约、样例、网络、凭证、容量和验收人', '每个接口明确范围、责任、版本和阻断状态；无证据项不进入开发'],
  ['P1 平台底座', '工程骨架、权限、接口目录、任务箱、状态机、审计、监控、管理端基础、部署流水线', '标准 API、配置发布、任务恢复、审计和双实例运行验证通过'],
  ['P2 首个业务闭环', '选择一个高价值低风险场景打通基层 → 平台 → 县医院 → 结果反馈', '正常、重复、超时、失败补偿、对账和业务验收全部通过'],
  ['P3 分域扩展', '按基础数据、转诊、检查检验、病历/报告等依赖顺序扩展适配器和组件', '各域独立灰度、容量和安全验收；接口矩阵证据完整'],
  ['P4 规模运行', '扩大机构范围，优化容量、告警、归档、灾备和运维制度', '达到连续运行周期指标，完成故障与恢复演练'],
  ['P5 技术升级', '评估 Spring Boot 4 及可选 Redis/MQ/对象存储', '仅在兼容性或容量证据充分时实施，独立验收和回退'],
], [1600, 4800, 2800], { zebra: true }));

children.push(h2('12.1 主要交付物'));
[
  '中间接口平台后端、管理端 Web、嵌入式组件包及版本说明。',
  '接口目录、OpenAPI、SOAP/FHIR 适配说明、正式契约与样例回归集。',
  '数据库设计、Flyway 脚本、配置模板、部署手册、备份恢复和回退手册。',
  '接口矩阵、字段映射、错误码、幂等规则、重试补偿、对账规则和运维处置手册。',
  '测试报告、安全检查、性能基线、上线检查表、灰度记录和验收证据包。',
].forEach((x) => children.push(bullet(x)));

children.push(h1('13. 待确认事项与技术决策门禁'));
children.push(table(['级别', '待确认事项', '责任建议', '影响'], [
  ['阻断', '首批机构、首个业务闭环、接口代码和不做项', '县医院 + 基层业务负责人', '决定开发和验收范围'],
  ['阻断', '正式 WSDL/OpenAPI、勘误、成功码、错误码、脱敏报文', '各系统厂商/平台主管', '决定适配器能否开发'],
  ['阻断', '测试/生产网络、TLS、白名单、凭证发放和轮换', '医院信息科/安全方', '决定能否联调和上线'],
  ['高', '县医院侧数据获取采用服务接口还是受控视图', '医院信息科 + HIS 厂商', '影响一致性、性能和责任边界'],
  ['高', '现网 SQL Server 版本、实例复用条件、Always On/备份能力、日志平台和密钥系统', '医院 DBA/运维', '影响驱动方言、部署、RPO/RTO 和安全实现'],
  ['高', '嵌入宿主类型、浏览器/WebView 版本、SSO 和页面白名单', 'HIS 厂商 + 基层代表', '影响组件技术形态'],
  ['中', '机构数、峰值并发、日交换量、大报文和保留期限', '双方业务 + 信息科', '影响容量和可选组件'],
  ['中', 'Spring Boot 4 切换时间窗', '研发负责人 + 运维', '不阻断首发；满足升级门禁后执行'],
], [900, 3900, 2200, 2200], { zebra: true }));

children.push(h2('13.1 评审建议结论'));
children.push(p('可立即开展平台底座、统一状态模型、接口目录、审计和测试框架建设；具体业务适配器必须逐项通过契约、网络、身份、数据和验收五类准入门槛。首个闭环建议选择调用频率可控、无资金风险、上下游责任清晰且具备正式样例的业务场景，完成后再复制到其他业务域。'));
children.push(p('本方案批准后，应将关键技术选择形成 ADR：Spring Boot 首发版本、数据库、县医院数据访问方式、嵌入方式、凭证系统、原始报文留存、可选 MQ/Redis 引入阈值。任何改变业务边界、数据用途或跨机构共享范围的事项，必须重新进行业务和安全评审。'));

const doc = new Document({
  creator: 'Codex',
  title: '县人民医院与基层卫生院中间接口平台技术方案',
  description: '县人民医院与所辖基层卫生院中间接口平台建设技术方案',
  styles: {
    default: {
      document: { run: { font: 'Microsoft YaHei', size: 20, color: '222222' }, paragraph: { spacing: { line: 320 } } },
      heading1: { run: { font: 'Microsoft YaHei', size: 30, bold: true, color: navy }, paragraph: { spacing: { before: 260, after: 150 }, outlineLevel: 0 } },
      heading2: { run: { font: 'Microsoft YaHei', size: 25, bold: true, color: blue }, paragraph: { spacing: { before: 190, after: 110 }, outlineLevel: 1 } },
      heading3: { run: { font: 'Microsoft YaHei', size: 22, bold: true, color: orange }, paragraph: { spacing: { before: 140, after: 90 }, outlineLevel: 2 } },
    },
  },
  numbering: {
    config: [{
      reference: 'bullets',
      levels: [{
        level: 0,
        format: 'bullet',
        text: '●',
        alignment: AlignmentType.LEFT,
        style: { paragraph: { indent: { left: 420, hanging: 220 } }, run: { color: blue, font: 'Microsoft YaHei' } },
      }, {
        level: 1,
        format: 'bullet',
        text: '○',
        alignment: AlignmentType.LEFT,
        style: { paragraph: { indent: { left: 760, hanging: 220 } }, run: { color: green, font: 'Microsoft YaHei' } },
      }],
    }],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 },
        margin: { top: 1050, right: 1050, bottom: 1050, left: 1050, header: 420, footer: 500 },
      },
    },
    footers: {
      default: new Footer({ children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [
          run('县人民医院与基层卫生院中间接口平台技术方案  |  ', { size: 16, color: gray }),
          new TextRun({ children: [PageNumber.CURRENT], font: 'Microsoft YaHei', size: 16, color: gray }),
        ],
      })] }),
    },
    children,
  }],
});

Packer.toBuffer(doc).then((buffer) => {
  fs.writeFileSync(output, buffer);
  console.log(`Created ${output} (${buffer.length} bytes)`);
});
