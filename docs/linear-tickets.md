# TaskForge Linear Ticket 清单

本文档把 [开发模块与子任务拆分](开发模块与子任务拆分.md) 的 **60 个一级子任务**（你提到的 ~57 实际为 60）翻译成 Linear ticket，并在对应里程碑内补充核对后发现的 **4 个遗留缺口 ticket**，供 Symphony 消费。

## 使用约定

- **Linear 项目 slug**：`0b9aeee7e125`
- **初始状态**：全部放 `Backlog`（Symphony 忽略 Backlog）；按里程碑 **M1 → M6** 逐批把当批 ticket 移到 `Todo`。Symphony 不按依赖自动排序，人工按里程碑放行。
- **Labels**：每个 ticket 打两个标签 —— 里程碑 `M1`~`M6` + 模块 `BASE`/`DATA`/`PROJECT`/`DOC`/`TASK`/`SCHED`/`WORK`/`EXEC`/`NOTICE`/`MERGE`/`WEB`/`OPS`/`QA`。
- **Depends on**：映射为 Linear 的 `blockedBy` 关系（用于人工排期，不驱动 Symphony 调度）。
- **Validation**：Symphony 把它当**不可协商的验收输入**，ticket 必须带这段命令才能被判定完成。

---

## M1 数据底座与脚手架

### TASK-BASE-01 注册 `yudao-module-agent` Maven 模块

- **Labels**: `M1`, `BASE`
- **Depends on**: —
- **Description**: 在 ruoyi-vue-pro 后端新增 `yudao-module-agent` 模块（含 `api`/`biz` 子模块），按既有模块模式注册到父 `pom.xml` 与 `yudao-server` 依赖。只建骨架与包结构，不写业务逻辑。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am -DskipTests package
  ```
  - [ ] 后端可跳过业务实现完成编译

### TASK-BASE-02 固化本地配置与 Docker 环境

- **Labels**: `M1`, `BASE`
- **Depends on**: `TASK-BASE-01`
- **Description**: 固化 `local,taskforge` 配置、`docker-compose.yml`、MySQL/Redis 启动参数与初始化 SQL，保证新环境一键启动。
- **Validation**:
  ```bash
  docker compose up -d && docker compose ps
  ```
  - [ ] 新环境按 README 启动，MySQL/Redis 健康检查通过

### TASK-BASE-03 统一前端环境变量与本地命令

- **Labels**: `M1`, `BASE`
- **Depends on**: `TASK-BASE-01`
- **Description**: 统一前端 API 环境变量（`VITE_API_BASE_URL` 等）、端口与 `pnpm dev` 本地开发命令，指向后端 `48080`。
- **Validation**:
  ```bash
  cd frontend && pnpm install && pnpm dev
  ```
  - [ ] `pnpm dev` 能访问后端配置的 API 地址（`http://localhost:48080/admin-api`）

### TASK-BASE-04 建立格式/构建/日志/分支约定

- **Labels**: `M1`, `BASE`
- **Depends on**: `TASK-BASE-01`
- **Description**: 固化代码格式、构建、日志与分支命名约定，使 README、AGENTS.md 与实际命令一致（harness engineering 基线）。
- **Validation**:
  ```bash
  git diff --check && cd backend && mvn -q -pl yudao-module-agent/yudao-module-agent-biz -am test-compile
  ```
  - [ ] README、AGENTS.md 与实际命令一致；`git diff --check` 无告警

### TASK-DATA-01 创建四类表迁移脚本

- **Labels**: `M1`, `DATA`
- **Depends on**: `TASK-BASE-01`
- **Description**: 创建 `agent_project`、`agent_task`、`agent_task_project`、`agent_task_operation_log` 的 Flyway/SQL 迁移脚本，含回滚说明。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-server -am -DskipTests package && java -jar yudao-server/target/yudao-server.jar --spring.profiles.active=local,taskforge
  ```
  - [ ] 迁移可重复执行，回滚说明可用；四类表结构符合架构文档

### TASK-DATA-02 实现 DO / Mapper / 查询条件

- **Labels**: `M1`, `DATA`
- **Depends on**: `TASK-DATA-01`
- **Description**: 实现四类表的 MyBatis-Plus DO、Mapper 与 XML 查询条件（分页、租约条件、索引查询）。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test
  ```
  - [ ] CRUD、租约条件与索引查询可执行

### TASK-DATA-03 补齐租户/软删除/乐观锁/代次约束

- **Labels**: `M1`, `DATA`
- **Depends on**: `TASK-DATA-01`
- **Description**: 补齐 `tenant_id`、`deleted`、乐观锁、`execution_generation` 与审计字段约束，与架构文档、实体定义一致。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test
  ```
  - [ ] 字段与架构文档、实体定义一致；唯一键/索引生效

### TASK-DATA-04 数据库约束与并发查询集成测试

- **Labels**: `M1`, `DATA`
- **Depends on**: `TASK-DATA-02`, `TASK-DATA-03`
- **Description**: 编写唯一键、索引与 `FOR UPDATE SKIP LOCKED` 并发行为的集成测试（Testcontainers MySQL）。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*ConcurrencyTest,*IntegrationTest'
  ```
  - [ ] 唯一键、索引、`SKIP LOCKED` 行为可验证

### TASK-PROJECT-01 项目资产 CRUD API

- **Labels**: `M1`, `PROJECT`
- **Depends on**: `TASK-DATA-02`
- **Description**: 实现项目资产分页、详情、新增、修改、启停、软删除 API，附权限校验与接口测试。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*ProjectServiceTest'
  ```
  - [ ] CRUD 可用，权限校验生效，API 文档齐全

### TASK-PROJECT-02 项目配置校验

- **Labels**: `M1`, `PROJECT`
- **Depends on**: `TASK-PROJECT-01`
- **Description**: 校验 Git 地址、默认分支、构建工具、测试命令与凭证引用；非法配置在保存时拒绝且错误可定位。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*ProjectValidationTest'
  ```
  - [ ] 非法配置被拒绝，错误信息可定位字段

### TASK-PROJECT-03 任务引用项目校验

- **Labels**: `M1`, `PROJECT`
- **Depends on**: `TASK-PROJECT-01`
- **Description**: 任务引用项目时校验启用状态、基线分支与子目录映射冲突，投递与恢复排队前均执行。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*ProjectRefValidationTest'
  ```
  - [ ] 未启用项目、冲突子目录在投递/恢复前被拒绝

### TASK-PROJECT-04 项目管理页面与权限路由

- **Labels**: `M1`, `PROJECT`
- **Depends on**: `TASK-PROJECT-01`
- **Description**: 完成项目管理页面（列表/编辑/启停）与权限路由。
- **Validation**:
  ```bash
  cd frontend && pnpm run ts:check && pnpm run build:prod
  ```
  - [ ] 可维护项目元数据并显示启停状态，权限路由与后端一致

---

## M2 控制面与状态流转

### TASK-DOC-01 YAML Front Matter 解析

- **Labels**: `M2`, `DOC`
- **Depends on**: `TASK-DATA-02`, `TASK-PROJECT-02`
- **Description**: 实现 YAML Front Matter 解析与字段类型转换，支持单仓与多仓示例，解析错误包含字段位置。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*DocumentParserTest'
  ```
  - [ ] 单仓/多仓示例可解析，解析错误含字段位置

### TASK-DOC-02 必填字段与项目校验

- **Labels**: `M2`, `DOC`
- **Depends on**: `TASK-DOC-01`
- **Description**: 实现必填字段、状态值、项目、分支与子目录校验。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*DocumentValidationTest'
  ```
  - [ ] 缺字段、重复目录、未启用项目被拒绝

### TASK-DOC-03 正文小节与验收步骤校验

- **Labels**: `M2`, `DOC`
- **Depends on**: `TASK-DOC-01`
- **Description**: 校验正文小节（需求目标/执行计划/验收步骤/验收标准）与可执行验收步骤。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*DocumentSectionTest'
  ```
  - [ ] 计划、验收步骤、验收标准缺失时返回明确错误

### TASK-DOC-04 投递 API 与编号/版本生成

- **Labels**: `M2`, `DOC`
- **Depends on**: `TASK-DOC-02`, `TASK-DOC-03`
- **Description**: 实现 `POST /admin-api/agent/task/submit`、任务编号生成与文档版本保存，幂等键去重。
- **Validation**:
  ```bash
  curl -X POST http://localhost:48080/admin-api/agent/task/submit -H 'Content-Type: application/json' -d '{"document":"..."}'
  ```
  - [ ] 返回任务编号/状态/版本；重复幂等键不重复建任务

### TASK-DOC-05 文档编辑与乐观锁

- **Labels**: `M2`, `DOC`
- **Depends on**: `TASK-DOC-04`
- **Description**: 实现暂停任务的文档编辑（`PATCH /document`）与 `docVersion` 乐观锁，冲突返回 `409`。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*DocVersionTest'
  ```
  - [ ] 版本冲突不覆盖他人修改，写入操作日志

### TASK-TASK-01 状态枚举与转换矩阵

- **Labels**: `M2`, `TASK`
- **Depends on**: `TASK-DATA-03`, `TASK-DOC-04`
- **Description**: 固化状态枚举、合法转换矩阵与条件更新 SQL，所有状态动作走统一服务入口。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*StateMachineTest'
  ```
  - [ ] 所有状态动作有统一入口；未列出的转换返回 `409`

### TASK-TASK-02 暂停/恢复/取消/重投/软删除

- **Labels**: `M2`, `TASK`
- **Depends on**: `TASK-TASK-01`
- **Description**: 实现暂停、恢复、取消、重新入队与软删除动作，非法状态返回业务错误且不改数据。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*LifecycleTest'
  ```
  - [ ] 非法状态操作返回业务错误且不改数据

### TASK-TASK-03 Resetting 流程与清理委托

- **Labels**: `M2`, `TASK`
- **Depends on**: `TASK-TASK-01`
- **Description**: 实现 Resetting 流程：`PAUSED → RESETTING → PENDING/PAUSED`，外部清理幂等，资源不存在仍完成元数据清理。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*ResetTest'
  ```
  - [ ] 重置幂等，资源不存在仍完成元数据清理

### TASK-TASK-04 验收/打回/冲突人工入口

- **Labels**: `M2`, `TASK`
- **Depends on**: `TASK-TASK-01`
- **Description**: 实现验收、打回与合并冲突人工处理入口，记录反馈、操作者、版本与前后状态。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*AcceptanceTest'
  ```
  - [ ] 记录反馈、操作者、版本、前后状态

### TASK-TASK-05 审计/幂等键/错误响应

- **Labels**: `M2`, `TASK`
- **Depends on**: `TASK-TASK-01`
- **Description**: 完成操作审计（`agent_task_operation_log`）、请求幂等键（`X-Idempotency-Key`）与统一错误响应（400/403/404/409/500）。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*IdempotencyTest'
  ```
  - [ ] 重试请求不产生重复副作用；错误不泄露凭证

### TASK-TASK-06 重投支持克隆为全新任务编号

- **Labels**: `M2`, `TASK`
- **Depends on**: `TASK-TASK-02`
- **Description**: 重投（re-enqueue）支持原位恢复为 `PENDING` 或克隆为全新任务编号；克隆时复制任务文档与项目引用、生成新 `task_no`、清空执行结果与耗时、执行代次归零，审计区分「重投」与「克隆重投」。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*ReEnqueue*'
  ```
  - [ ] `CANCELED` / `REJECTED` / `FAILED` 可克隆为新编号；克隆件与原任务独立；重复幂等键不重复克隆

### TASK-SCHED-01 短事务抢占

- **Labels**: `M2`, `SCHED`
- **Depends on**: `TASK-TASK-01`
- **Description**: 实现按优先级升序、创建时间升序的 `FOR UPDATE SKIP LOCKED` 短事务抢占；锁事务不包围外部操作。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*ClaimServiceTest'
  ```
  - [ ] 仅一台 Worker 获得同一任务；锁事务不包围外部操作

### TASK-SCHED-02 Redis 租约与心跳

- **Labels**: `M2`, `SCHED`
- **Depends on**: `TASK-SCHED-01`
- **Description**: 实现 Redis 租约、心跳与执行代次校验；续租只允许当前 Worker 与当前代次。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*LeaseTest'
  ```
  - [ ] 续租只允许当前 Worker 和当前代次更新

### TASK-SCHED-03 取消信号与停止写入规则

- **Labels**: `M2`, `SCHED`
- **Depends on**: `TASK-SCHED-02`
- **Description**: 实现取消信号（`agent:task:cancel`）与 Worker 停止写入规则。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*CancelSignalTest'
  ```
  - [ ] 取消或失租后不再提交、推送、写回结果

### TASK-SCHED-04 租约过期扫描与失败恢复

- **Labels**: `M2`, `SCHED`
- **Depends on**: `TASK-SCHED-02`
- **Description**: 实现租约过期扫描（`RUNNING AND lease_until < NOW`）与失败恢复，旧 Worker 无法覆盖新结果。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*LeaseRecoveryTest'
  ```
  - [ ] 过期任务进入 `FAILED` 或恢复队列；旧 Worker 无法覆盖结果

### TASK-SCHED-05 调度器指标与状态检查

- **Labels**: `M2`, `SCHED`
- **Depends on**: `TASK-SCHED-01`
- **Description**: 实现调度器指标与运行状态检查（队列长度、运行中任务、租约异常）。
- **Validation**:
  ```bash
  curl -s http://localhost:48080/admin-api/agent/task/scheduler/status
  ```
  - [ ] 可查询队列长度、运行中任务、租约异常

---

## M3 沙箱与 AI 执行

### TASK-WORK-01 Bare Repo 缓存初始化

- **Labels**: `M3`, `WORK`
- **Depends on**: `TASK-PROJECT-02`
- **Description**: 实现 Bare Repo 缓存初始化、拉取与锁定；首次可初始化，重复使用不破坏缓存。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*BareRepoTest'
  ```
  - [ ] 首次可初始化，重复使用不破坏缓存

### TASK-WORK-02 多仓 Worktree 聚合布局

- **Labels**: `M3`, `WORK`
- **Depends on**: `TASK-WORK-01`
- **Description**: 实现多仓 Worktree 创建与聚合目录布局，`backend`/`frontend` 子目录按映射挂载。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*WorktreeManagerTest'
  ```
  - [ ] 子目录按映射挂载；`.ai/task.md` 写入聚合根

### TASK-WORK-03 任务分支创建与清理

- **Labels**: `M3`, `WORK`
- **Depends on**: `TASK-WORK-01`
- **Description**: 实现任务特性分支创建、基线校验与分支清理，命名/基线/远端操作可追踪。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*BranchTest'
  ```
  - [ ] 分支命名、基线与远端操作可追踪

### TASK-WORK-04 路径校验/互斥/残留检测

- **Labels**: `M3`, `WORK`
- **Depends on**: `TASK-WORK-02`
- **Description**: 实现工作区路径校验（拒绝 `..`/绝对路径/符号链接）、并发互斥与残留检测。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*PathSafetyTest'
  ```
  - [ ] 无法逃逸工作根目录，异常退出后可发现残留

### TASK-WORK-05 幂等清理与错误归一化

- **Labels**: `M3`, `WORK`
- **Depends on**: `TASK-WORK-02`
- **Description**: 实现幂等清理（worktree remove/物理删除/prune）与 Git 命令错误归一化。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*CleanupTest'
  ```
  - [ ] 重复清理成功；错误含仓库与操作上下文

### TASK-EXEC-01 `.ai/WORKFLOW.md` 注入协议

- **Labels**: `M3`, `EXEC`
- **Depends on**: `TASK-WORK-02`
- **Description**: 固化 `.ai/WORKFLOW.md` 与任务文档注入协议，Codex 能读取任务目标、计划、验收标准与工程约束。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*WorkflowInjectionTest'
  ```
  - [ ] `.ai/task.md` 与 `.ai/WORKFLOW.md` 内容符合契约

### TASK-EXEC-02 非交互子进程与进程组管理

- **Labels**: `M3`, `EXEC`
- **Depends on**: `TASK-WORK-02`, `TASK-EXEC-01`
- **Description**: 实现 Codex/Claude 非交互子进程启动与进程组管理，记录 stdout/stderr、退出码、PID 与启动参数。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*CodexRunnerTest'
  ```
  - [ ] stdout/stderr、退出码、PID、启动参数可记录

### TASK-EXEC-03 命令白名单与顺序执行

- **Labels**: `M3`, `EXEC`
- **Depends on**: `TASK-EXEC-02`
- **Description**: 实现构建/测试命令白名单与顺序执行，所有验收命令通过后才允许提交与推送。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*CommandGateTest'
  ```
  - [ ] 白名单外命令被拒；验收命令非零退出不提交/推送

### TASK-EXEC-04 两次自修复重试与归档

- **Labels**: `M3`, `EXEC`
- **Depends on**: `TASK-EXEC-03`
- **Description**: 实现最多两次自修复重试与结果归档，每次重试有独立日志、耗时与原因。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*RetryTest'
  ```
  - [ ] 每次重试有独立日志/耗时/原因，最多两次

### TASK-EXEC-05 超时/资源限制/失败清理/写回

- **Labels**: `M3`, `EXEC`
- **Depends on**: `TASK-EXEC-03`
- **Description**: 实现超时熔断（终止整个进程组）、资源限制、失败清理与执行结果写回。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*TimeoutTest'
  ```
  - [ ] 超时终止完整进程组，任务进入 `FAILED`

---

## M4 通知与多仓合并

### TASK-NOTICE-01 通知事件与卡片数据契约

- **Labels**: `M4`, `NOTICE`
- **Depends on**: `TASK-TASK-01`
- **Description**: 定义通知事件、卡片数据与脱敏规则，待验收/失败/冲突卡片字段稳定。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*NoticeContractTest'
  ```
  - [ ] 卡片字段稳定且不含秘密

### TASK-NOTICE-02 企微/飞书 Webhook 适配器

- **Labels**: `M4`, `NOTICE`
- **Depends on**: `TASK-NOTICE-01`
- **Description**: 实现企业微信/飞书 Webhook 适配器，消息发送、超时与错误可重试。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*WebhookAdapterTest'
  ```
  - [ ] 发送/超时/错误可重试

### TASK-NOTICE-03 HMAC 签名与回放防护

- **Labels**: `M4`, `NOTICE`
- **Depends on**: `TASK-NOTICE-02`
- **Description**: 实现 HMAC-SHA256 签名、时间窗（默认 5 分钟）与回放防护。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*SignatureTest'
  ```
  - [ ] 非法或重复回调被拒绝

### TASK-NOTICE-04 卡片验收/打回回调与幂等

- **Labels**: `M4`, `NOTICE`
- **Depends on**: `TASK-NOTICE-03`, `TASK-TASK-04`
- **Description**: 实现卡片验收、打回回调与幂等处理，回调复用控制面动作并返回可重试结果。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*CallbackTest'
  ```
  - [ ] 回调复用控制面动作，重复回调幂等

### TASK-MERGE-01 Git 平台接口抽象

- **Labels**: `M4`, `MERGE`
- **Depends on**: `TASK-TASK-04`
- **Description**: 抽象 GitLab/GitHub/Gitea 平台接口：创建 MR/PR、查询状态、执行合并。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*GitApiOperatorTest'
  ```
  - [ ] 三种平台统一接口，可用假适配器测试

### TASK-MERGE-02 合并前预检

- **Labels**: `M4`, `MERGE`
- **Depends on**: `TASK-MERGE-01`
- **Description**: 实现所有仓库合并前预检，任一仓冲突或检查失败时不发生部分合并。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*MergePrecheckTest'
  ```
  - [ ] 任一仓冲突时零仓合并（全有或全无）

### TASK-MERGE-03 Fast-Forward/Squash 合并与回写

- **Labels**: `M4`, `MERGE`
- **Depends on**: `TASK-MERGE-02`
- **Description**: 实现 Fast-Forward/Squash 合并与结果回写，各仓结果与任务状态一致，重复回调幂等。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*MergeServiceTest'
  ```
  - [ ] 各仓 `merge_status` 与任务状态一致，重复回调幂等

### TASK-MERGE-04 冲突转人工与继续合并

- **Labels**: `M4`, `MERGE`
- **Depends on**: `TASK-MERGE-02`
- **Description**: 实现冲突转人工（`MERGE_CONFLICT_PENDING_MANUAL`）、重试与继续合并的可恢复路径。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*MergeConflictTest'
  ```
  - [ ] 冲突具备可恢复路径，阻断其他仓合并

### TASK-MERGE-05 合并完成后资源清理

- **Labels**: `M4`, `MERGE`
- **Depends on**: `TASK-MERGE-03`
- **Description**: 实现合并完成后的 Worktree、分支与缓存清理，全部清理完成后才进入 `COMPLETED`。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*MergeCleanupTest'
  ```
  - [ ] 全部资源清理完成才进入 `COMPLETED`

### TASK-WEB-01 项目资产页面

- **Labels**: `M4`, `WEB`
- **Depends on**: `TASK-PROJECT-01`
- **Description**: 实现项目资产列表、编辑与启停页面（`/agent/project/index.vue`），字段/校验/权限与后端一致。
- **Validation**:
  ```bash
  cd frontend && pnpm run ts:check && pnpm run build:prod
  ```
  - [ ] 字段、校验、权限与后端一致

### TASK-WEB-02 任务列表与生命周期操作

- **Labels**: `M4`, `WEB`
- **Depends on**: `TASK-TASK-02`, `TASK-SCHED-05`
- **Description**: 实现任务列表、状态过滤与生命周期操作（暂停/恢复/取消/重置/重投/删除）。
- **Validation**:
  ```bash
  cd frontend && pnpm run ts:check && pnpm run build:prod
  ```
  - [ ] 生命周期操作完整，状态刷新正确

### TASK-WEB-03 任务文档编辑抽屉

- **Labels**: `M4`, `WEB`
- **Depends on**: `TASK-DOC-05`, `TASK-TASK-02`
- **Description**: 实现任务文档编辑抽屉（`TaskDocEditorDrawer.vue`）与版本冲突提示。
- **Validation**:
  ```bash
  cd frontend && pnpm run ts:check && pnpm run build:prod
  ```
  - [ ] 可编辑计划/验收/执行参数，版本冲突提示清晰

### TASK-WEB-04 Diff/日志/报告/分支查看

- **Labels**: `M4`, `WEB`
- **Depends on**: `TASK-EXEC-05`, `TASK-MERGE-03`
- **Description**: 实现 Diff、日志、测试报告与分支信息查看（`TaskDiffViewerDialog.vue`）。
- **Validation**:
  ```bash
  cd frontend && pnpm run ts:check && pnpm run build:prod
  ```
  - [ ] 长日志、空结果、失败结果均可阅读

### TASK-WEB-05 验收/打回/冲突处理与反馈

- **Labels**: `M4`, `WEB`
- **Depends on**: `TASK-TASK-04`, `TASK-NOTICE-04`, `TASK-MERGE-04`
- **Description**: 实现验收、打回、冲突处理与操作反馈，页面操作复用 API 幂等机制并刷新状态。
- **Validation**:
  ```bash
  cd frontend && pnpm run ts:check && pnpm run build:prod
  ```
  - [ ] 页面操作复用幂等机制，反馈与状态刷新正确

### TASK-WEB-06 任务结果读取接口（Diff/日志/报告/分支）

- **Labels**: `M4`, `WEB`
- **Depends on**: `TASK-WEB-04`, `TASK-EXEC-05`, `TASK-MERGE-03`
- **Description**: 补齐 `GET /agent/task/{id}/diff`，聚合 `agent_task`、`agent_task_project`、`agent_project` 并解析 `.ai/workpad_summary.json`，返回前端 `AgentTaskDiffResp` 所需的分支信息、Diff 统计、变更文件、执行日志与测试报告。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*Diff*'
  curl -s http://localhost:48080/admin-api/agent/task/{id}/diff
  ```
  - [ ] 返回字段与前端 `AgentTaskDiffResp` 一致；空 Diff / 日志 / 报告返回明确空值

---

## M5 安全与观测

### TASK-OPS-01 凭证引用与日志脱敏

- **Labels**: `M5`, `OPS`
- **Depends on**: `TASK-DATA-01`, `TASK-NOTICE-02`
- **Description**: 接入 Secret Manager 凭证引用与日志脱敏，Git/Webhook/命令参数/异常信息不泄露秘密。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*SecretRedactTest'
  ```
  - [ ] 凭证不入日志，脱敏覆盖 URL/Header/命令参数/异常

### TASK-OPS-02 路径/命令/仓库/权限安全策略

- **Labels**: `M5`, `OPS`
- **Depends on**: `TASK-WORK-04`, `TASK-EXEC-03`
- **Description**: 实现路径、命令、仓库与权限安全策略；非法路径/命令/无权限项目被拒绝。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*SecurityPolicyTest'
  ```
  - [ ] 非法路径、命令、无权限项目被拒绝

### TASK-OPS-03 traceId/结构化日志/指标

- **Labels**: `M5`, `OPS`
- **Depends on**: `TASK-SCHED-05`, `TASK-EXEC-02`
- **Description**: 补齐 `traceId`、结构化日志、耗时与失败指标，可按任务/Worker/外部调用定位一次执行。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*ObservabilityTest'
  ```
  - [ ] 可按任务/Worker/外部调用定位一次执行

### TASK-OPS-04 补偿任务与告警

- **Labels**: `M5`, `OPS`
- **Depends on**: `TASK-WORK-05`, `TASK-SCHED-04`, `TASK-NOTICE-02`
- **Description**: 实现残留工作区、过期租约与失败通知的补偿任务，可重复执行且有告警记录。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*CompensationTest'
  ```
  - [ ] 补偿任务可重复执行且有告警记录

---

## M6 测试与灰度

### TASK-QA-01 核心单元测试

- **Labels**: `M6`, `QA`
- **Depends on**: `TASK-TASK-02`, `TASK-DOC-02`, `TASK-OPS-02`
- **Description**: 状态机、文档解析、幂等与权限单元测试，覆盖正常、边界与非法输入。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test
  ```
  - [ ] 核心规则覆盖正常/边界/非法输入

### TASK-QA-02 MySQL/Redis/Testcontainers 集成测试

- **Labels**: `M6`, `QA`
- **Depends on**: `TASK-DATA-04`, `TASK-SCHED-04`
- **Description**: MySQL/Redis/Testcontainers 集成测试，锁、租约、代次与恢复行为可复现。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am verify -Dtest='*IntegrationTest'
  ```
  - [ ] 锁、租约、代次、恢复行为可复现

### TASK-QA-03 Worktree/假 Codex/超时/合并测试

- **Labels**: `M6`, `QA`
- **Depends on**: `TASK-WORK-05`, `TASK-EXEC-05`, `TASK-MERGE-03`
- **Description**: Worktree 生命周期、假 Codex、超时恢复与多仓合并测试，外部进程与 Git 场景可自动验证。
- **Validation**:
  ```bash
  cd backend && mvn -pl yudao-module-agent/yudao-module-agent-biz -am test -Dtest='*E2eTest,*WorktreeTest'
  ```
  - [ ] 外部进程与 Git 场景可自动验证

### TASK-QA-04 前端 API 与端到端测试

- **Labels**: `M6`, `QA`
- **Depends on**: `TASK-WEB-05`
- **Description**: 前端 API、关键交互与端到端测试，任务主流程可从页面完成。
- **Validation**:
  ```bash
  cd frontend && pnpm run ts:check && pnpm run build:prod
  ```
  - [ ] 任务主流程可从页面完成（含 Playwright 关键交互）

### TASK-QA-05 灰度与回滚演练

- **Labels**: `M6`, `QA`
- **Depends on**: `TASK-QA-01`~`TASK-QA-04`（全部）
- **Description**: 单 Worker、单租户、非生产仓库灰度运行 24 小时并演练回滚，确认无重复提交、租约泄漏、残留进程。
- **Validation**:
  - 24 小时灰度运行，监控无重复提交/租约泄漏/残留进程
  - 执行一次回滚演练并记录结果
  - [ ] 灰度 24 小时无重复提交、租约泄漏、残留进程

### TASK-QA-06 24 小时连续灰度观察与归档

- **Labels**: `M6`, `QA`
- **Depends on**: `TASK-QA-01`~`TASK-QA-05`
- **Description**: 跑满 `TASK-QA-05` 尚未完成的 24 小时单 Worker 灰度窗口，复核无重复提交、租约泄漏、残留进程与工作区，并回填 `docs/灰度与回滚演练.md`。
- **Validation**:
  ```bash
  pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/qa-gray-drill.ps1 -SoakHours 24 -IntervalSeconds 60
  ```
  - [ ] 连续 24 小时全项通过；观察项无异常；结论回填至灰度报告

### TASK-QA-07 前端全量类型检查与既有遗留复核

- **Labels**: `M6`, `QA`
- **Depends on**: `TASK-WEB-05`
- **Description**: 执行前端全量 `ts:check` 与 `build:prod`，确认 agent 模块与全量均无类型错误，清理或跟踪既有无关模块类型错误（LZC-70）。
- **Validation**:
  ```bash
  cd frontend && pnpm run ts:check && pnpm run build:prod
  ```
  - [ ] agent 模块类型检查 0 error，全量构建通过

---

## 汇总

| 里程碑 | 模块 | 子任务数 |
| --- | --- | --- |
| M1 | BASE, DATA, PROJECT | 12 |
| M2 | DOC, TASK, SCHED | 16 |
| M3 | WORK, EXEC | 10 |
| M4 | NOTICE, MERGE, WEB | 15 |
| M5 | OPS | 4 |
| M6 | QA | 7 |
| **合计** | | **64** |
