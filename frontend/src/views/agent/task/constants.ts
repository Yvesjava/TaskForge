/** 任务状态下拉选项，值与后端 agent_task.status 列约定一致 */
export const AGENT_TASK_STATUS_OPTIONS = [
  { value: 'PENDING', label: '待执行', tagType: 'info' },
  { value: 'PAUSED', label: '已暂停', tagType: 'warning' },
  { value: 'RUNNING', label: '执行中', tagType: 'primary' },
  { value: 'WAITING_ACCEPTANCE', label: '待验收', tagType: 'warning' },
  { value: 'ACCEPTED', label: '已验收', tagType: 'success' },
  { value: 'COMPLETED', label: '已完成', tagType: 'success' },
  { value: 'REJECTED', label: '已打回', tagType: 'danger' },
  { value: 'CANCELED', label: '已取消', tagType: 'info' },
  { value: 'FAILED', label: '执行失败', tagType: 'danger' },
  { value: 'RESETTING', label: '重置中', tagType: 'warning' },
  { value: 'MERGE_CONFLICT_PENDING_MANUAL', label: '合并冲突待处理', tagType: 'danger' }
] as const

/** 任务状态值转展示文案 */
export const agentTaskStatusLabel = (value?: string) => {
  return AGENT_TASK_STATUS_OPTIONS.find((item) => item.value === value)?.label || value || ''
}

/** 任务状态值转标签颜色类型 */
export const agentTaskStatusTagType = (value?: string) => {
  return AGENT_TASK_STATUS_OPTIONS.find((item) => item.value === value)?.tagType || 'info'
}

/** 耗时毫秒转可读文案 */
export const formatCostMs = (ms?: number) => {
  if (ms === null || ms === undefined) {
    return ''
  }
  if (ms < 1000) {
    return `${ms}ms`
  }
  const seconds = ms / 1000
  if (seconds < 60) {
    return `${seconds.toFixed(1)}s`
  }
  const minutes = Math.floor(seconds / 60)
  const remainSeconds = Math.round(seconds % 60)
  return `${minutes}m${remainSeconds}s`
}
