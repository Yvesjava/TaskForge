import request from '@/config/axios'
import { generateUUID } from '@/utils'

/** AI 研发任务信息 */
export interface AgentTask {
  id?: number // 任务主键 ID
  taskNo?: string // 任务唯一编号
  title?: string // 任务简述
  status?: string // 任务状态
  priority?: number // 执行优先级
  docVersion?: number // 文档版本号
  dependsOnTaskId?: number // 前置任务 ID
  workspacePath?: string // 工作区绝对路径
  targetBranch?: string // 特性分支
  timeoutMinutes?: number // 超时时长（分钟）
  cancelReason?: string // 取消原因
  retryTimes?: number // 重试次数
  costMs?: number // 总耗时（毫秒）
  workerId?: string // 当前租约持有者
  leaseUntil?: Date // 租约到期时间
  executionGeneration?: number // 执行代次
  heartbeatTime?: Date // 最近心跳时间
  startedTime?: Date // 开始执行时间
  finishedTime?: Date // 完成时间
  createTime?: Date // 创建时间
  updateTime?: Date // 更新时间
}

/** 任务生命周期操作响应 */
export interface AgentTaskOperationResult {
  taskId?: number
  taskNo?: string
  status?: string
  docVersion?: number
  executionGeneration?: number
  operationId?: string
}

/** 任务重置响应 */
export interface AgentTaskResetResult {
  taskId?: number
  taskNo?: string
  status?: string
}

/** 生成写操作幂等键（长度 16-128，仅字母数字和 . _ -） */
const idempotencyHeaders = () => ({ 'X-Idempotency-Key': generateUUID() })

// AI 研发任务 API
export const AgentTaskApi = {
  // 查询任务分页
  getTaskPage: async (params: any) => {
    return await request.get({ url: '/agent/task/page', params })
  },

  // 查询任务详情
  getTask: async (id: number) => {
    return await request.get({ url: '/agent/task/get?id=' + id })
  },

  // 暂停任务
  pause: async (id: number) => {
    return await request.post({ url: `/agent/task/${id}/pause`, headers: idempotencyHeaders() })
  },

  // 恢复任务
  resume: async (id: number) => {
    return await request.post({ url: `/agent/task/${id}/resume`, headers: idempotencyHeaders() })
  },

  // 取消任务
  cancel: async (id: number, cancelReason?: string) => {
    return await request.post({
      url: `/agent/task/${id}/cancel`,
      data: { cancelReason },
      headers: idempotencyHeaders()
    })
  },

  // 重置任务
  reset: async (id: number, keepPaused?: boolean) => {
    return await request.post({
      url: `/agent/task/${id}/reset`,
      data: { keepPaused: !!keepPaused },
      headers: idempotencyHeaders()
    })
  },

  // 重新入队任务
  reEnqueue: async (id: number) => {
    return await request.post({
      url: `/agent/task/${id}/re-enqueue`,
      headers: idempotencyHeaders()
    })
  },

  // 软删除任务
  deleteTask: async (id: number) => {
    return await request.delete({ url: `/agent/task/${id}`, headers: idempotencyHeaders() })
  }
}
