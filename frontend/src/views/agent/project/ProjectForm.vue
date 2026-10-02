<template>
  <Dialog :title="dialogTitle" v-model="dialogVisible" width="760px">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="120px"
      v-loading="formLoading"
    >
      <el-form-item label="项目代号" prop="projectCode">
        <el-input
          v-model="formData.projectCode"
          placeholder="请输入项目代号，如 backend-service"
          maxlength="64"
        />
      </el-form-item>
      <el-form-item label="项目名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入项目名称" maxlength="128" />
      </el-form-item>
      <el-form-item label="Git 仓库地址" prop="gitUrl">
        <el-input
          v-model="formData.gitUrl"
          placeholder="请输入 Git 仓库地址（SSH/HTTP）"
          maxlength="255"
        />
      </el-form-item>
      <el-row>
        <el-col :span="12">
          <el-form-item label="默认分支" prop="defaultBranch">
            <el-input v-model="formData.defaultBranch" placeholder="如 main" maxlength="64" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="构建工具" prop="buildTool">
            <el-select v-model="formData.buildTool" placeholder="请选择构建工具" class="!w-full">
              <el-option
                v-for="option in BUILD_TOOL_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="测试命令" prop="testCommand">
        <el-input v-model="formData.testCommand" placeholder="如 mvn clean test" maxlength="255" />
      </el-form-item>
      <el-form-item label="凭证引用" prop="credentialRef">
        <el-input
          v-model="formData.credentialRef"
          placeholder="Secret Manager 中的 Git 凭证引用"
          maxlength="128"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script setup lang="ts">
import { AgentProject, AgentProjectApi } from '@/api/agent/project'
import { BUILD_TOOL_OPTIONS } from './constants'

/** 代码项目资产表单 */
defineOptions({ name: 'AgentProjectForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const dialogTitle = ref('') // 弹窗的标题
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formType = ref('') // 表单的类型：create - 新增；update - 修改
const formData = ref({
  id: undefined as number | undefined,
  projectCode: undefined as string | undefined,
  name: undefined as string | undefined,
  gitUrl: undefined as string | undefined,
  defaultBranch: undefined as string | undefined,
  buildTool: undefined as string | undefined,
  testCommand: undefined as string | undefined,
  credentialRef: undefined as string | undefined
})
const formRules = reactive({
  projectCode: [{ required: true, message: '项目代号不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '项目名称不能为空', trigger: 'blur' }],
  gitUrl: [{ required: true, message: 'Git 仓库地址不能为空', trigger: 'blur' }],
  defaultBranch: [{ required: true, message: '默认分支不能为空', trigger: 'blur' }],
  buildTool: [{ required: true, message: '构建工具不能为空', trigger: 'change' }]
})
const formRef = ref() // 表单 Ref

/** 打开弹窗 */
const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm()
  if (id) {
    formLoading.value = true
    try {
      formData.value = await AgentProjectApi.getProject(id)
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  await formRef.value.validate()
  // 提交请求
  formLoading.value = true
  try {
    const data = formData.value as unknown as AgentProject
    if (formType.value === 'create') {
      await AgentProjectApi.createProject(data)
      message.success(t('common.createSuccess'))
    } else {
      await AgentProjectApi.updateProject(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    projectCode: undefined,
    name: undefined,
    gitUrl: undefined,
    defaultBranch: 'main',
    buildTool: undefined,
    testCommand: undefined,
    credentialRef: undefined
  }
  formRef.value?.resetFields()
}
</script>
