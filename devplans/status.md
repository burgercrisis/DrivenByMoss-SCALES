# Project Status

## Table of Contents

- [Bitwig Custom Scales Panel](#bitwig-custom-scales-panel)
- [Push 1 Controller Improvements](#push-1-controller-improvements)
- [Scale Control via Script Parameters](#scale-control-via-script-parameters)
 - [Sequencer Modes & Note/Play Selector](#sequencer-modes--noteplay-selector)

## Bitwig Custom Scales Panel
### Current State

- **Core model & storage**
  - `CustomScale` and `CustomScaleLibrary` implemented.
  - Custom scales persisted as JSON in `custom-scales.json` with validation (name, intervals, constraints) and safe write semantics.

- **Engine integration**
  - `Scales` extended to accept custom scales via `setCustomScales(...)`.
  - `getCurrentScaleName()` surfaces the active scale name (built-in or custom) for UI/config use.
  - Startup wiring loads `custom-scales.json` and injects custom scales into `Scales` (via `AbstractControllerSetup.createScales()`).

- **UI sweep for scale name display**
  - All relevant controllers now use `getCurrentScaleName()` for **display** and **configuration storage** instead of `getScale().getName()`.
  - This ensures custom scales are correctly reflected in displays and persisted configuration.

- **Bitwig settings panel infrastructure**
  - `AbstractConfiguration` now defines `CATEGORY_CUSTOM_SCALES` and
    `activateCustomScalesSettings(ISettingsUI)`.
  - Panel implementation:
    - Fixed **8 slots**, each with three `IStringSetting`s under `Scales - Custom Scales`:
      - `Custom Scale N Name`
      - `Custom Scale N Intervals`
      - `Custom Scale N Description`
    - On activation:
      - Loads existing custom scales from `custom-scales.json`.
      - Pre-populates each slot (up to 8) with name / intervals / description.
    - On any change in any slot:
      - Rebuilds an in-memory list of `CustomScale` objects.
      - Validates:
        - Non-empty name and intervals for non-empty slots.
        - Unique names per slot.
        - Intervals parse as comma-separated integers.
        - `CustomScale.validate(...)` constraints (0–11, sorted, includes 0, ≥2 degrees).
      - If **any** slot fails validation:
        - Shows the first error via `host.showNotification(...)`.
        - **Does not** overwrite `custom-scales.json`.
      - If validation succeeds for all non-empty slots:
        - Writes the full list to `custom-scales.json` using `CustomScaleLibrary.saveAll(...)`.
        - IO failures are logged via `host.error(...)` and surfaced via `host.showNotification(...)`.

- **Panel location & wiring (Push & others)**
  - Devplan decision: Custom Scales panel lives in **global controller settings** (`globalSettings`), not per-project `documentSettings`.
  - `PushConfiguration.init(...)` now calls:
    - `activateScaleSetting(documentSettings)` (existing behavior).
    - `activateScaleBaseSetting(documentSettings)`.
    - `activateScaleInScaleSetting(documentSettings)`.
    - `activateScaleLayoutSetting(documentSettings)`.
    - **New:** `activateCustomScalesSettings(globalSettings)`.
  - Result: Push exposes a `Scales - Custom Scales` section where the 8 slots can be edited.
  - Additional controllers wired to the same global panel:
    - `MaschineConfiguration` (Maschine MK3).
    - `APCConfiguration` (Akai APC).
    - `APCminiConfiguration` (Akai APCmini).

- **Runtime behavior**
  - Devplan decision: v1 is **restart-only** for engine integration.
    - Editing custom scales in the panel updates `custom-scales.json` immediately (on successful validation).
    - `Scales` picks up changes on the next script reload / Bitwig restart via existing startup wiring.
    - No live refresh of running `Scales` instances yet.

- **TODO alignment**
  - Core model + storage + integration: **done**.
  - UI sweep to `getCurrentScaleName()`: **done**.
  - Bitwig custom scales panel, wired to `CustomScaleLibrary` with validation and notifications: **done** (for Push, Maschine MK3, APC, APCmini).
  - Remaining tracked TODO: **End-to-end test** (see below).

### Remaining Work

- **E2E test (Push first)**
  - In Bitwig, for the DrivenByMoss Push extension:
    - Open global settings and locate `Scales - Custom Scales`.
    - Define a scale in a slot:
      - Example: Name `My Blues`, Intervals `0,3,5,6,7,10`, optional description.
    - Confirm:
      - `custom-scales.json` contains this scale.
      - After script reload / Bitwig restart, the main `Scale` dropdown shows `My Blues` appended to built-ins.
      - Selecting `My Blues` causes Push note modes to play in that scale.
      - The choice persists across Bitwig restart.

- **Further rollout beyond current controllers (optional next)**
  - Mirror `activateCustomScalesSettings(globalSettings)` into additional scale-aware configurations beyond Push, Maschine MK3, APC, and APCmini:
    - e.g. Fire, Launchpad, Oxi One, etc.
  - This is independent of engine/core work and simply exposes the already-implemented panel across more controllers.

- **Deletion semantics for in-use scales (future)**
  - Devplan has a locked decision: "Disallow deletion of a custom scale while it is active for any configuration".
  - Current slot-based UI does not yet enforce this (there is no explicit "delete" action; users clear a slot by emptying fields).
  - A richer list+editor UI would need to implement this constraint explicitly.

- **Live refresh of Scales (future)**
  - Currently, changes only take effect after reload.
  - A later improvement could:
    - Watch for successful saves from the panel.
    - Notify all `AbstractControllerSetup` instances to re-load `custom-scales.json` and call `scales.setCustomScales(...)` again.
    - Re-apply `setScaleByName(configuration.getScale())` so active custom scales update in-place.

### Remaining Choices (with recommendations)

1. **Whether to implement live in-session refresh of Scales now**

   - **Option A – Keep restart-only (current state)**
     - **Pros**
       - No additional cross-layer wiring; relies on existing startup path.
       - Simpler and safer; fewer moving parts to break.
     - **Cons**
       - Users must reload the script / restart Bitwig to see changes take effect.
   - **Option B – Add live refresh in v1**
     - **Pros**
       - Better UX: changes in the Custom Scales panel are visible immediately.
     - **Cons**
       - Requires designing and implementing a notification path from configuration → controller setup.
       - More invasive; higher regression risk in core lifecycle.
   - **Recommendation:** **Option A (keep restart-only for v1)**. Defer live refresh until after end-to-end testing confirms the core flow is stable.

2. **Rollout scope across controllers**

   - **Option A – Push-only initial rollout (current state)**
     - **Pros**
       - Smaller surface area to test and debug.
       - Aligns with existing Push-focused devplans.
     - **Cons**
       - Other controllers won’t surface the Custom Scales panel yet, even though core support exists.
   - **Option B – Immediate rollout to all scale-aware controllers**
     - **Pros**
       - Consistent feature set across devices.
       - No need to revisit each configuration later.
     - **Cons**
       - More configurations to inspect and test in one go.
   - **Recommendation:** **Option A (Push-first)**. Complete E2E testing on Push, then selectively roll out to a short list of high-value controllers.

3. **UI sophistication for custom scales management**

   - **Option A – Keep the current fixed-slot, text-field UI for v1 (current state)**
     - **Pros**
       - Fully implemented and wired.
       - Minimal use of `ISettingsUI`, no complex widgets required.
     - **Cons**
       - Limited to a fixed number of scales (8 slots).
       - No explicit list, Add/Delete/Duplicate actions; users manage slots manually.
   - **Option B – Implement a richer list+editor UI later**
     - **Pros**
       - Closer to the original design (list + CRUD actions).
       - Could better enforce decisions like "don’t delete scales that are in use".
     - **Cons**
       - May be constrained by `ISettingsUI` capabilities.
       - More development and UX work; not necessary to ship v1.
   - **Recommendation:** **Option A for v1**, with Option B as a follow-up enhancement if the fixed-slot UI proves too limiting in practice.

### Recommended Next Immediate Step

- Perform the **Push E2E test** and record any issues or UX friction:
  - If the flow is acceptable with restart-only behavior, proceed to rolling the panel out to one or two additional controllers.
  - If the restart-only requirement feels too painful, reconsider elevating "live refresh of Scales" from future enhancement to near-term task.

## Push 1 Controller Improvements

### Current State

- **Multi-page ConfigurationMode (Push 1)**
  - `ConfigurationMode` now has three pages: `Noob`, `Pro`, and `Info`.
  - While `ConfigurationMode` is active on Push 1, SCENE 1–3 act as page tabs:
    - Scene 1 → Noob page.
    - Scene 2 → Pro page.
    - Scene 3 → Info page.
  - Scene button presses in this mode are consumed by `ConfigurationMode` and do not trigger their usual view behaviour.

- **Noob page – everyday performance setup**
  - Exposes a small, high-value subset of settings:
    - Pad feel preset (coarse threshold/curve combination).
    - Pad threshold (coarse list of useful values).
    - Velocity curve (coarse list of useful curves).
    - Note repeat period.
    - Note repeat length (when supported by the host).
    - Accent level (Off / 96 / 110 / 127).
    - Session view mode (Clips / Flipped / Scenes).
    - Startup view (Play / Drum / Session).
  - Shows a simple pad response meter ("Pad Vel") based on the last played pad velocity.

- **Pro page – power-user tuning**
  - Exposes the deeper configuration set for Push 1:
    - Pad feel preset (same family as Noob).
    - Full pad threshold list.
    - Full velocity curve list.
    - Track cursor behaviour (`cursorKeysTrackOption`: page / step / swap).
    - Scene cursor behaviour (`cursorKeysSceneOption`: page / step).
    - Ribbon mode (`RIBBON_MODE_*` family).
    - Ribbon note repeat mode (`NOTE_REPEAT_*` family).
  - Also shows a pad response meter for fine-tuning pad feel while editing these parameters.

- **Info page – quick diagnostics**
  - Displays:
    - Firmware version.
    - Startup view.
    - Current Session view mode (Clips / Flipped / Scenes).

- **Hardware-side reset shortcuts**
  - In `ConfigurationMode` on Push 1, holding Delete and touching a knob resets pad feel parameters:
    - Knob 1: reset both pad threshold and velocity curve to defaults.
    - Knob 2: reset pad threshold to default.
    - Knob 3: reset velocity curve to default.

- **Session view & navigation tweaks**
  - `SHIFT + Session` cycles the session view mode:
    - Clips → Flipped → Scenes → Clips → …
    - Each change updates the underlying configuration and shows a short banner on the Push 1 display (e.g. "SESSION VIEW: Scenes").
  - When already in a session-style view, changing the Session view mode in configuration (from hardware or Bitwig settings) automatically switches between:
    - `Views.SESSION` for Clips/Flipped.
    - `Views.SCENE_PLAY` for Scenes.
  - Track and scene cursor behaviour options are driven from the Pro page:
    - Track cursor: move by page / move by 1 / swap tracks.
    - Scene cursor: move by page / move by 1.

### Remaining Work

- **Testing & ergonomics**
  - Finalize and run a concise manual test checklist for:
    - Enter/exit of `ConfigurationMode`.
    - SCENE tabbing between Noob/Pro/Info.
    - Pad feel editing (including Delete+knob resets).
    - Session view mode changes from both hardware and settings.
  - Validate labels and layout on-device for readability on the Push 1 text display.

- **Potential future extensions**
  - Consider adding more expression-related settings to the Pro page if needed (e.g. additional accent / aftertouch options).
  - Evaluate whether any of the existing Pro settings should also be surfaced, in simplified form, on the Noob page based on user feedback.

## Sequencer Modes & Note/Play Selector

### Current State

- **Design context**
  - Detailed specs for a new **ratchet/polymeter note sequencer** on Push 1 live in `devplans/push1-sequencer-mode.md`.
  - A complementary **parameter sequencer mode** (automation layer) is defined in `devplans/push-parameter-sequencer-mode.md`.
  - Both modes share a **SHIFT-banked Note/Play selector** via `NoteViewSelectMode`:
    - **Primary bank (SHIFT up)** keeps the existing NoteViewSelect views (Play, Chords, Piano, Drum64, Poly Sequencer, Raindrops, Drum variants, Clip Length, Program Change, etc.).
    - **Advanced bank (SHIFT down)** is reserved exclusively for new SCALES-specific modes, with a v1 layout of:
      - Column 1 / top row: ratchet/polymeter sequencer (`Views.RATCHET_SEQ`).
      - Column 2 / top row: Parameter Sequencer lane-focus view (`Views.PARAM_SEQ`).
      - Column 3 / top row: Parameter Overview view (`Views.PARAM_OVERVIEW`).
      - Columns 4–8: reserved for future tools.

- **Status (Dec 2025)**
  - **Planning**
    - Devplans for the Push 1 ratchet/polymeter sequencer and the parameter sequencer are written, internally consistent, and aligned on the shared Note/Play selector model.
    - Advanced-bank layout for `NoteViewSelectMode` is defined at a high level as above; devplans treat this as the v1 target.
  - **Implementation**
    - No new sequencer modes are registered or wired in `PushControllerSetup` yet (no `Modes`/`Views` entries for `Views.RATCHET_SEQ`, `Views.PARAM_SEQ`, or `Views.PARAM_OVERVIEW`).
    - `NoteViewSelectMode` has not yet been modified to support primary vs advanced banks based on `surface.isShiftPressed()`.
  - This section centralizes sequencer status; `push1-sequencer-mode.md` and `push-parameter-sequencer-mode.md` are treated as **spec documents only**.

## Scale Control via Script Parameters

### Current State

- **Canonical control surface for scales**
  - The existing configuration fields in `AbstractConfiguration` are treated as the canonical external control surface for scale settings:
    - `SCALES_SCALE` (scale type).
    - `SCALES_BASE` (scale base note).
    - `SCALES_IN_KEY` (In-Key vs Chromatic).
    - `SCALES_LAYOUT` (scale layout).
  - Controllers, host automation, and Remote Controls are expected to drive these parameters rather than any dedicated MIDI messages for scale control.

- **Observer wiring**
  - `AbstractControllerSetup` wires configuration changes back into `Scales` (and then into active views) via scale observers:
    - Changes to `scale` → `setScaleByName(...)` → update view note mappings.
    - Changes to `scaleBase` → `setScaleOffsetByName(...)` → update view note mappings.
    - Changes to `scaleInKey` → `setChromatic(!isScaleInKey)` → update view note mappings.
    - Changes to `scaleLayout` → `setScaleLayoutByName(...)` → update view note mappings.

- **Design decision**
  - Dedicated MIDI note/channel/CC mappings for scale control are explicitly out of scope for this fork.
  - New work that needs to influence scale, base note, in-key/chromatic, or layout should do so via these configuration parameters.

### Remaining Work

- **Documentation & examples**
  - Add a short "How to automate scales" section to the user-facing documentation, showing:
    - How to map these parameters in Bitwig’s controller settings.
    - How to expose them to Remote Controls or modulators.

- **Light PoC / validation**
  - Build and verify a small Bitwig project where:
    - Remote Controls or automation lanes drive `scale`, `scaleBase`, `scaleInKey`, and `scaleLayout`.
    - Changes are reflected immediately in `Scales` and the active play-related views on Push.
