export type CaseStatus = '结果未知' | '校验未通过' | '越权拒绝' | '已受理' | '处理成功' | '重复已识别'

export type Scenario = {
  id: string
  domain: string
  title: string
  status: CaseStatus
  direction: string
  interfaceCode: string
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
}

// All values are invented for the prototype. Codes and field concepts follow the reference contracts.
export const scenarios: Scenario[] = [
  {
    id: 'DESIGN-01', domain: '健康档案', title: '上传超时后回执不明', status: '结果未知',
    direction: '院内系统 → 健康云', interfaceCode: 'ACB666576D94A008',
    organization: '示例青禾镇卫生院', organizationCode: 'SIM-ORG-001',
    sourceSystem: '院内 HIS', targetSystem: '健康档案云平台',
    sourceRecordId: 'SIM-EHR-240914-018', patientRef: 'SIM-PAT-018',
    requestId: 'SIM-REQ-20260914-0018', occurredAt: '2026-09-14 09:26:43',
    receipt: '未取得明确业务回执；id / sjlyId 待核查',
    question: '目标端是否已写入，能否按源业务主键补查？',
    steps: ['09:26:43 已受理源记录 SIM-EHR-240914-018', '09:26:44 机构与必填字段校验通过', '09:27:14 发送超时，未取得明确业务回执', '待核查目标结果或进入对账，不自动重发'],
  },
  {
    id: 'DESIGN-02', domain: '检查报告', title: '申请单与报告版本不一致', status: '校验未通过',
    direction: '医技系统 → HIS', interfaceCode: '500-003',
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
    direction: '外部系统 → 平台', interfaceCode: '100-008',
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
    direction: '院内系统 → 健康云', interfaceCode: 'ACB666576D94A000',
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
    direction: '医技系统 → HIS', interfaceCode: '600-003',
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
    direction: '院内系统 → 健康云', interfaceCode: 'ACB666576D94A008',
    organization: '示例青禾镇卫生院', organizationCode: 'SIM-ORG-001',
    sourceSystem: '院内 HIS', targetSystem: '健康档案云平台',
    sourceRecordId: 'SIM-EHR-240914-007', patientRef: 'SIM-PAT-007',
    requestId: 'SIM-REQ-20260914-0039', occurredAt: '2026-09-14 11:03:02',
    receipt: '引用原请求 SIM-REQ-20260914-0007；不生成第二笔写入',
    question: '相同机构、源主键和版本是否命中原有处理记录？',
    steps: ['11:03:02 收到相同源业务主键和版本', '11:03:02 平台识别已存在请求', '11:03:02 返回原处理记录引用', '不再次写入目标系统'],
  },
]
