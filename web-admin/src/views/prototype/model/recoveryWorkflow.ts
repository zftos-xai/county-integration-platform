/** Prototype-only workflow facts. No payload, real sending, deletion or vendor query occurs here. */
/** 原型异常恢复场景类别。 */
export type RecoveryKind = 'restart' | 'unknown' | 'version' | 'expiry'
/** 原型恢复流程支持的用户动作。 */
export type RecoveryAction = 'claim' | 'check' | 'resume' | 'written' | 'not-written' | 'uncertain' | 'hold-version' | 'request-version' | 'link-request' | 'stop' | 'confirm-cleanup' | 'clean-failed' | 'clean' | 'handoff'
/** 执行原型恢复动作时可补充的办理信息。 */
export type RecoveryDetails = {
  assignee?: string; nextCheckAt?: string; externalOwner?: string; externalDueAt?: string
  cleanupScope?: string; linkedRequestId?: string
}
/** 原型恢复事项的完整内存状态。 */
export type RecoveryTask = {
  id: string; scenarioId: string; kind: RecoveryKind; title: string; owner: string
  status: string; dataStatus: string; target: string; originalVersion: string; latestVersion: string
  deadline: string; policy: string; checked: boolean; stopped: boolean; cleanupFailed: boolean
  handoff: boolean; evidence: string; revision: number
  activeSender?: boolean
  resultConflict?: boolean
  assignee: string; nextCheckAt: string; externalOwner: string; externalDueAt: string
  cleanupScope: string; cleanupScopeConfirmed: boolean; linkedRequestId: string
  history: { time: string; action: string; evidence: string; note: string }[]
}
/** 恢复场景类别的界面名称。 */
export const recoveryKinds: Record<RecoveryKind, string> = {
  restart: '重启恢复', unknown: '结果未知', version: '报告版本变化', expiry: '数据到期',
}
/** 恢复动作的审计展示名称。 */
export const recoveryActions: Record<RecoveryAction, string> = {
  claim: '领取核查', check: '检查恢复条件', resume: '恢复到待发送', written: '登记目标已写入',
  'not-written': '登记明确未写入', uncertain: '登记仍无法确认',
  'hold-version': '保持版本拦截', 'request-version': '登记外部处理', 'link-request': '关联新请求',
  stop: '停止后续发送', 'confirm-cleanup': '确认清理范围', 'clean-failed': '登记清理失败', clean: '执行到期清理', handoff: '登记后续责任',
}
/** 创建一组相互隔离的合成恢复事项。 */
export function createRecoveryTasks(): RecoveryTask[] {
  const base = { owner: '平台运维', assignee: '', nextCheckAt: '', externalOwner: '', externalDueAt: '', cleanupScope: '', cleanupScopeConfirmed: false, linkedRequestId: '', originalVersion: '1', latestVersion: '1', checked: false, stopped: false,
    cleanupFailed: false, handoff: false, evidence: '', revision: 0, history: [], policy: 'SIM-POLICY-01（演示规则）' }
  return [
    { ...base, id: 'SIM-REC-01', scenarioId: 'DESIGN-04', kind: 'restart', title: '已受理后重启，等待恢复检查',
      status: '待恢复检查', dataStatus: '暂存可用', target: '尚未发送', deadline: '2026-09-16 12:00:00' },
    { ...base, id: 'SIM-REC-02', scenarioId: 'DESIGN-01', kind: 'unknown', title: '发送超时，目标结果需要核查',
      status: '结果未知', dataStatus: '暂存可用', target: '无法确认', deadline: '2026-09-16 12:00:00' },
    { ...base, id: 'SIM-REC-03', scenarioId: 'DESIGN-07', kind: 'version', title: '原报告第1版被第2版修订',
      status: '版本拦截', dataStatus: '仅可重取最新版本', target: '第1版明确未写入', latestVersion: '2', deadline: '不在平台暂存' },
    { ...base, id: 'SIM-REC-04', scenarioId: 'DESIGN-08', kind: 'expiry', title: '暂存已到期，业务仍未完成',
      status: '到期暂停', dataStatus: '已到期', target: '无法确认', deadline: '2026-09-15 11:00:00' },
    { ...base, id: 'SIM-REC-05', scenarioId: 'DESIGN-10', kind: 'unknown', title: '恢复旧记录后核查接收结果',
      status: '结果未知', dataStatus: '暂存可用', target: '无法确认', deadline: '2026-09-16 12:00:00' },
    { ...base, id: 'SIM-REC-06', scenarioId: 'DESIGN-11', kind: 'version', title: '原报告版本无法取得',
      status: '版本拦截', dataStatus: '原版本不可取得', target: '第1版明确未写入', latestVersion: '2', deadline: '不在平台暂存' },
    { ...base, id: 'SIM-REC-07', scenarioId: 'DESIGN-12', kind: 'expiry', title: '到期清理失败', stopped: true, cleanupFailed: true, cleanupScopeConfirmed: true, cleanupScope: '本次请求对应的暂存正文与重试副本',
      status: '发送已停止', dataStatus: '清理失败', target: '无法确认', deadline: '2026-09-15 11:00:00' },
    { ...base, id: 'SIM-REC-08', scenarioId: 'DESIGN-13', kind: 'unknown', title: '接收端版本与本次请求不一致', resultConflict: true,
      status: '结果未知', dataStatus: '仅保存交换记录', target: 'HIS仅确认第1版', originalVersion: '2', latestVersion: '2', deadline: '不在平台暂存' },
  ].map(task => ({ ...task, history: [] })) as RecoveryTask[]
}

/** Returns why an action cannot run, including role and current-state checks. */
export function recoveryBlock(task: RecoveryTask, action: RecoveryAction, role: string): string {
  if (role !== 'operator') return '管理视角只读，请切换运维视角办理。'
  const allowed: Record<RecoveryKind, RecoveryAction[]> = {
    restart: ['check', 'resume'], unknown: ['claim', 'written', 'not-written', 'uncertain', 'check', 'resume'],
    version: ['hold-version', 'request-version', 'link-request'], expiry: ['stop', 'confirm-cleanup', 'clean-failed', 'clean', 'handoff'],
  }
  if (!allowed[task.kind].includes(action)) return '此操作不适用于当前场景。'
  if (action === 'claim' && task.assignee) return '该事项已领取。'
  if (['written', 'not-written', 'uncertain'].includes(action) && !task.assignee) return '请先领取核查事项。'
  if (['stop', 'clean', 'clean-failed', 'check', 'resume'].includes(action) && task.activeSender) return '仍有正在发送的请求，请先核查执行状态。'
  if (action === 'check' || action === 'resume') {
    if (!['待恢复检查', '明确未写入', '待恢复'].includes(task.status)) return '当前处理阶段不能恢复发送。'
    if (task.dataStatus !== '暂存可用' || task.originalVersion !== task.latestVersion) return '原版本数据不可用，不能恢复发送。'
    if (!['尚未发送', '明确未写入'].includes(task.target)) return '必须先明确目标端未处理。'
    if (action === 'check' && task.checked) return '恢复条件已检查。'
    if (action === 'resume' && !task.checked) return '请先检查原版本、数据期限和目标处理结果。'
  }
  if (['written', 'not-written', 'uncertain'].includes(action) && !['结果未知', '持续核查'].includes(task.status)) return '已登记明确结果，不能覆盖结论。'
  if (task.resultConflict && ['written', 'not-written'].includes(action)) return '核查依据与本次请求版本不匹配，不能登记明确结果。'
  if (action === 'request-version' && task.handoff) return '新版本交接已登记。'
  if (action === 'link-request' && !task.handoff) return '请先登记外部处理人与期限。'
  if (action === 'link-request' && task.linkedRequestId) return '新请求已经关联。'
  if (action === 'hold-version' && task.handoff) return '已交接源系统，原请求继续保持拦截。'
  if (action === 'stop' && task.stopped) return '后续发送已停止。'
  if (['clean', 'clean-failed'].includes(action)) {
    if (!task.stopped) return '必须先停止后续发送，确认没有正在发送的任务。'
    if (!task.cleanupScopeConfirmed) return '请先确认清理范围。'
    if (task.dataStatus === '已清理') return '暂存数据已经清理。'
    if (action === 'clean-failed' && task.cleanupFailed) return '已有清理失败记录，请处理后重试清理。'
  }
  if (action === 'handoff' && task.dataStatus !== '已清理') return '完成到期处置后再登记交接；目标结果仍需核查。'
  if (action === 'handoff' && task.handoff) return '交接已登记，等待源系统和目标系统核查。'
  return ''
}

/** Creates a new state only after checking the action and evidence. Repeated stale submissions fail. */
export function transitionRecovery(task: RecoveryTask, action: RecoveryAction, role: string, evidence: string, note: string, revision: number, time: string, details: RecoveryDetails = {}): RecoveryTask {
  if (revision !== task.revision) throw new Error('记录已变化，请重新查看后提交。')
  const blocked = recoveryBlock(task, action, role)
  if (blocked) throw new Error(blocked)
  if (action !== 'claim' && (!evidence.trim() || !note.trim())) throw new Error('请填写依据编号和处理说明。')
  if (action === 'uncertain' && !details.nextCheckAt?.trim()) throw new Error('结果仍未知时必须安排下次核查时间。')
  if (['request-version', 'handoff'].includes(action) && (!details.externalOwner?.trim() || !details.externalDueAt?.trim())) throw new Error('请登记外部处理人和完成期限。')
  if (action === 'confirm-cleanup' && !details.cleanupScope?.trim()) throw new Error('请填写并确认清理范围。')
  if (action === 'link-request' && !details.linkedRequestId?.trim()) throw new Error('请填写需要关联的新请求编号。')
  const savedEvidence = action === 'claim' ? evidence.trim() || 'SYSTEM-CLAIM' : evidence.trim()
  const savedNote = action === 'claim' ? note.trim() || '当前运维领取处理事项' : note.trim()
  const next = { ...task, revision: task.revision + 1, evidence: savedEvidence, history: [...task.history] }
  switch (action) {
    case 'claim': next.assignee = details.assignee?.trim() || '当前运维'; break
    case 'check': next.checked = true; next.status = '待恢复'; break
    case 'resume': next.status = '待发送'; break
    case 'written': next.target = '已写入'; next.status = '已核实成功'; break
    case 'not-written': next.target = '明确未写入'; next.status = '明确未写入'; break
    case 'uncertain': next.status = '持续核查'; next.nextCheckAt = details.nextCheckAt?.trim() || ''; break
    case 'hold-version': next.status = '版本拦截'; break
    case 'request-version': next.handoff = true; next.status = '等待源系统新版本'; next.externalOwner = details.externalOwner?.trim() || ''; next.externalDueAt = details.externalDueAt?.trim() || ''; break
    case 'link-request': next.linkedRequestId = details.linkedRequestId?.trim() || ''; next.status = '已关联新请求'; break
    case 'stop': next.stopped = true; next.status = '发送已停止'; break
    case 'confirm-cleanup': next.cleanupScopeConfirmed = true; next.cleanupScope = details.cleanupScope?.trim() || ''; break
    case 'clean-failed': next.cleanupFailed = true; next.dataStatus = '清理失败'; break
    case 'clean': next.cleanupFailed = false; next.dataStatus = '已清理'; break
    case 'handoff': next.handoff = true; next.status = '待核查后重新提交'; next.externalOwner = details.externalOwner?.trim() || ''; next.externalDueAt = details.externalDueAt?.trim() || ''; break
  }
  next.history.unshift({ time, action: recoveryActions[action], evidence: savedEvidence, note: savedNote })
  return next
}
