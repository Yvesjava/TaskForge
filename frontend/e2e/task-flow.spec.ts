import { expect, test, type Page } from '@playwright/test'

interface TaskState {
  id: number
  taskNo: string
  title: string
  status: string
  priority: number
  targetBranch: string
  timeoutMinutes: number
  docVersion: number
  createTime: string
}

interface AppState {
  tasks: TaskState[]
  nextId: number
}

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': '*',
  'Access-Control-Allow-Headers': '*'
}

function ok(data: unknown) {
  return { code: 200, data, msg: '' }
}

function taskFromDocument(state: AppState, document: string): TaskState {
  const title = document.match(/^title:\s*"?([^"\n]+)"?\s*$/m)?.[1] || 'E2E 任务'
  const taskNo =
    document.match(/^taskId:\s*"?([^"\n]+)"?\s*$/m)?.[1] || `TASK-E2E-${state.nextId}`
  const targetBranch =
    document.match(/^targetBranch:\s*"?([^"\n]+)"?\s*$/m)?.[1] || 'feat/e2e'

  return {
    id: state.nextId++,
    taskNo,
    title,
    status: 'PENDING',
    priority: 100,
    targetBranch,
    timeoutMinutes: 30,
    docVersion: 1,
    createTime: new Date().toISOString()
  }
}

async function installApiMock(page: Page, state: AppState) {
  await page.addInitScript(() => {
    // web-storage-cache 将值序列化为 { c, e, v }；v 为值的 JSON 序列化结果。
    localStorage.setItem(
      'ACCESS_TOKEN',
      JSON.stringify({
        c: Date.now(),
        e: 253402300799000,
        v: JSON.stringify('e2e-access-token')
      })
    )
  })

  await page.route('**/admin-api/**', async (route) => {
    const request = route.request()
    const method = request.method()
    const pathname = new URL(request.url()).pathname

    if (method === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: CORS_HEADERS })
      return
    }

    const fulfill = (data: unknown) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: CORS_HEADERS,
        body: JSON.stringify(ok(data))
      })

    if (pathname.endsWith('/system/auth/get-permission-info')) {
      return fulfill({
        user: { id: 1, nickname: 'admin', avatar: '', deptId: 1 },
        roles: ['super_admin'],
        permissions: ['*:*:*'],
        menus: [
          {
            id: 1000,
            parentId: 0,
            name: '研发智能体',
            path: '/agent',
            component: '',
            componentName: '',
            icon: 'ep:cpu',
            visible: true,
            keepAlive: true,
            alwaysShow: true,
            children: [
              {
                id: 1001,
                parentId: 1000,
                name: '任务列表',
                path: 'task',
                component: 'agent/task/index',
                componentName: 'AgentTask',
                icon: 'ep:list',
                visible: true,
                keepAlive: true,
                alwaysShow: true,
                children: []
              },
              {
                id: 1002,
                parentId: 1000,
                name: '项目管理',
                path: 'project',
                component: 'agent/project/index',
                componentName: 'AgentProject',
                icon: 'ep:folder',
                visible: true,
                keepAlive: true,
                alwaysShow: true,
                children: []
              }
            ]
          }
        ]
      })
    }

    if (pathname.endsWith('/system/dict-data/simple-list')) {
      return fulfill([])
    }

    if (pathname.endsWith('/agent/task/page')) {
      return fulfill({ list: state.tasks, total: state.tasks.length })
    }

    if (pathname.endsWith('/agent/task/submit') && method === 'POST') {
      const body = request.postDataJSON() as { document: string }
      const task = taskFromDocument(state, body.document)
      state.tasks.unshift(task)
      return fulfill({
        taskId: task.id,
        taskNo: task.taskNo,
        status: task.status,
        docVersion: task.docVersion,
        executionGeneration: 0,
        operationId: 'op-e2e'
      })
    }

    const actionMatch = pathname.match(
      /\/agent\/task\/(\d+)\/(pause|resume|cancel|re-enqueue|accept|diff)/
    )
    if (actionMatch) {
      const id = Number(actionMatch[1])
      const action = actionMatch[2]
      const task = state.tasks.find((item) => item.id === id)
      if (action === 'diff') {
        return fulfill({
          taskId: id,
          taskNo: task?.taskNo,
          title: task?.title,
          status: task?.status,
          targetBranch: task?.targetBranch,
          branches: [],
          diffStat: '{}',
          changedFiles: ['README.md'],
          executionLog: 'build ok',
          logTruncated: false,
          testReport: { allPassed: true, testsExecuted: ['pnpm test'], modifiedFiles: ['README.md'] }
        })
      }
      if (task) {
        const nextStatus = {
          pause: 'PAUSED',
          resume: 'PENDING',
          cancel: 'CANCELED',
          're-enqueue': 'PENDING',
          accept: 'ACCEPTED'
        }[action]
        if (nextStatus) {
          task.status = nextStatus
        }
      }
      return fulfill({
        taskId: id,
        taskNo: task?.taskNo,
        status: task?.status,
        docVersion: task?.docVersion ?? 1,
        executionGeneration: 0,
        operationId: 'op-e2e'
      })
    }

    // 兜底：避免未覆盖的系统接口（消息轮询等）导致请求悬挂。
    return fulfill({})
  })
}

async function actOnRow(page: Page, title: string, action: string, confirmText?: string) {
  const row = page.locator('tr', { hasText: title })
  await row.getByRole('button', { name: action, exact: true }).click()
  if (confirmText) {
    await page.getByRole('button', { name: confirmText, exact: true }).click()
  }
}

test('任务主流程可从页面完成（提交 → 生命周期 → 验收）', async ({ page }) => {
  const state: AppState = { tasks: [], nextId: 1 }
  await installApiMock(page, state)

  await page.goto('/agent/task')
  await expect(page.getByRole('button', { name: '提交新任务' })).toBeVisible()

  // 1. 提交新任务
  await page.getByRole('button', { name: '提交新任务' }).click()
  await expect(page.getByRole('button', { name: /保\s*存/ })).toBeVisible()
  const drawer = page.getByRole('dialog', { name: '提交新任务' })
  await drawer.getByPlaceholder('如 TASK-20261001-001').fill('TASK-E2E-001')
  await drawer.getByPlaceholder('请输入任务标题').fill('E2E 主流程任务')
  await drawer.getByPlaceholder('如 feat/task-001').fill('feat/e2e-001')
  await drawer.getByPlaceholder('git@host:path 或 https://...').fill('git@host:team/e2e.git')
  await drawer.getByPlaceholder('如 main').fill('main')
  await drawer.getByPlaceholder('请描述需求目标与上下文').fill('goal')
  await drawer.getByPlaceholder('请使用可勾选清单描述执行计划').fill('- [ ] plan')
  await drawer.getByPlaceholder('请填写至少一个可执行命令或明确检查项').fill('pnpm test')
  await drawer.getByPlaceholder('请使用可勾选清单描述验收标准').fill('- [ ] all green')
  await page.getByRole('button', { name: /保\s*存/ }).click()

  await expect(page.locator('tr', { hasText: 'E2E 主流程任务' })).toContainText('待执行')

  // 2. 暂停
  await actOnRow(page, 'E2E 主流程任务', '暂停', '确定')
  await expect(page.locator('tr', { hasText: 'E2E 主流程任务' })).toContainText('已暂停')

  // 3. 恢复
  await actOnRow(page, 'E2E 主流程任务', '恢复', '确定')
  await expect(page.locator('tr', { hasText: 'E2E 主流程任务' })).toContainText('待执行')

  // 4. 取消
  await actOnRow(page, 'E2E 主流程任务', '取消', '确定')
  await expect(page.locator('tr', { hasText: 'E2E 主流程任务' })).toContainText('已取消')

  // 5. 重新入队
  await actOnRow(page, 'E2E 主流程任务', '重投', '确定')
  await expect(page.locator('tr', { hasText: 'E2E 主流程任务' })).toContainText('待执行')

  // 6. 模拟 Worker 完成执行，任务进入待验收
  state.tasks[0].status = 'WAITING_ACCEPTANCE'
  await page.getByRole('button', { name: '搜索' }).click()
  await expect(page.locator('tr', { hasText: 'E2E 主流程任务' })).toContainText('待验收')

  // 7. 查看结果
  await actOnRow(page, 'E2E 主流程任务', '查看结果')
  await expect(page.getByText('Diff / 日志 / 报告 / 分支')).toBeVisible()
  await page.getByRole('tab', { name: '测试报告' }).click()
  await expect(page.getByText('已通过')).toBeVisible()
  await page.getByRole('button', { name: '关 闭', exact: true }).click()

  // 8. 确认验收
  await actOnRow(page, 'E2E 主流程任务', '确认验收', '确定')
  await expect(page.locator('tr', { hasText: 'E2E 主流程任务' })).toContainText('已验收')
})
