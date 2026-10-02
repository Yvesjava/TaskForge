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

## Automatic merge and status transition (no human review)

You are authorized to complete this issue end-to-end without waiting for a
human. Do not stop in `Merging` or wait for approval.

### Linear state updates

Use the `linear_graphql` tool for every Linear read and mutation. Resolve the
issue to its internal `id`, fetch its team workflow states, and use the exact
`stateId` for the target state. Never hard-code state IDs or assume a state name
has a particular ID.

Example transition:

```graphql
mutation MoveIssueToState($id: String!, $stateId: String!) {
  issueUpdate(id: $id, input: { stateId: $stateId }) {
    success
    issue {
      id
      identifier
      state { id name }
    }
  }
}
```

### Merge gate

Before merging, keep the current branch up to date with `origin/main`:

1. Run `git fetch origin main`, then `git rebase origin/main`.
2. If the rebase conflicts, inspect `git status` and resolve the unmerged paths.
   Prefer the change that satisfies the ticket's intent and preserves compatible
   upstream changes; do not blindly take `ours` or `theirs`.
3. After resolving, run `git add -A`, `git rebase --continue`, re-run the
   relevant checks, and confirm `git diff --check` is clean.
4. If the conflict cannot be resolved safely within the remaining turns, run
   `git rebase --abort`, move the issue to `Rework` via `linear_graphql`, and
   comment the conflicting files and why they could not be resolved.

### Land

When all ticket acceptance validations pass, the rebase is clean, and
`git diff --check` is clean:

1. Push the working branch:
   ```sh
   git push -u origin HEAD
   ```
2. Fast-forward `origin/main` directly from the current branch:
   ```sh
   git push origin HEAD:main
   ```
3. If that push is rejected because `main` moved, fetch and rebase again, then
   retry step 2.
4. If it is rejected by branch protection or permissions, create a pull request
   and enable auto-merge as a fallback:
   ```sh
   gh pr create --fill
   gh pr merge --auto --merge
   ```
   Use `--squash` instead of `--merge` when the repository requires it. Poll
   `gh pr view --json state,mergeStateStatus` until the PR is merged; fix and
   retry if it becomes blocked or conflicted.
5. After `main` is updated, move the issue to `Done` using `linear_graphql`.
