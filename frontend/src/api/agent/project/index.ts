import request from '@/config/axios'

/** 代码项目资产信息 */
export interface AgentProject {
  id?: number // 编号
  projectCode: string // 项目代号
  name: string // 项目名称
  gitUrl: string // Git 仓库地址（SSH/HTTP）
  defaultBranch: string // 默认主干分支
  buildTool: string // 构建工具：MAVEN、PNPM、GRADLE、GO
  testCommand?: string // 标准测试命令
  credentialRef?: string // Secret Manager 中的 Git 凭证引用
  status?: number // 状态：0-开启，1-关闭
  createTime?: Date // 创建时间
}

// 代码项目资产 API
export const AgentProjectApi = {
  // 查询项目分页
  getProjectPage: async (params: any) => {
    return await request.get({ url: '/agent/project/page', params })
  },

  // 查询项目详情
  getProject: async (id: number) => {
    return await request.get({ url: '/agent/project/get?id=' + id })
  },

  // 新增项目
  createProject: async (data: AgentProject) => {
    return await request.post({ url: '/agent/project/create', data })
  },

  // 修改项目
  updateProject: async (data: AgentProject) => {
    return await request.put({ url: '/agent/project/update', data })
  },

  // 启停项目
  updateProjectStatus: async (id: number, status: number) => {
    return await request.put({ url: '/agent/project/update-status', data: { id, status } })
  },

  // 删除项目
  deleteProject: async (id: number) => {
    return await request.delete({ url: '/agent/project/delete?id=' + id })
  }
}
