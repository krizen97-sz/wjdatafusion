# Apple HIG adaptation for RYNEW

Use this reference when the request names Apple, macOS, Apple HIG, premium or
quiet design, or when a RYNEW page has lost hierarchy through excessive accent
color, decoration, card layers, or visual noise.

The goal is not to imitate an Apple application. Translate durable Apple design
principles into the existing Vue 3, Element Plus, RuoYi, and RYNEW theme system.
The repository remains the source of truth.

Authoritative guidance:

- [Apple HIG: Color](https://developer.apple.com/design/human-interface-guidelines/color)
- [Apple HIG: Typography](https://developer.apple.com/design/human-interface-guidelines/typography)
- [Apple HIG: Layout](https://developer.apple.com/design/human-interface-guidelines/layout)
- [Apple HIG: Materials](https://developer.apple.com/design/human-interface-guidelines/materials)
- [Apple HIG: Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)

## Translation map

| Apple principle | RYNEW implementation | Do not do |
| --- | --- | --- |
| Semantic system colors | `--app-heading`, `--app-text`, `--app-muted`, `--surface-*`, `--el-*` | Hardcode Apple color values or create a page palette |
| Accent communicates interaction | Use primary color for links, current selection, primary actions, focus, and meaningful state | Color every title, value, version number, icon, and card blue |
| Type conveys hierarchy | Use the installed system stack, a small number of size/weight steps, and readable line height | Bundle SF Pro, use thin weights, or create hierarchy only with color |
| Alignment improves scanning | Align labels, values, controls, tables, and repeated rows to shared grid lines | Eyeball offsets, baseline-mix unrelated content, or use coordinate layout |
| Standard materials organize content | Use existing semantic surfaces and separators for content groups | Add Liquid Glass, blur, gradients, or glow to ordinary content cards |
| Functional layers may be distinct | Navigation, transient controls, and selected state may use a subtle elevated/control surface | Apply translucency across the entire content layer |
| Appearance is adaptive | Keep identical semantics in light/dark mode and use theme variables | Maintain a separate dark palette that recolors all headings with accent |

## Color hierarchy

Audit every use of the primary color and classify it before editing:

1. **Action or link** — accent is appropriate.
2. **Current selection or keyboard focus** — accent or a tinted selection surface
   is appropriate; keep text contrast sufficient.
3. **Status with established meaning** — use the existing health or Element Plus
   semantic mapping, never a decorative color.
4. **Static heading, value, metadata, or body copy** — use heading, text, or muted
   semantic tokens instead of accent.

Recommended RYNEW mapping:

- Page and detail titles: `var(--app-heading)`.
- Body content and important static values: `var(--app-text)` or
  `var(--app-heading)` according to hierarchy.
- Metadata, timestamps, labels, and explanations: `var(--app-muted)`.
- Separators and inactive outlines: `var(--surface-border)` or
  `var(--surface-border-strong)`.
- Hover without selection: `var(--surface-hover)`.
- Current selection: a small `color-mix()` of `--el-color-primary` with the
  current semantic surface, plus an accent border or text where needed.

Do not use accent merely because a string is a version number, total, heading,
or technical value. Static information is not an action.

## Typography

Use the repository's system stack. Where a scoped surface needs an explicit
stack, use:

```css
font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', 'PingFang SC',
  'Microsoft YaHei', sans-serif;
```

This requests an installed system face; it does not bundle Apple fonts.

- Prefer Regular/Medium/Semibold-style weights. Avoid thin text and avoid making
  every heading bold.
- Establish at most the levels the content actually needs: page title, section
  title, body, and metadata are usually sufficient.
- Use size, weight, line height, and spacing before color.
- Remove duplicate eyebrow/kicker labels such as “改动内容 / 详细说明” when one
  clear heading carries the meaning.
- Keep long Chinese titles readable and permit wrapping in the content pane;
  truncate only in navigation rows where the full detail is available elsewhere.

## Layout and spacing

- Preserve reading order: primary task near the top/leading edge, navigation on
  the leading side, detail content after the selected item.
- Use the existing 4/8px rhythm and align repeated elements to shared columns.
- Related label/value pairs must have equal row heights and true vertical
  centering; do not rely on `align-items: baseline` when values have different
  lengths or types.
- Use proximity before adding another card or border.
- At narrow widths or large text sizes, reduce columns or reflow groups rather
  than squeezing text or introducing hidden horizontal overflow.
- Visual order, DOM order, and keyboard order must remain consistent.

## Surfaces and materials

- Content areas use standard `--surface-*` backgrounds and semantic separators.
- A content group chooses one depth signal: usually a separator, occasionally a
  shadow for a true elevated layer, never both by habit.
- Remove decorative hero gradients, colored halos, glow shadows, and nested
  glass panels from administration and reading surfaces.
- A sidebar or transient toolbar may use a subtly distinct control surface only
  when it helps navigation. Do not call decorative blur “Apple style.”
- Keep radius values within the project's existing range; pills remain for tags,
  compact filters, and segmented choices.

## Element Plus mapping

Apple HIG changes visual decisions, not component ownership:

- Read-only label/value groups: `el-descriptions`.
- Small mutually exclusive choice: `el-segmented` or `el-radio-group`.
- Page content navigation: `el-tabs`.
- Hierarchical navigation: existing menu/tree/disclosure implementation.
- Current state and status: existing `el-tag`, `DictTag`, or module mapping.
- Actions: `el-button`; primary color only for the page's actual primary action.
- Loading, empty, dialog, drawer, table, pagination, uploads, and feedback:
  continue using existing project and Element Plus capabilities.

Never create AppleButton, GlassCard, MacSidebar, SFIcon, or similar wrapper
components solely to mimic appearance.

## Light and dark mode

- Use the same DOM, hierarchy, and state mapping in both modes.
- Verify that inactive headings and values remain neutral in dark mode.
- Search global styles for old `!important` overrides before adding a new scoped
  override; remove obsolete duplicate ownership when it is safe and exact.
- Dark mode may use an elevated semantic surface, but it must not turn every
  label or version value into the support accent.

## Required verification

- List the components and tokens reused before editing.
- Check the real route in light and dark mode.
- Confirm static titles/values are neutral and accent corresponds to interaction
  or state.
- Check alignment, long text, the relevant responsive breakpoint, keyboard
  focus, unnamed controls, and browser console output.
- Run the focused test, `npm run ui:guard`, and `npm run verify:frontend` when
  dependencies are available.
- Inspect global theme files for stale page-specific overrides.
- Preserve business logic, permissions, API contracts, routes, and data meaning.

In the handoff, name the Apple HIG principles translated into RYNEW and identify
anything intentionally not copied, especially Liquid Glass, proprietary fonts,
platform-specific navigation chrome, and Apple assets.
