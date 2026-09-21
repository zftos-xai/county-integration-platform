/** 原型状态徽标可使用的视觉含义。 */
export type StatusTone = 'success' | 'primary' | 'warning' | 'danger' | 'neutral'

const businessResultNames: Record<string, string> = {
  '处理成功': '已确认写入',
  '已核实成功': '已确认写入',
  '已写入': '已确认写入',
  'HIS仅确认第1版': '结果未知',
  '暂时无法确认': '结果未知',
  '仍无法确认': '结果未知',
  '无法确认': '结果未知',
  '第1版明确未写入': '明确未写入',
}

const progressNames: Record<string, string> = {
  '结果未知': '待核查',
  '持续核查': '核查中',
  '已核实成功': '已完成',
  '明确未写入': '已完成',
  '版本拦截': '已拦截',
  '等待源系统新版本': '等待新请求',
  '已关联新请求': '已完成',
  '发送已停止': '已停止发送',
  '到期暂停': '已停止发送',
  '待核查后重新提交': '等待外部处理',
  '待恢复检查': '待检查',
}

const dataStatusNames: Record<string, string> = {
  '暂存可用': '临时保存中',
  '已到期': '待清理',
  '仅保存交换记录': '无正文暂存',
  '原版本不可取得': '无正文暂存',
  '仅可重取最新版本': '无正文暂存',
}

/** Returns the approved display name for a business result without changing stored workflow values. */
export function businessResultLabel(value: string): string {
  return businessResultNames[value] ?? value
}

/** Returns the approved display name for workflow progress. */
export function progressStatusLabel(value: string, assigned = false): string {
  if (value === '结果未知' && assigned) return '核查中'
  return progressNames[value] ?? value
}

/** Returns the approved display name for temporary-data state. */
export function dataStatusLabel(value: string): string {
  return dataStatusNames[value] ?? value
}

/** Returns the common visual tone for a business-result badge. */
export function businessResultTone(value: string): StatusTone {
  const label = businessResultLabel(value)
  if (['已确认写入', '重复已识别'].includes(label)) return 'success'
  if (['结果未知', '尚未发送'].includes(label)) return 'warning'
  if (['明确未写入', '校验未通过', '查询失败', '越权拒绝', '到期未完成'].includes(label)) return 'danger'
  return 'neutral'
}

/** Returns the common visual tone for processing progress. */
export function progressStatusTone(value: string): StatusTone {
  const label = progressStatusLabel(value)
  if (label === '已完成') return 'success'
  if (['处理中', '核查中'].includes(label)) return 'primary'
  if (['已拦截', '已停止发送'].includes(label)) return 'danger'
  if (label.startsWith('待') || label.startsWith('等待')) return 'warning'
  return 'neutral'
}

/** Returns the common visual tone for data-retention state. */
export function dataStatusTone(value: string): StatusTone {
  const label = dataStatusLabel(value)
  if (label === '已清理' || label === '已入库') return 'success'
  if (label === '临时保存中') return 'primary'
  if (label === '清理失败' || label === '待清理') return 'danger'
  return 'neutral'
}

/** Makes prototype-only enablement explicit while keeping the operational state name stable. */
export function interfaceStatusLabel(value: string): string {
  return value === '演示启用' ? '已启用' : value
}

/** Returns the approved display name for enabled or disabled managed resources. */
export function enablementStatusLabel(value: string): string {
  if (value === '启用') return '已启用'
  if (value === '停用') return '已停用'
  return value
}

/** Makes foundation-data status self-explanatory outside its page context. */
export function foundationStatusLabel(value: string): string {
  return value === '正常' ? '同步正常' : value
}
