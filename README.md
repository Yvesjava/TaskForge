# TaskForge

基于 **Spec-Driven Development（文档驱动开发）** 的无人值守 AI Agent 研发流水线控制台。

工程师在本地与 AI 沟通梳理业务需求与架构设计后，将结构化需求文档（Markdown）投递至系统。系统自动完成入队调度、跨仓聚合沙箱构建、AI 批量编码、自动化单测自验、提交推送与验收通知，最终实现一键合并主分支并回收资源的完整闭环。

## 核心能力

- **项目资产管理**：维护多代码仓元数据（仓库地址、默认分支、构建工具、测试命令），支持启用/停用。
- **任务队列控制面**：任务投递后在队列中可执行暂停、继续编辑、恢复排队、取消、软删除、初态重置等生命周期管控。
- **沙箱构建与自动执行**：基于 `git worktree` 毫秒级创建聚合工作区，驱动 Codex CLI / Claude Code 批量编码并串行跑通验收测试。
- **提交与验收通知**：自测通过后自动推送分支，通过企微/飞书交互式卡片推送代码改动统计与测试报告。
- **一键合并与清理**：验收后自动调用 Git 平台 API 合入各仓主分支，并销毁工作区物理目录。

## 技术栈

| 分层 | 技术选型 |
| --- | --- |
| 后端 | Java 17 + Spring Boot 3.x（`ruoyi-vue-pro` 新增 `yudao-module-agent` 模块） |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus（`yudao-ui-admin-vue3`） |
| 调度与锁 | MySQL 8.0 `FOR UPDATE SKIP LOCKED` + Redis 7.x |
| 沙箱与版本控制 | Bare Repo 缓存 + `git worktree` |
| AI 运行层 | Codex CLI / Claude Code（非交互批处理模式） |

## 系统架构

```
客户端/协同层 ──► 控制面(ruoyi-vue-pro) ──► 状态机调度引擎(FOR UPDATE SKIP LOCKED)
                                              │ 派发异步任务
                                              ▼
                          执行 Worker 引擎(聚合工作区 + Codex 驱动 + 自验链)
                                              │ 回传结果
                                              ▼
                          协同集成与合并闭环(企微/飞书 + GitLab/GitHub API)
```

## 任务状态流转

`PENDING → RUNNING → WAITING_ACCEPTANCE → ACCEPTED`，同时支持 `PAUSED`、`CANCELED`、`FAILED` 分支流转与重置。

## 文档

- [产品文档](docs/产品文档.md)
- [技术开发与架构设计文档](docs/技术开发与架构设计文档.md)
- [开发模块与子任务拆分](docs/开发模块与子任务拆分.md)

## 本地开发环境

前后端源码已经放入 TaskForge 根目录：

| 目录 | 来源 | 分支 | 本地端口 |
| --- | --- | --- | --- |
| `backend/` | `https://gitee.com/zhijiantianya/ruoyi-vue-pro.git` | `master-jdk17` | `48080` |
| `frontend/` | `https://gitee.com/yudaocode/yudao-ui-admin-vue3.git` | `master` | `3000` |

环境要求：Java 17、Maven 3.8+、Node.js 20.19+、pnpm 8.6+、Docker Desktop。

首次启动：

```powershell
Copy-Item .env.example .env
Copy-Item frontend/.env.local.example frontend/.env.local
docker compose up -d
```

启动后端：

```powershell
Set-Location backend
$env:SPRING_PROFILES_ACTIVE = 'local,taskforge'
mvn -pl yudao-server -am -DskipTests package
java -jar yudao-server/target/yudao-server.jar --spring.profiles.active=local,taskforge
```

启动前端（另开一个终端）：

```powershell
Set-Location frontend
pnpm install
pnpm dev
```

访问 `http://localhost:3000`，前端 API 地址为 `http://localhost:48080/admin-api`。MySQL 首次启动会自动导入 `backend/sql/mysql/ruoyi-vue-pro.sql` 和 Quartz 表结构；数据卷已存在时不会重复导入。

前端本地环境变量统一收敛到 `frontend/.env.local.example`（复制为 `frontend/.env.local`）：`VITE_PORT` 固定前端端口 `3000`，`VITE_BASE_URL`（后端来源地址）与 `VITE_API_URL`（`admin-api` 前缀）拼接为接口基地址 `http://localhost:48080/admin-api`。可用 `pnpm check:env`（在 `frontend/` 下）校验该约定未被破坏。

## 实施路线

1. **里程碑 1**：数据底座与模块脚手架（`agent_project` / `agent_task` 表结构 + 基础 CRUD）
2. **里程碑 2**：任务控制面与状态流转（HTTP 投递 + 调度器 + 生命周期管控）
3. **里程碑 3**：Worktree 聚合沙箱与 Codex 驱动闭环
4. **里程碑 4**：通知集成与自动化合并
