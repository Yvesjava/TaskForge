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

# 开启自动合入（Auto-Merge / Auto-Land）
merging:
  strategy: auto_merge_when_checks_pass
  delete_branch_after_merge: true

linear:
  # 跑完自验后跳过等待审核，直接推进
  target_state_on_success: "Done"

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

Before merging, keep the branch up to date with `origin/main`:
1. Run `git fetch origin main`, then `git rebase origin/main`.
2. If the rebase conflicts, inspect `git status` and resolve the unmerged paths.
   Prefer the change that satisfies the ticket's intent and preserves compatible
   upstream changes; do not blindly take `ours` or `theirs`.
3. After resolving, run `git add -A`, `git rebase --continue`, re-run the
   relevant checks, and confirm `git diff --check` is clean.
4. If the conflict cannot be resolved safely within the remaining turns, run
   `git rebase --abort`, set the Linear issue state to "Rework", and add a
   comment listing the conflicting files and why they could not be resolved.

When all ticket acceptance validations pass, the rebase is clean, and
`git diff --check` is clean:
1. Push the branch to origin.
2. Fast-forward merge the branch into origin/main directly.
3. Update the Linear issue state directly to "Done".
