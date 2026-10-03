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

    // ========== 任务文档模块 1-061-002-000 ==========
    ErrorCode DOCUMENT_EMPTY = new ErrorCode(1_061_002_000, "任务文档不能为空");
    ErrorCode DOCUMENT_FRONT_MATTER_NOT_FOUND = new ErrorCode(1_061_002_001, "任务文档缺少 YAML Front Matter（--- 起始块）");
    ErrorCode DOCUMENT_FRONT_MATTER_NOT_CLOSED = new ErrorCode(1_061_002_002, "任务文档 Front Matter 缺少结束分隔符 ---");
    ErrorCode DOCUMENT_FRONT_MATTER_EMPTY = new ErrorCode(1_061_002_003, "任务文档 Front Matter 内容不能为空");
    ErrorCode DOCUMENT_FRONT_MATTER_INVALID = new ErrorCode(1_061_002_004, "任务文档 Front Matter 解析失败：{}");
    ErrorCode DOCUMENT_FRONT_MATTER_NOT_MAPPING = new ErrorCode(1_061_002_005, "任务文档 Front Matter 顶层必须是键值对映射");
    ErrorCode DOCUMENT_FIELD_TYPE_ERROR = new ErrorCode(1_061_002_006, "任务文档字段 {}（第 {} 行第 {} 列）{}");

}
