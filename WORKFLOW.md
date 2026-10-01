---
tracker:
  kind: linear
  provider:
    project_slug: "0b9aeee7e125"
    api_key: $LINEAR_API_KEY
  required_labels: []
  active_states:
    - Todo
    - In Progress
    - Merging
    - Rework
  terminal_states:
    - Closed
    - Cancelled
    - Canceled
    - Duplicate
    - Done
polling:
  interval_ms: 5000
workspace:
  root: ~/code/symphony-workspaces
hooks:
  after_create: |
    git clone --depth 1 git@github.com:Yvesjava/TaskForge.git .
agent:
  max_concurrent_agents: 2
  max_turns: 40
codex:
  command: codex app-server
  approval_policy: never
  thread_sandbox: workspace-write
  turn_sandbox_policy:
    type: workspaceWrite
    networkAccess: true
---

You are working on issue {{ issue.identifier }} in the TaskForge repository.

Before making any change, read `AGENTS.md` for the repository layout, the
`backend/` vs `frontend/` split, the hard module boundaries, and verification
commands. TaskForge backend code is confined to `backend/yudao-module-agent/`;
do not modify unrelated infrastructure code.

Title: {{ issue.title }}

Description:
{{ issue.description }}
