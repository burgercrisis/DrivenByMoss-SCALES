# Project Status

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

- **Panel location & wiring (Push)**
  - Devplan decision: Custom Scales panel lives in **global controller settings** (`globalSettings`), not per-project `documentSettings`.
  - `PushConfiguration.init(...)` now calls:
    - `activateScaleSetting(documentSettings)` (existing behavior).
    - `activateScaleBaseSetting(documentSettings)`.
    - `activateScaleInScaleSetting(documentSettings)`.
    - `activateScaleLayoutSetting(documentSettings)`.
    - **New:** `activateCustomScalesSettings(globalSettings)`.
  - Result: Push exposes a `Scales - Custom Scales` section where the 8 slots can be edited.

- **Runtime behavior**
  - Devplan decision: v1 is **restart-only** for engine integration.
    - Editing custom scales in the panel updates `custom-scales.json` immediately (on successful validation).
    - `Scales` picks up changes on the next script reload / Bitwig restart via existing startup wiring.
    - No live refresh of running `Scales` instances yet.

- **TODO alignment**
  - Core model + storage + integration: **done**.
  - UI sweep to `getCurrentScaleName()`: **done**.
  - Bitwig custom scales panel, wired to `CustomScaleLibrary` with validation and notifications: **done** (for Push).
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

- **Rollout beyond Push (optional next)**
  - Mirror `activateCustomScalesSettings(globalSettings)` into other scale-aware configurations:
    - e.g. Maschine, APC/APCmini, Fire, Launchpad, Oxi One, etc.
  - This is independent of engine/core work and simply exposes the already-implemented panel across controllers.

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
