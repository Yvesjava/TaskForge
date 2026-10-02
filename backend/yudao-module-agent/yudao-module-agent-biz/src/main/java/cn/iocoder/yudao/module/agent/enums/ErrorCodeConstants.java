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

}
