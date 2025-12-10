# Push 1 & Push Family – Controller Improvements (Speculative Dev Plan)

## 1. Goal & Scope

- **Goal**
  - Identify concrete UX and feature improvements for the Ableton Push 1 controller integration in this extension.
  - Where it makes sense, propose changes that also benefit Push 2/3 via shared infrastructure in `PushControllerSetup` / `PushConfiguration` / `PushControlSurface`.

- **Scope (initial)**
  - On-device configuration UX for Push 1 (`ConfigurationMode`, `SetupCommand` behaviour when `PushVersion.VERSION_1`).
  - Session/grid workflow and navigation on the 8×8 pad matrix.
  - Pad behaviour / expression (thresholds, curves, note repeat, accent) and related configuration.
  - Scale / layout / ribbon interactions as experienced from the hardware.
  - Display and feedback improvements for the Push 1 text display (`Push1Display`).

- **Out of scope (for now)**
  - Major refactors of the shared controller framework.
  - New MPE architectures beyond the existing `handleMPEPitchbend` / `PushPadGrid` behaviour.
  - Host-level features in Bitwig outside the controller extension.

---

## 2. Current State (relevant pieces)

- **Controller definitions & setup**
  - `de.mossgrabers.controller.ableton.push.Push1ControllerDefinition`
    - Simple definition + MIDI discovery, no Push-1-specific behaviour here.
  - `de.mossgrabers.bitwig.controller.ableton.push.Push1ControllerExtensionDefinition`
    - Binds Bitwig extension to `PushControllerSetup` with `PushVersion.VERSION_1`.
  - `de.mossgrabers.controller.ableton.push.PushControllerSetup`
    - Shared setup for Push 1/2/3.
    - Switches behaviour based on `pushVersion`:
      - Model configuration (browser sizes, sends, markers, track list behaviour).
      - Surface creation: `Push1Display` (text) vs `Push2Display` (graphics).
      - Mode registration: Push 1 uses `ConfigurationMode`; Push 2/3 have `InfoMode`, `SetupMode`, `MPEConfigurationMode`, `AudioConfigurationMode`.
      - Observers: Push 1 skips several display-related observers (`DEBUG_WINDOW`, color settings, etc.).

- **Configuration & settings**
  - `de.mossgrabers.controller.ableton.push.PushConfiguration`
    - Extends `AbstractConfiguration` with Push-specific settings for ribbon, pads, MPE, colors, audio, etc.
    - A lot of functionality is version-dependent:
      - Push 1: velocity curve + pad threshold (via `changeVelocityCurve`, `changePadThreshold`).
      - Push 2: display / LED brightness, pad sensitivity/gain/dynamics.
      - Push 3: pad curve parameters, MPE-related in-tune options, audio interface configuration.
    - Uses `ISettingsUI` to expose scale, note repeat, session view options (`SESSION_VIEW`), workflow, device favourites, ribbon settings, etc.
    - Many user-facing behaviours live only in Bitwig settings panels (global + document) and are not discoverable from the hardware.

- **Control surface & grid**
  - `de.mossgrabers.controller.ableton.push.controller.PushControlSurface`
    - Shared control surface class for all three versions.
    - Defines the full set of physical controls (buttons, knobs, ribbon, footswitches, etc.).
    - Contains non-trivial logic for:
      - Pad curve and threshold handling (Push 2/3 data tables).
      - MPE pitch-bend integration with `Scales` in `handleMPEPitchbend`.
    - Uses `PushPadGrid` only for Push 3 (MPE-aware grid gating); Push 1/2 use `PadGridImpl`.
  - `de.mossgrabers.controller.ableton.push.controller.PushPadGrid`
    - Simple MPE-aware translation: disables passing notes to controller in MPE-enabled expression views.
    - Tied to `PushControlSurface` and `PushConfiguration` for Push 3.

- **Push 1 display & configuration mode**
  - `de.mossgrabers.controller.ableton.push.controller.Push1Display`
    - Text-only 4×8×17 display with custom bar/character rendering.
    - Provides helpers for value bars, pan bars, menus (`createMenuList`), notifications.
  - `de.mossgrabers.controller.ableton.push.mode.configuration.ConfigurationMode`
    - Push-1-only mode (registered in `PushControllerSetup.createModes` for VERSION_1).
    - Maps knobs 1–4 to pad threshold and velocity curve (coarse/global control).
    - Displays: pad threshold, velocity curve, firmware version, plus a warning when threshold is too low.

- **Push 2/3 configuration modes for comparison**
  - `SetupMode` (Push 2/3)
    - Graphical layout for brightness + pad sensitivity/curve.
    - Supports `Delete + knob touch` to reset certain parameters to defaults.
    - Shows a pad sensitivity curve graph (`createPadSensitivityCurvePush2/3`).
  - `InfoMode` (Push 2)
    - Graphical info screen with firmware, build number, board revision, serial number.
  - `SetupCommand`
    - Behaviour differs across versions:
      - Push 2/3: toggles `Modes.SETUP` as a temporary mode.
      - Push 1: `Setup` (or equivalent button) toggles `Modes.USER`, SHIFT+Setup toggles `Modes.CONFIGURATION`.

- **Session / view handling**
  - `PushConfiguration`
    - `SESSION_VIEW_OPTIONS = { "Clips", "Flipped", "Scenes" }`.
    - `activateSessionView` wires an enum setting that controls flipped/scenes view and notifies observers.
    - `setSceneView()` convenience method sets the `Scenes` option.
  - `PushControllerSetup`
    - Registers `SessionMode` + `SessionViewSelectMode` for all versions.
    - Adds observer on `PushConfiguration.SESSION_VIEW` to re-route `ViewManager` between `Views.SESSION` and `Views.SCENE_PLAY` when in a session view.

---

## 3. Improvement & Addition Ideas

### 3.1 On-device configuration UX for Push 1

- **Idea 1 – Extend `ConfigurationMode` into a multi-page text UI**
  - Today, `ConfigurationMode` exposes only pad threshold + velocity curve.
  - Reuse `Push1Display` and `createMenuList` to build additional pages for other high-value settings that currently live only in Bitwig preferences, e.g.:
    - Note repeat: enable/disable, default rate, default gate.
    - Quantize amount.
    - Accent value.
    - Startup view (`Views.PLAY`, `Views.DRUM`, etc.).
    - Session view mode (`SESSION_VIEW_OPTIONS`: Clips/Scenes/Flipped).
  - Structure these into at least two top-level pages:
    - A **Noob Page** exposing the most important, safe settings in a simple layout.
    - A **Pro Page** that surfaces a broader set of performance-related settings for power users.
  - Potential interaction patterns:
    - Use **Scene 1** and **Scene 2** as tabs to switch between the Noob and Pro pages while in `ConfigurationMode` (with Scene 3 optionally reserved for an Info page later).
    - Guard scene-button handling in **all** views that normally respond to SCENE buttons (Play, Session, Drum variants, Chords, PrgChange, NoteRepeat helpers, etc.) so that, when `Modes.CONFIGURATION` is active, scene presses are ignored by those views and are instead consumed by `ConfigurationMode` for page switching.
    - Use SHIFT + encoder(s) to jump between groups on a given page if needed.
  - Initial content split (Minimal Noob, deep Pro):
    - **Noob Page (everyday performance setup)**
      - **Pad Feel Preset** – e.g. `Soft / Normal / Hard`, mapping to (threshold, curve) pairs.
      - **Pad Threshold (coarse)** – shortened list of the most useful Push 1 thresholds.
      - **Velocity Curve (coarse)** – shortened list of the most distinct curves.
      - **Note Repeat Default Rate** – e.g. `1/8, 1/8T, 1/16, 1/16T, 1/32`.
      - **Note Repeat Default Gate** – a few fixed percentages (e.g. `25%, 50%, 75%, 100%`).
      - **Accent Level** – simple choices such as `Off, 96, 110, 127`.
      - **Session View Mode** – `SESSION_VIEW_OPTIONS` (`Clips / Flipped / Scenes`).
      - **Startup View** – a small set like `Play / Drum / Session` for where Push 1 lands on startup.
      - Optional: show the **pad response meter** (Idea 8) as a visual aid while adjusting the above.
    - **Pro Page (power-user tuning & expression)**
      - **v1 scope (trimmed Pro v1)**
        - **Pad Threshold (full list)** – expose the complete Push 1 threshold table.
        - **Velocity Curve (full list)** – full list of available curves.
        - **Pad Feel Preset (read/write)** – same presets as the Noob page, shown alongside raw values.
        - **Track Cursor Behaviour** – `Step / Page / Swap`.
        - **Scene Cursor Behaviour** – `Step / Page`.
        - **Ribbon Mode Default** – one of the existing `RIBBON_MODE_*` values (e.g. `Pitch / CC / Fader / Last Touched`).
        - **Ribbon Note-Repeat Mode Default** – one of `RIBBON_NOTE_REPEAT_VALUES` (`Off / Period / Length`).
      - **Later-phase expression extensions (beyond v1)**
        - **Velocity Shaping / Compression Mode** – `Off / Soft / Hard / Compressed` as an extra software layer on top of hardware curves.
        - **Accent Mode** – e.g. `Button / Velocity / Both` for how accents are triggered.
        - **Accent Boost Amount** – stepped boost values or fixed velocity targets.
        - **Note Repeat Accent Behaviour** – e.g. `First Hit Only / All Hits`.
        - **Aftertouch Target** – e.g. `Off / Filter / Volume / Pitch / Last Touched`.
        - **Aftertouch Depth** – coarse depth control (e.g. `Low / Medium / High`).

- **Idea 2 – Add a Push-1 `Info` sub-view**
  - Push 2 has `InfoMode` with firmware/board/serial.
  - For Push 1, we already show firmware version in `ConfigurationMode`, but could:
    - Provide a dedicated `Info` page within `ConfigurationMode` that lists:
      - Firmware version, board revision (if available), serial number.
      - Extension version and possibly a short changelog hint (e.g. "SCALES vX.Y").
    - Entry path options:
      - SHIFT + Configuration.
      - Knob 8 press (if supported) when on the main config screen.

- **Idea 3 – Hardware-side reset shortcuts**
  - Mimic `SetupMode` behaviour: `Delete + knob touch` resets some settings.
  - For Push 1 `ConfigurationMode`, reserve a subset of knobs for resettable values (e.g. threshold, curve) and support:
    - Hold `Delete` + touch knob → reset to default preset.
  - Pros:
    - Consistent ergonomics across Push versions.
    - Easier to recover from a bad pad threshold choice.

### 3.2 Session view & navigation improvements

- **Idea 4 – Hardware toggle for `SESSION_VIEW_OPTIONS`**
  - Expose the Clips / Flipped / Scenes choice directly from hardware, instead of only via `ISettingsUI`.
  - **Gesture (locked)**:
    - `SHIFT + Session` cycles `SESSION_VIEW_OPTIONS` (Clips → Flipped → Scenes).
  - Implementation sketch:
    - Extend `SelectSessionViewCommand` to detect `surface.isShiftPressed()` on `ButtonEvent.DOWN` and, when pressed, cycle the `sessionViewSetting` / `SESSION_VIEW_OPTIONS` value in `PushConfiguration`.
    - Reflect the current mode briefly on `Push1Display` (e.g. one-row banner: "SESSION VIEW: SCENES").

- **Idea 5 – Clearer Scenes vs Clips state feedback on Push 1**
  - When `SESSION_VIEW_OPTIONS[2]` (Scenes) is active, `PushControllerSetup` already re-routes views.
  - We could improve Push-1 feedback by:
    - Adding a short notification on mode change (top line of `Push1Display`).
    - Using one of the scene buttons’ LEDs to indicate "Scenes focus" vs "Clips focus".

- **Idea 6 – Navigation helpers for large sessions**
  - Build on existing track/scene cursor options in `PushConfiguration`:
    - Offer an on-device quick toggle between "move by page" and "move by 1".
  - Potential UI:
    - In the extended `ConfigurationMode`, add a row for cursor behaviour with 2–3 textual options.
    - This simply sets the corresponding enums used by `getCursorKeysTrackOption` / `getCursorKeysSceneOption`.

### 3.3 Pad behaviour & expression on Push 1

- **Idea 7 – Friendly pad threshold & curve presets**
  - Today, Push 1 exposes threshold and curve by index via `PUSH_PAD_THRESHOLDS_NAME` / `PUSH_PAD_CURVES_NAME`.
  - We could add higher-level labelled presets built on top of the raw tables, e.g.:
    - "Light touch", "Normal", "Heavy", each mapping to specific threshold/curve pairs.
  - UI options:
    - Additional line in `ConfigurationMode` showing a preset name, with the underlying raw values still tweakable.
    - Or a quick-preset toggle via a button combo (e.g. SHIFT + Accent cycles presets).

- **Idea 8 – Visual pad response meter in `ConfigurationMode`**
  - When configuration is active, use one row on `Push1Display` as a simple velocity meter:
    - Show last played pad’s velocity as a bar (reuse existing bar rendering helpers).
  - This helps users dial in threshold/curve more confidently.

- **Idea 9 – Clarify pad settings as global**
  - Pad configuration is treated as a global property (via global settings), matching the decision in Section 5.3.
  - Ensure the UI clearly communicates that pad sensitivity/curve settings are global-only to avoid confusion.
  - Where relevant, show a short note or icon on configuration screens indicating "Global pad feel" so users do not expect per-project differences.

### 3.4 Scale, layout & ribbon interactions

- **Design note**
  - For any future external or automated control of scale, base, chromatic/in-key, or layout, prefer using existing or newly added **script parameters / configuration values** (driven by Bitwig settings/automation) rather than adding new MIDI note/channel/CC mappings, whenever that is technically possible.

- **Idea 10 – Enhance on-device scale visibility for Push 1**
  - `ScalesMode` and `ScaleLayoutMode` exist but Push 1 only has a text display.
  - Improve clarity by:
    - Showing current scale + root + chromatic/in-key status at the top of `ScalesMode`.
    - Indicating the layout name (e.g. "In-Key 4ths", "Chromatic") and whether pads outside the scale are turned off.
  - Could reuse the menu helpers from `Push1Display` to present scale choices in a compact list.

- **Idea 11 – Ribbon (touchstrip) mode feedback & quick toggles**
  - `PushConfiguration` already supports ribbon modes (`RIBBON_MODE_*`) and note-repeat modes for the ribbon.
  - For Push 1, we can:
    - Add a small ribbon mode indicator somewhere in normal play views (e.g. row of short labels like "PITCH", "CC", "FADER").
    - Keep **existing application behaviour and configuration paths** for ribbon and ribbon note-repeat modes (no new hardware gestures added in this plan).
  - Optional (future): If ergonomics and available gesture space allow it later, introduce SHIFT-based hardware shortcuts (e.g. SHIFT + Tap/TapTempo or SHIFT + Repeat) to cycle ribbon modes and ribbon note-repeat behaviour, but these are explicitly out of scope for the current iteration.

### 3.5 Cross-version parity & internal architecture

- **Idea 12 – Shared configuration metadata for text vs graphics**
  - Right now, Push 1 uses `ConfigurationMode` (text), Push 2/3 use `SetupMode`/`InfoMode` (graphics) with their own layouts.
  - Consider extracting a small configuration metadata layer:
    - A description of each exposed setting (name, group, min/max, default, formatter) that can be rendered by:
      - A text renderer (Push 1, `Push1Display`).
      - A graphic renderer (Push 2/3, existing `IGraphicDisplay` code).
  - Benefits:
    - New settings (e.g. new pad curve controls) can be exposed on both Push 1 and Push 2/3 with minimal duplication.
    - Makes it easier to keep Push 1 from falling behind feature-wise.

- **Idea 13 – Cleaner per-version hooks in `PushControllerSetup`**
  - There are several places where `pushVersion` is checked inline (e.g., choosing which modes to register, which settings observers to add).
  - For new features, consider:
    - Moving version-specific registrations into small helpers: `registerPush1Modes`, `registerPush2Modes`, etc.
    - Using a version-capabilities enum or flag set, so new features can declare their requirements rather than scattering `if (pushVersion == ...)` checks.
  - This is mostly a maintainability improvement but will make further iteration on Push 1 behaviour safer.

### 3.6 Developer tooling & sandboxing

- **Idea 14 – Push 1 behaviour sandbox**
  - Define a small test harness or automated scenario:
    - Uses a dummy/loopback MIDI device to emulate Push 1, verifying pad threshold / curve behaviour and configuration UI navigation.
    - Optionally runs without a physical device connected, to validate `Push1Display` strings and mode transitions.
  - Could live as a separate test or debug entry point to avoid impacting the main extension.

- **Idea 15 – Logging hooks for firmware and pad settings**
  - Provide a debug-mode-only logger that outputs:
    - Firmware version, board revision, serial number on startup.
    - Current pad threshold/curve settings when switched or when entering `ConfigurationMode`.
  - This helps diagnose support issues for Push 1 users without requiring manual reproduction.

---

## 4. Possible Implementation Phases

### Phase 0 – Investigation & UX decisions

- **Tasks**
  - Verify all existing Push 1 button/shift combinations to avoid collisions with new gestures.
  - Confirm which settings are most valuable to surface on-device (survey: note repeat, session view, cursor behaviour, etc.).
  - Catalogue what firmware/board info is reliably available for Push 1 (compared to Push 2/3).

### Phase 1 – `ConfigurationMode` enhancements for Push 1

- **Deliverables**
  - Extended `ConfigurationMode` with a **Noob Page** (safe, commonly used settings) and a **Pro Page** (broader set of performance-related settings), plus any additional pages as needed.
  - Optional `Info` sub-page with firmware and hardware info.
  - Consistent `Delete + knob touch` reset behaviour for pad threshold/curve.
  - Minimal updates to `Push1Display` helpers if necessary (e.g. convenience methods for short banners / headings).
 
- **Implementation plan (ordered)**
  - **Step 1 – Page model and SCENE guards**
    - Add a simple `page` enum/field to `ConfigurationMode` (`NOOB`, `PRO`, later `INFO`).
    - Wire `Scene 1` / `Scene 2` button events in `ConfigurationMode` to switch pages while `Modes.CONFIGURATION` is active.
    - In all views that handle SCENE buttons, add a guard to ignore SCENE presses while `Modes.CONFIGURATION` is active (distributed guard pattern).
  - **Step 2 – Noob Page: knobs & settings**
    - Map knobs 1–8 to the Noob Page settings defined in Section 3.1 (pad feel preset, coarse threshold/curve, note repeat defaults, accent, session view, startup view).
    - For each setting, rely on existing `PushConfiguration` fields where possible; only add new configuration values when no appropriate backing already exists.
  - **Step 3 – Pro Page v1: knobs & settings**
    - Implement the trimmed Pro v1 scope: full threshold/curve lists, pad feel preset, track/scene cursor behaviours, ribbon defaults.
    - Keep page layout stable even if some settings are temporarily stubbed or not yet wired, to avoid churn in the on-device mental model.
  - **Step 4 – Delete + knob touch resets**
    - Mirror the `SetupMode` pattern by extending `ConfigurationMode.onKnobTouch` to check `Delete` state and reset only the relevant settings (e.g. pad threshold/curve, pad feel preset) to defaults.
    - Ensure that reset behaviour is only active while in `ConfigurationMode` to avoid surprises in other modes.
  - **Step 5 – Display layout & pad response meter**
    - Update `updateDisplay1` to render distinct layouts for Noob vs Pro pages, matching the row/column design in Section 3.1.
    - Add an optional pad response meter row that reuses existing bar helpers and a "last hit velocity" value sourced from `PushControlSurface` / `PushPadGrid`.
  - **Step 6 – Testing & toggles**
    - Add temporary debug logging for page switches and key setting changes to help with early testing.
    - Define a simple manual test script covering entry/exit, SCENE tabbing, knob changes, and reset gestures.

- **Risks & mitigations (Phase 1)**
  - **Risk – Breaking existing SCENE button workflows**
    - Mitigation: centralise the configuration-mode check into a small helper that views call before handling SCENE buttons; add regression tests for Session/Play/Drum views.
  - **Risk – Configuration drift between hardware UI and Bitwig settings panels**
    - Mitigation: always route Noob/Pro page changes through `PushConfiguration` setters so that the on-device UX is a thin layer over the canonical settings.
  - **Risk – Overloading the text display and making pages hard to parse**
    - Mitigation: keep labels short, avoid dynamic reflow, and validate layouts on-device early with a minimal spike before filling in all settings.
  - **Risk – Future extension making the page model brittle**
    - Mitigation: treat page IDs and knob roles as a small internal "contract" (documented here) and avoid reusing knobs for unrelated concepts within the same page.

### Phase 2 – Session view & navigation tweaks

- **Deliverables**
  - `SHIFT + Session` as the hardware shortcut for cycling `SESSION_VIEW_OPTIONS` (Clips/Flipped/Scenes).
  - Clear state indication for Clips vs Scenes mode on Push 1 (text + LED where possible).
  - Exposed cursor behaviour options on-device, tied into existing `cursorKeys*` configuration.

### Phase 3 – Pad behaviour UX & ribbon improvements

- **Deliverables**
  - Named pad threshold/curve presets built on top of current tables.
  - Visual pad response meter in `ConfigurationMode`.
  - Ribbon mode indicator + quick toggle gesture (if ergonomically acceptable).
  - Clear in-UI communication that pad sensitivity/curve settings are global-only, in line with the decision in Section 5.3.

### Phase 4 – Cross-version config metadata & cleanup

- **Deliverables**
  - A small abstraction describing configurable parameters in a controller-agnostic way.
  - Updated `ConfigurationMode` and `SetupMode`/`InfoMode` to consume this abstraction.
  - Cleaned-up `PushControllerSetup` version-specific branches for easier future changes.

### Phase 5 – Stabilization & sandbox testing

- **Deliverables**
  - Basic automated/sandbox tests for Push 1 configuration flows.
  - Manual regression test list covering:
    - Enter/exit of `ConfigurationMode` and any new pages.
    - Interaction between hardware shortcuts (e.g. `Session`/SHIFT+`Session`) and `SESSION_VIEW_OPTIONS`.
    - Pad response behaviour across a few preset combinations.
  - Optional: short documentation update in the user manual describing new on-device configuration capabilities.

---

## 5. Open Decisions

### 5.1 How much configuration to move onto the device

- **Decision – Broad coverage of settings via Noob/Pro pages**
  - We will aim to expose most performance-relevant settings on-device.
  - To keep this manageable on a text display, configuration is organized into:
    - A **Noob Page** for the most important, low-risk controls.
    - A **Pro Page** for deeper and less frequently changed options.
  - This balances broad coverage with a clear mental model for different user levels.

### 5.2 Gesture design and backwards compatibility

- **Design guardrails (Decision – locked)**
  - New gestures should:
    - Be hard to trigger accidentally (favouring SHIFT combinations or clearly intentional long-presses).
    - Stay consistent with Push 2/3 conventions where possible.
    - **Prefer SHIFT combos for new gestures**, using long-press only where there is an especially natural hold-based semantic.

- **Questions (still open)**
  - Are users attached to existing SHIFT and mode-button behaviours on Push 1, or is there room for re-mapping within these guardrails?
  - **Ribbon**:
    - Keep ribbon mode and ribbon note-repeat behaviour controlled via existing Bitwig settings and current in-app behaviour; no new ribbon gestures are introduced in this plan.

### 5.3 Global vs project-level pad settings

- **Decision – Keep pad sensitivity/curve settings global (for now)**
  - Pad feel remains a global property to ensure predictable behaviour across projects.
  - This avoids ambiguity about which pad settings are currently in effect.
  - Future phases may explore per-project overrides, but they are explicitly out of scope for the initial implementation.

### 5.4 Push 1 vs Push 2/3 parity level

- **Decision – Conceptual parity, visual divergence**
  - Push 1 should offer conceptually similar capabilities to Push 2/3 (e.g. being able to configure pads, access info, and adjust key performance parameters).
  - Visual richness (graphs, complex layouts, detailed overlays) can remain Push 2/3-only where they depend on the graphic display.
  - New features should be designed so that Push 1 has a clear, text-based counterpart wherever practical.

### 5.5 Configuration metadata abstraction timing

- **Decision – Introduce a shared config metadata layer as a later-phase refactor**
  - The shared configuration metadata abstraction (see Idea 12 and Phase 4) is planned but not required for the first implementation phases.
  - Initial work can continue to use the existing per-mode layout code.
  - Once early iterations of the Noob/Pro pages are stable, we will refactor toward the shared metadata layer to keep Push 1 and Push 2/3 in sync more easily.

---

## 6. Immediate Next Steps

1. Decide which of the improvement clusters (config UX, session navigation, pad behaviour, scales/ribbon) to prioritise for the next push.
2. For the chosen cluster, sketch concrete UI flows (which buttons, what the Push 1 display shows, how to exit) and validate there are no conflicts with existing behaviours.
3. Spike a small change (e.g. adding a single additional field to `ConfigurationMode` and a one-line banner for `SESSION_VIEW_OPTIONS`) to validate the approach before committing to a broader refactor.
4. Once patterns feel good, iterate on the remaining ideas from the chosen cluster, then expand to others in subsequent phases.
