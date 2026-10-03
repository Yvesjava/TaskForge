# TaskForge Agent Guide

## Project Overview

TaskForge is a document-driven AI research and development pipeline console. The root repository is the source of truth and is tracked on GitHub:

```text
git@github.com:Yvesjava/TaskForge.git
```

Read these documents before changing product behavior or architecture:

- `docs/产品文档.md`
- `docs/技术开发与架构设计文档.md`
- `docs/开发模块与子任务拆分.md`
- `docs/工程约定.md`
- `README.md`

The product and architecture documents are authoritative for task states, data models, API behavior, worker execution, and milestone scope. Update the relevant document when an implementation changes those contracts.

## Repository Layout

- `backend/`: Java 17, Spring Boot 3.x, Maven, based on the Yudao/RuoYi Vue Pro server.
- `frontend/`: Vue 3, TypeScript, Vite, Element Plus, based on the Yudao admin console.
- `docs/`: TaskForge product and architecture documentation.
- `docker-compose.yml`: local MySQL 8.4 and Redis 7 services.
- `.env.example`: root Docker and backend environment template.
- `frontend/.env.local.example`: frontend development environment template.

There are no nested Git repositories. Do not reintroduce `backend/.git` or `frontend/.git`; all source changes belong to the TaskForge root repository.

## Module Boundary (Critical)

TaskForge business code is confined to its own module and directory boundaries. Agents must honor these hard limits:

- Backend lives under `backend/`; **all new TaskForge backend code goes in `backend/yudao-module-agent/`**. Do not place TaskForge logic in existing infrastructure modules (`yudao-module-system`, `yudao-module-infra`, `yudao-framework`, `yudao-server`, etc.).
- Frontend lives under `frontend/`; API clients and types go in `frontend/src/api/`, pages/components under `frontend/src/views/` and `frontend/src/components/`.
- **Do not modify unrelated existing infrastructure code.** The shared `yudao-*` modules, framework, and server bootstrap are baseline. Any change to them must be explicitly justified and is never bundled into a feature task.
- Register new Maven modules in `backend/pom.xml` using the existing module pattern; never repurpose another module's build to host TaskForge code.

## Local Environment

Prerequisites:

- Java 17
- Maven 3.8 or newer
- Node.js 20.19 or newer
- pnpm 8.6 or newer
- Docker Desktop

Initialize local configuration from the repository root:

```powershell
Copy-Item .env.example .env
Copy-Item frontend/.env.local.example frontend/.env.local
docker compose up -d
```

Non-local builds (`build:dev` / `build:test` / `build:stage` / `build:prod`) load the committed templates `frontend/.env.dev` / `.env.test` / `.env.stage` / `.env.prod` (placeholder domains only, no secrets); see `docs/工程约定.md`.

The Compose stack exposes MySQL on `3306` and Redis on `6379`. The first MySQL initialization imports `backend/sql/mysql/ruoyi-vue-pro.sql` and `backend/sql/mysql/quartz.sql`. Existing Docker volumes are not reinitialized automatically.

Start the backend from `backend/`:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local,taskforge'
mvn -pl yudao-server -am -DskipTests package
java -jar yudao-server/target/yudao-server.jar --spring.profiles.active=local,taskforge
```

The backend listens on `http://localhost:48080`. Its TaskForge profile is `backend/yudao-server/src/main/resources/application-taskforge.yaml`.

Start the frontend in a second terminal from `frontend/`:

```powershell
pnpm install
pnpm dev
```

The frontend listens on `http://localhost:3000` and sends API requests to `http://localhost:48080/admin-api`.

## Development Conventions

### Backend

- Follow the existing Yudao module structure: controller, application/service, domain data object, mapper, and DTO/VO layers.
- TaskForge business code lives in `backend/yudao-module-agent/` (`yudao-module-agent-api` + `yudao-module-agent-biz`), already registered in the parent POM and `yudao-server`. Add further sub-modules with the existing Maven module pattern.
- Preserve tenant, audit, logical-delete, permission, and idempotency conventions from the surrounding modules.
- Implement state transitions through explicit transition checks and operation records. Do not update task status from arbitrary controllers or workers.
- Use parameterized queries and existing MyBatis/Yudao data-access helpers. Do not build SQL with string concatenation.
- Treat worker paths, subprocess commands, Git URLs, credentials, and webhook payloads as untrusted input. Validate paths and redact secrets in logs.

### Frontend

- Put API clients and types under `frontend/src/api/` and pages/components under the existing `frontend/src/views/` and `frontend/src/components/` conventions.
- Reuse the existing Element Plus, VueUse, and local component patterns before adding abstractions.
- Keep API base URLs in environment files; do not hard-code environment-specific hosts in source code.
- Preserve permission checks, route metadata, table/form conventions, loading states, empty states, and error handling used by neighboring pages.

### Documentation

- Product and architecture text is written in Chinese and should remain UTF-8.
- Keep examples executable and consistent with the actual ports, profiles, table names, and status values.
- Update `README.md` when a developer command or required environment variable changes.

### Code Format

- Repo-wide encoding, line endings, and indentation are defined by the root `.editorconfig` and `.gitattributes` (UTF-8, LF, no tabs); `frontend/.editorconfig` governs frontend files.
- Backend Java uses 4-space indentation and Yudao layer naming (`Controller` / `Service` / `ServiceImpl` / `Mapper` / `DO` / `VO` / `Convert`). There is no backend formatter plugin yet, so format consistency is enforced by review plus compilation.
- Frontend formatting is enforced by ESLint, Prettier, and Stylelint. Run `pnpm lint:eslint` / `pnpm lint:style` / `pnpm lint:format` (fix) from `frontend/`. `pnpm lint` is a mandatory gate and must pass with 0 errors on a clean checkout; lint only the files you changed (lint-staged) and never reformat unrelated modules.
- Keep the full convention set in `docs/工程约定.md`; do not restate divergent rules here or in `README.md`.

### Logging

- Use `ERROR` for failures needing human action, `WARN` for self-healing or degraded paths, `INFO` for state transitions, scheduling, merges, and notifications, and `DEBUG` only for temporary troubleshooting.
- Key events carry task number, execution generation, repository identifier, and before/after status; cross-module calls and error responses carry `TracerUtils.getTraceId()` (OpenTelemetry TraceId, possibly empty).
- Never log Git credentials, tokens, cookies, `Authorization` headers, private keys, passwords, or full environment variables; redact `git_url` and subprocess command arguments first.
- Worker subprocess stdout/stderr is captured to the task log; truncate around the configured limit (keep head and tail) before persisting to `execution_log`, and record the truncation marker with the original log path.

## Verification

Run focused checks for the area changed:

```powershell
# Root configuration
docker compose config

# Backend build
Set-Location backend
mvn -B clean test-compile
mvn -q -pl yudao-module-agent/yudao-module-agent-biz -am test-compile
mvn -pl yudao-server -am -DskipTests package

# Frontend build
Set-Location ..\frontend
pnpm install --frozen-lockfile
pnpm build:local
pnpm build:prod   # CI gatekeeper（--mode prod，加载 frontend/.env.prod）
```

`mvn -B clean test-compile` and `pnpm install --frozen-lockfile && pnpm run build:prod` are the CI gatekeeper commands (`.github/workflows/ci.yml`); keep them working. For service-level verification, confirm Docker health, `http://localhost:48080/v3/api-docs`, and `http://localhost:3000/`. Run broader tests when a change crosses module boundaries or changes shared contracts.

## Git and Security Rules

- Work only in the root TaskForge Git repository and keep `origin` pointed at GitHub TaskForge.
- Name feature branches `<type>/<ticket-id>-<short-slug>` in lowercase, for example `feat/lzc-9-engineering-conventions`; branch from the latest `origin/main` and merge `origin/main` back before handoff.
- Use Conventional Commit messages, for example `feat(agent): ...`, `fix(agent): ...`, or `docs: ...`.
- Never commit `.env`, `frontend/.env.local`, credentials, private keys, tokens, generated logs, `target/`, `node_modules/`, or `dist/` output. Use the checked-in example files instead.
- SQL fixtures and request examples must contain placeholders such as `YOUR_QINIU_ACCESS_KEY`, never real cloud credentials.
- Before committing, inspect `git diff --check`, `git status --short`, and the staged file list. Do not force-push or rewrite shared history unless explicitly requested.
- Keep changes scoped to the requested behavior. Do not overwrite unrelated user changes or perform destructive cleanup without explicit authorization.
