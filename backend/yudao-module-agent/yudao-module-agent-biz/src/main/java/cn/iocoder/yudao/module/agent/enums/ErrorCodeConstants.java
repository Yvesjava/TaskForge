package cn.iocoder.yudao.module.agent.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Agent 错误码枚举类
 *
 * agent 系统，使用 1-061-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 项目资产模块 1-061-000-000 ==========
    ErrorCode PROJECT_NOT_FOUND = new ErrorCode(1_061_000_000, "项目资产不存在");
    ErrorCode PROJECT_CODE_DUPLICATE = new ErrorCode(1_061_000_001, "项目代号已存在");
    ErrorCode PROJECT_REF_NOT_FOUND = new ErrorCode(1_061_000_002, "项目代号不存在：{}");
    ErrorCode PROJECT_REF_DISABLED = new ErrorCode(1_061_000_003, "项目已停用，无法引用：{}");
    ErrorCode PROJECT_REF_BASE_BRANCH_INVALID = new ErrorCode(1_061_000_004, "项目 {} 的基线分支无效：{}");
    ErrorCode PROJECT_REF_SUB_DIR_INVALID = new ErrorCode(1_061_000_005, "项目 {} 的子目录名无效：{}");
    ErrorCode PROJECT_REF_SUB_DIR_CONFLICT = new ErrorCode(1_061_000_006, "子目录映射冲突：{}");

    // ========== 工作区模块 1-061-001-000 ==========
    ErrorCode BARE_REPO_PROJECT_CODE_INVALID = new ErrorCode(1_061_001_000, "Bare Repo 项目代号无效：{}");
    ErrorCode BARE_REPO_BRANCH_INVALID = new ErrorCode(1_061_001_001, "Bare Repo 分支无效，项目 {}：{}");
    ErrorCode BARE_REPO_INIT_FAILED = new ErrorCode(1_061_001_002, "Bare Repo 缓存初始化失败，项目 {}：{}");
    ErrorCode BARE_REPO_FETCH_FAILED = new ErrorCode(1_061_001_003, "Bare Repo 拉取失败，项目 {}，分支 {}：{}");
    ErrorCode BRANCH_TASK_NO_INVALID = new ErrorCode(1_061_001_004, "任务编号无效，无法生成特性分支：{}");
    ErrorCode BRANCH_NAME_INVALID = new ErrorCode(1_061_001_005, "项目 {} 的分支名无效：{}");
    ErrorCode BRANCH_BASELINE_NOT_FOUND = new ErrorCode(1_061_001_006, "基线分支不存在，项目 {}：{}");
    ErrorCode BRANCH_BASELINE_CHECK_FAILED = new ErrorCode(1_061_001_007, "基线分支检查失败，项目 {}，分支 {}：{}");
    ErrorCode BRANCH_CREATE_FAILED = new ErrorCode(1_061_001_008, "特性分支创建失败，项目 {}，分支 {}：{}");
    ErrorCode BRANCH_PUSH_FAILED = new ErrorCode(1_061_001_009, "特性分支推送失败，项目 {}，分支 {}：{}");
    ErrorCode BRANCH_DELETE_FAILED = new ErrorCode(1_061_001_010, "特性分支删除失败，项目 {}，分支 {}：{}");
    ErrorCode WORKTREE_TASK_NO_INVALID = new ErrorCode(1_061_001_011, "工作区任务编号无效：{}");
    ErrorCode WORKTREE_BRANCH_INVALID = new ErrorCode(1_061_001_012, "工作区分支无效：{}");
    ErrorCode WORKTREE_PROJECT_CODE_INVALID = new ErrorCode(1_061_001_013, "工作区项目代号无效：{}");
    ErrorCode WORKTREE_PROJECTS_EMPTY = new ErrorCode(1_061_001_014, "任务未配置任何代码项目");
    ErrorCode WORKTREE_SUB_DIR_INVALID = new ErrorCode(1_061_001_015, "项目 {} 的子目录映射无效：{}");
    ErrorCode WORKTREE_SUB_DIR_CONFLICT = new ErrorCode(1_061_001_016, "子目录映射冲突：{}");
    ErrorCode WORKTREE_CREATE_FAILED = new ErrorCode(1_061_001_017, "项目 {} 工作树挂载失败（子目录 {}）：{}");
    ErrorCode WORKTREE_ROOT_ESCAPE = new ErrorCode(1_061_001_018, "工作区根目录越界：{}");
    ErrorCode WORKTREE_WRITE_TASK_DOC_FAILED = new ErrorCode(1_061_001_019, "写入聚合任务文档失败：{}");
    ErrorCode WORKTREE_GIT_URL_INVALID = new ErrorCode(1_061_001_020, "项目 {} 的仓库地址无效");
    ErrorCode WORKTREE_SYMLINK_NOT_ALLOWED = new ErrorCode(1_061_001_021, "工作区路径不允许包含符号链接：{}");
    ErrorCode WORKTREE_RESIDUE_SCAN_FAILED = new ErrorCode(1_061_001_022, "工作区残留检测失败：{}");
    ErrorCode WORKTREE_WRITE_WORKFLOW_FAILED = new ErrorCode(1_061_001_023, "写入系统级执行约束失败：{}");
    ErrorCode WORKTREE_CLEANUP_FAILED = new ErrorCode(1_061_001_024, "工作区清理失败：{}");
    ErrorCode BARE_REPO_DELETE_FAILED = new ErrorCode(1_061_001_025, "Bare Repo 缓存清理失败，项目 {}：{}");

    // ========== 任务文档模块 1-061-002-000 ==========
    ErrorCode DOCUMENT_EMPTY = new ErrorCode(1_061_002_000, "任务文档不能为空");
    ErrorCode DOCUMENT_FRONT_MATTER_NOT_FOUND = new ErrorCode(1_061_002_001, "任务文档缺少 YAML Front Matter（--- 起始块）");
    ErrorCode DOCUMENT_FRONT_MATTER_NOT_CLOSED = new ErrorCode(1_061_002_002, "任务文档 Front Matter 缺少结束分隔符 ---");
    ErrorCode DOCUMENT_FRONT_MATTER_EMPTY = new ErrorCode(1_061_002_003, "任务文档 Front Matter 内容不能为空");
    ErrorCode DOCUMENT_FRONT_MATTER_INVALID = new ErrorCode(1_061_002_004, "任务文档 Front Matter 解析失败：{}");
    ErrorCode DOCUMENT_FRONT_MATTER_NOT_MAPPING = new ErrorCode(1_061_002_005, "任务文档 Front Matter 顶层必须是键值对映射");
    ErrorCode DOCUMENT_FIELD_TYPE_ERROR = new ErrorCode(1_061_002_006, "任务文档字段 {}（第 {} 行第 {} 列）{}");
    ErrorCode DOCUMENT_FIELD_REQUIRED = new ErrorCode(1_061_002_007, "任务文档必填字段缺失：{}");
    ErrorCode DOCUMENT_REPO_MODE_CONFLICT = new ErrorCode(1_061_002_008, "任务文档同时声明单仓与多仓仓库配置");
    ErrorCode DOCUMENT_REPO_MODE_MISSING = new ErrorCode(1_061_002_009, "任务文档缺少仓库配置（单仓 repoUrl/baseBranch 或多仓 projects）");
    ErrorCode DOCUMENT_TARGET_BRANCH_INVALID = new ErrorCode(1_061_002_010, "任务文档目标分支无效：{}");
    ErrorCode DOCUMENT_BASE_BRANCH_INVALID = new ErrorCode(1_061_002_011, "任务文档基线分支无效：{}");
    ErrorCode DOCUMENT_SECTION_MISSING = new ErrorCode(1_061_002_012, "任务文档缺少正文小节：{}");
    ErrorCode DOCUMENT_SECTION_EMPTY = new ErrorCode(1_061_002_013, "任务文档正文小节内容为空：{}");
    ErrorCode DOCUMENT_SECTION_NOT_CHECKLIST = new ErrorCode(1_061_002_014, "任务文档正文小节 {} 必须包含可勾选清单");
    ErrorCode DOCUMENT_SECTION_STEP_NOT_FOUND = new ErrorCode(1_061_002_015, "任务文档正文小节 {} 缺少验收步骤列表");
    ErrorCode DOCUMENT_SECTION_STEP_NOT_EXECUTABLE = new ErrorCode(1_061_002_016, "任务文档正文小节 {} 的验收步骤必须为可执行命令或明确检查项");
    ErrorCode DOCUMENT_TASK_NO_INVALID = new ErrorCode(1_061_002_017, "任务文档任务编号无效：{}");

    // ========== 任务投递模块 1-061-003-000 ==========
    ErrorCode TASK_SUBMIT_IDEMPOTENCY_KEY_REQUIRED = new ErrorCode(1_061_003_000, "缺少请求幂等键 X-Idempotency-Key");
    ErrorCode TASK_SUBMIT_IDEMPOTENCY_KEY_INVALID = new ErrorCode(1_061_003_001, "请求幂等键格式无效，长度 16-128，仅允许字母、数字、点、下划线、连字符");
    ErrorCode TASK_SUBMIT_TASK_NO_DUPLICATE = new ErrorCode(1_061_003_002, "任务编号已存在：{}");
    ErrorCode TASK_NOT_FOUND = new ErrorCode(1_061_003_003, "任务不存在");
    ErrorCode TASK_DOCUMENT_STATE_INVALID = new ErrorCode(1_061_003_004, "仅暂停（PAUSED）状态的任务允许编辑文档");
    ErrorCode TASK_DOC_VERSION_CONFLICT = new ErrorCode(1_061_003_005, "文档版本冲突，当前最新版本为 {}");
    ErrorCode TASK_DOCUMENT_IF_MATCH_REQUIRED = new ErrorCode(1_061_003_006, "缺少乐观锁请求头 If-Match");
    ErrorCode TASK_DOCUMENT_IF_MATCH_INVALID = new ErrorCode(1_061_003_007, "乐观锁请求头 If-Match 格式无效，应为正整数文档版本");
    ErrorCode TASK_DOCUMENT_IF_MATCH_MISMATCH = new ErrorCode(1_061_003_008, "If-Match 请求头与请求体 docVersion 不一致");
    ErrorCode TASK_STATUS_TRANSITION_NOT_ALLOWED = new ErrorCode(1_061_003_009, "任务状态不允许执行该转换：当前状态 {}，动作 {}");
    ErrorCode TASK_STATUS_TRANSITION_TARGET_REQUIRED = new ErrorCode(1_061_003_010, "动作 {} 存在多个目标状态，必须显式指定目标状态");
    ErrorCode TASK_STATUS_UPDATE_CONFLICT = new ErrorCode(1_061_003_011, "任务 {} 状态已变化，条件更新失败：当前状态 {}，动作 {}");
    ErrorCode TASK_CANNOT_RESET_NOT_PAUSED = new ErrorCode(1_061_003_012, "仅暂停（PAUSED）状态的任务允许重置");
    ErrorCode TASK_REJECT_FEEDBACK_REQUIRED = new ErrorCode(1_061_003_013, "打回反馈不能为空");
    ErrorCode TASK_REJECT_FEEDBACK_INVALID = new ErrorCode(1_061_003_014, "打回反馈长度不能超过 2000 个字符");

    // ========== 合并模块 1-061-004-000 ==========
    ErrorCode MERGE_TASK_STATE_INVALID = new ErrorCode(1_061_004_000, "任务当前状态不允许合并：{}");
    ErrorCode MERGE_CLEANUP_REPOS_NOT_MERGED = new ErrorCode(1_061_004_001, "任务仍有仓库未合并完成，不能清理收尾");

}
