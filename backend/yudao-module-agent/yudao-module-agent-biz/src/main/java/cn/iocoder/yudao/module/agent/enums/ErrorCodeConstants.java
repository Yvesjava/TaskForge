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
    ErrorCode WORKTREE_TASK_NO_INVALID = new ErrorCode(1_061_001_010, "工作区任务编号无效：{}");
    ErrorCode WORKTREE_BRANCH_INVALID = new ErrorCode(1_061_001_011, "工作区分支无效：{}");
    ErrorCode WORKTREE_PROJECT_CODE_INVALID = new ErrorCode(1_061_001_012, "工作区项目代号无效：{}");
    ErrorCode WORKTREE_PROJECTS_EMPTY = new ErrorCode(1_061_001_013, "任务未配置任何代码项目");
    ErrorCode WORKTREE_SUB_DIR_INVALID = new ErrorCode(1_061_001_014, "项目 {} 的子目录映射无效：{}");
    ErrorCode WORKTREE_SUB_DIR_CONFLICT = new ErrorCode(1_061_001_015, "子目录映射冲突：{}");
    ErrorCode WORKTREE_CREATE_FAILED = new ErrorCode(1_061_001_016, "项目 {} 工作树挂载失败（子目录 {}）：{}");
    ErrorCode WORKTREE_ROOT_ESCAPE = new ErrorCode(1_061_001_017, "工作区根目录越界：{}");
    ErrorCode WORKTREE_WRITE_TASK_DOC_FAILED = new ErrorCode(1_061_001_018, "写入聚合任务文档失败：{}");
    ErrorCode WORKTREE_GIT_URL_INVALID = new ErrorCode(1_061_001_019, "项目 {} 的仓库地址无效");

}
