export type CaseStatus = '结果未知' | '明确未写入' | '校验未通过' | '越权拒绝' | '已受理' | '处理成功' | '重复已识别' | '待发送' | '查询失败'

export type Scenario = {
  id: string
  domain: string
  title: string
  status: CaseStatus
  direction: string
  interfaceCode: string
  interfaceName: string
  interfaceVersion: string
  interfaceLinkStatus: '已关联' | '方向待核实'
  organization: string
  organizationCode: string
  sourceSystem: string
  targetSystem: string
  sourceRecordId: string
  patientRef: string
  requestId: string
  occurredAt: string
  receipt: string
  question: string
  steps: string[]
  savedContent?: string
  comparison?: { sourceVersion: string; targetVersion: string; sourceResult: string; targetResult: string; basis: string }
}

// All values are invented for the prototype. Codes and field concepts follow the reference interface material.
export const scenarios: Scenario[] = [
  {
    id: 'DESIGN-13', domain: '检查报告', title: '同一报告两端版本不一致', status: '结果未知',
    direction: 'PACS → HIS', interfaceCode: '500-003', interfaceName: '回写检查报告', interfaceVersion: '参考文档V1.0', interfaceLinkStatus: '已关联', organization: '示例县人民医院', organizationCode: 'SIM-ORG-000',
    sourceSystem: 'PACS', targetSystem: 'HIS', sourceRecordId: 'SIM-APPL-240915-053', patientRef: 'SIM-PAT-053',
    requestId: 'SIM-REQ-20260915-0053', occurredAt: '2026-09-15 10:53:00',
    receipt: '双方均查询到1条同业务号报告，但PACS为第2版、HIS为第1版；本次第2版结果仍未确认。',
    question: '需按当前请求版本继续核查，不能仅凭条数一致确认成功。',
    comparison: { sourceVersion: '2', targetVersion: '1', sourceResult: '已审核', targetResult: '第1版已写入', basis: 'SIM-QUERY-COMPARE-053（合成查询依据）' },
    steps: ['10:53:00 第2版发送后回执未知', '11:05:00 合成查询：双方业务号相同，各1条', '11:05:00 HIS仅有第1版，第2版继续核查'],
  },
  {
    id: 'DESIGN-09', domain: '报告查询', title: '报告来源系统暂时不可用', status: '查询失败',
    direction: '基层系统 → PACS', interfaceCode: 'SIM-REPORT-QUERY', interfaceName: '查询检查报告', interfaceVersion: '演示规则V1', interfaceLinkStatus: '已关联', organization: '示例青禾镇卫生院', organizationCode: 'SIM-ORG-001',
    sourceSystem: '基层业务系统', targetSystem: 'PACS', sourceRecordId: 'SIM-QUERY-240915-049', patientRef: 'SIM-PAT-049',
    requestId: 'SIM-REQ-20260915-0049', occurredAt: '2026-09-15 10:49:00',
    receipt: 'PACS连接失败；本次无法查询，不能判断是否存在报告。未提供离线缓存。',
    question: '来源恢复后，由原业务系统重新发起查询。',
    steps: ['10:49:00 收到报告查询', '10:49:02 PACS连接失败', '10:49:02 返回查询失败，未返回空报告或历史报告'],
  },
  {
    id: 'DESIGN-10', domain: '健康档案', title: '备份恢复后需核查接收结果', status: '结果未知',
    direction: '院内系统 → 健康云', interfaceCode: 'ACB666576D94A008', interfaceName: '健康档案上传', interfaceVersion: '演示规则V1', interfaceLinkStatus: '已关联', organization: '示例青禾镇卫生院', organizationCode: 'SIM-ORG-001',
    sourceSystem: '院内 HIS', targetSystem: '健康档案云平台', sourceRecordId: 'SIM-EHR-240915-050', patientRef: 'SIM-PAT-050',
    requestId: 'SIM-REQ-20260915-0050', occurredAt: '2026-09-15 10:50:00',
    receipt: '恢复的记录早于最后一次发送，接收系统可能已经处理；自动发送暂停。',
    question: '先查询接收系统再决定是否恢复发送。',
    steps: ['10:50:00 接收档案请求', '10:51:00 发出请求，后续记录未包含在演示恢复点', '11:10:00 模拟恢复旧记录，暂停发送并核查结果'],
  },
  {
    id: 'DESIGN-11', domain: '检查报告', title: '来源无法提供原报告版本', status: '校验未通过',
    direction: 'PACS → HIS', interfaceCode: '500-003', interfaceName: '回写检查报告', interfaceVersion: '参考文档V1.0', interfaceLinkStatus: '已关联', organization: '示例县人民医院', organizationCode: 'SIM-ORG-000',
    sourceSystem: 'PACS', targetSystem: 'HIS', sourceRecordId: 'SIM-APPL-240915-051', patientRef: 'SIM-PAT-051',
    requestId: 'SIM-REQ-20260915-0051', occurredAt: '2026-09-15 10:51:00',
    receipt: '原请求为第1版；PACS只提供第2版，原版本不可取得，停止原请求发送。',
    question: '由源系统重新提供符合版本约定的请求。',
    steps: ['10:51:00 原请求第1版发送被拒绝', '10:55:00 来源查询仅返回第2版', '10:55:00 拦截恢复，未用新版替换原请求'],
  },
  {
    id: 'DESIGN-12', domain: '体检上传', title: '暂存清理失败，等待重试', status: '结果未知',
    direction: '院内系统 → 健康云', interfaceCode: 'ACB666576D94A000', interfaceName: '体检信息上传', interfaceVersion: '演示规则V1', interfaceLinkStatus: '已关联', organization: '示例青禾镇卫生院', organizationCode: 'SIM-ORG-001',
    sourceSystem: '院内 HIS', targetSystem: '健康档案云平台', sourceRecordId: 'SIM-EXAM-240915-052', patientRef: 'SIM-PAT-052',
    requestId: 'SIM-REQ-20260915-0052', occurredAt: '2026-09-15 10:52:00',
    receipt: '结果未知，发送已停止；暂存清理失败，需重试清理。',
    question: '清理完成后继续核查业务结果。',
    steps: ['10:52:00 请求发送超时', '11:00:00 到期后停止发送', '11:01:00 演示清理失败，保留失败记录'],
  },
  {
    id: 'DESIGN-07', domain: '检查报告', title: '原报告发送失败后产生修订版', status: '校验未通过',
    direction: 'PACS → HIS', interfaceCode: '500-003', interfaceName: '回写检查报告', interfaceVersion: '参考文档V1.0', interfaceLinkStatus: '已关联', organization: '示例县人民医院', organizationCode: 'SIM-ORG-000',
    sourceSystem: 'PACS', targetSystem: 'HIS', sourceRecordId: 'SIM-APPL-240915-041', patientRef: 'SIM-PAT-041',
    requestId: 'SIM-REQ-20260915-0041', occurredAt: '2026-09-15 10:00:00',
    receipt: '第1版明确未写入；来源已修订为第2版，禁止以最新内容替换原请求',
    question: '原版本能否恢复，修订版如何独立提交？',
    steps: ['10:00:00 第1版回写被明确拒绝', '10:10:00 源系统产生第2版', '10:12:00 发现版本变化，拦截原请求重试'],
  },
  {
    id: 'DESIGN-08', domain: '健康档案', title: '暂存到期且目标结果仍未知', status: '结果未知',
    direction: '院内系统 → 健康云', interfaceCode: 'ACB666576D94A008', interfaceName: '健康档案上传', interfaceVersion: '演示规则V1', interfaceLinkStatus: '已关联', organization: '示例青禾镇卫生院', organizationCode: 'SIM-ORG-001',
    sourceSystem: '院内 HIS', targetSystem: '健康档案云平台', sourceRecordId: 'SIM-EHR-240915-042', patientRef: 'SIM-PAT-042',
    requestId: 'SIM-REQ-20260915-0042', occurredAt: '2026-09-15 09:00:00',
    receipt: '发送超时；演示暂存期限已到，暂停后续发送', question: '如何清理到期数据，同时继续核查业务结果？',
    steps: ['09:00:00 已受理合成请求', '09:01:00 发送超时，目标结果未知', '11:00:00 达到演示期限，自动暂停'],
  },
  {
    id: 'DESIGN-01', domain: '健康档案', title: '上传超时后回执不明', status: '结果未知',
    direction: '院内系统 → 健康云', interfaceCode: 'ACB666576D94A008', interfaceName: '健康档案上传', interfaceVersion: '演示规则V1', interfaceLinkStatus: '已关联',
    organization: '示例青禾镇卫生院', organizationCode: 'SIM-ORG-001',
    sourceSystem: '院内 HIS', targetSystem: '健康档案云平台',
    sourceRecordId: 'SIM-EHR-240914-018', patientRef: 'SIM-PAT-018',
    requestId: 'SIM-REQ-20260914-0018', occurredAt: '2026-09-14 09:26:43',
    receipt: '未取得明确业务回执；id / sjlyId 待核查',
    question: '目标端是否已写入，能否按源业务主键补查？',
    steps: ['09:26:43 已受理源记录 SIM-EHR-240914-018', '09:26:44 机构与必填字段校验通过', '09:27:14 发送超时，未取得明确业务回执', '待核查目标结果，不自动重发'],
  },
  {
    id: 'DESIGN-02', domain: '检查报告', title: '申请单与报告版本不一致', status: '校验未通过',
    direction: '医技系统 → HIS', interfaceCode: '500-003', interfaceName: '回写检查报告', interfaceVersion: '参考文档V1.0', interfaceLinkStatus: '已关联',
    organization: '示例县人民医院', organizationCode: 'SIM-ORG-000',
    sourceSystem: 'PACS', targetSystem: 'HIS',
    sourceRecordId: 'SIM-APPL-240914-026', patientRef: 'SIM-PAT-026',
    requestId: 'SIM-REQ-20260914-0026', occurredAt: '2026-09-14 10:08:17',
    receipt: '未发送：申请单版本 2、报告引用版本 1',
    question: '报告是否已审核，申请单号和版本是否一致？',
    steps: ['10:08:17 收到 PACS 审核报告回写', '10:08:17 比对申请单、机构与报告版本', '10:08:18 版本不一致，停止写入 HIS', '待医技系统责任方核实后重新校验'],
  },
  {
    id: 'DESIGN-03', domain: '机构授权', title: '机构无接口访问权限', status: '越权拒绝',
    direction: '外部系统 → 平台', interfaceCode: '100-008', interfaceName: '医疗机构信息查询', interfaceVersion: '参考文档V1.0', interfaceLinkStatus: '方向待核实',
    organization: '示例未授权机构', organizationCode: 'SIM-ORG-099',
    sourceSystem: '基层业务系统', targetSystem: '院内集成平台',
    sourceRecordId: 'SIM-ACCESS-240914-003', patientRef: '未传递',
    requestId: 'SIM-REQ-20260914-0003', occurredAt: '2026-09-14 08:41:05',
    receipt: '服务端拒绝；未交换业务数据',
    question: '调用机构是否已配置该接口的访问权限？',
    steps: ['08:41:05 收到机构范围查询', '08:41:05 校验身份、机构和业务用途', '08:41:05 服务端拒绝越权请求', '记录不含敏感报文的审计事件'],
  },
  {
    id: 'DESIGN-04', domain: '体检上传', title: '业务审核后的异步受理', status: '已受理',
    direction: '院内系统 → 健康云', interfaceCode: 'ACB666576D94A000', interfaceName: '体检信息上传', interfaceVersion: '演示规则V1', interfaceLinkStatus: '已关联',
    organization: '示例青禾镇卫生院', organizationCode: 'SIM-ORG-001',
    sourceSystem: '院内 HIS', targetSystem: '健康档案云平台',
    sourceRecordId: 'SIM-EXAM-240914-011', patientRef: 'SIM-PAT-011',
    requestId: 'SIM-REQ-20260914-0011', occurredAt: '2026-09-14 09:12:06',
    receipt: '本地已受理；目标回执待取得',
    question: '已受理不等于目标端处理成功。',
    steps: ['09:12:05 原业务系统完成体检审核', '09:12:06 平台可靠保存源记录', '09:12:06 返回跟踪编号和已受理状态', '等待明确业务回执后再核对'],
  },
  {
    id: 'DESIGN-05', domain: '检验报告', title: '审核报告回写并取得回执', status: '处理成功',
    direction: '医技系统 → HIS', interfaceCode: '600-003', interfaceName: '回写检验报告', interfaceVersion: '参考文档V1.0', interfaceLinkStatus: '已关联',
    organization: '示例县人民医院', organizationCode: 'SIM-ORG-000',
    sourceSystem: 'LIS', targetSystem: 'HIS',
    sourceRecordId: 'SIM-APPL-240914-032', patientRef: 'SIM-PAT-032',
    requestId: 'SIM-REQ-20260914-0032', occurredAt: '2026-09-14 10:42:19',
    receipt: '演示业务回执 SIM-RCPT-0032；报告版本 1',
    question: '申请单、报告版本及 HIS 展示状态能否一致？',
    steps: ['10:41:50 LIS 按申请单号取得申请数据', '10:42:10 医技系统审核报告', '10:42:19 回写 HIS 并取得演示业务回执', '10:42:20 对照申请单与报告版本完成核对'],
  },
  {
    id: 'DESIGN-06', domain: '健康档案', title: '重复请求返回原处理结果', status: '重复已识别',
    savedContent: '已清理',
    direction: '院内系统 → 健康云', interfaceCode: 'ACB666576D94A008', interfaceName: '健康档案上传', interfaceVersion: '演示规则V1', interfaceLinkStatus: '已关联',
    organization: '示例青禾镇卫生院', organizationCode: 'SIM-ORG-001',
    sourceSystem: '院内 HIS', targetSystem: '健康档案云平台',
    sourceRecordId: 'SIM-EHR-240914-007', patientRef: 'SIM-PAT-007',
    requestId: 'SIM-REQ-20260914-0039', occurredAt: '2026-09-14 11:03:02',
    receipt: '原请求 SIM-REQ-20260914-0007 已成功；正文已清理，保留原结果引用，不生成第二笔写入',
    question: '相同机构、源主键和版本是否命中原有处理记录？',
    steps: ['11:03:02 收到相同源业务主键和版本', '11:03:02 平台识别已存在请求', '11:03:02 返回原处理记录引用', '不再次写入目标系统'],
  },
]
