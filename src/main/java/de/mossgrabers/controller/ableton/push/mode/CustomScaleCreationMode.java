// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode;

import java.util.Arrays;
import java.util.List;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.display.ITextDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IItem;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.featuregroup.AbstractMode;
import de.mossgrabers.framework.scale.CustomScale;
import de.mossgrabers.framework.scale.CustomScaleLibrary;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.utils.ButtonEvent;

/**
 * Mode for creating custom scales directly on Push hardware.
 *
 * @author Jürgen Moßgraber
 */
public class CustomScaleCreationMode extends BaseMode<IItem> {
    private static final String[] SCALE_TEMPLATES = {
            "Major",
            "Minor",
            "Pentatonic",
            "Blues",
            "Dorian",
            "Mixolydian",
            "Chromatic",
            "Custom"
    };

    private static final int[][] TEMPLATE_INTERVALS = {
            { 0, 2, 4, 5, 7, 9, 11 }, // Major
            { 0, 2, 3, 5, 7, 8, 10 }, // Minor
            { 0, 2, 4, 7, 9, -1, -1 }, // Pentatonic
            { 0, 3, 5, 6, 7, 10, -1 }, // Blues
            { 0, 2, 3, 5, 7, 9, 10 }, // Dorian
            { 0, 2, 4, 5, 7, 9, 10 }, // Mixolydian
            { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 }, // Chromatic (first 7 shown)
            { -1, -1, -1, -1, -1, -1, -1 } // Custom (empty)
    };

    private static final String[] INTERVAL_NAMES = {
            "Off",
            "Root",
            "2nd",
            "3rd",
            "4th",
            "5th",
            "6th",
            "7th"
    };

    private static final String[] PRESET_NAMES = {
            "Custom 1",
            "Custom 2",
            "Custom 3",
            "Custom 4",
            "Custom 5",
            "Custom 6",
            "Custom 7",
            "Custom 8",
            "My Scale",
            "My Scale 2",
            "My Scale 3",
            "My Scale 4"
    };

    private final IHost host;
    private final Scales scales;
    private final IValueChanger valueChanger;

    private int selectedTemplate = 0;
    private int[] currentIntervals = new int[7];
    private int selectedNameIndex = 0;
    private boolean isEditingName = false;

    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model   The model
     */
    public CustomScaleCreationMode(final PushControlSurface surface, final IModel model) {
        super("Create Scale", surface, model);
        this.host = model.getHost();
        this.scales = model.getScales();
        this.valueChanger = model.getValueChanger();

        // Initialize with first template
        this.loadTemplate(0);
    }

    /** {@inheritDoc} */
    @Override
    public void onKnobValue(final int index, final int value) {
        if (!this.increaseKnobMovement())
            return;

        if (index == 0) {
            // Template selection
            final int newTemplate = this.valueChanger.changeValue(value, this.selectedTemplate, 0,
                    SCALE_TEMPLATES.length - 1);
            if (newTemplate != this.selectedTemplate) {
                this.selectedTemplate = newTemplate;
                this.loadTemplate(this.selectedTemplate);
                this.host.showNotification("Template: " + SCALE_TEMPLATES[this.selectedTemplate]);
            }
        } else if (index >= 1 && index <= 7) {
            // Interval editing
            final int intervalIndex = index - 1;
            final int currentInterval = this.currentIntervals[intervalIndex];
            int newInterval = currentInterval;

            if (currentInterval == -1)
                newInterval = 0; // Start with Root
            else if (currentInterval == 0)
                newInterval = 2; // Skip 1 (can't have 1st)
            else if (currentInterval == 11)
                newInterval = -1; // Back to Off
            else
                newInterval = currentInterval + 1;

            this.currentIntervals[intervalIndex] = newInterval;
            this.host.showNotification("Interval " + (intervalIndex + 1) + ": " + this.getIntervalName(newInterval));
        } else if (index == 7 && this.isEditingName) {
            // Name selection
            final int newNameIndex = this.valueChanger.changeValue(value, this.selectedNameIndex, 0,
                    PRESET_NAMES.length - 1);
            if (newNameIndex != this.selectedNameIndex) {
                this.selectedNameIndex = newNameIndex;
                this.host.showNotification("Name: " + PRESET_NAMES[this.selectedNameIndex]);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onKnobTouch(final int index, final boolean isTouched) {
        this.setTouchedKnob(index, isTouched);

        if (!isTouched || !this.surface.isDeletePressed())
            return;

        this.surface.setTriggerConsumed(ButtonID.DELETE);

        if (index >= 1 && index <= 7) {
            // Reset interval to Off
            this.currentIntervals[index - 1] = -1;
            this.host.showNotification("Interval " + index + " reset");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onFirstRow(final int index, final ButtonEvent event) {
        if (event != ButtonEvent.UP)
            return;

        if (index == 7) {
            // Save button
            this.saveCustomScale();
        } else if (index == 6) {
            // Toggle name editing
            this.isEditingName = !this.isEditingName;
            this.host.showNotification(this.isEditingName ? "Editing Name" : "Editing Intervals");
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getButtonColor(final ButtonID buttonID) {
        final int index = this.isButtonRow(0, buttonID);
        if (index >= 0) {
            if (index == 6)
                return this.colorManager.getColorIndex(
                        this.isEditingName ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON);
            if (index == 7)
                return this.colorManager.getColorIndex(AbstractMode.BUTTON_COLOR_HI);
        }

        return super.getButtonColor(buttonID);
    }

    /** {@inheritDoc} */
    @Override
    public void updateDisplay1(final ITextDisplay display) {
        // Header
        display.setBlock(0, 0, "Create Custom Scale");
        display.setBlock(0, 2, "Template: " + SCALE_TEMPLATES[this.selectedTemplate]);

        // Intervals
        display.setBlock(1, 0, "Intervals:");
        for (int i = 0; i < 7; i++) {
            final String intervalName = this.getIntervalName(this.currentIntervals[i]);
            display.setCell(2, i, (i + 1) + ":" + intervalName);
        }

        // Name
        display.setBlock(3, 0, "Name: " + PRESET_NAMES[this.selectedNameIndex]);
        display.setBlock(3, 2, this.isEditingName ? "(Editing)" : "");

        // Instructions
        display.setBlock(0, 3, "Knob1: Template");
        display.setBlock(1, 3, "Knobs2-8: Intervals");
        display.setBlock(2, 3, "Btn6: Name | Btn7: Save");
    }

    /** {@inheritDoc} */
    @Override
    public void updateDisplay2(final IGraphicDisplay display) {
        display.addOptionElement("Template", SCALE_TEMPLATES[this.selectedTemplate], false, "", "", false, false);

        // Show intervals
        for (int i = 0; i < 7; i++) {
            final String intervalName = this.getIntervalName(this.currentIntervals[i]);
            display.addOptionElement("Interval " + (i + 1), intervalName, false, "", "", false, false);
        }

        // Name section
        display.addOptionElement("Scale Name", PRESET_NAMES[this.selectedNameIndex], this.isEditingName, "", "", false,
                false);

        // Instructions
        display.addOptionElement("", "Press Save (Btn7) to create scale", false, "", "", false, false);
    }

    /**
     * Load a template into the current intervals.
     *
     * @param templateIndex The template index
     */
    private void loadTemplate(final int templateIndex) {
        final int[] template = TEMPLATE_INTERVALS[templateIndex];
        System.arraycopy(template, 0, this.currentIntervals, 0, Math.min(template.length, 7));
    }

    /**
     * Get the display name for an interval value.
     *
     * @param interval The interval value (-1 for off)
     * @return The display name
     */
    private String getIntervalName(final int interval) {
        if (interval == -1)
            return "Off";
        if (interval == 0)
            return "Root";
        if (interval >= 2 && interval <= 7)
            return INTERVAL_NAMES[interval];
        return "Err";
    }

    /**
     * Save the current custom scale.
     */
    private void saveCustomScale() {
        try {
            // Build intervals array (filter out -1 values)
            final int[] intervals = Arrays.stream(this.currentIntervals)
                    .filter(i -> i != -1)
                    .toArray();

            if (intervals.length == 0) {
                this.host.showNotification("Error: No intervals selected");
                return;
            }

            // Validate intervals
            if (!this.validateIntervals(intervals)) {
                this.host.showNotification("Error: Invalid scale intervals");
                return;
            }

            // Create custom scale
            final String name = PRESET_NAMES[this.selectedNameIndex];
            final String id = "push-" + System.currentTimeMillis();
            final CustomScale customScale = new CustomScale(id, name, intervals, "Created on Push");

            // Save to library
            final CustomScaleLibrary library = new CustomScaleLibrary(CustomScaleLibrary.getDefaultFile());
            final List<CustomScale> existingScales = library.loadAll();

            // Check for duplicate names
            for (final CustomScale existing : existingScales) {
                if (name.equals(existing.getName())) {
                    this.host.showNotification("Error: Scale name already exists");
                    return;
                }
            }

            existingScales.add(customScale);
            library.saveAll(existingScales);

            // Update scales system
            this.scales.setCustomScales(existingScales);

            this.host.showNotification("Scale saved: " + name);

            // Reset for next scale
            this.selectedTemplate = 0;
            this.selectedNameIndex = (this.selectedNameIndex + 1) % PRESET_NAMES.length;
            this.loadTemplate(0);
        } catch (final Exception ex) {
            this.host.showNotification("Error saving scale: " + ex.getMessage());
        }
    }

    /**
     * Validate that intervals form a valid scale.
     *
     * @param intervals The intervals to validate
     * @return True if valid
     */
    private boolean validateIntervals(final int[] intervals) {
        // Must contain root (0)
        boolean hasRoot = false;
        for (final int interval : intervals) {
            if (interval == 0) {
                hasRoot = true;
                break;
            }
        }
        if (!hasRoot)
            return false;

        // Must be strictly ascending
        for (int i = 1; i < intervals.length; i++) {
            if (intervals[i] <= intervals[i - 1])
                return false;
        }

        // Must be within 0-11 range
        for (final int interval : intervals) {
            if (interval < 0 || interval > 11)
                return false;
        }

        return true;
    }
}
