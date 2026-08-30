---
name: ruoyi-element-plus-ui
description: Use this project skill for every RYNEW Vue frontend task involving RuoYi, Vue 3, Element Plus, page layout, forms, queries, tables, pagination, toolbars, dialogs, drawers, buttons, Tabs, Switches, status display, tags, icons, loading/empty/error states, UI refactors or reviews, and implementation from screenshots, prototypes, mockups, design files, or Apple HIG requests. Trigger even when the user only asks to add or polish one frontend page or component. It enforces repository-first component reuse, Apple HIG-inspired semantic hierarchy adapted to RYNEW, prototype-to-project adaptation, UI Guard validation, and consistency over novelty; do not use it for backend-only, database-only, deployment-only, or the legacy Vue 2 UI unless that scope is explicit.
---

# RuoYi + Element Plus UI governance

Use this workflow for `RuoYi-Vue3-master`. The repository is the source of truth.
The legacy `WDF100.0/wjdatafusion-ui` uses Vue 2 and Element UI; do not migrate or
mix it into Vue 3 work without an explicit request.

## Governing principles

- The current repository is the source of truth.
- Prefer consistency over novelty.
- Prefer semantic reuse over pixel reproduction.
- Do not recreate existing components.
- Adapt external prototypes to the current design system.
- Never mechanically translate every visual element into custom DOM and CSS.
- Use Apple HIG as a hierarchy and restraint discipline, not as permission to copy
  iOS/macOS chrome or replace Element Plus.
- Preserve business flow, permissions, API contracts, and current module behavior.
- Continue with the most conservative consistent choice when only minor design
  detail is missing; ask only when a conflict can change business correctness or
  materially expand scope.

## Before coding

1. Run the repository and worktree preflight required by `AGENTS.md`.
2. Read:
   - `docs/frontend/DESIGN.md`
   - `docs/frontend/COMPONENT-CATALOG.md`
   - `docs/frontend/REFERENCE-PAGES.md`
3. For Apple, macOS, HIG, premium, quiet, overly blue, visual hierarchy, material,
   or typography requests, read
   [references/apple-hig-adaptation.md](references/apple-hig-adaptation.md).
4. Check `RuoYi-Vue3-master/package.json`; never assume or upgrade a dependency.
5. Inspect `RuoYi-Vue3-master/src/components` and the current module.
6. Find at least two similar pages, including one from `REFERENCE-PAGES.md` when
   its semantics match.
7. Search existing uses of every planned Element Plus or project component.
8. Write the component reuse plan below, then proceed.

| Page need | Existing implementation | Chosen component | Reference file | Custom work needed |
| --- | --- | --- | --- | --- |

Search in this order: current module, shared components, local RuoYi patterns,
installed Element Plus, existing icons and variables, a justified shared component,
then page-local custom code as the final option.

## Apple HIG adaptation floor

These rules apply to every design refinement; the reference above supplies the
full mapping when Apple/HIG styling is explicit:

- Let semantic neutrals carry static headings, values, body text, surfaces, and
  separators. Reserve the platform accent for links, current selection, primary
  actions, focus, and meaningful status.
- Build hierarchy with reading order, alignment, size, weight, line height, and
  spacing before adding color, borders, shadows, gradients, or blur.
- Use the installed system-font stack; do not bundle or imitate SF Pro.
- Keep content on standard theme surfaces. Glass, translucency, and blur belong
  only to a functional navigation/control layer and only when the existing app
  structure supports them.
- Light and dark mode must preserve the same semantic hierarchy. Do not add dark
  overrides that recolor every heading or version value with the accent.
- Apple HIG never overrides the repository, Element Plus behavior, accessibility,
  permissions, or business flow.

## When a screenshot or prototype is provided

Read `docs/frontend/PROTOTYPE-ADAPTATION.md` and reason from product intent before
visual geometry:

1. Identify user, task, primary information, primary action, states, and failure paths.
2. Extract semantic regions and reading order.
3. Classify components by behavior, not appearance.
4. Search current capabilities and two similar pages.
5. Produce this table before editing:

| Prototype region | Product intent | Prototype expression | Project mapping | Reference page | Adaptation |
| --- | --- | --- | --- | --- | --- |

Preserve information architecture, workflow, operation hierarchy, content grouping,
important states, and responsive relationships. Adapt typography, colors, radii,
shadows, spacing, icons, buttons, Tabs, tables, forms, dialogs, and motion to the
current project.

## Component decisions

- Dictionary status: `DictTag`.
- Static fixed status: `el-tag` with a single module mapping.
- Editable boolean: `el-switch`, value types aligned with the API, confirmation
  and rollback when risky.
- Count or reminder: `el-badge`.
- Page-level result: `el-result`.
- Empty data: `el-empty`.
- Loading: `v-loading`, or `el-skeleton` only when structural placeholder value is clear.
- Query/list: RuoYi query form + `RightToolbar` + `el-table` + `Pagination`.
- Simple edit: `el-dialog + el-form`; contextual long detail: `el-drawer`;
  read-only fields: `el-descriptions`.
- Feedback: existing `$modal` / Element Plus message, notification, and confirm APIs.
- Icons: `@element-plus/icons-vue`, `SvgIcon`, `IconSelect`, and the existing local
  catalog only.

Do not build equivalents for Tag, Badge, Switch, Loading, Empty, Result, Dialog,
Drawer, Table, Pagination, Tabs, uploads, preview, editor, dictionary, or permission
capabilities. Do not introduce a second UI framework or icon library.

## Implementation boundaries

- Do not write inline SVG in business pages, draw routine icons with CSS, or use
  Emoji, Unicode symbols, `data:image/svg+xml`, screenshots, or Canvas as ordinary UI.
- Do not use large absolute-positioned layouts to reproduce management pages.
- Do not hardcode a new palette, spacing scale, radius scale, typography system,
  or shadow vocabulary inside one page.
- Do not paint every title, numeric value, badge, icon, and selected state with
  the same primary color; static information is not an action.
- Do not add hero gradients, decorative glass, glow halos, or card-on-card depth
  merely to make an admin page feel "Apple-like".
- Do not download, bundle, or claim Apple's proprietary system fonts or assets.
- Do not globally restyle Element Plus from a page-scoped task.
- Do not copy complex module visuals into ordinary CRUD pages.
- Do not broaden a touched-page cleanup into historical mass refactoring.

For a real chart, map, topology, spreadsheet, or workflow exception, read
`docs/frontend/UI-EXCEPTIONS.md`, keep standard controls on Element Plus, and add
the smallest auditable allowlist entry. Never treat an old deviation as approval.

## Verification

1. Run `npm run ui:guard` from `RuoYi-Vue3-master`.
2. Run the smallest relevant existing tests while iterating.
3. Run `npm run verify:frontend` before a full frontend handoff when the installed
   dependencies are available.
4. If lint, typecheck, or a test command does not exist, report that fact; never
   fabricate a result.
5. Inspect `git diff` for business logic, dependency, icon-system, global-theme,
   and unrelated-page changes.
6. For visual changes, inspect both light and dark modes and confirm that static
   headings remain neutral while accent use corresponds to interaction or state.
7. Check alignment, 200% zoom/long text where practical, keyboard focus order,
   and browser console output on the real local route.

## Handoff format

Report:

1. component reuse plan and prototype adaptation table, when applicable;
2. reused global, module, RuoYi, Element Plus, icon, and token capabilities;
3. intentional custom work and why existing capabilities were insufficient;
4. UI exception IDs and Guard exceptions, if any;
5. validation commands and exact results;
6. visual or business adaptations, including which Apple HIG principles were
   translated rather than copied;
7. assumptions and remaining risks.
