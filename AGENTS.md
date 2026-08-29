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

- `platform-ui`
- `auto-inspection`
- `site-fusion`
- `document-management`
- `knowledge-center`
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

## Frontend design governance

These rules govern the current Vue 3 frontend in `RuoYi-Vue3-master`. The legacy
Vue 2 / Element UI application in `WDF100.0/wjdatafusion-ui` must not be migrated
or mixed with Element Plus as a side effect of an ordinary frontend task.

### Reuse order

Before creating UI code, search in this order:

1. The current feature module.
2. Shared components under `RuoYi-Vue3-master/src/components`.
3. Existing local RuoYi page and component patterns.
4. Components supported by the installed Element Plus version.
5. Existing icons, CSS variables, theme styles, and utilities.
6. A new shared component only after repeated cross-page need is demonstrated.
7. Page-local custom UI only as the final option.

Do not create a component until this search is complete.

### Required pre-coding review

Before changing a Vue page or frontend component:

1. Read `docs/frontend/DESIGN.md`.
2. Read `docs/frontend/COMPONENT-CATALOG.md`.
3. Read `docs/frontend/REFERENCE-PAGES.md`.
4. Check `RuoYi-Vue3-master/package.json`; do not assume a dependency exists.
5. Check `RuoYi-Vue3-master/src/components`.
6. Find at least two similar pages.
7. Find existing uses of every planned core component.
8. Write a component reuse plan, then proceed without waiting on routine design choices.

Use this minimum plan:

| Page need | Existing implementation | Chosen component | Reference file | Custom work needed |
| --- | --- | --- | --- | --- |

When a screenshot or prototype is supplied, also read
`docs/frontend/PROTOTYPE-ADAPTATION.md` and produce its prototype adaptation
table before coding. Preserve the business goal, information architecture,
interaction hierarchy, and states; adapt visual expression to this repository.

### Prohibited frontend shortcuts

- Do not write inline SVG in business pages, draw routine icons with CSS, or use
  Emoji, Unicode symbols, `data:image/svg+xml`, screenshots, or sliced prototypes
  as interactive UI.
- Do not introduce a second UI framework, icon library, or design system.
- Do not recreate existing Tag, Badge, Switch, Loading, Empty, Result, Dialog,
  Drawer, Table, Pagination, Tabs, upload, preview, editor, dictionary, or
  permission capabilities.
- Do not use large absolute-positioned layouts or Canvas to reproduce ordinary
  management screens.
- Do not hardcode colors, radii, shadows, typography, or spacing when existing
  Element Plus or project variables express the same role.
- Do not restyle global buttons, tabs, forms, or tables from a single page.
- Do not perform a broad visual redesign unless the user explicitly requests it.

Use `docs/frontend/UI-EXCEPTIONS.md` and
`RuoYi-Vue3-master/scripts/ui-guard-allowlist.json` for genuine chart, map,
topology, workflow, or other reviewed exceptions. Existing historical code is
not precedent for new deviations.

### Verification and reporting

Run `npm run ui:guard` for every frontend change. Run the smallest relevant
tests during implementation and `npm run verify:frontend` before a full frontend
handoff when dependencies are available. Report reused components, deliberate
deviations, validation results, and any exception entry used. Do not claim lint,
typecheck, tests, or builds that the project does not provide or that were not run.
