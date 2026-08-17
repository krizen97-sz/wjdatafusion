# RYNEW Codex Parallel Development Rules

These rules apply to every Codex task in this repository.

## Mandatory preflight

Before reading broadly or editing files, run and report:

```bash
pwd
git rev-parse --show-toplevel
git branch --show-current
git rev-parse --short HEAD
git rev-parse --short origin/main
git status --short
git worktree list
```

- A coding task must run in a dedicated Git worktree created from the latest `origin/main`.
- One writable Codex task maps to one worktree and one feature branch.
- Never let two writable Codex tasks share the same physical directory.
- If `origin/main` is not an ancestor of `HEAD`, stop and synchronize the branch before editing.
- If the worktree contains changes outside the requested module, stop and move the task to a clean module worktree.

Use `tools/codex-worktree.sh create <module> <task-slug>` to create worktrees and
`tools/codex-worktree.sh verify <module>` before editing or committing.

Supported module names are:

- `auto-inspection`
- `site-fusion`
- `document-management`
- `ipam`

## Branch and checkout safety

- The primary checkout is an integration workspace. Do not develop features directly on `main`.
- Do not run `git switch`, `git checkout`, `git reset`, `git clean`, or broad restore commands in a dirty shared checkout.
- Do not create a worktree from a branch with uncommitted changes. Use clean `origin/main` as the base.
- Do not check out the same branch in multiple worktrees. Use Codex Handoff when the same task must move between Local and Worktree.
- Existing changes belong to their current task. Never discard, overwrite, or silently absorb them into another module commit.

## Commit boundaries

- Stage explicit paths. Never use `git add .` or `git add -A` in this repository.
- Review `git diff --cached --name-only` and `git diff --cached --check` before committing.
- A version commit contains one module's implementation, focused tests, version record, and its independent upgrade SQL when applicable.
- Generated output, delivery packages, Playwright captures, Graphify output, build directories, and local caches are not source changes.
- Shared files such as `releaseNotes.js`, `router/index.js`, root/full SQL files, POM files, and `package.json` require explicit review during integration.

## Merge and release

- Merge only verified commits into `main`; do not merge a dirty worktree.
- Build release artifacts from a clean detached worktree at the exact `main` commit or release tag.
- Record the commit hash and dirty-state check in release evidence.
- Follow `docs/CODEX_WORKTREES.md` for the full workflow.
