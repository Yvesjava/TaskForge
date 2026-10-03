import axios from 'axios'
import request from '@/config/axios'
import { config } from '@/config/axios/config'
import { getAccessToken, getTenantId } from '@/utils/auth'
import { generateUUID } from '@/utils'

/** 任务文档编辑请求（对应后端 AgentTaskUpdateDocumentReqVO） */
export interface AgentTaskUpdateDocumentReq {
  /** 当前文档版本号（乐观锁） */
  docVersion: number
  /** 完整任务 Markdown 文档（含 YAML Front Matter 与正文小节） */
  document: string
  /** 超时时长（分钟） */
  timeoutMinutes?: number
  /** 执行优先级（数值越小越优先） */
  priority?: number
  /** 可选的前置任务 ID */
  dependsOnTaskId?: number
}

/** 任务文档编辑响应（对应后端 AgentTaskUpdateDocumentRespVO） */
export interface AgentTaskUpdateDocumentResp {
  taskId: number
  taskNo: string
  status: string
  docVersion: number
  executionGeneration: number
  operationId: string
}

/** 任务投递响应（对应后端 AgentTaskSubmitRespVO） */
export interface AgentTaskSubmitResp {
  taskId: number
  taskNo: string
  status: string
  docVersion: number
  executionGeneration: number
  operationId: string
}

/** AI 研发任务信息 */
export interface AgentTask {
  id?: number // 任务主键 ID
  taskNo?: string // 任务唯一编号
  title?: string // 任务简述
  status?: string // 任务状态
  taskDoc?: string // 任务需求文档（含计划与验收标准）
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

/** 任务分支信息（对应后端 Diff 查看接口的 branch 条目） */
export interface AgentTaskBranchInfo {
  projectCode?: string
  projectName?: string
  baseBranch?: string
  featureBranch?: string
  branchUrl?: string
  subDir?: string
  mergeStatus?: string
  commitHash?: string
}

/** 测试报告（对应 Worker 写入的 .ai/workpad_summary.json） */
export interface AgentTaskTestReport {
  allPassed?: boolean
  testsExecuted?: string[]
  modifiedFiles?: string[]
  notes?: string
}

/** Diff/日志/测试报告/分支信息查看响应 */
export interface AgentTaskDiffResp {
  taskId?: number
  taskNo?: string
  title?: string
  status?: string
  targetBranch?: string
  retryTimes?: number
  costMs?: number
  startedTime?: string
  finishedTime?: string
  executionGeneration?: number
  branches?: AgentTaskBranchInfo[]
  diffStat?: string
  changedFiles?: string[]
  executionLog?: string
  logTruncated?: boolean
  originalLogPath?: string
  testReport?: AgentTaskTestReport
}

/** 业务错误：保留后端返回的错误码，便于前端识别版本冲突等场景 */
export class AgentTaskApiError extends Error {
  code?: number

  constructor(message: string, code?: number) {
    super(message)
    this.name = 'AgentTaskApiError'
    this.code = code
  }
}

/** 文档版本冲突错误码，对应后端 ErrorCodeConstants.TASK_DOC_VERSION_CONFLICT */
export const AGENT_TASK_DOC_VERSION_CONFLICT_CODE = 1061003005

/** 生成写操作幂等键（长度 16-128，仅字母数字和 . _ -） */
const idempotencyHeaders = () => ({ 'X-Idempotency-Key': generateUUID() })

// AI 研发任务 API
export const AgentTaskApi = {
  // 查询任务分页
  getTaskPage: async (params: any) => {
    return await request.get({ url: '/agent/task/page', params })
  },

  // 查询任务详情（含完整 taskDoc）
  getTask: async (id: number): Promise<AgentTask> => {
    return await request.get({ url: '/agent/task/get?id=' + id })
  },

  // 投递任务文档
  submit: async (document: string): Promise<AgentTaskSubmitResp> => {
    return await request.post({
      url: '/agent/task/submit',
      data: { document },
      headers: idempotencyHeaders()
    })
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

  // 验收通过
  accept: async (id: number) => {
    return await request.post({ url: `/agent/task/${id}/accept`, headers: idempotencyHeaders() })
  },

  // 打回任务
  reject: async (id: number, feedback: string) => {
    return await request.post({
      url: `/agent/task/${id}/reject`,
      data: { feedback },
      headers: idempotencyHeaders()
    })
  },

  // 合并冲突转人工处理
  markMergeConflict: async (id: number) => {
    return await request.post({
      url: `/agent/task/${id}/merge-conflict`,
      headers: idempotencyHeaders()
    })
  },

  // 软删除任务
  deleteTask: async (id: number) => {
    return await request.delete({ url: `/agent/task/${id}`, headers: idempotencyHeaders() })
  },

  // 查询任务 Diff/日志/测试报告/分支信息
  getTaskDiff: async (id: number): Promise<AgentTaskDiffResp> => {
    return await request.get({ url: `/agent/task/${id}/diff` })
  },

  /**
   * 编辑暂停任务的文档与执行参数。
   *
   * 该接口直接使用 axios 发送 PATCH，以便拿到后端返回的完整业务错误码，
   * 由调用方自行识别并展示清晰的版本冲突提示。
   *
   * @param id 任务 ID
   * @param data 编辑请求（携带当前 docVersion）
   * @param ifMatch 乐观锁请求头 If-Match（需与 data.docVersion 一致）
   */
  async updateTaskDocument(
    id: number,
    data: AgentTaskUpdateDocumentReq,
    ifMatch: string
  ): Promise<AgentTaskUpdateDocumentResp> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'If-Match': ifMatch
    }
    const accessToken = getAccessToken()
    if (accessToken) {
      headers.Authorization = `Bearer ${accessToken}`
    }
    const tenantId = getTenantId()
    if (import.meta.env.VITE_APP_TENANT_ENABLE === 'true' && tenantId) {
      headers['tenant-id'] = String(tenantId)
    }

    const response = await axios({
      method: 'PATCH',
      url: `${config.base_url}/agent/task/${id}/document`,
      data,
      headers,
      validateStatus: () => true
    })

    const body = response.data
    if (body && (body.code === 0 || body.code === 200)) {
      return body.data as AgentTaskUpdateDocumentResp
    }
    throw new AgentTaskApiError(body?.msg || '任务文档更新失败', body?.code)
  }
}
