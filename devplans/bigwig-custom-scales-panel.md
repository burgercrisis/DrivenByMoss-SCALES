# Bigwig Custom Scales Panel – Dev Plan

## 1. Goal & Scope

- **Goal**
  - Add a panel in the Bitwig ("Bigwig") controller settings for this extension that lets users define and manage *custom musical scales*.
  - Custom scales must integrate with the existing `Scales` / configuration system so all supported controllers can use them transparently.
  - Where possible, **scale selection and behavior should be exposed as script parameters / configuration values**, so controllers and automation work by driving these parameters rather than introducing additional MIDI protocols for scale control.

- **Scope (initial)**
  - 12‑TET scales defined by semitone offsets within an octave (e.g. `0,2,3,5,7,8,10`).
  - Global to the extension (not per‑project) and shared across controllers.
  - CRUD: create, edit, delete named scales, and use them anywhere an existing scale can be selected.

- **Out of scope (for now)**
  - Arbitrary microtonal tunings beyond 12‑TET.
  - Per‑clip/per‑track custom scales.
  - Import/export to external formats (Scala `.scl`, etc.) – can be a later phase.

---

## 2. Current State (relevant pieces)

- **Scale representation**
  - `de.mossgrabers.framework.scale.Scale`
    - Currently used as an enum‑like list of built‑in scale types (e.g. `MAJOR`, `MINOR`, ...).
    - `Scale.getNames()` is used to populate UI drop‑downs.
  - `de.mossgrabers.framework.scale.Scales`
    - Holds current scale state (scale, base note, layout, chromatic vs. in‑key, octave, etc.).
    - Provides operations like `nextScale`, `prevScale`, `nextScaleLayout`, `isChromatic`, `updateScaleProperties(Configuration)`.

- **Configuration & settings**
  - `de.mossgrabers.framework.configuration.AbstractConfiguration`
    - Methods like `activateScaleSetting`, `activateScaleBaseSetting`, `activateScaleInScaleSetting`, `activateScaleLayoutSetting`.
    - Uses `ISettingsUI.getEnumSetting("Scale", CATEGORY_SCALES, scaleNames, defaultScale)` to expose scale selection.
  - `Configuration` (implementation) stores:
    - `scale` (string name), `scaleBase`, `scaleInKey` (bool), `scaleLayout` (string).

- **Controller integration examples**
  - Various `*PlayConfigurationMode` / `ScalesMode` implementations (Push, Maschine, Oxi One, etc.) read/write from `Scales` and `Configuration` but are otherwise agnostic about the actual scale library.
  - `Scales.updateScaleProperties(Configuration)` already syncs current scale state back into configuration.

---

## 3. High‑Level Design

### 3.1 Conceptual model

- **CustomScale model**
  - Fields:
    - `id`: stable identifier (e.g. UUID or normalized name).
    - `name`: user‑visible label.
    - `intervals`: ordered list of semitone offsets `[0..11]` representing scale degrees within one octave, always including `0`.
    - `description` (optional): free text.
  - Constraints:
    - At least 2 degrees.
    - Sorted ascending, unique values, between 0 and 11.

- **Decision (locked)**
  - In v1, `description` is only used inside the Custom Scales panel UI as documentation/notes for the user. It is not surfaced to controller UIs or other parts of the system.

- **Library**
  - A `CustomScaleLibrary` abstraction responsible for:
    - Loading custom scales at startup.
    - Persisting them when changed from the UI.
    - Providing lookup by `id` or `name`.

- **Integration into `Scales`**
  - Treat custom scales as additional entries in the scale list returned to the UI and to controller modes.
  - Ensure they show up wherever `Scale.getNames()` is used today, without requiring each controller mode to be changed individually (ideally).

### 3.2 Storage strategy (high level)

- Use an **external JSON file** (e.g. `custom-scales.json`) in a writable per‑user data location, not under `resources` (because that is packaged read‑only).
- **Primary target location**: Bitwig’s own controller data directory for this extension, if the Bitwig API exposes an appropriate path.
- **Fallbacks** (if needed): a well‑defined per‑OS application data folder for this extension (e.g. `%APPDATA%/DrivenByMoss-SCALES/` on Windows), with small pointers/flags in Bitwig preferences if required.

---

## 4. Bigwig Panel UX

### 4.1 Panel location

- **Where**
  - In Bitwig’s *Controller Settings* for this extension, add a dedicated section/category, e.g. `Scales › Custom Scales`.
  - Implemented through the existing `ISettingsUI` / `SettingsUIImpl` used elsewhere in the project.

### 4.2 Panel layout (MVP)

- **Main panel structure**
  - **Custom scales list**
    - List of existing custom scales by name.
    - Buttons: `Add`, `Edit`, `Delete`, `Duplicate`.
  - **Details editor**
    - Fields:
      - `Name` (text field).
      - `Intervals` (text field, e.g. `0,2,3,5,7,8,10`).
      - `Description` (optional text).
    - Actions:
      - `Save` / `Cancel`.

- **Design decision (locked)**
  - For v1, use a minimal list + text‑field editor UX (no rich grid or piano‑roll editor). Start with this minimal layout and only introduce more complex UIs if `ISettingsUI` constraints or clear usability issues require it.

- **Validation feedback**
  - Invalid interval syntax → inline error (e.g. "Intervals must be integers 0–11, comma‑separated, unique, ascending").
  - Duplicate name → validation error; **names must be unique**.
  - **Decision (locked)**: Use strict validation and reject bad input; scales with out‑of‑range, duplicate, unsorted, or missing‑0 intervals must be corrected by the user before `Save` succeeds.

### 4.3 Interaction flows

- **Add scale**
  - User clicks `Add` → empty editor with sensible defaults (e.g. `0,2,4,5,7,9,11` and name `My Scale`).
  - On `Save`: validate, add to library, persist, refresh list.

- **Edit scale**
  - Select existing scale → click `Edit` → fields pre‑filled.
  - On `Save`: validate, update in library, persist, refresh list.

- **Delete scale**
  - Select scale → click `Delete` → confirm dialog.
  - If currently in use as the active scale, either:
    - Prevent deletion, or
    - Automatically fall back to a default (e.g. Major) and warn the user.

- **Duplicate scale**
  - Select existing scale → click `Duplicate` → creates `Copy of …` with identical intervals to tweak.

---

## 5. Integration with Existing Scale System

### 5.1 How custom scales appear in selection UIs

- **Goal**
  - When the user opens any existing scale selector (e.g. in `CATEGORY_SCALES`), custom scales should appear alongside built‑in ones.

- **Approach**
  - Introduce a `ScaleDescriptor` abstraction used by settings UIs and modes, instead of exposing the raw `Scale` enum directly.
  - `ScaleDescriptor` would unify:
    - `builtIn` scales (existing enum entries).
    - `custom` scales loaded from `CustomScaleLibrary`.
  - The scale name string remains the primary persisted identifier in `Configuration` (backward compatible).

- **Decision (locked)**
  - Present all scales in a single flat list, keeping the existing built‑in ordering and appending all custom scales to the end of that list.

- **Backwards compatibility**
  - Existing projects/configurations that reference only built‑in scale names continue to work without change.
  - When a custom scale is renamed, either:
    - Keep an internal ID separate from the name, or
    - Update configuration strings to the new name (requires a small migration step on rename).

### 5.2 `Scales` class changes

- **Responsibilities to extend**
  - On initialization, `Scales` (or a collaborating service) loads `CustomScaleLibrary` and merges custom scales into its internal list.
  - Methods like `nextScale`, `prevScale`, `changeScale(int)` operate over the combined list.
  - `getScale().getName()` returns either a built‑in or custom name; `updateScaleProperties(Configuration)` remains unchanged from the configuration’s perspective.

- **Possible implementation detail**
  - Internally, maintain a `List<ScaleDescriptor>` and a mapping from name → descriptor.
  - Existing code that expects `Scale` enum values is wrapped, but public APIs exposed to controllers remain as simple as possible.

---

## 6. Implementation Phases

### Phase 0 – Spike / Investigation

- **Tasks**
  - Confirm the exact API surface of `ISettingsUI` / `SettingsUIImpl` (what widgets are available, how to register custom sections).
  - Inspect `Scale` and `Scales` in detail to understand constraints around adding non‑enum data.
  - Finalize the exact user data path and helper functions for the chosen storage mechanism (external JSON file) on all platforms.

### Phase 1 – Data model & storage

- **Deliverables**
  - `CustomScale` (POJO or record).
  - `CustomScaleLibrary` with:
    - `List<CustomScale> loadScales()`.
    - `void saveScales(List<CustomScale>)`.
    - `Optional<CustomScale> findByName(String)`.
  - Serialization format (likely JSON) and versioning strategy.
  - Unit tests for parsing, validation, and persistence.

### Phase 2 – Integration with `Scales`

- **Deliverables**
  - Extension of `Scales` to:
    - Load custom scales on startup.
    - Expose combined list of built‑in + custom scales to configuration and controller modes.
  - Any necessary adaptation so existing modes (Push, Maschine, Oxi One, etc.) work unchanged but can now select custom scales by name.

### Phase 3 – Bigwig panel UI

- **Deliverables**
  - New `Custom Scales` section in the Bitwig controller settings for this extension.
  - List + editor UI with full CRUD and validation.
  - Wiring from this panel to `CustomScaleLibrary` (load on open, save on changes).

### Phase 4 – Stabilization & sandbox testing

- **Deliverables**
  - Manual testing in a dedicated Bitwig sandbox project:
    - Add/edit/delete scales and verify they appear in all relevant scale selectors.
    - Verify persistence across Bitwig restarts.
    - Verify that deleting or renaming scales behaves gracefully when they are in use.
  - Documentation updates:
    - Short section in README / manual describing Custom Scales usage.

---

## 7. Decisions

### 7.1 Storage location for custom scales

- **Decision (locked)**
  - Store custom scales in an **external JSON file** (`custom-scales.json`) with this resolution order:
    - Prefer Bitwig’s own **controller data directory** for this extension when the Bitwig API exposes a suitable path.
    - Otherwise, fall back to a well-defined per‑OS **application data folder** for this extension (e.g. `%APPDATA%/DrivenByMoss-SCALES/` on Windows), optionally referenced from Bitwig preferences if helpful.

### 7.2 Identifier strategy for scales

- **Decision (locked)**
  - For the MVP, use the **scale name as the ID**, with the restriction that renaming a scale should show an inline warning near the save action that it may affect existing setups (no extra confirmation dialog).

### 7.3 Behavior when deleting a scale in use

- **Decision (locked)**
  - Disallow deletion of a custom scale while it is active for any configuration, to avoid surprising changes in live sessions.

### 7.4 Behavior when `custom-scales.json` is corrupted

- **Decision (locked)**
  - If the custom scales file is unreadable or contains invalid JSON:
    - Do **not** overwrite, truncate, or auto-fix the file.
    - Treat it as "no custom scales loaded" for this session.
    - Surface a clear error or warning in the Bigwig custom scales panel so the user can fix or replace the file manually.

---

## 8. Immediate Next Steps

1. Implement Phase 0 investigation tasks and finalize:
   - Available widgets in `ISettingsUI` for building the panel.
   - Final storage location path and access helpers for the external JSON file.
2. Sketch the minimal Bitwig panel layout in code (or mocks) to confirm what is feasible.
3. Revisit decisions from Section 7 only if technical constraints force changes, and document any deviations explicitly in this dev plan.
