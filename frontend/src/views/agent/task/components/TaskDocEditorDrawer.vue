<template>
  <el-drawer
    v-model="drawerVisible"
    title="编辑任务文档"
    size="76%"
    direction="rtl"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    @closed="handleClosed"
  >
    <div v-loading="loading" class="task-doc-editor">
      <el-descriptions :column="2" border size="small" class="task-doc-meta">
        <el-descriptions-item label="任务编号">{{
          currentTask?.taskNo || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="文档版本">
          <el-tag type="info" size="small">v{{ docVersion }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="标题">{{
          form.frontMatter.title || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="目标分支">
          {{ form.frontMatter.targetBranch || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form :model="form" label-width="130px">
        <el-divider content-position="left">执行参数</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="超时时长（分钟）" prop="timeoutMinutes">
              <el-input-number
                v-model="form.frontMatter.timeoutMinutes"
                :min="1"
                :max="1440"
                controls-position="right"
                class="!w-full"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="执行优先级" prop="priority">
              <el-input-number
                v-model="form.frontMatter.priority"
                :min="0"
                :max="1000"
                controls-position="right"
                class="!w-full"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="前置任务 ID" prop="dependsOnTaskId">
              <el-input-number
                v-model="form.frontMatter.dependsOnTaskId"
                :min="1"
                controls-position="right"
                placeholder="可选"
                class="!w-full"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">需求目标与上下文</el-divider>
        <el-input
          v-model="form.sections.goal"
          type="textarea"
          :rows="6"
          placeholder="请描述需求目标与上下文"
        />

        <el-divider content-position="left">任务执行计划</el-divider>
        <el-input
          v-model="form.sections.plan"
          type="textarea"
          :rows="10"
          placeholder="请使用可勾选清单描述执行计划"
        />

        <el-divider content-position="left">验收步骤</el-divider>
        <el-input
          v-model="form.sections.steps"
          type="textarea"
          :rows="10"
          placeholder="请填写至少一个可执行命令或明确检查项"
        />

        <el-divider content-position="left">验收标准</el-divider>
        <el-input
          v-model="form.sections.criteria"
          type="textarea"
          :rows="8"
          placeholder="请使用可勾选清单描述验收标准"
        />
      </el-form>
    </div>

    <template #footer>
      <el-button :loading="saving" type="primary" @click="handleSave">保 存</el-button>
      <el-button :disabled="saving" @click="drawerVisible = false">取 消</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import {
  AGENT_TASK_DOC_VERSION_CONFLICT_CODE,
  AgentTaskApi,
  AgentTaskApiError
} from '@/api/agent/task'
import {
  buildTaskDocument,
  parseTaskDocument,
  type TaskDocumentSectionKey,
  type TaskDocumentSections,
  type TaskFrontMatter
} from '../taskDoc'

/** 任务文档编辑抽屉 */
defineOptions({ name: 'TaskDocEditorDrawer' })

const emit = defineEmits<{
  (e: 'success', value: unknown): void
}>()

const message = useMessage()

const drawerVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const docVersion = ref(0)
const currentTask = ref<{ id?: number; taskNo?: string }>()

const SECTION_LABELS: Record<TaskDocumentSectionKey, string> = {
  goal: '需求目标与上下文',
  plan: '任务执行计划',
  steps: '验收步骤',
  criteria: '验收标准'
}

const form = reactive<{
  frontMatter: TaskFrontMatter
  sections: TaskDocumentSections
}>({
  frontMatter: {},
  sections: { goal: '', plan: '', steps: '', criteria: '' }
})

/** 打开编辑抽屉；row 需携带完整 taskDoc 与当前 docVersion */
const open = (row: { id: number; taskNo?: string; docVersion: number; taskDoc: string }) => {
  currentTask.value = row
  docVersion.value = Number(row.docVersion) || 0
  resetForm()

  loading.value = true
  try {
    const parsed = parseTaskDocument(row.taskDoc || '')
    form.frontMatter = { ...parsed.frontMatter }
    if (form.frontMatter.priority === undefined) {
      form.frontMatter.priority = 100
    }
    form.sections = { ...parsed.sections }
  } catch {
    message.error('任务文档解析失败，请检查文档格式后重试')
    return
  } finally {
    loading.value = false
  }
  drawerVisible.value = true
}

defineExpose({ open })

/** 保存文档与执行参数 */
const handleSave = async () => {
  const taskId = currentTask.value?.id
  if (!taskId) {
    return
  }
  if (!form.frontMatter.timeoutMinutes || form.frontMatter.timeoutMinutes < 1) {
    message.warning('请填写有效的超时时长（1-1440 分钟）')
    return
  }
  for (const key of Object.keys(SECTION_LABELS) as TaskDocumentSectionKey[]) {
    if (!form.sections[key].trim()) {
      message.warning(`请填写「${SECTION_LABELS[key]}」内容`)
      return
    }
  }

  const priority = form.frontMatter.priority ?? 100
  const dependsOnTaskId = form.frontMatter.dependsOnTaskId ?? undefined
  const document = buildTaskDocument(
    { ...form.frontMatter, priority, dependsOnTaskId },
    form.sections
  )

  saving.value = true
  try {
    const response = await AgentTaskApi.updateTaskDocument(
      taskId,
      {
        docVersion: docVersion.value,
        document,
        timeoutMinutes: form.frontMatter.timeoutMinutes,
        priority,
        dependsOnTaskId
      },
      String(docVersion.value)
    )
    message.success('任务文档已保存')
    drawerVisible.value = false
    emit('success', response)
  } catch (error) {
    if ((error as AgentTaskApiError).code === AGENT_TASK_DOC_VERSION_CONFLICT_CODE) {
      showVersionConflict(error as Error)
    } else {
      message.error((error as Error).message || '任务文档保存失败')
    }
  } finally {
    saving.value = false
  }
}

/** 版本冲突时给出清晰提示，避免覆盖他人修改 */
const showVersionConflict = (error: Error) => {
  const latest = /最新版本为\s*(\d+)/.exec(error.message)?.[1]
  const content = latest
    ? `该任务文档已被其他操作更新，当前最新版本为 v${latest}。为避免覆盖他人修改，本次保存已取消，请关闭抽屉后重新打开以加载最新内容。`
    : '该任务文档已被其他操作更新。为避免覆盖他人修改，本次保存已取消，请关闭抽屉后重新打开以加载最新内容。'
  ElMessageBox.alert(content, '文档版本冲突', {
    type: 'warning',
    confirmButtonText: '知道了'
  })
}

const handleClosed = () => {
  resetForm()
}

const resetForm = () => {
  form.frontMatter = {}
  form.sections = { goal: '', plan: '', steps: '', criteria: '' }
}
</script>

<style scoped>
.task-doc-editor {
  padding-bottom: 8px;
}

.task-doc-meta {
  margin-bottom: 16px;
}
</style>
