# Push – Future Sequencer & Arpeggio Ideas

This document tracks *potential* future integrations and experiments for Push controllers in the SCALES/Sequencer area. It is intentionally exploratory and not part of the committed roadmap yet.

## 1. Markov melodic mode

- **Concept**
  - Use a simple Markov (or Markov-like) process to generate melodic lines inside the current scale.
  - The user defines a source pattern (or several patterns); transitions between notes are learned from that material and then used to generate new sequences.
- **Possible behaviours**
  - Strength / randomness control from "follow the original pattern closely" to "free generative".
  - Option to bias towards scale degrees, chord tones, or specific intervals.
  - Can run as:
    - A standalone generative clip mode, or
    - A modifier layered on top of an existing step pattern (occasionally replacing or inserting notes).

## 2. Pattern mutation / evolve

- **Concept**
  - Treat an existing step pattern as a "seed" and provide controlled mutation tools that introduce variation over time.
- **Possible behaviours**
  - One-shot operations like:
    - Slightly randomise velocities, microtiming, or selected steps.
    - Swap or rotate small fragments.
    - Constrain mutations to the current scale / chord.
  - Continuous "evolve" mode:
    - Each loop, apply a small random-but-musical change with user-set intensity.
  - Option to store / recall different mutation snapshots as variations of the same clip.

## 3. Scale-degree sequencer modes

- **Concept**
  - Instead of storing absolute MIDI pitches, steps store *relative* scale degrees (1, 2, 3, ♭3, 5, etc.) or chord degrees relative to the current root/scale.
- **Possible behaviours**
  - Changing root or scale automatically re-renders the pattern in a musically equivalent way.
  - Possible variants:
    - Monophonic scale-degree lane (single note per step).
    - Chord-degree lane where each step chooses a chord function (I, IV, V, vi, etc.).
  - Integration points with existing SCALES logic for scale selection, chord sets, and transposition.

## 4. Pattern remix mode

- **Concept**
  - Live tools to apply structural transforms to an existing pattern (without necessarily editing individual steps by hand).
- **Possible behaviours**
  - Common transforms:
    - Reverse, mirror, rotate left/right.
    - Thin / densify (e.g. mute every Nth step, or duplicate selected steps).
    - Compress / expand pattern length while keeping relative accents.
  - Pads or scene buttons trigger transforms temporarily (performance) or with a "commit" gesture to write back the transformed result.

## 5. Groove lane / swing sequencer

- **Concept**
  - Provide a dedicated lane (or lanes) that stores timing offsets and groove information separate from the raw on/off pattern.
- **Possible behaviours**
  - Per-step or per-group timing shifts (early/late) on top of the grid.
  - Pattern-based swing instead of global swing only.
  - Re-usable groove patterns that can be applied across multiple clips or tracks.
  - Visual feedback on Push for where groove is applied (e.g. different pad brightness or display indicators).

## 6. Contour arpeggios (non-sequencer)

- **Concept**
  - A performance-oriented arpeggio mode that sequences *interval contours* (up/down/same + relative size) instead of fixed notes or fixed step patterns.
- **Possible behaviours**
  - The player holds chords or notes; the contour pattern determines how the arpeggio climbs, falls, or circles around within the scale.
  - Contours defined as sequences like: +2, +1, -3, 0, etc., interpreted against:
    - The current scale degrees, and/or
    - The currently held chord tones.
  - Controls for:
    - Direction bias (mostly up, mostly down, alternating).
    - Register / octave span and how aggressively it moves across registers.
  - Can be combined with existing arpeggiator features (rate, gate, swing) but with a distinct "shape-first" workflow.

---

## Open questions

- How many of these should become *dedicated modes* versus optional modifiers on top of the existing step sequencer?
- Which of these are primarily studio / composition tools versus live performance features?
- What is the minimum viable UI for each idea on Push 1 (buttons, encoders, simple text-only feedback)?
- How should these ideas integrate with the existing SCALES and chord infrastructure to maximise reuse and consistency?
