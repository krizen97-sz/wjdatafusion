# Interaction and motion governance for RYNEW

Use this reference for global interaction polish, hover/press feedback, ripple,
loading-success morphs, animated Tabs, LayoutGroup-style continuity, Toggle Group
or Segmented controls, Stepper flows, and audio/model processing stages.

The goal is to absorb the useful behavior of other systems without importing
their component ownership or visual language. RYNEW remains a Vue 3 + Element
Plus desktop operations product. Motion supports scanability, confidence, and
continuity; it does not turn the admin interface into a showcase.

## Selected project direction: Precision Rail

Precision Rail is the default RYNEW interaction language selected for the current
frontend. It combines a restrained Apple HIG-inspired hierarchy with a visible,
project-native continuity cue:

- selection travels through the native Tabs active bar or Segmented selected
  surface. Tabs use one short accent rail; Segmented controls never stack an
  underline beneath a selected background;
- ordinary buttons use semantic surface change and a 1px press acknowledgment;
- workspace entrances can nudge only their directional icon by up to 2px;
- explicit submit/execute actions may use the bounded `v-motion-ripple` state
  layer and a real-result success morph;
- related content views may use a keyed 4-8px directional enter; dense data stays
  stable while the active control explains the change;
- loading uses the existing platform mark plus a restrained progress rail, never
  an upstream circular spinner or fabricated percentage;
- a low-frequency live pulse is allowed only while the business object is truly
  active, recording, streaming, or monitoring.

The old global icon pop, scale, colored drop-shadow/glow, bounce, and decorative
loop vocabulary is retired. A single interaction normally receives one primary
effect and at most one supporting effect.

### Context adaptation matrix

| Local context | Keep | Optional enhancement | Remove or avoid |
| --- | --- | --- | --- |
| Dense CRUD table | color, focus, 1px press | none | lift on link actions, ripple, content slide |
| Application-level Tabs | native active bar, icon/text state | short related-content handoff | card-style tabs, duplicate underline, scale |
| Compact result filter | native selected surface, semantic icon | neutral edge and light elevation | accent underline, separate colored pill per state |
| Workspace entrance | normal button semantics | 2px directional icon nudge | permanent glow or whole-button travel |
| Submit/execute action | real loading/error | bounded ripple, real success morph | timer success, repeated pulse, fake progress |
| Audio/model/import flow | real `el-steps` state | keyed phase content, live-state pulse | invented phases, percentages, autoplay |

Implementation ownership lives in `src/assets/styles/motion.scss`, shared motion
tokens, and the `motion-ripple` directive. Pages opt into the smallest applicable
capability and retain their own layout, route, filter, and request contracts.

## Authoritative references

- [Element Plus Button](https://element-plus.org/en-US/component/button.html)
- [Element Plus Tabs](https://element-plus.org/en-US/component/tabs.html)
- [Element Plus Segmented](https://element-plus.org/en-US/component/segmented.html)
- [Element Plus Steps](https://element-plus.org/en-US/component/steps.html)
- [Motion for Vue gestures](https://motion.dev/docs/vue-gestures)
- [Motion layout animation and LayoutGroup](https://motion.dev/docs/react-layout-animations)
- [Material Web Ripple](https://material-web.dev/components/ripple/)
- [Radix Toggle Group](https://www.radix-ui.com/primitives/docs/components/toggle-group)
- [Vue TransitionGroup](https://vuejs.org/guide/built-ins/transition-group.html)

These links explain the source ideas. They are not dependency recommendations.

## Translation map

| External idea | RYNEW owner | Native implementation | Boundary |
| --- | --- | --- | --- |
| Element Plus + Motion Hover/Press | Ordinary admin action | Existing `el-button`; CSS hover/active/focus-visible using semantic variables | No Motion dependency for color, 1px translation, or icon nudge |
| Interactive Hover Button | Application or workspace entrance | Existing `el-button`, router link, or current entry component with an explicit opt-in class | Directional icon movement max 2px; keep the text and destination clear |
| Ripple + Loading/Success Morph | Submit or execute action | `el-button` loading contract, platform loading mark, explicit request state, optional bounded state layer | Ripple is opt-in; success requires a confirmed business result and accessible text |
| Animated Tabs / LayoutGroup | Application-level content navigation | `el-tabs`, custom label slot, native active bar, route/query persistence, bounded Vue transition for content | Do not replace Tabs with divs or install a React-style layout system |
| Toggle Group / Segmented Control | Business/technical view or compact filter | `el-segmented` with its default slot and built-in sliding selected layer | One selected value unless the business explicitly needs multi-select |
| Stepper + Animated Content | Audio, model, import, analysis, or other true linear process | `el-steps` plus keyed Vue `Transition`; status comes from the business process | Do not fabricate phases, progress, or success with timers |

## Motion thesis

Before implementation, state four things:

1. **Continuity** — what relationship should remain understandable after change.
2. **Feedback** — which action needs immediate acknowledgment.
3. **Progress** — which real business state must remain visible while work runs.
4. **Budget** — how often the interaction occurs and what the target device can afford.

Operate-mode interfaces use motion sparingly. A routine switch should finish
before it feels like latency. One meaningful moving indicator is better than
simultaneously animating background, shadow, scale, icon, and content.

## Timing and easing floor

Use a small shared vocabulary instead of page-specific durations:

| Token intent | Range | Typical use |
| --- | --- | --- |
| Immediate | 100-140ms | press acknowledgment, focus or icon color |
| Fast | 140-180ms | hover, small content enter, error recovery |
| Standard | 180-240ms | Tabs indicator, Segmented thumb, selected surface |
| Deliberate | 240-320ms | Drawer-local layout continuity or Stepper phase content |

- Standard arrival easing: `cubic-bezier(0.16, 1, 0.3, 1)`.
- Compact state handoff: `cubic-bezier(0.2, 0.8, 0.2, 1)`.
- Exit is no slower than entrance.
- Do not use bounce, elastic overshoot, spin, or more than 8px displacement for
  routine management controls.

When code introduces global tokens, use semantic names such as
`--motion-duration-fast`, `--motion-duration-base`, `--motion-ease-out`, and
`--motion-ease-standard`; do not encode a page or component name in the token.

## Ordinary RuoYi buttons

Keep Element Plus as the component and state owner.

- Hover: adjust semantic background/border/text and optionally translate a
  directional icon by at most 1-2px. Apply only inside `@media (hover: hover)`.
- Press: acknowledge with a 1px downward translation or a very small opacity/
  surface change; never combine a large scale reduction with layout movement.
- Focus: retain a visible `:focus-visible` outline independent of hover.
- Disabled/loading: no hover lift, ripple, or press transform.
- Link/text buttons: usually use color and underline/surface feedback only; do
  not make table-row actions jump.

Do not globally add `overflow: hidden` merely to support a ripple; it can clip
focus rings, badges, dropdown anchors, and positioned content.

## Application entry actions

An entry action opens a workspace, cockpit, editor, or distinct route. It can
carry slightly stronger directional feedback than a CRUD button:

- retain an explicit text label and destination-oriented icon;
- use a stable semantic surface and one focus indicator;
- on hover-capable pointers, move only the directional icon up to 2px;
- on press, return the icon and control to the baseline quickly;
- never use a permanent glow, floating loop, hero gradient, or large scale lift.

This treatment is opt-in. A normal query, reset, cancel, row action, or dialog
button is not an application entry action.

## Submit and execute state machine

Model the control as an explicit state machine:

```text
idle -> pressed -> loading -> success
                         \-> error -> idle/retry
```

- `idle`: verb and icon describe the action.
- `pressed`: immediate pointer/keyboard acknowledgment.
- `loading`: bind real request/task state; preserve width and use the platform
  loading mark already owned by Element Plus styling.
- `success`: show only after confirmed success. A short check/label morph may be
  used when it prevents a duplicate action or confirms completion in context.
- `error`: restore the actionable label and show the existing message/error
  mechanism; never leave the control visually successful.

Success morph requirements:

- retain accessible text or an `aria-live` status;
- do not infer success from a timeout, animation end, or HTTP transport alone;
- do not hide a required next step behind a temporary icon;
- skip the morph for destructive actions, navigation-only actions, and operations
  whose completion happens asynchronously elsewhere.

## Ripple boundary

Material ripple is a state layer, not a universal decoration.

Use a bounded ripple/state layer only when all are true:

1. the action is an explicit, high-confidence submit or execute action;
2. pointer location feedback materially helps;
3. the control has a stable containing box and cleanup is deterministic;
4. loading, disabled, keyboard, reduced-motion, and repeated-click states remain correct.

Prefer a simple pressed surface when those conditions are not met. Never add the
Material Web package to obtain ripple, never place ripple on every `el-button`,
and never use it on table link actions or compact segmented items.

## Application Tabs

Use `el-tabs` for related content views that remain part of one application task.

- Use the label slot for an existing Element Plus or local Keyline icon plus text.
- Reuse the native active-bar geometry; style it through semantic tokens rather
  than drawing a second absolutely positioned indicator.
- Keep active state visible through indicator, text/icon change, and focus—not
  color alone.
- Persist route-driven views in route params/query exactly as the current module does.
- When content motion is valuable, use a keyed bounded enter transition of
  opacity plus 4-8px translation. Avoid `out-in` blank gaps on dense workspaces.
- Rapid repeated switching must interrupt cleanly and end on the actual selected view.

LayoutGroup describes shared-layout continuity. In this Vue project, first use
the active bar/thumb already owned by Element Plus. Use FLIP or View Transition
techniques only when separate components truly need coordinated geometry; do not
use the name as justification for a dependency.

## Business and technical view switches

Use `el-segmented` for a small mutually exclusive mode or filter such as business
view/technical view, all/abnormal/normal, or routine/frequent.

- Use the default slot to render an existing icon and label from the option item.
- Keep the native radio-group keyboard semantics and sliding selected layer.
- Use the selected surface for current choice; semantic status icon color can
  remain meaningful, but never turn every segment into a different colored button.
- When the selected surface already changes background, do not add a lower active
  rail. Use the moving surface, a neutral one-pixel edge, light elevation, and
  text/icon state so the control remains vertically balanced.
- Use `block` only when equal-width distribution helps the local layout.
- Keep labels short. At narrow widths, wrap the toolbar or allow an intentional
  local overflow strategy instead of shrinking targets below a usable size.

## Stepper and animated phase content

Use `el-steps` only when users need to understand a real ordered process, for
example upload -> decode -> ASR -> model -> synthesis -> complete.

- Drive `active`, finish status, error status, labels, and timestamps from real
  process data.
- Pair the selected phase with one keyed content region. A Vue `Transition`
  may fade/translate the phase detail; `TransitionGroup` is for inserted, removed,
  or reordered phase/result lists, not a replacement for Tabs.
- Keep logs, duration, retry, cancel, and error evidence accessible without waiting
  for an animation.
- Do not autoplay audio, animate fake progress, or mark a model phase complete
  because the front-end timer elapsed.

## Accessibility and reduced motion

- Preserve native buttons, radio inputs, Tabs, and Steps semantics.
- Verify keyboard focus order and `Enter` / `Space` activation.
- Hover styles must not become sticky on touch; use pointer/hover media queries.
- Selection and status require text, icon/shape, or indicator in addition to color.
- Under `prefers-reduced-motion: reduce`, remove displacement, ripple expansion,
  morph choreography, and nonessential loops. Keep immediate opacity/color/state
  changes and the final selected/loading/success/error state.

## Performance and ownership

- Prefer transform, opacity, and semantic color/background transitions.
- Avoid routine animation of width, height, top, left, margin, expensive blur,
  or large shadows. Use the Element Plus indicator transform or a bounded FLIP
  calculation when layout continuity truly requires it.
- Apply `will-change` only during a known animation, not permanently.
- Global CSS may define shared motion tokens and conservative `el-button` state
  feedback. Application-entry, ripple, success-morph, animated-tabs, and process
  treatments remain explicit opt-in classes/components with documented ownership.
- A page-scoped request does not authorize a global Element Plus rewrite.

## Verification matrix

| Surface | Required checks |
| --- | --- |
| Button | mouse hover, touch/no-hover, keyboard focus, press cancel, disabled, loading |
| Submit/execute | duplicate prevention, actual success, error recovery, stable width, screen-reader status |
| Tabs | click, keyboard, rapid switching, route refresh/back-forward, long label, active indicator |
| Segmented | radio-group keyboard behavior, selected thumb, status semantics, toolbar wrapping |
| Stepper | real phase order, error/retry/cancel, long labels, dynamic insert/remove when supported |
| Motion | reduced motion, light/dark mode, console, target desktop viewport, no layout overflow |

Run the focused tests, `npm run ui:guard`, the manual Impeccable detector over
changed UI targets, and `npm run verify:frontend` when runtime code changes.
