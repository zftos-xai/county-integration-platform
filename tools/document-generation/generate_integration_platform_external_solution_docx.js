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
const lightBlue = 'DDEBF7';
const lightGray = 'F3F6F8';
const gray = '666666';
const red = 'C00000';
const yellow = 'FFF2CC';
const border = { style: BorderStyle.SINGLE, size: 4, color: 'B7C9D6' };

const run = (text, options = {}) => new TextRun({
  text: String(text), font: 'Microsoft YaHei', size: 21, ...options,
});

const p = (text, options = {}) => new Paragraph({
  children: [run(text, options.run || {})],
  spacing: { before: options.before || 0, after: options.after ?? 120, line: options.line || 340 },
  alignment: options.alignment,
  heading: options.heading,
  pageBreakBefore: options.pageBreakBefore,
  keepNext: Boolean(options.heading),
  keepLines: Boolean(options.heading),
});

const bullet = (text) => new Paragraph({
  children: [run(text)],
  numbering: { reference: 'bullets', level: 0 },
  spacing: { after: 85, line: 320 },
});

const h1 = (text) => p(text, { heading: HeadingLevel.HEADING_1 });
const h2 = (text) => p(text, { heading: HeadingLevel.HEADING_2 });

const cell = (text, width, options = {}) => new TableCell({
  width: { size: width, type: WidthType.DXA },
  shading: options.header
    ? { fill: navy, type: ShadingType.CLEAR, color: 'auto' }
    : options.fill
      ? { fill: options.fill, type: ShadingType.CLEAR, color: 'auto' }
      : undefined,
  margins: { top: 80, bottom: 80, left: 100, right: 100 },
  borders: { top: border, bottom: border, left: border, right: border },
  children: [new Paragraph({
    children: [run(text, options.header ? { bold: true, color: 'FFFFFF' } : { size: 19 })],
    spacing: { after: 0, line: 280 },
  })],
});

const table = (headers, rows, widths, zebra = true) => new Table({
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
        fill: zebra && rowIndex % 2 === 1 ? lightGray : undefined,
      })),
      cantSplit: true,
    })),
  ],
});

const note = (title, text, fill = lightBlue) => new Table({
  width: { size: 9200, type: WidthType.DXA },
  columnWidths: [1700, 7500],
  rows: [new TableRow({
    children: [cell(title, 1700, { fill }), cell(text, 7500, { fill })],
    cantSplit: true,
  })],
});

const pageBreak = () => new Paragraph({ children: [new PageBreak()] });
const children = [];

children.push(new Paragraph({
  spacing: { before: 1500, after: 280 }, alignment: AlignmentType.CENTER,
  children: [run('县人民医院与基层卫生院', { bold: true, size: 38, color: navy })],
}));
children.push(new Paragraph({
  spacing: { after: 250 }, alignment: AlignmentType.CENTER,
  children: [run('中间接口平台技术方案', { bold: true, size: 46, color: blue })],
}));
children.push(new Paragraph({
  spacing: { after: 850 }, alignment: AlignmentType.CENTER,
  children: [run('对外交流版 · 私有化部署', { size: 24, color: gray })],
}));
children.push(table(['文档属性', '内容'], [
  ['方案版本', 'V1.2（对外交流稿）'],
  ['编制日期', '2026年9月10日'],
  ['建设方式', '部署在医院指定的本地服务器或私有云环境中'],
  ['适用范围', '县人民医院与所辖基层卫生院之间的信息化协同'],
  ['主要用途', '用于双方业务沟通、方案确认、厂商交流和项目实施准备'],
  ['技术方向', 'Java、Spring Boot、MyBatis、Microsoft SQL Server、Web 管理页面'],
], [2200, 7000]));
children.push(p('说明：本方案用于项目交流和建设范围确认，具体接口以正式接口文件、接入审批和双方确认结果为准。', {
  before: 340, alignment: AlignmentType.CENTER, run: { bold: true, color: red, size: 18 },
}));
children.push(pageBreak());

children.push(h1('一、项目需求'));
children.push(p('本项目不是简单地“把两个系统连起来”，而是要让县人民医院与所辖基层卫生院形成可管理、可追踪、可验收的双向业务协作关系。相关平台和信息系统为双方协同提供技术支撑，但不增加新的业务层级。对接工作的核心，是明确双方哪些业务要协同、各自提供和使用什么信息、由谁负责、何时完成、出现问题如何处理，以及什么证据能够证明真正可用。'));
children.push(table(['管理结论', '建议'], [
  ['建设方式', '由医院统一组织对接，各业务系统通过院内统一对接能力交换信息，避免各厂商各自建设、重复投入和责任不清。'],
  ['范围口径', '现有材料形成75项业务能力盘点，但这只是规划清单，不等于75项全部立刻建设，也不等于平台主管部门已全部批准。'],
  ['推进顺序', '先完成审批、责任、环境和基础信息准备，再推进健康档案、体检、检查检验报告等核心业务，随后分阶段扩展。'],
  ['验收原则', '以真实业务闭环和可复核记录为准，不以口头确认、页面能打开或单次调用成功作为完成依据。'],
  ['上线原则', '按业务领域分批试运行；任何患者错配、越权访问或无法确认的错误写入，都应立即停止相关写入。'],
], [2100, 7100]));

children.push(h2('1.1 项目建设要求'));
[
  '确认首期建设范围：建议先做基础准备、健康档案与体检、检查检验与心电报告闭环。',
  '确认项目牵头人、各业务领域负责人、厂商负责人和最终验收人。',
  '确认平台主管部门负责提供正式接入文件、测试环境、账号授权及现有规范争议的书面结论。',
  '确认电子病历回写是否纳入必做范围；现有清单写“不做”，但接口规范要求形成闭环。',
  '确认生产上线采用分批试运行，并预留停止写入和恢复原流程的窗口。',
].forEach((x) => children.push(bullet(x)));

children.push(h2('1.2 主要问题与预期成效'));
children.push(table(['当前痛点', '对接后的目标状态', '可见成效'], [
  ['系统多、厂商多，问题发生后难判断责任归属', '统一入口、统一台账、统一问题编号', '问题能够定位到业务、系统、责任人和处理阶段'],
  ['健康档案、体检和报告信息存在重复录入或回传不及时', '业务完成后按规则自动交换并核对结果', '减少重复操作，及时发现漏传、错传和重复'],
  ['机构、人员、科室、诊断等基础信息口径不一致', '建立统一映射并明确维护责任', '降低人员、机构和患者匹配错误'],
  ['部分业务依赖多个系统，单个接口成功仍无法完成全流程', '按患者实际就医场景进行端到端验收', '以挂号、就诊、报告、病历、转诊等完整结果判断成败'],
  ['发生异常时缺少补救、对账和追踪手段', '保留状态记录，支持核对、补偿和人工处理', '避免重复收费、重复写入或长期积压无人处理'],
], [2700, 3600, 2900]));

children.push(h1('二、平台定位和总体结构'));
children.push(h2('（一）平台定位'));
children.push(p('中间接口平台部署在县人民医院指定环境内，对县人民医院和基层卫生院提供统一接口服务。平台向县医院侧连接 HIS、电子病历、LIS、PACS 等系统，向基层侧连接基层 HIS、业务工作站或经批准的统建系统。'));
children.push(p('平台不改变双方现有系统的业务职责。原系统继续负责业务办理和数据维护，中间接口平台只交换本次协同所需的数据，并保存必要的处理记录和结果回执。'));

children.push(h2('（二）总体结构'));
children.push(table(['信息来源', '中间接口平台', '信息接收方'], [
  ['县人民医院 HIS、电子病历、LIS、PACS 等', '统一接入、身份验证、数据检查、格式转换、防止重复、失败重试、过程记录、运行管理', '所辖基层卫生院业务系统或工作站'],
  ['所辖基层卫生院 HIS、业务工作站或统建系统', '双向交换，同一平台统一管理', '县人民医院相关业务系统'],
], [2700, 3800, 2700]));
children.push(p('数据根据业务需要双向流动。例如，基层卫生院可向县人民医院提交转诊或检查申请，县人民医院可向基层卫生院反馈受理状态、检查报告、诊疗建议和后续处理结果。'));

children.push(h2('（三）技术方向'));
children.push(table(['项目', '建议选择', '说明'], [
  ['后台服务', 'Java 21、Spring Boot 3.5、MyBatis', '采用成熟稳定的 Java 技术体系建设一套后台服务；后续具备条件时再升级 Spring Boot 4。'],
  ['数据库', 'Microsoft SQL Server 2019/2022', '与医院现有技术体系保持一致，具体版本以医院现网支持情况为准。'],
  ['管理页面', 'Vue 3 + TypeScript', '用于机构、接口、运行记录、异常和系统配置管理。'],
  ['嵌入页面', '网页组件或嵌入页面', '根据 HIS、基层工作站的浏览器环境选择，不强制原系统采用同一前端技术。'],
], [1800, 3000, 4400]));
children.push(note('版本说明', '首期使用 Spring Boot 3.5 建设，不在同一系统中同时运行 Spring Boot 3 和 4。升级到 Spring Boot 4 前，应完成依赖检查、功能测试、性能测试和试运行。', yellow));

children.push(h1('三、主要建设内容'));
children.push(table(['建设内容', '主要功能'], [
  ['统一接口服务', '为县人民医院和基层卫生院提供统一访问地址、身份验证和调用规则；支持现有 REST、WebService、JSON、FHIR 等接口形式。'],
  ['数据转换服务', '完成双方字段、机构、人员、科室和业务编码转换；对缺少必要信息的数据及时提示。'],
  ['数据交换管理', '统一接收、发送和记录数据；防止同一业务被重复处理；对临时失败进行再次处理。'],
  ['业务状态查询', '按机构、业务类型、业务单号或跟踪编号查询处理过程和最终结果。'],
  ['异常处理', '集中展示失败原因、影响范围和处理建议；经授权后可再次发送或转人工处理。'],
  ['对账管理', '定期核对发送数量、接收结果和业务状态，发现双方记录不一致时形成差异清单。'],
  ['机构和接口管理', '管理接入机构、系统、接口范围、访问期限、接口地址、启停状态和变更记录。'],
  ['运行监控', '展示接口成功率、响应时间、待处理数量、异常趋势和上下游系统运行情况。'],
], [2400, 6800]));

children.push(h2('（一）管理端 Web'));
children.push(p('管理端主要面向医院信息科、实施和运维人员。不同人员根据职责查看相应机构和功能，敏感信息默认隐藏，重要操作保留完整记录。'));
children.push(table(['页面', '主要内容'], [
  ['运行概览', '今日交换数量、成功情况、待处理数量、主要异常和系统连接状态。'],
  ['交换记录', '查询每笔业务从哪里发起、发送到哪里、当前状态、处理时间和失败原因。'],
  ['异常处理', '查看待处理事项、再次发送、填写处理说明和记录处理结果。'],
  ['接口管理', '维护接口名称、业务用途、责任方、访问地址、启停状态和适用机构。'],
  ['机构与权限', '维护县人民医院、基层卫生院、接入系统、使用人员和可查看的数据范围。'],
  ['对账与报表', '按日或按月形成运行统计、差异清单和项目验收材料。'],
], [2200, 7000]));

children.push(h2('（二）嵌入式页面组件'));
children.push(p('对于需要在 HIS 或基层工作站中直接使用的业务，可提供轻量页面组件，例如转诊进度、患者协同摘要、检查检验结果和异常提醒。用户从原系统进入时，由原系统确认身份并传递一次性短期凭证，无需再次输入平台账号。'));
children.push(p('是否采用网页组件或嵌入页面，应根据现有系统支持的浏览器或页面内核确定。嵌入页面加载失败时不得影响 HIS 原有业务操作，并应提供独立打开或报障方式。'));

children.push(h1('四、业务数据如何交换'));
children.push(h2('（一）即时查询'));
children.push(p('适用于需要马上看到结果的场景，如查询机构、人员、患者摘要、转诊状态或报告信息。平台接到请求后完成身份检查、数据转换和目标系统调用，再把统一结果返回给业务系统。若目标系统暂时不可用，应返回明确提示和跟踪编号。'));

children.push(h2('（二）可靠发送'));
children.push(p('适用于档案上传、检查检验报告回传、转诊提交等写入类业务。平台先可靠保存本次请求并返回“已受理”，再向目标系统发送。只有取得对方明确的业务成功结果，才能标记为完成；连接成功或收到普通网页响应不代表业务已经成功。'));

children.push(h2('（三）处理状态'));
children.push(table(['对外状态', '含义'], [
  ['已受理', '平台已收到请求并生成跟踪编号。'],
  ['处理中', '平台正在检查数据或向目标系统发送。'],
  ['处理成功', '目标系统已明确返回业务成功结果。'],
  ['等待再次处理', '因临时网络或系统故障，平台将在规定时间内再次处理。'],
  ['需要人工处理', '平台无法自动判断或已达到再次处理次数，需要相关人员确认。'],
  ['业务未通过', '目标系统明确拒绝，需根据返回原因修改数据或业务条件。'],
], [2500, 6700]));

children.push(h2('（四）防止重复和数据对账'));
children.push(p('平台根据机构、来源系统、业务类型、业务单号和数据版本识别同一笔业务。重复请求不重复创建业务结果，而是返回原有处理记录。对于处理超时但结果不明确的情况，优先查询原处理状态，不直接再次发送。'));
children.push(p('平台按约定周期对比已发送记录、对方回执和双方业务状态，形成差异清单。涉及挂号、缴费、退费等高风险业务时，未确认结果前不得自动重复操作。'));

children.push(h1('五、私有化部署方案'));
children.push(h2('（一）部署原则'));
children.push(table(['原则', '具体要求'], [
  ['院内部署', '后台服务、数据库、管理页面和运行日志部署在医院指定的本地机房或私有云环境中。'],
  ['统一出口', '平台通过医院批准的专网或受控网络访问外部系统；基层卫生院通过规定网络访问平台。'],
  ['环境分开', '测试、联调和生产环境分别使用独立地址、数据库、账号和访问凭证。'],
  ['数据不外流', '除正式接口要求发送的数据外，不向外部厂商平台同步运行日志、患者数据或数据库备份。'],
  ['远程访问受控', '外部厂商不默认拥有生产环境远程访问权限；确需支持时按医院审批、限定时间和操作范围，并保留记录。'],
], [2200, 7000]));

children.push(h2('（二）建议部署组成'));
children.push(table(['组成', '建议方式'], [
  ['访问入口', '使用医院现有网关、负载均衡或 Nginx，统一处理 HTTPS 证书、访问来源限制和流量控制。'],
  ['平台服务', '部署一套中间接口平台应用；生产环境建议运行两个服务实例，单个实例维护时业务仍可继续。'],
  ['平台数据库', '使用 Microsoft SQL Server 2019/2022，建立独立的平台数据库、账号、备份和维护计划。'],
  ['前端页面', '管理端和嵌入页面部署在医院指定服务器，通过平台后台服务访问数据。'],
  ['监控与备份', '优先接入医院现有监控、日志和备份体系，明确告警接收人和恢复流程。'],
], [2300, 6900]));

children.push(h2('（三）与 HIS 数据库的关系'));
children.push(note('必须明确', '平台可以采用与 HIS 相同的 SQL Server 技术，也可以在条件允许时复用同一数据库服务器，但必须建立独立数据库和独立账号，不在 HIS 业务数据库中直接建立平台表。'));
[
  '平台数据库只保存接口配置、交换过程、必要回执、对账和审计信息，不作为新的完整病历数据库。',
  '读取 HIS 数据优先通过 HIS 厂商提供的正式服务；如必须使用数据库视图，应由医院和 HIS 厂商共同确认，并使用只读账号。',
  '平台访问不得影响 HIS 正常运行，应限制数据库连接数量、查询时间和批量任务时间。',
  '平台数据库应单独备份和恢复。复用同一服务器时，要评估 CPU、内存、磁盘、备份窗口和故障影响。',
].forEach((x) => children.push(bullet(x)));

children.push(h1('六、安全和运行保障'));
children.push(table(['方面', '保障措施'], [
  ['身份与权限', '按人员、机构和职责分配权限；基层卫生院只能查看本机构获准使用的数据。'],
  ['接口凭证', '账号、密钥、验证码和访问凭证由平台统一保管，按机构和环境区分，不保存在客户端或普通日志中。'],
  ['传输安全', '优先使用医疗专网；需要互联网访问时，必须经过审批并采用 HTTPS、固定访问来源和安全防护。'],
  ['数据保护', '仅交换业务必需数据；姓名、证件号、电话、病历和报告等敏感信息在日志和页面中按权限隐藏。'],
  ['操作记录', '登录、查询、导出、再次发送、权限修改和接口配置变更均记录操作人、时间和内容。'],
  ['备份恢复', '数据库、配置和必要文件按医院制度备份，并定期验证能否恢复。'],
  ['运行告警', '对接口连续失败、凭证失效、待处理积压、数据不一致和异常访问及时通知责任人。'],
], [2100, 7100]));
children.push(p('系统安全等级、日志保存期限、数据分类、密码应用和备份恢复目标，由医院安全和运维部门结合现有制度最终确认。'));

children.push(h2('（一）建议运行目标'));
children.push(table(['项目', '建议目标'], [
  ['平台可用性', '每月不低于 99.9%，已批准的维护时间除外。'],
  ['即时查询', '普通查询原则上在 15 秒内返回；大病历或大报告可按接口单独设置。'],
  ['请求受理', '可靠发送类请求原则上在 1 秒内完成本地受理并返回跟踪编号。'],
  ['数据恢复', '建议最多丢失 15 分钟内的数据，发生故障后 2 小时内恢复；最终以医院现有能力确认。'],
], [2300, 6900]));

children.push(h1('七、建设步骤和验收'));
children.push(table(['阶段', '主要工作', '完成标志'], [
  ['第一阶段：需求确认', '确认机构名单、业务场景、首批接口、数据范围、责任人和验收方式。', '形成双方确认的业务范围和接口清单。'],
  ['第二阶段：接入准备', '取得正式接口文件、测试数据、网络地址、访问凭证和厂商支持安排。', '各项接入条件具备，问题和差异有书面结论。'],
  ['第三阶段：平台建设', '完成平台基础服务、管理页面、权限、运行记录、异常处理和部署环境。', '平台基础功能和私有化部署验证通过。'],
  ['第四阶段：首个闭环', '选择一个风险较低、责任清晰的业务，从基层发起到县医院反馈全流程打通。', '正常、重复、故障恢复、对账和业务使用均通过。'],
  ['第五阶段：分批扩展', '按基础信息、转诊、检查检验、病历和报告等业务逐批接入。', '每批接口独立测试、试运行和验收。'],
  ['第六阶段：正式运行', '扩大机构范围，完善监控、备份、运维和值守制度。', '达到约定运行指标并完成故障恢复演练。'],
], [1800, 4400, 3000]));

children.push(h2('（一）验收重点'));
[
  '业务流程：基层发起、县医院处理、状态反馈和结果返回形成完整闭环。',
  '数据一致：双方关键患者信息、业务单号、申请内容和结果抽样一致。',
  '异常恢复：重复请求、断网、超时、目标系统不可用和服务重启后能够正确恢复。',
  '安全权限：不同机构和不同角色不能查看或操作未经授权的数据。',
  '运行管理：每笔业务可查询、失败可定位、处理有记录、数据可对账。',
  '私有化交付：安装包、源代码或约定制品、数据库脚本、配置说明、部署手册、运维手册和测试报告齐全。',
].forEach((x) => children.push(bullet(x)));

children.push(h1('八、需要共同明确的事项'));
children.push(table(['需要明确', '主要责任方', '未明确时的处理'], [
  ['首批基层卫生院、首个业务场景和首批接口范围', '县人民医院、基层卫生院', '暂不进入对应业务开发。'],
  ['正式接口地址、调用方式、字段、成功结果、错误说明和脱敏样例', '各业务系统厂商', '只进行平台基础建设，不猜测接口实现。'],
  ['测试和生产网络、专网或访问来源限制、证书及访问凭证', '医院信息科、网络安全方', '暂不开展在线联调。'],
  ['县医院 HIS 数据通过服务接口还是受控只读视图提供', '医院信息科、HIS 厂商', '暂不确定具体数据读取方式。'],
  ['现网 SQL Server 版本、可用服务器资源、备份和恢复能力', '医院数据库管理员、运维人员', '部署规模和恢复目标待定。'],
  ['嵌入的业务系统、浏览器环境、登录方式和需要展示的内容', 'HIS 厂商、基层代表', '先保留独立页面，不承诺具体嵌入方式。'],
  ['机构数量、终端数量、业务高峰、每日交换量和大报文规模', '双方业务部门、信息科', '先按基础规模设计，上线前必须完成容量测试。'],
], [3600, 2500, 3100]));

children.push(h2('评审建议'));
children.push(p('建议先确定首个业务闭环和正式接口条件，同时开展中间接口平台基础建设。首个闭环应优先选择调用量可控、不涉及资金、上下游责任清晰且已有正式数据样例的场景。完成业务、数据、异常和运维验证后，再按业务域扩展到其他接口。'));
children.push(p('后续如需增加消息队列、缓存或大文件存储，应根据实际机构数量、业务量、处理积压和报文大小决定，不在首期默认增加系统复杂度。任何扩大数据用途或跨机构共享范围的需求，都需要重新进行业务和安全确认。'));

const doc = new Document({
  creator: 'Codex',
  title: '县人民医院与基层卫生院中间接口平台技术方案',
  description: '对外交流版私有化部署中间接口平台技术方案',
  styles: {
    default: {
      document: { run: { font: 'Microsoft YaHei', size: 21, color: '222222' }, paragraph: { spacing: { line: 340 } } },
      heading1: { run: { font: 'Microsoft YaHei', size: 30, bold: true, color: navy }, paragraph: { spacing: { before: 260, after: 150 }, outlineLevel: 0 } },
      heading2: { run: { font: 'Microsoft YaHei', size: 25, bold: true, color: blue }, paragraph: { spacing: { before: 190, after: 110 }, outlineLevel: 1 } },
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
          run('县人民医院与基层卫生院中间接口平台技术方案（对外交流版）  |  ', { size: 16, color: gray }),
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
