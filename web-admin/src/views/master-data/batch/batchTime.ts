import type { MasterDataBatchSummary } from '@/api/master-data/batch'

/** 将服务端批次时间显示到秒；缺失或无效时间不会伪装成有效时间。 */
export function formatBatchTime(value: string | null) {
  if (!value) return '—'
  // 数据库时间按UTC返回但部分旧接口未附带时区后缀；统一补Z，避免详情面板比批次概要少8小时。
  const normalized = value.endsWith('Z') || /[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return '时间不可用'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

/** 用批次开始、结束时间计算总耗时；运行中以当前时刻计算已运行时间。 */
export function formatBatchDuration(batch: MasterDataBatchSummary, now: number) {
  if (!batch.startedAt) return '尚未开始'
  const startedAt = new Date(batch.startedAt).getTime()
  const endedAt = batch.finishedAt ? new Date(batch.finishedAt).getTime() : now
  if (!Number.isFinite(startedAt) || !Number.isFinite(endedAt)) return '时间不可用'
  const milliseconds = Math.max(0, endedAt - startedAt)
  if (milliseconds < 1000) return `${milliseconds}毫秒`
  const seconds = Math.floor(milliseconds / 1000)
  if (seconds < 60) return `${seconds}秒`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}分${seconds % 60}秒`
  return `${Math.floor(minutes / 60)}小时${minutes % 60}分${seconds % 60}秒`
}
