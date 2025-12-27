# Push Parameter Sequencer Mode (Automation Layer)

## Goal

Design a Push sequencing mode that:

- **Writes parameter changes (CC/program/channel) into the active Bitwig clip** instead of triggering notes.
- **Shares the same MIDI channel as the instrument** so CCs always affect the instrument.
- **Can be used as a layer alongside note sequencers on the same clip**, leveraging Bitwig's ability to store notes and CC/automation in one clip.
- **Minimizes MIDI traffic from Push** by letting Bitwig handle ramps between automation points.

This mode behaves like a step sequencer UI, but its steps represent parameter values over time instead of notes.

---

## Core Behaviour

- **Mode type**: Parameter sequencer (automation layer).
- **Clip integration**:
  - Always targets the **currently active/playing clip** on the selected track.
  - Writes **CC / Program Change / (optionally) Channel changes** as events into that clip.
  - Uses the **same MIDI channel as the instrument** so CCs/programs always hit the instrument.
- **Layering with other sequencers**:
  - Note sequencer modes continue to write notes to the clip.
  - Parameter sequencer mode writes CC/program/channel data into the same clip.
  - Result: one Bitwig clip holding both melodic/rhythmic data and parameter moves.

---

## Data Model

### Per-Clip / Pattern

- **Pattern length**:
  - Shared with the clip / main sequencer length (**same loop length**), in line with normal Bitwig behaviour.
  - No independent polymeter/polyrhythm in v1 (can be extended later).

- **Lanes**:
  - **8 parameter lanes** per page.
  - Lane pages for CC index ranges:
    - Page 1: CC 1–8
    - Page 2: CC 9–16
    - Page 3: CC 17–24
    - etc.
  - Per-lane settings:
    - Active/on-off state.
    - CC number (derived from page + lane index: `pageOffset + laneIndex`).
    - Optional lane name/label (for display only, if UI allows).

### Per-Lane, Per-Step

Each lane step stores:

- **active**: boolean — whether this step writes an explicit value.
- **value**: 0–127 — the CC value to write at this step.
- **shape/mode**: one of:
  - `HOLD` — send a single CC point at the step time (Bitwig holds until the next point).
  - `RAMP` — conceptually “slope” from previous step to this one, but implemented via CC points only at steps; Bitwig performs the internal ramp between points once recorded.
- **(optional, later)**: probability/accent/other step modifiers if needed.

> **Important**: Even in `RAMP` mode, the script purposefully **does not spam CCs**. It simply places CC points at each active step and relies on Bitwig’s internal ramping between these points.

### Storage and Synchronization

- The parameter pattern is **stored in-memory inside the Parameter Sequencer view**.
- When entering or re-syncing the view, data is **re-derived from the current Bitwig clip** (CC points and, where possible, curve/slope metadata).
- There is no separate persistent store: the **clip itself is the source of truth** for long-term storage.

---

## MIDI / Automation Mapping

### Channel and Event Types

- **MIDI channel**: **same channel as the instrument**.
  - Guarantees that CCs / programs affect the instrument consistently.
  - No separate “automation bus” channel; design assumes the instrument/track is already listening on this channel.

- **Event types supported** (v1 focus and future):
  - **v1**: Continuous Controllers (CC) only.
  - **Later** (future extensions, not MVP):
    - Program Change events (per-step, via dedicated lane type).
    - Channel changes (per-step channel change) if Bitwig and instrument routing make this manageable.

### CC Number Paging

- **Lane → CC mapping** per page:
  - `baseCC = 1 + (pageIndex * 8)`
  - Lane `i` (0–7) on page `pageIndex` uses `CC = baseCC + i`.
- UI exposes **page up/down** to move through CC ranges: 1–8, 9–16, 17–24, etc.
- Later enhancement: ability to **reassign CCs** beyond fixed 8-wide blocks.

### Future: Moving / Reassigning CCs

Later feature (not in MVP, but design-aware):

- A “CC move / remap” operation similar to lane assignment:
  - Select a lane or range of steps.
  - Reassign their CC number (e.g. move all data currently on CC 1 to CC 21).
  - Internally, rearrange step data / lane CC assignments, so automation can be reorganized without re-recording.

---

## Push UI and Interaction

### Overview

- **Shipping v1 view**: **Lane-focus view only.**
  - Full 8×8 grid dedicated to a single focused lane at a time.
  - Overview/multi-lane grid will be added later when users are comfortable with the core workflow.

- **Later view (not for v1)**: Lane overview view.
  - A toggleable alternative showing up to 8 lanes at once (rows as lanes).

### Grid (Lane-Focus View)

- **Horizontal (X-axis)** = time / steps.
  - Same step resolution and paging behaviour as existing note sequencers.
  - 8/16/32 steps per page depending on the current sequencer’s global settings.

- **Vertical (Y-axis)** = visualizing the focused lane’s step values.
  - Pads represent steps in time.
  - **Pad state**:
    - Off: no step / inactive.
    - On: step active.
    - Brightness or color shade: encodes value magnitude (low → high).
  - Interaction:
    - Tap a pad: toggle step active and set to a default value (e.g. 64) if just activated.
    - Hold a pad + turn encoder (per-lane encoder or designated “value” encoder): fine-tune the step value.

- **Row buttons for lane focus and mode** (Param view only):
  - **Row 1 buttons (`ROW1_i`)**:
    - Select lane `i` as the **focused lane** for the grid.
  - **Row 2 buttons (`ROW2_i`)**:
    - Toggle **HOLD ↔ RAMP** mode for lane `i`.

- **Step shape/mode selection (HOLD vs RAMP)**:
  - For v1, **per-lane mode** is used:
    - Each lane is either in HOLD or RAMP mode.
    - Mode is toggled from the hardware using **Row 2 buttons** in Param view: pressing `ROW2_i` toggles lane `i` between HOLD and RAMP.
    - All steps in that lane share the same interpretation, but still just place CC points; Bitwig derives ramps.

### Encoders

- **8 encoders map to the 8 lanes on the current page**:
  - Encoder `i` corresponds to lane `i` (CC = pageBase + i).

- **Encoder functions**:
  - Turn (no step held):
    - Adjust **global lane default value** or **offset**.
    - For RAMP mode, encoders can be interpreted as **slope/curve controls** when combined with a modifier (see below).
  - Press encoder:
    - Select that lane as the **focused lane** for the grid view.
  - Hold step + turn encoder:
    - Adjust that step’s **exact CC value**.

- **Slope / curve control via encoders** (in RAMP mode):
  - When a lane is in RAMP mode, **`SHIFT + encoder`** (in Param view) adjusts the **Bitwig automation curve/slope value** for the segment between consecutive active steps of that lane.
  - The curve value is a **numeric parameter in Bitwig** (slope/curve for that CC segment), so `SHIFT + encoder` movement directly writes that value for the affected CC segment(s), while **plain encoder turns** still adjust the lane/step values.
  - Implementation detail: this still only places **one CC point per active step**; Bitwig both interpolates the values and applies the stored curve/slope between those points.

### Paging / Navigation

- **Time pages**: same as existing sequencer (left/right or up/down for step pages).
- **CC pages**:
  - In Param view, the standard **Page Left/Right** buttons (`DEVICE_LEFT`/`DEVICE_RIGHT`) continue to control **time/step pages**.
  - **`SHIFT + Page Left/Right`** is reserved in Param view for **CC page up/down** across CC index ranges (1–8, 9–16, 17–24, ...).

### Mode Entry / Exit

- Dedicated way to enter **Parameter Sequencer Mode** from the existing Push Note/Play selector, aligned with the **true SHIFT-banked NoteViewSelect** concept used by the Push 1 note sequencer devplan:
  - **NoteViewSelect advanced-bank entry (column 2, top row)**:
    - In `NoteViewSelectMode`, within the **advanced SHIFT bank** of the Note/Play selector, **column 2 on the Sequence (top) row** is the "Parameter Sequencer" (lane-focus view), matching the advanced-bank layout in the Push 1 sequencer devplan.
    - Flow (conceptual):
      - Press `NOTE` to enter `NoteViewSelectMode`.
      - Hold `SHIFT` to view the **advanced bank** of play/sequence tools.
      - Press the advanced-bank column 2 / top-row slot labelled "Parameter Sequencer" to activate the lane-focus Parameter Sequencer view (`Views.PARAM_SEQ` or equivalent).
  - Additional entry mechanisms (mode cycle, layer toggle) can be added later, but the advanced-bank NoteViewSelect entry remains the primary, discoverable entry point on Push 1.

---

## Per-Step Behaviour & Bitwig Integration

- **When playback is running**:
  - At each active step in each active lane, the script sends a **single CC event** at the step's time with the stored value.
  - Bitwig records these CC points into the active clip if recording is enabled.

- **Hold vs Ramp interpretation**:
  - **HOLD mode**:
    - Conceptually: "jump to this value at step start, then hold until next point".
    - Implementation: send one CC event at the step; Bitwig holds its last value until the next CC.
  - **RAMP mode**:
    - Conceptually: "transition/slope from previous step value to this step's value".
    - Implementation: still only sends CC at step times; Bitwig internally interpolates as a ramp between the two stored CC points.

- **Recording behaviour**:
  - With recording enabled and an active clip:
    - Notes from note sequencer modes get written as usual.
    - CC data from parameter sequencer gets written at step positions.
  - The clip thereby stores both note and CC automation data.

---

## Future Extensions (Not MVP but Planned)

  - **Lane overview view**:
  - Toggleable view where rows = lanes and columns = time.
  - Coarse visualization of all 8 lanes simultaneously.
  - Accessed via a **latching selection in the advanced bank of NoteViewSelect**:
    - **Column 3 on the Sequence (top) row** of the advanced bank becomes "Parameter Overview", matching the advanced-bank layout in the Push 1 sequencer devplan.
    - Selecting that entry switches the Parameter Sequencer into overview layout; returning to the advanced-bank Parameter Sequencer entry (column 2 / top row) (or an internal toggle) brings it back to lane-focus.

- **Per-step mode overrides**:
  - Currently lanes share a single mode (HOLD or RAMP).
  - Potential extension: per-step mode between HOLD/RAMP for finer control.

- **Move / remap CC assignments**:
  - UI for reassigning CC numbers of lanes or step ranges to reorganize automation (e.g. CC 1 → CC 71).

- **Advanced probability / conditions per step**:
  - Randomization, probability, conditional triggering of parameter changes.

---

## Locked design decisions (user choices)

- **Program / Channel support in v1**: v1 is **CC-only**. Program and channel changes are planned as future extensions via dedicated lane types, not part of the MVP.
- **HOLD vs RAMP granularity**: **Per-lane mode** (HOLD or RAMP) for v1, with the internal step data model prepared for per-step overrides in a later version.
- **Lane focus & mode controls**: In Param view, **ROW1_i** selects the focused lane and **ROW2_i** toggles that lane’s mode between HOLD and RAMP.
- **Ramp curve behaviour & modifier**: In RAMP mode, **`SHIFT + encoder`** (Param view only) changes **Bitwig’s own automation curve/slope value** for the CC segment between two active steps, while plain encoder turns still set values; curve changes are applied **live per encoder move** as far as the Bitwig API allows.
- **CC page navigation**: In Param view, **Page Left/Right** remain time/step pagers; **`SHIFT + Page Left/Right`** is reserved for CC page up/down across CC ranges (1–8, 9–16, 17–24, ...).
- **Lane overview access model**: When implemented, the lane overview layout is handled by the **same underlying Parameter Sequencer view**, using an **internal layout flag** (FOCUS vs OVERVIEW). Two NoteViewSelect entries (e.g. ROW1_5 and ROW1_6) both select this view but switch its layout flag, acting as a latching toggle between lane-focus and overview.
- **Persistence model**: The Parameter Sequencer uses a **clip-as-truth, in-memory cache** model: lane/step/curve data lives in-memory while the view is active and is **re-derived from the clip** when entering or resyncing the view.
- **In-memory storage**: The parameter pattern is stored in-memory inside the Parameter Sequencer view, re-derived from the current Bitwig clip when entering or resyncing the view.
- **Live curve updates**: When the Bitwig API allows, curve changes are applied **live per encoder move**.

---

## Open Decisions / Implementation Notes

- **Exact Push button mappings** for:
  - Entering/exiting Parameter Sequencer mode.
  - Toggling HOLD/RAMP per lane.
  - CC page up/down.
  - Slope operations (which modifier + encoder combinations).

- **Bitwig-level mapping strategy**:
  - User will typically map CCs (on the instrument’s channel) to:
    - Track/Device Remote Controls, or
    - Direct device parameters via Bitwig's MIDI mapping.
  - Script should keep CC emission generic, not tied to any specific device.

- **Channel and Program Change handling**:
  - Decide whether to represent these as dedicated lanes or separate lane types.
  - Ensure they don't conflict with clip-level or track-level routing.

This document defines the MVP behaviour and structure for the Push Parameter Sequencer mode as an automation layer that integrates tightly with Bitwig clips and existing note sequencers, using the same instrument channel and leveraging Bitwig's internal ramping between CC points.

---

## Status & rollout tracking

Implementation progress and rollout decisions for this mode and its Note/Play selector slots are tracked centrally in `devplans/status.md` under *Sequencer Modes & Note/Play Selector*.
