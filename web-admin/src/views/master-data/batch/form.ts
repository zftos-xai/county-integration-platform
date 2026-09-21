import type {
  MasterDataCategory,
  MasterDataEnvironment,
  StartMasterDataBatchInput,
} from '@/api/master-data/batch'

/** 同步批次创建表单。 */
export type MasterDataBatchForm = {
  organizationCode: string
  environment: MasterDataEnvironment
  category: MasterDataCategory
  rangeStart: string
  rangeEnd: string
}

/** 供页面展示的基础数据类别名称。 */
export const masterDataCategoryLabels: Record<MasterDataCategory, string> = {
  HOSPITAL_DIRECTORY: '医院综合目录',
  MEDICAL_DIRECTORY: '药品、诊疗和耗材目录',
}

/** 创建未填写的同步批次表单。 */
export function emptyMasterDataBatchForm(
  organizationCode = '',
): MasterDataBatchForm {
  return {
    organizationCode,
    environment: 'PRODUCTION',
    category: 'HOSPITAL_DIRECTORY',
    rangeStart: '',
    rangeEnd: '',
  }
}

/** 校验发起同步前必须由用户明确的业务范围。 */
export function validateMasterDataBatchForm(form: MasterDataBatchForm) {
  if (!form.organizationCode) return '请选择同步机构'
  if (form.category === 'MEDICAL_DIRECTORY' && (!form.rangeStart || !form.rangeEnd)) {
    return '请填写 HIS 要求的来源查询开始和结束时间'
  }
  if (form.category === 'MEDICAL_DIRECTORY' && new Date(form.rangeEnd) < new Date(form.rangeStart)) {
    return '来源查询结束时间不能早于开始时间'
  }
  return null
}

/** 生成一次用户操作使用的随机请求标识，不作为业务批次号。 */
export function generateBatchRequestKey() {
  const bytes = crypto.getRandomValues(new Uint8Array(12))
  return Array.from(bytes, (value) => value.toString(16).padStart(2, '0'))
    .join('')
    .toUpperCase()
}

/** 将已校验表单转换为后端批次创建输入。 */
export function toStartMasterDataBatchInput(form: MasterDataBatchForm): StartMasterDataBatchInput {
  return {
    requestKey: generateBatchRequestKey(),
    organizationCode: form.organizationCode,
    environment: form.environment,
    category: form.category,
    rangeStart: form.rangeStart ? new Date(form.rangeStart).toISOString() : null,
    rangeEnd: form.rangeEnd ? new Date(form.rangeEnd).toISOString() : null,
  }
}
