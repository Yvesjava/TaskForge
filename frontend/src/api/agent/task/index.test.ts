import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock, axiosMock } = vi.hoisted(() => ({
  requestMock: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
    put: vi.fn()
  },
  axiosMock: vi.fn()
}))

vi.mock('@/config/axios', () => ({ default: requestMock }))
vi.mock('@/config/axios/config', () => ({ config: { base_url: 'http://localhost:48080/admin-api' } }))
vi.mock('@/utils/auth', () => ({
  getAccessToken: () => 'access-token',
  getTenantId: () => 1
}))
vi.mock('@/utils', () => ({ generateUUID: () => 'idempotency-key-1' }))

vi.mock('axios', () => ({ default: axiosMock }))

import {
  AGENT_TASK_DOC_VERSION_CONFLICT_CODE,
  AgentTaskApi,
  AgentTaskApiError
} from './index'

describe('AgentTaskApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('submits a task document with idempotency header', async () => {
    requestMock.post.mockResolvedValue({ taskId: 1, taskNo: 'TASK-1', status: 'PENDING' })

    await AgentTaskApi.submit('---\ntitle: demo\n---')

    expect(requestMock.post).toHaveBeenCalledWith({
      url: '/agent/task/submit',
      data: { document: '---\ntitle: demo\n---' },
      headers: { 'X-Idempotency-Key': 'idempotency-key-1' }
    })
  })

  it('pauses a task via the expected URL and idempotency header', async () => {
    requestMock.post.mockResolvedValue({ taskId: 7 })
    await AgentTaskApi.pause(7)

    expect(requestMock.post).toHaveBeenCalledWith({
      url: '/agent/task/7/pause',
      headers: { 'X-Idempotency-Key': 'idempotency-key-1' }
    })
  })

  it('queries the task page with params', async () => {
    requestMock.get.mockResolvedValue({ list: [], total: 0 })
    await AgentTaskApi.getTaskPage({ pageNo: 1, pageSize: 10, status: 'PENDING' })

    expect(requestMock.get).toHaveBeenCalledWith({
      url: '/agent/task/page',
      params: { pageNo: 1, pageSize: 10, status: 'PENDING' }
    })
  })

  it('returns the payload on a successful document update', async () => {
    axiosMock.mockResolvedValue({
      data: { code: 0, data: { taskId: 7, docVersion: 2 }, msg: '' }
    })

    const result = await AgentTaskApi.updateTaskDocument(
      7,
      { docVersion: 1, document: 'doc' },
      '1'
    )

    expect(result).toEqual({ taskId: 7, docVersion: 2 })
    expect(axiosMock).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'PATCH',
        url: 'http://localhost:48080/admin-api/agent/task/7/document',
        data: { docVersion: 1, document: 'doc' }
      })
    )
  })

  it('throws a typed error on version conflict', async () => {
    axiosMock.mockResolvedValue({
      data: {
        code: AGENT_TASK_DOC_VERSION_CONFLICT_CODE,
        msg: '任务文档版本冲突，最新版本为 3'
      }
    })

    await expect(
      AgentTaskApi.updateTaskDocument(7, { docVersion: 1, document: 'doc' }, '1')
    ).rejects.toMatchObject({
      name: 'AgentTaskApiError',
      code: AGENT_TASK_DOC_VERSION_CONFLICT_CODE
    } as AgentTaskApiError)
  })
})
