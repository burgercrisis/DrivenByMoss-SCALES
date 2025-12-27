What I'm doing:

 - **[Bitwig Custom Scales Panel](devplans/bigwig-custom-scales-panel.md)** – global custom scale library plus Bitwig settings panel shared across controllers.
 - **[Push 1 Controller Improvements](devplans/push1-controller-improvements.md)** – richer on-device configuration pages, pad feel presets, and session/navigation tweaks.
 - **[Scale Control via Script Parameters](devplans/push-midi-scale-control.md)** – treat scale, base note, in-key/chromatic, and layout as canonical script parameters for automation and Remote Controls.
 - **[Push 1 Sequencer Mode – Ratchets & Polymeter Lanes](devplans/push1-sequencer-mode.md)** – new note sequencer grid with ratchets and polymeter lanes in an advanced Note/Play selector bank.
 - **[Push Parameter Sequencer Mode](devplans/push-parameter-sequencer-mode.md)** – CC/automation sequencer layered with notes in the same Bitwig clip.
 - **[Future Sequencer & Arpeggio Ideas](devplans/push-future-sequencer-and-arpeggio-ideas.md)** – backlog of experimental sequencer/arp concepts to explore after the core modes land.

What Moss did:


# DrivenByMoss
Bitwig Studio extensions to support several controllers

### Building and Installing the extension

Users should download and install the version from the
[main site](http://www.mossgrabers.de/Software/Bitwig/Bitwig.html).
These directions are for developers to test changes prior to release.

1. Install Maven and dependences, either [from here](https://maven.apache.org/install.html)
or if on Linux, using the distro package manager, e.g. `yum install maven` or
`apt-get install maven`.
2. Run `mvn install` in this repo's root.
3. Follow [installation instructions] in the included manual for further steps.
