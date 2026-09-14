const fs = require('fs');
const path = require('path');
const {
  AlignmentType, BorderStyle, Document, Footer, HeadingLevel, PageBreak,
  PageNumber, Paragraph, Packer, Table, TableCell, TableRow, TextRun,
  WidthType, ShadingType
} = require('docx');

const projectRoot = path.resolve(__dirname, '..', '..');
const output = path.join(projectRoot, 'docs', 'plans', '县级人民医院_基层和健康V1.0对接总体方案.docx');
const blue = '1F4E78';
const lightBlue = 'D9EAF7';
const lightGray = 'E7E6E6';
const red = 'C00000';

const run = (text, options = {}) => new TextRun({ text, font: 'Microsoft YaHei', size: 21, ...options });
const p = (text, options = {}) => new Paragraph({
  children: [run(text, options.run || {})],
  spacing: { after: 120, line: 340 },
  alignment: options.alignment,
  heading: options.heading,
  pageBreakBefore: options.pageBreakBefore,
  keepNext: Boolean(options.heading),
  keepLines: Boolean(options.heading),
});
const bullet = (text) => new Paragraph({
  children: [run(text)],
  numbering: { reference: 'bullets', level: 0 },
  spacing: { after: 80, line: 320 },
});
const h1 = (text) => p(text, { heading: HeadingLevel.HEADING_1 });
const h2 = (text) => p(text, { heading: HeadingLevel.HEADING_2 });
const h3 = (text) => p(text, { heading: HeadingLevel.HEADING_3 });
const cell = (text, width, header = false) => new TableCell({
  width: { size: width, type: WidthType.DXA },
  shading: header ? { fill: blue, type: ShadingType.CLEAR, color: 'auto' } : undefined,
  margins: { top: 80, bottom: 80, left: 100, right: 100 },
  children: [new Paragraph({
    children: [run(String(text), header ? { bold: true, color: 'FFFFFF' } : {})],
    spacing: { after: 0, line: 280 },
  })],
});
const table = (headers, rows, widths) => new Table({
  width: { size: widths.reduce((a, b) => a + b, 0), type: WidthType.DXA },
  columnWidths: widths,
  rows: [
    new TableRow({ children: headers.map((x, i) => cell(x, widths[i], true)), tableHeader: true, cantSplit: true }),
    ...rows.map(row => new TableRow({ children: row.map((x, i) => cell(x, widths[i])) , cantSplit: true })),
  ],
});

const children = [];
children.push(new Paragraph({ spacing: { before: 1600, after: 300 }, alignment: AlignmentType.CENTER,
  children: [run('县级人民医院', { bold: true, size: 38, color: blue })] }));
children.push(new Paragraph({ spacing: { after: 260 }, alignment: AlignmentType.CENTER,
  children: [run('“基层和健康 V1.0”对接总体方案', { bold: true, size: 44, color: blue })] }));
children.push(new Paragraph({ spacing: { after: 900 }, alignment: AlignmentType.CENTER,
  children: [run('基于《四川省健康档案云服务平台应用接口规范（V1.0）》与《四川省基层医疗卫生机构管理信息系统应用接口规范（V1.0）》')] }));
children.push(table(['文档属性', '内容'], [
  ['方案版本', 'V1.1（评审稿）'], ['接口规范版本', 'V1.0'], ['编制日期', '2026年9月10日'],
  ['适用对象', '医院信息科、HIS/EMR厂商、集成平台厂商、业务科室、实施与测试团队'],
  ['配套文件', '县级人民医院_基层和健康V1.0接口矩阵.xlsx'],
  ['本次修订', '新增信息边界、环境拓扑、统一状态模型、联调准入门槛、责任里程碑、上线检查和量化回退触发']
], [2200, 7000]));
children.push(new Paragraph({ children: [new PageBreak()] }));

children.push(h1('1. 执行摘要'));
children.push(p('建议以“院内集成平台”为唯一对外接口边界，现有 C/S HIS 工作站只负责业务入口和结果展示，不直接持有云平台密钥、不直接访问外网，也不在客户端实现重试。集成平台统一完成 REST/JSON 与 SOAP WebService 两套协议适配、鉴权、数据映射、异步队列、审计、对账和异常补偿。'));
children.push(p('原 Excel 不是可直接据此开发的最终范围：其中健康云21行、基层HIS 53行，共74行；通知正文称21+52=73个。基层HIS中的远程会诊复用了300-001/300-002，而规范另有一个无独立交易码的“报告跨机构调阅”能力。项目必须分别按“接口行、唯一交易码、业务能力”确认口径。'));
children.push(p('首轮范围需做三项修正：云平台登录/区划/用户与基层100-*基础接口由“按实际/99”提升为P0前置；电子病历400-003虽在Excel标记“不做”，但规范明确要求病历回写，应列为P2必补；报告跨机构调阅需补入范围确认。'));

children.push(h2('1.1 核心结论'));
[
  '健康云21个接口全部由院内集成平台主动调用省云平台，采用HTTPS、REST/JSON、hie_secret签名、c_token与登录token。',
  '基层HIS接口以医院侧WebService方式暴露，第三方系统按TradeCode调用；电子病历、远程会诊、报告互认还包含HIS打开第三方页面的反向流程。',
  '原规范没有形成完整的幂等、超时、重试、熔断、补偿和监控约定，必须由院内集成平台补齐。',
  '规范内存在多处阻断性冲突，尤其是500-003/500-004、700-003/700-004、300-003成功值，以及PHIS_Interface/HIS_Interface；未书面确认前不得进入生产。',
  '生产环境应使用专网或严格IP白名单、TLS、等保要求、最小权限和敏感日志脱敏；测试文档中的HTTP地址、账号、验证码只用于隔离联调。'
].forEach(x => children.push(bullet(x)));

children.push(h1('2. 范围与现状评估'));
children.push(h2('2.1 接口范围'));
children.push(table(['域', 'Excel行数', '现状', '方案判断'], [
  ['健康档案云平台', '21', '6项“可做”、6项“可做但依赖其他系统改造”、8项按实际、1项不做', '上传/修改是核心；查询与基础接口是前置或工作站能力'],
  ['基层HIS', '53', '30项可做、23项不做；含远程会诊复用接口', '基础、PACS/LIS/心电、转诊、EMR具备改造基础'],
  ['规范补充能力', '1', 'Excel未列', '报告跨机构调阅，无独立交易码，需作为页面/API能力确认']
], [2100, 1200, 3100, 3000]));
children.push(h2('2.2 原优先级的依赖倒置'));
children.push(p('原清单把PACS/LIS/心电与健康档案上传列为优先级1，却把认证、机构、人员和目录接口列为99或“按实际”。这会导致高优先级业务没有稳定主数据和授权基础。实施优先级应按依赖重排，而不是照抄原数字。'));
children.push(h2('2.3 编制依据与信息边界'));
children.push(table(['依据', '用途', '当前证据边界'], [
  ['省级开放通知', '确认开放范围、申请流程和安全要求', '可确认政策要求；不能替代医院接入批复'],
  ['两套V1.0接口规范', '识别协议、字段、交易码和调用流程', '存在勘误项；冲突处不得据此猜测实现'],
  ['原《接口列表.xlsx》', '识别医院初步范围、优先级和改造意见', '属于需求输入，不代表平台主管部门或厂商最终承诺'],
  ['联调环境资料', '固化URL、WSDL、证书、账号、白名单、限流和真实回执', '目前未纳入本方案证据；取得后登记到配套矩阵并版本化']
], [2500, 2800, 3900]));
children.push(p('本方案给出目标设计与执行基线，不等同于接口开通批准或生产可用证明。所有标记为“待提供、待确认、阻断”的内容，在责任方提交可复核证据前均保持未关闭状态。'));

children.push(h1('3. 目标架构'));
children.push(h2('3.1 逻辑架构'));
children.push(p('C/S工作站 -> HIS业务服务/数据库视图 -> 院内集成平台 -> 省健康档案云平台、基层统建系统及第三方业务系统。外部系统调用医院时，流量先经过医院边界接入区/API网关，再进入院内集成平台，最终访问受控HIS服务；禁止外部系统直连HIS数据库。'));
children.push(table(['组件', '职责'], [
  ['API网关/边界代理', 'TLS终止、IP白名单、限流、报文大小限制、访问审计；SOAP入口仅开放所需方法'],
  ['健康云适配器', 'REST路由、MD5签名、token缓存与刷新、0/200成功码兼容、字段字典转换'],
  ['基层HIS适配器', 'PHIS_Interface统一入口、TradeCode路由、SOAP/XML与JSON字符串解析、响应模型归一'],
  ['数据映射层', '患者主索引、机构/人员/科室、诊断、三大目录和公共卫生字典映射'],
  ['可靠消息/出站箱', '上传、修改、报告回写等写操作异步化；去重、重试、死信和人工重放'],
  ['回执与对账库', '保存云平台id/sjlyId、外部流水、原始报文摘要、状态轨迹和日对账结果'],
  ['运维控制台', '接口开关、版本化配置、凭证轮换、失败检索、脱敏重放、指标与告警']
], [2400, 6800]));
children.push(h2('3.2 C/S工作站改造原则'));
[
  '工作站只调用院内服务，使用当前登录用户与就诊上下文，不保存省平台账号、token或验证码。',
  '档案摘要、电子病历、互认报告采用按需入口；长耗时操作异步提交并显示可追踪状态。',
  '上传/修改在业务审核完成后触发；未通过字段校验的数据进入待处理列表，不阻塞HIS主流程。',
  'PACS/LIS/心电报告回写必须在报告审核后执行，撤销或修订通过版本号控制。'
].forEach(x => children.push(bullet(x)));

children.push(h2('3.3 部署与环境隔离'));
children.push(table(['区域/环境', '部署内容', '边界要求'], [
  ['院内业务区', 'HIS/EMR、受控只读视图或院内服务', '不得被外部系统直连数据库；仅向集成区开放最小端口'],
  ['院内集成区', 'API网关、适配器、队列、映射库、回执与运维控制台', '按机构和环境隔离配置；管理面与业务面分离'],
  ['边界接入区', '反向代理、WAF/访问控制、证书与出口策略', '专网优先；临时互联网仅限审批后的IP和时段'],
  ['开发/测试/生产', '独立地址、账号、密钥、队列、数据库和日志索引', '禁止跨环境复用凭证与真实健康数据；测试数据须脱敏'],
  ['省平台/第三方', '健康云、基层统建应用、PACS/LIS/心电/互认等', '以书面授权、正式路由、WSDL/OpenAPI和限流基线为准']
], [1900, 3300, 4000]));

children.push(h1('4. 接口调用与数据流'));
children.push(h2('4.1 健康档案云平台'));
children.push(p('调用链为：取得接入授权 -> 登录获取access token -> 缓存区划与本机构用户 -> HIS业务数据校验与字典转换 -> POST上传 -> 事务保存id/sjlyId/jtId回执 -> 后续PUT修改。所有调用携带hie_event_code、hie_app_key、hie_time_stamp、hie_secret，以及c_token和accesstoken请求头。'));
children.push(p('hie_secret按文档要求计算md5(hie_app_key+授权key+hie_event_code+hie_time_stamp)。平台允许时间偏差窗口最长半小时，但医院服务器仍应统一NTP校时；生产不得因兼容MD5而放宽网络边界控制。'));
children.push(h2('4.2 基层HIS WebService'));
children.push(p('所有交易经SOAP POST调用统一入口，一级参数为TradeCode与InputParameter，后者是JSON字符串。适配层必须同时容忍Result/result、Msg/msg、数值/字符串和Msg嵌套JSON字符串等历史差异，并将其归一成内部统一响应；原始报文需加密留存或留摘要以便审计。'));
children.push(h2('4.3 关键业务闭环'));
children.push(table(['业务', '闭环'], [
  ['健康档案', 'HIS视图 -> 校验/映射 -> 上传 -> 回执落库 -> 修改引用id/sjlyId -> 对账'],
  ['PACS/LIS/心电', '医技站按申请单号拉取 -> 执行 -> 审核报告 -> FHIR+Base64回写 -> HIS展示/状态更新'],
  ['电子病历', 'HIS携业务ID和短效token打开EMR -> EMR验token -> 查询患者/诊断/护理 -> 保存签名 -> 400-003回写'],
  ['双向转诊', '上转查询300-001/002；下转先取机构信息，再以FHIR Base64调用300-003并保存回执'],
  ['便民挂缴查', '基础目录与虚拟收费员 -> 挂号/缴费 -> 未知结果查状态 -> 原路退费协调 -> 200-015日对账'],
  ['报告互认', 'HIS按患者身份查询有无报告 -> 展示状态 -> 打开互认平台报告页；需明确授权和最小披露']
], [2100, 7100]));

children.push(h2('4.4 统一处理状态与审计链'));
children.push(p('院内集成平台对所有写操作使用统一状态：RECEIVED（已接收）-> VALIDATED（已校验）-> SENT（已发送）-> ACKNOWLEDGED（已取得明确回执）-> RECONCILED（已对账）。可恢复失败进入RETRY_WAIT，不可自动判断的超时或冲突进入MANUAL_REVIEW，明确业务拒绝进入REJECTED。只有取得可核验回执才能标记ACKNOWLEDGED，HTTP 200本身不等于业务成功。'));
children.push(p('每次状态迁移至少记录requestId、机构、接口代码、源业务主键、报文摘要、目标地址标识、开始/结束时间、响应代码、重试次数、操作者或服务身份和配置版本。敏感原文按最小化原则加密保存，并设置访问审批和保留期限。'));

children.push(h1('5. 可靠性与安全设计'));
children.push(h2('5.1 幂等与补偿'));
[
  '上传：以机构+源系统+源主键+业务类型建立唯一键；平台回执与本地状态同事务保存。',
  '修改：以平台id+sjlyId+源版本号去重；发生版本冲突时停止自动覆盖并转人工。',
  '报告/病历回写：以申请单或业务ID+文档类型+版本建立唯一键，重复请求返回既有处理结果。',
  '挂号/缴费/退费：使用厂商唯一标识和外部业务单号；超时结果未知时先查挂号、已缴费或账单状态，禁止直接重发。',
  '查询：仅对网络异常、超时和5xx执行最多2次指数退避重试；鉴权、参数和权限错误不重试。'
].forEach(x => children.push(bullet(x)));
children.push(h2('5.2 超时与容量'));
children.push(p('建议连接超时3秒、普通查询读取15秒、写操作30秒、大病历/护理报文60秒。健康云缺省QPS=300，但医院网关应按业务和厂商设置更低的配额并支持动态调整。轮询类接口需加入抖动，避免多个终端同时整秒请求。'));
children.push(h2('5.3 安全控制'));
[
  '密钥、c_token、平台账号和基层验证码放入集中凭证库，按环境和机构隔离，禁止写入客户端、源码和普通日志。',
  '日志默认脱敏姓名、证件号、电话、地址、病历和报告；排障查看明文需审批并全程审计。',
  '单点登录token应一次性、短时有效并绑定用户、机构、业务ID与目标系统；URL中只放不可逆短票据。',
  '外部调用经专网或IP白名单和TLS，SOAP解析关闭外部实体，限制XML/JSON/Base64及解压后大小。',
  '按接口事件码/交易码授予最小权限；离职人员、停用机构和过期授权应自动失效。'
].forEach(x => children.push(bullet(x)));

children.push(h1('6. 规范冲突与待确认项'));
children.push(table(['级别', '问题', '处理要求'], [
  ['阻断', '健康云成功码200与示例0冲突', '联调确认；未确认前用配置兼容0/200并校验业务数据'],
  ['阻断', '基层接口要求HTTPS但地址/示例为HTTP；PHIS_Interface与HIS_Interface冲突', '主管部门提供正式WSDL、URL、SOAPAction、证书与函数名基线'],
  ['阻断', '500-003详细交易号写500-004；700-003写700-004', '书面确认实际TradeCode，代码配置化'],
  ['阻断', '300-003的Result成功值自相矛盾', '以正式回执样例和联调结果固化'],
  ['高', '100-009又被写为9000/3102；900-001参数表写1002', '按总览代码联调并形成勘误单'],
  ['高', '400-003在Excel不做，但规范要求病历回写', '纳入电子病历验收范围'],
  ['高', '接口73/74口径、复用交易码、无交易码能力不一致', '申请表按业务能力列明，验收表同时记录行数和唯一交易码数'],
  ['高', '日期、大小写、Msg类型、路径存在多种格式', '适配层配置化容错，医院内部模型保持严格类型']
], [1000, 4100, 4100]));

children.push(h2('6.1 联调准入门槛'));
children.push(table(['门槛', '必须具备的证据', '未满足时处理'], [
  ['G1 范围', '接入申请获批；机构、业务域、接口代码和使用期限一致', '不安排该域开发联调'],
  ['G2 契约', '正式WSDL/OpenAPI、勘误确认、成功码和错误码、报文样例', '标记CONTRACT_BLOCKED'],
  ['G3 网络', '测试/生产地址、专网或白名单、端口、DNS、TLS证书链验证通过', '仅做离线契约准备'],
  ['G4 身份', '厂商编号、账号、验证码、app key/token的发放与轮换责任明确', '不得以共享或测试凭证替代'],
  ['G5 数据', '脱敏样例、字典版本、机构人员映射和患者匹配规则经业务确认', '不得用臆造字段映射开发'],
  ['G6 验收', '测试范围、数据准备、指标、签字人和证据存放位置明确', '不得宣称该域已具备上线条件']
], [1600, 4800, 2800]));

children.push(h1('7. 分期实施计划'));
children.push(table(['阶段', '范围', '退出条件'], [
  ['P0 底座', '审批、网络、安全、网关、健康云基础接口、基层100-*、字典和主索引', '认证/目录连续7天成功率不低于99.9%，冲突项有书面基线'],
  ['P1 核心', '健康档案与体检上传/修改；PACS/LIS/心电', '重复、超时、重放、报告回写、业务抽样和对账通过'],
  ['P2 病历', '400-001~010，含400-003回写', 'SSO、查询、保存、回写、历史调阅及审计闭环'],
  ['P3 协同', '档案摘要查询、双向转诊', '患者匹配和转诊FHIR全链路通过'],
  ['P4 扩展', '慢病、家医、便民、叫号、远程会诊、报告互认', '业务主管部门确认范围后分域验收']
], [1400, 5000, 2800]));
children.push(h2('7.1 角色分工'));
children.push(table(['角色', '主要责任'], [
  ['医院信息科', '项目牵头、网络与安全、主数据、厂商协调、变更和上线审批'],
  ['业务科室', '字段口径、流程与权限确认、样例数据、业务验收'],
  ['HIS/EMR厂商', '受控服务/视图、工作站入口、业务状态回写、性能与数据一致性'],
  ['集成平台厂商', '协议适配、映射、队列、幂等、监控、对账、工具与技术文档'],
  ['省/基层平台方', '授权、正式WSDL与路由、勘误确认、联调环境、限流和错误码解释']
], [2300, 6900]));

children.push(h2('7.2 责任与里程碑控制'));
children.push(p('每项接口仅设一个执行责任方和一个验收责任方。医院信息科维护范围、阻断项和版本基线；厂商按矩阵登记负责人、计划日期、当前状态和证据链接。状态只能按“未开始、合同阻断、环境阻断、开发中、联调中、待验收、已通过、暂缓”流转，口头完成不作为关闭依据。'));
children.push(table(['里程碑', '主责', '完成证据'], [
  ['M0 范围冻结', '医院信息科', '批准的申请表、接口范围和不做项决策记录'],
  ['M1 契约基线', '省/基层平台方+集成平台厂商', 'WSDL/OpenAPI、勘误单、样例、版本指纹'],
  ['M2 环境就绪', '医院信息科+网络安全', '连通性、TLS、白名单、NTP和凭证验证记录'],
  ['M3 单接口通过', '对应开发厂商', '请求响应、断言、日志traceId和缺陷关闭记录'],
  ['M4 业务域验收', '业务科室+信息科', '端到端场景、数据一致性、补偿和对账报告'],
  ['M5 生产准入', '医院变更审批责任人', '上线检查表、回退演练、监控值班和签字记录']
], [2000, 3200, 4000]));

children.push(h1('8. 测试与验收'));
[
  '契约测试：每个事件码/交易码覆盖必填、边界、空结果、非法字典、大小写与响应类型变体。',
  '业务测试：按患者、就诊、申请单、报告、病历、转诊和收费执行端到端闭环。',
  '可靠性测试：重复请求、服务超时、断网、进程重启、回执丢失、消息积压、死信重放和对账差异。',
  '安全测试：越权、过期token、签名错误、重放、恶意XML、超大Base64、日志脱敏和凭证轮换。',
  '性能测试：按峰值并发与轮询终端数压测，验证网关限流、连接池、队列积压和HIS数据库负载。',
  '上线准入：阻断项关闭，P0稳定运行，回退方案演练，监控告警接入，业务与信息科共同签字。'
].forEach(x => children.push(bullet(x)));
children.push(h2('8.1 建议指标'));
children.push(table(['指标', '目标'], [
  ['查询接口成功率', '不低于99.9%（剔除明确业务拒绝）'],
  ['写入最终成功率', '经自动补偿后不低于99.95%'],
  ['重复业务写入', '0'], ['患者/机构/人员错配', '0'],
  ['P95响应时间', '普通查询不高于3秒；大病历按联调基线'],
  ['故障可追踪性', '100%请求具备requestId/traceId和状态轨迹']
], [3200, 6000]));

children.push(h2('8.2 验收证据最小集合'));
children.push(p('每个接口至少保留：契约版本或文件哈希、脱敏请求与响应、预期断言、实际结果、requestId/traceId、执行时间、环境、配置版本、执行人、缺陷编号和复测结论。写接口还应提供重复请求、超时未知、重放、回执落库和对账证据；页面跳转类还应提供短票据失效、越权和返回路径测试。'));

children.push(h1('9. 上线与回退'));
children.push(p('上线采用机构/业务域双维度灰度。接口、事件码、交易码、路由、成功码和超时均配置化；每个写入业务均有独立开关。回退时先停新写入，再清空或冻结队列，保留查询能力和完整台账，不删除已生成的回执映射。'));
children.push(p('上线后前两周每日核对上传、报告回写、病历回写和异常队列；随后转为自动日报与周度抽查。任何规范变更都先更新契约测试和接口矩阵，再按变更流程发布。'));

children.push(h2('9.1 上线检查与量化回退触发'));
children.push(table(['类别', '上线前检查', '触发回退/停写条件'], [
  ['数据正确性', '患者、机构、人员、申请单和报告抽样100%匹配', '发现患者错配、跨机构越权或不可逆错误写入，立即停写'],
  ['业务成功率', '核心写入在试运行窗口达到约定最终成功率', '连续15分钟最终成功率低于99%，且补偿无法恢复'],
  ['性能容量', '峰值压测通过，HIS数据库和队列有容量余量', 'P95连续15分钟超过基线2倍或队列预计2小时无法清空'],
  ['安全', '权限、脱敏、证书、凭证轮换和审计验证通过', '出现凭证泄露、未授权访问或敏感日志外泄'],
  ['可运维性', '告警、值班、重放、对账和回退演练完成', '关键告警失效、状态不可追踪或无法执行已批准回退步骤']
], [1800, 4100, 3300]));
children.push(p('实际阈值应在容量测试和业务评审后写入上线检查表；上表未确认的数值仅作为评审起点。回退动作、审批人、执行人和恢复条件必须在变更单中具体到业务域与机构。'));

children.push(h1('附录A 交付清单'));
[
  '总体方案（本文件）。',
  '接口矩阵（含75项：Excel原74行+规范补充能力1项）、待确认事项、实施计划、数据治理清单和健康档案字段。',
  '联调后补充：正式WSDL/OpenAPI、勘误确认单、字段映射、错误码表、测试报告、上线与回退记录。'
].forEach(x => children.push(bullet(x)));

children.push(h1('附录B 首轮十个工作日行动清单'));
children.push(table(['时点', '行动', '输出'], [
  ['D1-D2', '确认申请范围、机构清单、责任人和不做项；向平台主管部门提交规范冲突清单', '范围基线、责任表、勘误请求'],
  ['D2-D4', '取得并校验测试环境WSDL/OpenAPI、URL、证书链、白名单、账号与限流', '环境参数表、连通性证据'],
  ['D3-D5', '准备脱敏患者/就诊/申请单/报告/病历样例与字典映射', '样例包、字段映射初稿'],
  ['D5-D7', '完成P0认证、机构、人员和目录的契约测试与最小调用链', '测试记录、原始报文摘要、问题单'],
  ['D8-D10', '评审P1范围、容量基线、验收场景和上线窗口', 'P1任务包、验收基线、更新后的矩阵']
], [1500, 5000, 2700]));

children.push(h1('附录C 文档维护规则'));
children.push(p('总体方案描述稳定架构与治理规则；接口矩阵维护逐接口状态、责任和证据；正式契约及样例独立归档并记录版本或哈希。任何范围、交易码、字段必填性、成功码、路由或安全规则变化，均先登记变更，再同步更新契约测试与本方案版本。'));

const doc = new Document({
  styles: {
    default: { document: { run: { font: 'Microsoft YaHei', size: 21 }, paragraph: { spacing: { line: 340 } } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { font: 'Microsoft YaHei', size: 32, bold: true, color: blue },
        paragraph: { spacing: { before: 280, after: 140 }, outlineLevel: 0 } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { font: 'Microsoft YaHei', size: 26, bold: true, color: blue },
        paragraph: { spacing: { before: 220, after: 100 }, outlineLevel: 1 } },
      { id: 'Heading3', name: 'Heading 3', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { font: 'Microsoft YaHei', size: 23, bold: true, color: '333333' },
        paragraph: { spacing: { before: 180, after: 80 }, outlineLevel: 2 } }
    ]
  },
  numbering: { config: [{ reference: 'bullets', levels: [{ level: 0, format: 'bullet', text: '•', alignment: AlignmentType.LEFT,
    style: { paragraph: { indent: { left: 520, hanging: 260 } } } }] }] },
  sections: [{
    properties: { page: { margin: { top: 1000, right: 900, bottom: 900, left: 900 } } },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
      children: [run('县级人民医院“基层和健康 V1.0”对接总体方案  |  '), new TextRun({ children: [PageNumber.CURRENT], font: 'Microsoft YaHei', size: 18 })] })] }) },
    children
  }]
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync(output, buffer);
  console.log(`已生成 ${output}`);
});
