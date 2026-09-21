/** 判断执行确认文本是否与当前维护方案编号完全一致。 */
export function databaseContractExecutionConfirmationMatches(value: string, planNo: string) {
  return value === planNo
}
