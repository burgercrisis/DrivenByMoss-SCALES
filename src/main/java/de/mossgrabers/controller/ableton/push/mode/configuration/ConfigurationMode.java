// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.configuration;

import de.mossgrabers.controller.ableton.push.PushVersion;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.display.ITextDisplay;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IItem;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.StringUtils;

/**
 * Configuration settings for Push 1.
 *
 * @author Jürgen Moßgraber
 */
public class ConfigurationMode extends BaseMode<IItem>
{
    private enum Page
    {
        NOOB,
        PRO,
        INFO
    }

    private Page page = Page.NOOB;

    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public ConfigurationMode (final PushControlSurface surface, final IModel model)
    {
        super ("Configuration", surface, model);
    }

    /**
     * Handle a scene button press while in configuration mode on Push 1.
     *
     * @param surface The control surface
     * @param buttonID The pressed button
     * @param event The button event
     * @return True if the event was consumed for configuration
     */
    public static boolean handleSceneButtonForConfiguration (final PushControlSurface surface, final ButtonID buttonID, final ButtonEvent event)
    {
        if (surface == null)
            return false;

        if (event != ButtonEvent.DOWN || !ButtonID.isSceneButton (buttonID))
            return false;

        if (surface.getConfiguration ().getPushVersion () != PushVersion.VERSION_1)
            return false;

        final ModeManager modeManager = surface.getModeManager ();
        if (!modeManager.isActive (Modes.CONFIGURATION))
            return false;

        final IMode mode = modeManager.get (Modes.CONFIGURATION);
        if (!(mode instanceof ConfigurationMode))
            return false;

        final int index = buttonID.ordinal () - ButtonID.SCENE1.ordinal ();
        ((ConfigurationMode) mode).setPageBySceneIndex (index);

        // Prevent LONG/UP events from reaching the views for this press.
        surface.setTriggerConsumed (buttonID);
        return true;
    }

    private void setPageBySceneIndex (final int index)
    {
        if (index == 0)
            this.page = Page.NOOB;
        else if (index == 1)
            this.page = Page.PRO;
        else if (index == 2)
            this.page = Page.INFO;
        else
            return;

        this.updateDisplay ();
    }

    /** {@inheritDoc} */
    @Override
    public void onKnobValue (final int index, final int value)
    {
        if (index == 0 || index == 1)
            this.surface.getConfiguration ().changePadThreshold (value);
        else if (index == 2 || index == 3)
            this.surface.getConfiguration ().changeVelocityCurve (value);
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        this.setTouchedKnob (index, isTouched);
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay1 (final ITextDisplay display)
    {
        display.setBlock (0, 0, "Pad Threshold").setBlock (1, 0, this.surface.getSelectedPadThreshold ());
        display.setBlock (0, 1, "Velocity Curve").setBlock (1, 1, this.surface.getSelectedVelocityCurve ());
        display.setBlock (0, 3, "Firmware: " + this.surface.getMajorVersion () + "." + this.surface.getMinorVersion ());
        if (this.surface.getConfiguration ().getPadThresholdPush1 () < 20)
            display.setRow (3, StringUtils.pad ("Low threshold maycause stuck pads!", 68));
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        // Intentionally empty - mode is only for Push 1
    }
}