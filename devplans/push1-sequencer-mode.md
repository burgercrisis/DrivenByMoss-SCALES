# Push 1 – Sequencer Mode (Ratchets & Polymeter Lanes)

## 1. Goal & Scope

- **Goal**
  - Design a Push 1–friendly step-sequencer mode that makes **advanced rhythm tools** (ratchets / step repeats and polymeter lanes) **playable in real time**.
  - Prioritise **performance ergonomics** and **clarity on a text display** over exposing every possible parameter.

- **Scope (initial)**
  - A sequencer mode on the 8×8 pad matrix with:
    - Per-step **ratchet / repeat factors** (e.g. 1×–8×) for fills and motion.
    - Per-lane **polymeter lengths** (e.g. lanes at 5, 7, 9 steps against a 16-step grid).
  - Runs inside Bitwig clips / transport, not a standalone clock.

- **Out of scope (for now)**
  - Conditional / probability trigs.
  - Per-step micro-timing nudge UI.
  - Full song/pattern chaining UI.
  - Deep parameter locks beyond what the existing infrastructure already supports.

---

## 2. High-Level Sequencing Model (Assumptions)

- **Timeline & resolution**
  - Base grid is a fixed **step resolution** (e.g. 1/16 notes) tied to the host.
  - Ratchets subdivide this base step; we do **not** change global clip resolution for v1.

- **Lanes & steps**
  - A **lane** is a horizontal row on the pad matrix (e.g. one pitch, drum sound, or note-row).
  - A **step** is a time slot within a lane, indexed from 1..N.
  - Each step can be **active/inactive** and may carry metadata (velocity, ratchet amount, etc.).

- **Pattern vs lane length**
  - There is a **global pattern length** in steps (e.g. 16) used for the grid and clip.
  - Each lane may define its own **lane length ≤ pattern length** to enable polymeter.

- **Playback model (initial)**
  - Sequencer progresses in lockstep with host transport.
  - For v1, tempo and swing are taken from the host only; the sequencer does not add its own tempo layer.

---

## 3. Ratchets / Step Repeats (Fills & Motion)

### 3.1 Behaviour & Musical Intent

- **Ratchets** repeat a step multiple times within its original duration.
- Typical factors: **1× (off) up to 8×**.
- Use cases:
  - **Fills** at the end of a bar.
  - **Motion** on otherwise static patterns (e.g. adding rolls to hats or percussion).
  - **Energy ramps** by increasing ratchet density on selected steps.

### 3.2 Functional Requirements

- **Per-step ratchet factor**
  - Each step stores an integer **ratchetFactor ∈ {1,2,3,4,5,6,7,8}**, where `1` means no ratchet.
  - Later extensions could allow asymmetric or patterned ratchets, but v1 keeps to simple integer factors.

- **Timing**
  - If base step = 1/16 note and `ratchetFactor = N`, we schedule **N sub-steps per 1/16**.
  - Sub-steps are evenly spaced within the host step duration.
  - If the step is near the clip end, any sub-steps that fall outside the clip are **clipped at the clip boundary** (they do not wrap or play past the end; see decision in Section 7.2).

- **Gate / length behaviour**
  - v1: all ratchet hits share a **fixed gate length fraction** of the sub-step (e.g. 50–75%).
  - Later: consider per-step **ratchet gate** or accent patterns.

- **Velocity / accent**
  - v1: all ratchet hits share the **same velocity** as the underlying step.
  - Later: optional patterns (e.g. first hit louder, decaying rolls, alternation).

### 3.3 Push 1 UX Design (Ratchets)

- **Base interaction principle**
  - Ratchet amounts should be **fast to toggle** while playing but **hard to hit by accident**.
  - Re-use an existing modifier (e.g. `SHIFT` or a performance button) + pad to change ratchet for that step.

- **Per-step editing gesture (candidate)**
  - **Gesture idea A (cycle per pad)**
    - While in sequencer mode, hold a **ratchet modifier** (e.g. SHIFT or Repeat) and **tap a step pad**.
    - Each tap **cycles ratchetFactor** for that step:
      - `1× → 2× → 3× → 4× → 5× → 6× → 7× → 8× → 1× → ...`.
    - Pros: very quick, no extra controls required.
    - Cons: limited visibility of which factor is currently active without clear feedback.

  - **Gesture idea B (direct selection)**
    - Use a small set of **encoder positions or dedicated pads** to directly set 1×/2×/3×/4× for the **selected step(s)**.
    - Pros: clearer mapping; easier to support more than 4 values later.
    - Cons: slightly slower in performance.

- **Multi-step editing**
  - Allow selecting **a range of steps** (e.g. by pressing and dragging across pads) and then applying a ratchet factor to all of them at once.
  - Useful for quickly creating patterns like “every other hat is ratcheted at 2×”.

- **Visual feedback on pads** (conceptual)
  - Distinguish ratcheted vs non-ratcheted steps using **pad colour, brightness, or blink rate**.
  - Example mapping (conceptual only, to be validated against existing colour use):
    - `1×`: normal step colour.
    - `2×`: slightly brighter or alternative colour.
    - `3×/4×`: stronger colour change or subtle blink.

- **Display feedback**
  - When a step is focused or edited, show something like:
    - `STEP 09  RAT: 3x  VEL: 96`.
  - Consider a compact **ratchet legend row**:
    - Short text summarising the full range (e.g. `RATS 1–8x`) plus a per-step detail line, since listing all eight factors explicitly may not fit on a Push 1 row.

### 3.4 Data Model & Integration

- **Per-step state**
  - Extend step representation with `ratchetFactor` and ensure it:
    - Is **stored per step per lane**.
    - Is persisted with the pattern/clip so reloads are stable.

- **Engine behaviour**
  - At playback time, the sequencer schedules **`ratchetFactor` sub-events** per active step.
  - Implementation detail (to be decided later):
    - Emit multiple notes toward the host.
    - Or drive an internal scheduler that gates a held note.

- **Defaults & clearing**
  - New steps default to `ratchetFactor = 1`.
  - Clearing a step resets ratchet factor to 1.
  - Provide a **“reset ratchets in selection”** gesture (e.g. modifier + clear).

### 3.5 Edge Cases & Constraints

- **Clip boundary behaviour**
  - **Decision – Clip at end**: ratchet sub-steps are **clipped** at the clip end; they do not wrap to the clip start and are not scheduled beyond the clip boundary.
  - Alternatives considered (not chosen for v1):
    - **Wrap** to the clip start for more experimental looping.
    - **Suppress** any sub-steps that would cross the boundary.

- **Interaction with swing / groove**
  - v1: ratchet timing simply follows the host’s swing/groove where applicable.
  - Later: explore per-lane or per-step swing.

- **Max ratchet factor**
  - v1 cap at **8×**, trading some UI simplicity for more creative options.
  - Further extension beyond 8× is not planned unless CPU/timing and UX remain acceptable.

---

## 4. Polymeter Lanes (Advanced Rhythmic Lanes)

### 4.1 Behaviour & Musical Intent

- Each lane has its **own length in steps**, independent of other lanes.
  - Example: kick lane 16 steps, snare lane 12, hat lane 5, perc lane 7.
- Lanes loop inside the same clip, **phasing** against each other to create evolving patterns.
- All lanes share the same **base step duration**; only their **cycle length** differs.

### 4.2 Functional Requirements

- **Per-lane length**
  - Each lane `L` stores `laneLength[L]` such that:
    - `1 ≤ laneLength[L] ≤ patternLength`.
  - Lanes play steps `1..laneLength[L]`, then wrap back to `1`.

- **Global pattern length**
  - A top-level `patternLength` used for:
    - Grid layout on the Push pads (e.g. 16 steps visible at once).
    - Default lane length when not overridden.

- **Phase & reset semantics**
  - Lanes advance independently but must agree on **when a cycle starts**.
  - **Decision – Reset phases on start**: when transport starts or a clip is launched, all lanes **reset phase to step 1**.

### 4.3 Push 1 UX Design (Polymeter)

- **Lane focus**
  - At any time, there is a **focused lane** (e.g. selected by pressing its row or dedicated selector).
  - Lane-level actions (length changes, clear, duplicate) apply to the focused lane.

- **Per-lane length editing gestures (candidates)**
  - **Gesture idea A – Encoder-based length (chosen for v1)**
    - Select a lane, then use an **encoder** to change `laneLength`.
    - Display could show: `LANE 4 LEN: 7 / 16`.

  - **Gesture idea B – “Last active step” selection (alternative)**
    - In a lane, the **rightmost lit step** defines the lane length.
    - Pressing a pad beyond the current length **extends** the lane to that step.
    - Clearing steps from the end **shrinks** the lane.
    - Pros: very visual and discoverable.
    - Cons: couples “lane length” and “last active step” semantics; needs clear behaviour when inner steps are empty.

- **Visualisation on the pad grid**
  - For lanes shorter than `patternLength`, pads **beyond laneLength** should be visually distinct:
    - Dimmed, unlit, or using a “ghost” colour.
  - Optionally mark **wrap points** (e.g. a small indication at step 1) so it’s obvious where each lane loops.

- **Display feedback**
  - Show lane info when focused:
    - `LANE 3  LEN: 5  PHASE: 2/5` (phase display is optional but useful for debugging).
  - Optionally show a **table-like summary** across lanes on one page (short names + lengths).

### 4.4 Data Model & Integration

- **Per-lane state**
  - Extend the lane model with:
    - `laneLength` (int).
    - Possibly `laneOffset` (for later phase-offset features; v1 can keep this at 0).

- **Playback iteration**
  - For each lane `L` at each step tick `t`:
    - Compute `laneStepIndex = (t mod laneLength[L]) + 1` (or equivalent).
    - Use `laneStepIndex` to look up the active step and its ratchet factor.

- **Interaction with ratchets**
  - Ratchets operate **inside** each lane step, regardless of lane length.
  - Polymeter simply changes **which steps are visited when**; ratchets decide **how densely each visited step is fired**.

### 4.5 Edge Cases & Constraints

- **Very short lanes**
  - Lanes with length 1–2 steps will repeat extremely fast patterns; may require careful CPU / MIDI scheduling.

- **Lane length changes while playing**
  - Behaviour is governed by a **sequencer timing mode setting** (shared with other play-mode style options):
    - **Immediate mode** – changing `laneLength` takes effect as soon as possible (from the next step tick).
    - **Quantized mode** – lane-length changes are queued and applied on a configurable **quantized boundary** (e.g. next bar or next full pattern cycle), controlled by a small boundary mode setting (see Section 7.6), so structural changes land on-grid.

- **Pattern resizing**
  - **Decision – Clamp on shrink, leave as-is on grow**:
    - When `patternLength` is **reduced**, any `laneLength` greater than the new `patternLength` is **clamped down** to `patternLength`.
    - When `patternLength` is **increased**, existing `laneLength` values are **left unchanged** until explicitly edited.
  - This keeps lane lengths predictable and avoids implicit proportional scaling of polymeter patterns.

---

## 5. Combined Behaviour: Phasing, Quantization & Performance

- **Phasing**
  - Combined effect of **different lane lengths** and **per-step ratchets** should produce evolving, non-repeating textures.
  - Ensure the engine can handle **LCM(periods)** without drift.

- **Quantization & launch behaviour**
  - Sequencer starts and stops are still controlled by host clip launching.
  - **Decision – Reset on clip launch and transport restart**:
    - Lane phases are reset on **clip launch**.
    - Lane phases are reset on **global transport restart**.

- **Performance gestures**
  - High-level goal:
    - Ratchet changes should be **safe to hit live**.
    - Lane length changes should be slightly more **deliberate** (e.g. rely on encoder + focused lane).

---

## 6. Possible Implementation Phases

### Phase 0 – Technical Spike & Model Skeleton

- **Deliverables**
  - Minimal step-sequencer loop with:
    - Fixed patternLength (e.g. 16 steps).
    - Multiple lanes without polymeter or ratchets.
  - Simple mapping from grid pads → steps.
  - Basic display showing current step and lane.

### Phase 1 – Per-Step Ratchets (Core)

- **Deliverables**
  - Data structure for per-step `ratchetFactor`.
  - Playback engine that schedules subdivided hits per step.
  - Basic Push 1 gesture for cycling ratchet factors on steps.
  - Visual and display feedback for ratchet states.

- **Notes**
  - Focus on correctness and timing stability before adding more UX sugar.

### Phase 2 – Polymeter Lanes (Core)

- **Deliverables**
  - Per-lane `laneLength` state and playback logic.
  - UI to set lane length (encoder or last-active-step gesture).
  - Visual feedback for lane length vs pattern length.

- **Notes**
  - Verify behaviour with a small set of classic polymeter combos (3 vs 4, 5 vs 7 vs 9, etc.).

### Phase 3 – Performance UX Polish

- **Deliverables**
  - Multi-step ratchet editing.
  - Quick “reset ratchets” and “reset lane lengths” gestures.
  - Optional short-cuts for common polymeter setups (e.g. presets: euclidean-ish templates or classic ratios).

### Phase 4 – Testing & Stabilisation

- **Deliverables**
  - Manual test script for:
    - Ratchet behaviour at various tempos / host quantizations.
    - Polymeter phasing under clip launch / stop / restart.
  - Any light-weight sandbox or logging helpers needed to debug timing.

---

## 7. Open Decisions

### 7.1 Ratchet Factor Range

- **Decision – Use extended set {1,2,3,4,5,6,7,8}**
  - We accept the display/selection complexity in exchange for more creative rhythmic options.
  - Implementation notes:
    - Per-step details show the exact factor (e.g. `RAT: 7x`).
    - Grid-level feedback is more qualitative ("ratcheted" vs "non-ratcheted") rather than trying to show all eight values simultaneously.

### 7.2 Clip Boundary Behaviour for Ratchets

- **Decision – Clip at end**
  - Ratchet sub-steps that would extend beyond the clip end are simply **clipped**.
  - This matches typical DAW behaviour and keeps the mental model predictable.
  - Wrap-around and suppression semantics were considered but rejected for v1.

### 7.3 Lane Phase Reset Semantics

- **Decision – Reset on clip launch and transport restart**
  - All lanes reset their phase to step 1 when:
    - A clip is launched, or
    - Global transport is restarted.
  - This is the most predictable model for most users and easiest to test.

### 7.4 Lane Length Editing UX

- **Decision – Encoder-driven lane length for the focused lane**
  - Primary interaction uses an **encoder** to adjust `laneLength` on the currently focused lane.
  - Pad-driven and hybrid approaches remain future options but are not planned for v1.

### 7.5 Interaction with Other Modes / Views (Note / Play Selector)

- **Context from current code**
  - `ButtonID.NOTE` is wired to `SelectPlayViewCommand` in `PushControllerSetup.registerTriggerCommands`.
  - `SelectPlayViewCommand` toggles the temporary `Modes.VIEW_SELECT` mode (when not in a Session view), implemented by `NoteViewSelectMode`.
  - `NoteViewSelectMode` already exposes a **2×8 grid of play-related views** using `VIEWS` (bottom row) and `VIEWS_TOP` (top row), including `Views.SEQUENCER`, `Views.POLY_SEQUENCER`, `Views.RAINDROPS`, `Views.DRUM` variants, etc.
  - SHIFT is **not currently used** as a modifier for Note/view selection; it is used elsewhere (e.g. Undo/Redo, sends page, stop-clip behaviour).

- **Option A – Reuse existing NoteViewSelectMode grid (no new SHIFT behaviour)**
  - Keep the current `NOTE → SelectPlayViewCommand → NoteViewSelectMode` flow exactly as is.
  - Treat the existing `Views.SEQUENCER` slot in `NoteViewSelectMode.VIEWS_TOP` as the **home for the new ratchet/polymeter sequencer** (or repoint one of the `null` slots if needed).
  - **Pros**
    - No change to the Note button gesture; users still press `Note` to see a grid of play views.
    - Leverages existing two-row layout already grouping "Sequence" and "Play" tools.
    - Lowest implementation risk; only the `Views.SEQUENCER` implementation changes under the hood.
  - **Cons**
    - No direct one-gesture access; entering the sequencer always goes via the Note view grid.
    - Advanced sequencer shares space/mental model with other play tools; could feel crowded.

- **Option B – Add SHIFT+Note quick access to sequencer (keep NoteViewSelectMode)**
  - Extend `SelectPlayViewCommand.execute` so that:
    - If **SHIFT is held** and `ButtonID.NOTE` is pressed, jump directly to the sequencer view (e.g. `Views.SEQUENCER`) instead of toggling `Modes.VIEW_SELECT`.
    - If SHIFT is **not** held, keep today’s behaviour: either recall preferred view from Session or open/close `NoteViewSelectMode`.
  - `NoteViewSelectMode` still lists the sequencer in its grid; SHIFT+Note is just a **shortcut**.
  - **Pros**
    - One-gesture access to the sequencer while preserving the existing Note-view selection UX.
    - Fits existing patterns where SHIFT modifies button semantics (Undo/Redo, send paging, stop clip, etc.).
  - **Cons**
    - Adds another SHIFT combination users must learn.
    - Slightly more complex logic in `SelectPlayViewCommand`.

- **Option C – Turn NoteViewSelectMode into a true SHIFT-banked selector (more invasive)**
  - Redesign `NoteViewSelectMode` so that **without SHIFT** it shows a "primary" set of views, and **with SHIFT held** it shows an alternate/advanced bank (e.g. sequencer-heavy views).
  - Implementation sketch:
    - Use one row (or both rows) for everyday views when SHIFT is up.
    - When SHIFT is pressed, repurpose the same row(s) to display a different set (e.g. advanced sequencers, polymetric tools) while `Modes.VIEW_SELECT` is active.
  - **Pros**
    - Matches the mental model of "hold SHIFT while in the play mode selector to see a second set of choices".
    - Keeps advanced modes visually separated from everyday play modes.
  - **Cons**
    - Requires non-trivial changes to `NoteViewSelectMode` display and button handling.
    - Higher risk of regressions in existing play-view selection behaviour.
    - Needs careful on-device testing to ensure the text display stays readable.

### 7.6 Lane Length Change Timing Setting

- **Decision – Expose Immediate vs Quantized as a setting**
  - Lane-length change behaviour is controlled by a small **sequencer timing mode setting**, similar to timing-related settings in other play modes.
  - **Immediate** (default for performance): lane-length tweaks take effect on the next step tick, making live mangling responsive but potentially more chaotic.
  - **Quantized**: lane-length tweaks are queued and only applied on a musical boundary, making structural changes feel intentional and “DJ-style” while trading some immediacy.
  - Within **Quantized**, a separate **quantized boundary mode** controls *where* changes land, similar to other quantization-style modes:
    - **Bar** *(default)* – apply queued changes at the start of the next bar.
    - **Pattern Cycle** – apply queued changes at the start of the next full pattern cycle.
  - **Default**: Bar, to match common clip quantization behaviour; Pattern Cycle remains available as an advanced alternative if needed later.
