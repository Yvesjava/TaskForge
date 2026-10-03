<template>
  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="92px"
    >
      <el-form-item label="任务编号" prop="taskNo">
        <el-input
          v-model="queryParams.taskNo"
          placeholder="请输入任务编号"
          clearable
          @keyup.enter="handleQuery"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="任务标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入任务标题"
          clearable
          @keyup.enter="handleQuery"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="任务状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-180px">
          <el-option
            v-for="option in AGENT_TASK_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目标分支" prop="targetBranch">
        <el-input
          v-model="queryParams.targetBranch"
          placeholder="请输入目标分支"
          clearable
          @keyup.enter="handleQuery"
          class="!w-200px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table
      row-key="id"
      v-loading="loading"
      :data="list"
      :stripe="true"
      :show-overflow-tooltip="true"
    >
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="任务编号" align="center" prop="taskNo" min-width="150" />
      <el-table-column label="任务标题" align="center" prop="title" min-width="180" />
      <el-table-column label="状态" align="center" prop="status" width="150">
        <template #default="scope">
          <el-tag :type="agentTaskStatusTagType(scope.row.status)">
            {{ agentTaskStatusLabel(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="priority" width="80" />
      <el-table-column label="目标分支" align="center" prop="targetBranch" min-width="150" />
      <el-table-column label="超时(分)" align="center" prop="timeoutMinutes" width="90" />
      <el-table-column label="Worker" align="center" prop="workerId" min-width="120" />
      <el-table-column label="重试" align="center" prop="retryTimes" width="70" />
      <el-table-column label="耗时" align="center" prop="costMs" width="100">
        <template #default="scope">
          {{ formatCostMs(scope.row.costMs) }}
        </template>
      </el-table-column>
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        :formatter="dateFormatter"
        width="170px"
      />
      <el-table-column label="操作" align="center" width="260px" fixed="right">
        <template #default="scope">
          <el-button
            v-if="scope.row.status === 'PENDING'"
            link
            type="primary"
            @click="handlePause(scope.row)"
            v-hasPermi="['agent:task:pause']"
          >
            暂停
          </el-button>
          <el-button
            v-if="scope.row.status === 'PAUSED'"
            link
            type="primary"
            @click="handleResume(scope.row)"
            v-hasPermi="['agent:task:resume']"
          >
            恢复
          </el-button>
          <el-button
            v-if="scope.row.status === 'PAUSED'"
            link
            type="primary"
            @click="handleReset(scope.row)"
            v-hasPermi="['agent:task:reset']"
          >
            重置
          </el-button>
          <el-button
            v-if="['PENDING', 'PAUSED'].includes(scope.row.status)"
            link
            type="warning"
            @click="handleCancel(scope.row)"
            v-hasPermi="['agent:task:cancel']"
          >
            取消
          </el-button>
          <el-button
            v-if="['CANCELED', 'REJECTED', 'FAILED'].includes(scope.row.status)"
            link
            type="primary"
            @click="handleReEnqueue(scope.row)"
            v-hasPermi="['agent:task:re-enqueue']"
          >
            重投
          </el-button>
          <el-button
            v-if="scope.row.status === 'WAITING_ACCEPTANCE'"
            link
            type="success"
            @click="handleAccept(scope.row)"
            v-hasPermi="['agent:task:accept']"
          >
            确认验收
          </el-button>
          <el-button
            v-if="scope.row.status === 'WAITING_ACCEPTANCE'"
            link
            type="warning"
            @click="handleReject(scope.row)"
            v-hasPermi="['agent:task:reject']"
          >
            打回
          </el-button>
          <el-button
            v-if="scope.row.status === 'ACCEPTED'"
            link
            type="danger"
            @click="handleMarkMergeConflict(scope.row)"
            v-hasPermi="['agent:task:merge-conflict']"
          >
            合并冲突
          </el-button>
          <el-button
            v-if="scope.row.status === 'CANCELED'"
            link
            type="danger"
            @click="handleDelete(scope.row)"
            v-hasPermi="['agent:task:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import { dateFormatter } from '@/utils/formatTime'
import { AgentTask, AgentTaskApi } from '@/api/agent/task'
import {
  AGENT_TASK_STATUS_OPTIONS,
  agentTaskStatusLabel,
  agentTaskStatusTagType,
  formatCostMs
} from './constants'

/** AI 研发任务列表 */
defineOptions({ name: 'AgentTask' })

const message = useMessage() // 消息弹窗

const loading = ref(true) // 列表的加载中
const list = ref<AgentTask[]>([]) // 列表的数据
const total = ref(0) // 列表的总条数
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  taskNo: undefined as string | undefined,
  title: undefined as string | undefined,
  status: undefined as string | undefined,
  targetBranch: undefined as string | undefined
})
const queryFormRef = ref() // 搜索的表单

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await AgentTaskApi.getTaskPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

/** 暂停任务 */
const handlePause = async (row: AgentTask) => {
  try {
    await message.confirm(`确认暂停任务「${row.taskNo}」吗？`)
    await AgentTaskApi.pause(row.id!)
    message.success('暂停成功')
    await getList()
  } catch {}
}

/** 恢复任务 */
const handleResume = async (row: AgentTask) => {
  try {
    await message.confirm(`确认恢复任务「${row.taskNo}」到待调度队列吗？`)
    await AgentTaskApi.resume(row.id!)
    message.success('恢复成功')
    await getList()
  } catch {}
}

/** 重置任务 */
const handleReset = async (row: AgentTask) => {
  try {
    await message.confirm(`确认重置任务「${row.taskNo}」吗？重置将清理工作区与特性分支并重新排队。`)
    await AgentTaskApi.reset(row.id!, false)
    message.success('重置成功')
    await getList()
  } catch {}
}

/** 取消任务 */
const handleCancel = async (row: AgentTask) => {
  try {
    const { value } = await message.prompt('请输入取消原因（可为空）', '取消任务')
    await AgentTaskApi.cancel(row.id!, value || undefined)
    message.success('取消成功')
    await getList()
  } catch {}
}

/** 重新入队任务 */
const handleReEnqueue = async (row: AgentTask) => {
  try {
    await message.confirm(`确认重新入队任务「${row.taskNo}」吗？`)
    await AgentTaskApi.reEnqueue(row.id!)
    message.success('重投成功')
    await getList()
  } catch {}
}

/** 验收通过 */
const handleAccept = async (row: AgentTask) => {
  try {
    await message.confirm(`确认验收任务「${row.taskNo}」并进入合并流程吗？`)
    await AgentTaskApi.accept(row.id!)
    message.success('验收成功')
    await getList()
  } catch {}
}

/** 打回任务 */
const handleReject = async (row: AgentTask) => {
  try {
    const { value } = await message.prompt('请输入打回反馈', '打回任务')
    const feedback = (value || '').trim()
    if (!feedback) {
      message.error('打回反馈不能为空')
      return
    }
    if (feedback.length > 2000) {
      message.error('打回反馈长度不能超过 2000 个字符')
      return
    }
    await AgentTaskApi.reject(row.id!, feedback)
    message.success('打回成功')
    await getList()
  } catch {}
}

/** 合并冲突转人工处理 */
const handleMarkMergeConflict = async (row: AgentTask) => {
  try {
    await message.confirm(`确认将任务「${row.taskNo}」标记为合并冲突待处理吗？`)
    await AgentTaskApi.markMergeConflict(row.id!)
    message.success('已标记合并冲突')
    await getList()
  } catch {}
}

/** 删除任务 */
const handleDelete = async (row: AgentTask) => {
  try {
    await message.delConfirm('软删除任务会保留操作审计记录，是否确认删除？')
    await AgentTaskApi.deleteTask(row.id!)
    message.success('删除成功')
    await getList()
  } catch {}
}

/** 初始化 **/
onMounted(() => {
  getList()
})
</script>
