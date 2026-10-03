<template>
  <el-dialog
    v-model="visible"
    title="Diff / 日志 / 报告 / 分支"
    width="920px"
    top="6vh"
    destroy-on-close
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <div v-loading="loading" class="task-diff-viewer">
      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
        show-icon
        class="mb-16px"
      >
        <template #default>
          <el-button link type="primary" @click="fetchData">重新加载</el-button>
        </template>
      </el-alert>

      <template v-else-if="data">
        <el-descriptions :column="2" border size="small" class="mb-16px">
          <el-descriptions-item label="任务编号">{{ data.taskNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="agentTaskStatusTagType(data.status)">
              {{ agentTaskStatusLabel(data.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{
            data.title || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="目标分支">{{
            data.targetBranch || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">{{ data.retryTimes ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="总耗时">{{
            formatCostMs(data.costMs) || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="执行代次">
            {{ data.executionGeneration ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">
            {{ formatNullableDate(data.startedTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="完成时间">
            {{ formatNullableDate(data.finishedTime) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="分支信息" name="branches">
            <el-table v-if="branches.length" :data="branches" border size="small">
              <el-table-column label="项目代号" prop="projectCode" min-width="130" />
              <el-table-column label="项目名称" prop="projectName" min-width="130" />
              <el-table-column label="子目录" prop="subDir" min-width="100" />
              <el-table-column label="基线分支" prop="baseBranch" min-width="120" />
              <el-table-column label="特性分支" prop="featureBranch" min-width="150" />
              <el-table-column label="合并状态" prop="mergeStatus" width="120" align="center">
                <template #default="scope">
                  <el-tag
                    v-if="scope.row.mergeStatus"
                    :type="mergeStatusTagType(scope.row.mergeStatus)"
                  >
                    {{ mergeStatusLabel(scope.row.mergeStatus) }}
                  </el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column
                label="提交号"
                prop="commitHash"
                min-width="110"
                show-overflow-tooltip
              />
              <el-table-column label="分支地址" min-width="130">
                <template #default="scope">
                  <el-link
                    v-if="scope.row.branchUrl"
                    :href="scope.row.branchUrl"
                    target="_blank"
                    type="primary"
                  >
                    打开
                  </el-link>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无分支信息" :image-size="90" />
          </el-tab-pane>

          <el-tab-pane label="Diff" name="diff">
            <template v-if="hasDiff">
              <div v-if="diffStatText" class="mb-16px">
                <div class="section-title">Diff 统计</div>
                <pre class="code-block">{{ diffStatText }}</pre>
              </div>
              <div v-if="changedFiles.length">
                <div class="section-title">变更文件（{{ changedFiles.length }}）</div>
                <ul class="file-list">
                  <li v-for="file in changedFiles" :key="file">{{ file }}</li>
                </ul>
              </div>
            </template>
            <el-empty v-else description="暂无 Diff 信息" :image-size="90" />
          </el-tab-pane>

          <el-tab-pane label="测试报告" name="report">
            <template v-if="testReport">
              <div class="report-header">
                <el-tag :type="reportConclusion.tagType" size="large">
                  {{ reportConclusion.label }}
                </el-tag>
              </div>
              <div v-if="testReport.testsExecuted?.length" class="report-section">
                <div class="section-title">执行的测试命令</div>
                <ul class="plain-list">
                  <li v-for="cmd in testReport.testsExecuted" :key="cmd">{{ cmd }}</li>
                </ul>
              </div>
              <div v-if="testReport.modifiedFiles?.length" class="report-section">
                <div class="section-title">修改文件</div>
                <ul class="plain-list">
                  <li v-for="file in testReport.modifiedFiles" :key="file">{{ file }}</li>
                </ul>
              </div>
              <div v-if="testReport.notes" class="report-section">
                <div class="section-title">备注</div>
                <p class="report-notes">{{ testReport.notes }}</p>
              </div>
              <el-empty
                v-if="
                  !testReport.testsExecuted?.length &&
                  !testReport.modifiedFiles?.length &&
                  !testReport.notes
                "
                description="测试报告无明细内容"
                :image-size="90"
              />
            </template>
            <el-empty v-else description="暂无测试报告" :image-size="90" />
          </el-tab-pane>

          <el-tab-pane label="日志" name="log">
            <template v-if="log">
              <div class="log-toolbar">
                <span>共 {{ logLines }} 行</span>
                <el-button link type="primary" @click="copyLog">
                  <Icon icon="ep:copy-document" class="mr-4px" /> 复制日志
                </el-button>
              </div>
              <el-alert
                v-if="data.logTruncated"
                title="日志过长已截断，仅保留头部与尾部"
                type="warning"
                :closable="false"
                show-icon
                class="mb-8px"
              >
                <template #default v-if="data.originalLogPath">
                  原始日志路径：{{ data.originalLogPath }}
                </template>
              </el-alert>
              <pre class="code-block log-block">{{ log }}</pre>
            </template>
            <el-empty v-else description="暂无执行日志" :image-size="90" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <template #footer>
      <el-button @click="visible = false">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { formatNullableDate } from '@/utils/formatTime'
import { AgentTaskApi, AgentTaskDiffResp } from '@/api/agent/task'
import { agentTaskStatusLabel, agentTaskStatusTagType, formatCostMs } from '../constants'

/** Diff/日志/测试报告/分支信息查看弹窗 */
defineOptions({ name: 'TaskDiffViewerDialog' })

const message = useMessage()

const visible = ref(false)
const loading = ref(false)
const loadError = ref('')
const activeTab = ref('branches')
const taskId = ref<number>()
const data = ref<AgentTaskDiffResp>()

const branches = computed(() => data.value?.branches || [])
const changedFiles = computed(() => data.value?.changedFiles || [])
const testReport = computed(() => data.value?.testReport)
const log = computed(() => data.value?.executionLog || '')
const logLines = computed(() => (log.value ? log.value.split('\n').length : 0))

const hasDiff = computed(() => Boolean(diffStatText.value || changedFiles.value.length))

const reportConclusion = computed<{ label: string; tagType: 'success' | 'danger' | 'info' }>(() => {
  const allPassed = testReport.value?.allPassed
  if (allPassed === true) {
    return { label: '已通过', tagType: 'success' }
  }
  if (allPassed === false) {
    return { label: '未通过', tagType: 'danger' }
  }
  return { label: '未记录', tagType: 'info' }
})

const diffStatText = computed(() => {
  const raw = data.value?.diffStat
  if (!raw) {
    return ''
  }
  try {
    const parsed = JSON.parse(raw)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return raw
  }
})

/** 打开弹窗并加载任务结果数据 */
const open = (row: { id?: number }) => {
  const id = row?.id
  if (!id) {
    message.warning('缺少任务 ID，无法查看结果')
    return
  }
  taskId.value = id
  activeTab.value = 'branches'
  visible.value = true
  fetchData()
}

defineExpose({ open })

const fetchData = async () => {
  if (!taskId.value) {
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    data.value = await AgentTaskApi.getTaskDiff(taskId.value)
  } catch (error) {
    loadError.value = (error as Error).message || '结果加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const copyLog = async () => {
  if (!log.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(log.value)
    message.success('日志已复制')
  } catch {
    message.error('复制失败，请手动选择复制')
  }
}

const handleClosed = () => {
  data.value = undefined
  loadError.value = ''
  activeTab.value = 'branches'
}

const MERGE_STATUS_OPTIONS = [
  { value: 'UNMERGED', label: '未合并', tagType: 'info' },
  { value: 'PRECHECKING', label: '预检中', tagType: 'warning' },
  { value: 'MERGING', label: '合并中', tagType: 'primary' },
  { value: 'MERGED', label: '已合并', tagType: 'success' },
  { value: 'CONFLICT', label: '冲突', tagType: 'danger' },
  { value: 'FAILED', label: '合并失败', tagType: 'danger' }
] as const

const mergeStatusLabel = (value?: string) => {
  return MERGE_STATUS_OPTIONS.find((item) => item.value === value)?.label || value || '-'
}

const mergeStatusTagType = (value?: string) => {
  return MERGE_STATUS_OPTIONS.find((item) => item.value === value)?.tagType || 'info'
}
</script>

<style scoped>
.task-diff-viewer {
  min-height: 360px;
}

.section-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.code-block {
  margin: 0;
  padding: 12px;
  overflow: auto;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  white-space: pre;
}

.log-block {
  max-height: 420px;
}

.log-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.file-list,
.plain-list {
  margin: 0;
  padding-left: 20px;
  line-height: 1.9;
}

.report-header {
  margin-bottom: 12px;
}

.report-section {
  margin-bottom: 16px;
}

.report-notes {
  margin: 0;
  white-space: pre-wrap;
  color: var(--el-text-color-regular);
}
</style>
