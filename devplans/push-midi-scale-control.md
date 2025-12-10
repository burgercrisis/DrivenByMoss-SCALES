# Scale Control  Script Parameters First

## Context
- **Project**: DrivenByMoss-SCALES
- **Controllers**: Ableton Push 1 (initial target), Push 2/3 and others potentially follow the same pattern.
- **Goal**: Expose musical scale settings (scale type, base note, layout, inkey/chromatic) as **script parameters / configuration values** that can be driven by Bitwigs controller settings, automation, or other host-side mechanisms, without relying on dedicated MIDI notes/channels whenever a parameter-based path is available.

## Requirements
- **R1**: From an **external control source** (Bitwig controller settings, automation, Remote Controls, etc.), select the current **scale type** (e.g. Major, Dorian, Harmonic Minor, ).
- **R2**: From an external control source, select the **scale base note** (CB, i.e. scale offset).
- **R3 (optional)**: Toggle **In-Key vs Chromatic** mode via external control.
- **R4 (optional)**: Change **scale layout** via external control.
- **R5**: Changes must immediately update note mapping for the active play-related views (same behavior as using `ScalesMode` on Push).
- **R6**: Must not interfere with existing pad behavior/playback.
- **R7**: Ideally reusable across controllers that share the `Scales` / configuration infrastructure.

## Existing Architecture Notes
- **Scale model**: `de.mossgrabers.framework.scale.Scales`
  - Holds `selectedScale`, `scaleOffset`, `scaleLayout`, `chromaticOn`, octave, etc.
  - Has helpers: `setScaleByName`, `setScaleOffsetByName`, `setChromatic`, `setScaleLayoutByName`, `prev/next*` etc.
  - `updateScaleProperties(Configuration)` writes current scale state back into configuration fields (scale name, base, in-key flag, layout).

- **Configuration observers**: `de.mossgrabers.framework.controller.AbstractControllerSetup.createScaleObservers` wires configuration → scales:
  - On `AbstractConfiguration.SCALES_SCALE`: `scales.setScaleByName(conf.getScale())` then `updateViewNoteMapping()`.
  - On `SCALES_BASE`: `scales.setScaleOffsetByName(conf.getScaleBase())` then `updateViewNoteMapping()`.
  - On `SCALES_IN_KEY`: `scales.setChromatic(!conf.isScaleInKey())` then `updateViewNoteMapping()`.
  - On `SCALES_LAYOUT`: `scales.setScaleLayoutByName(conf.getScaleLayout())` then `updateViewNoteMapping()`.

- **Push integration**:
  - `PushControllerSetup.createSurface`:
    - Creates `IMidiInput input = midiAccess.createInput("Pads", filters ...)`.
    - Constructs `PushControlSurface(host, colorManager, configuration, output, input)`.
  - `AbstractControlSurface` constructor:
    - Calls `input.setMidiCallback(this::handleMidi)`.
  - `PushControlSurface.handleMidi(...)` overrides the base handler to pre-filter certain messages for Push 3 (MPE, etc.), then calls `super.handleMidi(status, data1, data2)`.
  - `ScalesMode` (Push) manipulates `Scales` directly (via buttons/knobs) and calls `this.scales.updateScaleProperties(this.surface.getConfiguration())` to keep configuration in sync.

## Non-goals

- This plan **does not** define any dedicated MIDI note/channel/CC mappings for scale control.
- If a future host limitation forces a MIDI-based solution, that will be designed and documented separately.

## Recommended Direction

- **Primary approach (locked)**: Use **script parameters / configuration values** for `scale`, `scaleBase`, `scaleInKey`, `scaleLayout` as the canonical control surface for scale changes.

Status:
- Script-parameter approach: **chosen** as the default for new work.
- Dedicated MIDI mappings for scale control are **out of scope** for this plan.

## Implementation Plan (High-Level)

1. **Confirm and centralize scale configuration fields**
   - Treat the existing `AbstractConfiguration` scale fields:
     - `SCALES_SCALE`
     - `SCALES_BASE`
     - `SCALES_IN_KEY`
     - `SCALES_LAYOUT`
   - as the **single source of truth** for scale, base, in-key/chromatic, and layout.
   - Verify they are exposed via `ISettingsUI` in a way that is easy to automate or remote-control in Bitwig.

2. **Document host-side control path**
   - In user documentation, describe how to:
     - Change these scale settings from Bitwig controller preferences / document settings.
     - Map them to Remote Controls / modulators / automation lanes if desired.
   - Emphasize that external devices or scripts should drive **these parameters**, not send dedicated MIDI for scale changes.

3. **Small PoC for external automation**
   - Create a simple Bitwig project where:
     - One Remote Control or macro is mapped to `scale` and/or `scaleBase`.
     - Automating these parameters causes:
       - `Scales` to update (via `createScaleObservers`).
       - Push play views to refresh note mapping (`updateViewNoteMapping()`).
   - Validate that on-device scale UIs (e.g. `ScalesMode`) stay in sync when parameters change externally.

4. **(Optional) Improve discoverability**
   - If the existing settings are hard to find, consider:
     - Grouping them in a dedicated "Scale" section in the settings UI.
     - Adding short labels/descriptions to clarify their role as the main external control surface for scales.

5. **Testing strategy**
   - Manual tests in Bitwig:
     - Change scale parameters from the host UI / Remote Controls and verify Push pads update immediately.
     - Confirm that playing pads, MPE behaviour, and on-device scale selection still work as before.
     - Ensure bidirectional sync: changing scales from Push updates configuration and any visible host parameters.

## Decisions

- **Use existing `SCALES_*` fields as-is** for all external scale control:
  - `SCALES_SCALE`, `SCALES_BASE`, `SCALES_IN_KEY`, `SCALES_LAYOUT`.
- **No additional guard** like "Allow external scale automation" in `PushConfiguration` for now.
- **v1 scope** includes the full set: **type, base, in-key/chromatic, and layout**.
- **Settings UX/discoverability**: start with minimal/no extra UX work; only add a dedicated "Scale" section or similar enhancements if discoverability actually feels bad in practice.
- **Testing depth**: start with a manual, light PoC (one Bitwig project with Remote Controls) and only add automated/sandbox regression later if the feature proves fragile or regresses.
 - **Labels/grouping in settings**: keep the current labels and grouping for the scale parameters unless they prove confusing in practice; only refactor grouping or naming based on concrete usability feedback.
 - **Rollout scope for PoC**: focus the initial PoC and early iteration on **Push 1**; consider extending documentation and formal testing to Push 2/3 and other controllers once the approach is validated.
 - **Documentation level**: start with a small "How to automate scales" snippet in the user-facing docs; only add a larger dedicated section with full examples if the feature becomes central to common workflows.

## Next Steps

- [ ] Confirm in code that `scale`, `scaleBase`, `scaleInKey`, `scaleLayout` configuration fields are wired as the canonical external control surface for scales.
- [ ] Verify how these parameters appear in Bitwigs settings/automation/remote-control system and adjust labels/grouping if needed.
- [ ] Build the small PoC project described above and validate end-to-end behaviour on Push 1 (and optionally Push 2/3) for **all four** parameters (type, base, in-key, layout).
- [ ] Add tests / manual checklist for externally-driven changes of each of the four parameters.
