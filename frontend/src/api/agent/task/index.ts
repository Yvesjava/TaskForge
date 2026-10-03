import axios from 'axios'
import { config } from '@/config/axios/config'
import { getAccessToken, getTenantId } from '@/utils/auth'

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

/** 任务控制面 API */
export const AgentTaskApi = {
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
