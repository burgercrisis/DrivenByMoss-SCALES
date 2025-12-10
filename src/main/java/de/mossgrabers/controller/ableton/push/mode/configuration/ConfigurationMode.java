// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.configuration;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.PushVersion;
import de.mossgrabers.controller.ableton.push.controller.Push1Display;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;

import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.display.ITextDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.constants.Capability;
import de.mossgrabers.framework.daw.constants.Resolution;
import de.mossgrabers.framework.daw.data.IItem;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.StringUtils;
import de.mossgrabers.framework.view.Views;

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

    private static final Views [] STARTUP_VIEWS = new Views []
    {
        Views.PLAY,
        Views.DRUM,
        Views.SESSION
    };

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
        if (this.page == Page.NOOB)
        {
            this.handleNoobKnobValue (index, value);
            return;
        }

        if (this.page == Page.PRO)
        {
            this.handleProKnobValue (index, value);
            return;
        }

        if (index == 0 || index == 1)
            this.surface.getConfiguration ().changePadThreshold (value);
        else if (index == 2 || index == 3)
            this.surface.getConfiguration ().changeVelocityCurve (value);
    }


    private void handleNoobKnobValue (final int index, final int value)
    {
        final PushConfiguration configuration = this.surface.getConfiguration ();
        final IValueChanger valueChanger = this.model.getValueChanger ();

        switch (index)
        {
            case 0:
                this.changePadFeelPreset (configuration, valueChanger, value);
                break;

            case 1:
                configuration.changePadThreshold (value);
                break;

            case 2:
                configuration.changeVelocityCurve (value);
                break;

            case 3:
                this.changeNoteRepeatPeriod (configuration, valueChanger, value);
                break;

            case 4:
                this.changeNoteRepeatLength (configuration, valueChanger, value);
                break;

            case 5:
                this.changeAccentLevel (configuration, valueChanger, value);
                break;

            case 6:
                this.changeSessionViewMode (configuration, valueChanger, value);
                break;

            case 7:
                this.changeStartupView (configuration, valueChanger, value);
                break;

            default:
                break;
        }
    }


    private void handleProKnobValue (final int index, final int value)
    {
        final PushConfiguration configuration = this.surface.getConfiguration ();
        final IValueChanger valueChanger = this.model.getValueChanger ();

        switch (index)
        {
            case 0:
                this.changePadFeelPreset (configuration, valueChanger, value);
                break;

            case 1:
                configuration.changePadThreshold (value);
                break;

            case 2:
                configuration.changeVelocityCurve (value);
                break;

            case 3:
            {
                final int current = configuration.getCursorKeysTrackOption ();
                final boolean inc = valueChanger.isIncrease (value);
                final int max = PushConfiguration.CURSOR_KEYS_TRACK_OPTION_SWAP;
                final int next = Math.max (0, Math.min (max, current + (inc ? 1 : -1)));
                if (next != current)
                    configuration.setCursorKeysTrackOption (next);
                break;
            }

            case 4:
            {
                final int current = configuration.getCursorKeysSceneOption ();
                final boolean inc = valueChanger.isIncrease (value);
                final int max = PushConfiguration.CURSOR_KEYS_SCENE_OPTION_MOVE_BANK_BY_1;
                final int next = Math.max (0, Math.min (max, current + (inc ? 1 : -1)));
                if (next != current)
                    configuration.setCursorKeysSceneOption (next);
                break;
            }

            case 5:
            {
                final int current = configuration.getRibbonMode ();
                final boolean inc = valueChanger.isIncrease (value);
                final int max = PushConfiguration.RIBBON_MODE_LAST_TOUCHED;
                final int next = Math.max (PushConfiguration.RIBBON_MODE_PITCH, Math.min (max, current + (inc ? 1 : -1)));
                if (next != current)
                    configuration.setRibbonMode (next);
                break;
            }

            case 6:
            {
                final int current = configuration.getRibbonNoteRepeat ();
                final boolean inc = valueChanger.isIncrease (value);
                final int max = PushConfiguration.NOTE_REPEAT_LENGTH;
                final int next = Math.max (PushConfiguration.NOTE_REPEAT_OFF, Math.min (max, current + (inc ? 1 : -1)));
                if (next != current)
                    configuration.setRibbonNoteRepeat (next);
                break;
            }

            default:
                break;
        }
    }


    private void changePadFeelPreset (final PushConfiguration configuration, final IValueChanger valueChanger, final int value)
    {
        final int numThresholds = PushControlSurface.PUSH_PAD_THRESHOLDS_NAME.size ();
        final int numCurves = PushControlSurface.PUSH_PAD_CURVES_NAME.size ();
        if (numThresholds == 0 || numCurves == 0)
            return;

        final int [] thresholdPresets = new int []
        {
            Math.max (0, numThresholds / 4),
            Math.max (0, numThresholds / 2),
            Math.max (0, numThresholds - 1)
        };

        final int [] curvePresets = new int []
        {
            0,
            Math.max (0, numCurves / 2),
            Math.max (0, numCurves - 1)
        };

        final int currentThreshold = configuration.getPadThresholdPush1 ();
        final int currentCurve = configuration.getVelocityCurve ();

        int presetIndex = 0;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < thresholdPresets.length; i++)
        {
            final int dt = thresholdPresets[i] - currentThreshold;
            final int dc = curvePresets[i] - currentCurve;
            final int score = dt * dt + dc * dc;
            if (score < bestScore)
            {
                bestScore = score;
                presetIndex = i;
            }
        }

        final boolean inc = valueChanger.isIncrease (value);
        presetIndex = Math.max (0, Math.min (thresholdPresets.length - 1, presetIndex + (inc ? 1 : -1)));

        configuration.setPadThresholdPush1 (thresholdPresets[presetIndex]);
        configuration.setVelocityCurve (curvePresets[presetIndex]);
    }


    private void changeNoteRepeatPeriod (final PushConfiguration configuration, final IValueChanger valueChanger, final int value)
    {
        final int index = Resolution.getMatch (configuration.getNoteRepeatPeriod ().getValue ());
        final int sel = Resolution.change (index, valueChanger.isIncrease (value));
        configuration.setNoteRepeatPeriod (Resolution.values ()[sel]);
    }


    private void changeNoteRepeatLength (final PushConfiguration configuration, final IValueChanger valueChanger, final int value)
    {
        if (!this.model.getHost ().supports (Capability.NOTE_REPEAT_LENGTH))
            return;

        final int index = Resolution.getMatch (configuration.getNoteRepeatLength ().getValue ());
        final int sel = Resolution.change (index, valueChanger.isIncrease (value));
        configuration.setNoteRepeatLength (Resolution.values ()[sel]);
    }


    private void changeAccentLevel (final PushConfiguration configuration, final IValueChanger valueChanger, final int value)
    {
        final int [] levels = new int []
        {
            0,
            96,
            110,
            127
        };

        int currentIndex;
        if (!configuration.isAccentActive ())
            currentIndex = 0;
        else
        {
            final int current = configuration.getFixedAccentValue ();
            int bestIndex = 1;
            int bestDiff = Integer.MAX_VALUE;
            for (int i = 1; i < levels.length; i++)
            {
                final int diff = Math.abs (levels[i] - current);
                if (diff < bestDiff)
                {
                    bestDiff = diff;
                    bestIndex = i;
                }
            }
            currentIndex = bestIndex;
        }

        final boolean inc = valueChanger.isIncrease (value);
        final int newIndex = Math.max (0, Math.min (levels.length - 1, currentIndex + (inc ? 1 : -1)));

        if (newIndex == 0)
        {
            configuration.setAccentEnabled (false);
            return;
        }

        configuration.setAccentEnabled (true);
        configuration.setFixedAccentValue (levels[newIndex]);
    }


    private void changeSessionViewMode (final PushConfiguration configuration, final IValueChanger valueChanger, final int value)
    {
        int index;
        if (configuration.isScenesClipViewSelected ())
            index = 2;
        else if (configuration.isFlipSession ())
            index = 1;
        else
            index = 0;

        final boolean inc = valueChanger.isIncrease (value);
        final int newIndex = Math.max (0, Math.min (2, index + (inc ? 1 : -1)));

        if (newIndex == index)
            return;

        switch (newIndex)
        {
            case 0:
                configuration.setFlipSession (false);
                break;

            case 1:
                configuration.setFlipSession (true);
                break;

            case 2:
            default:
                configuration.setSceneView ();
                break;
        }
    }


    private void changeStartupView (final PushConfiguration configuration, final IValueChanger valueChanger, final int value)
    {
        final Views currentView = configuration.getStartupView ();
        int index = 0;
        for (int i = 0; i < STARTUP_VIEWS.length; i++)
        {
            if (STARTUP_VIEWS[i] == currentView)
            {
                index = i;
                break;
            }
        }

        final boolean inc = valueChanger.isIncrease (value);
        final int max = STARTUP_VIEWS.length - 1;
        final int newIndex = Math.max (0, Math.min (max, index + (inc ? 1 : -1)));
        if (newIndex == index)
            return;

        configuration.setStartupView (STARTUP_VIEWS[newIndex]);
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        this.setTouchedKnob (index, isTouched);

        if (!isTouched || !this.surface.isDeletePressed ())
            return;

        this.surface.setTriggerConsumed (ButtonID.DELETE);

        final PushConfiguration configuration = this.surface.getConfiguration ();

        switch (index)
        {
            case 0:
                configuration.setPadThresholdPush1 (20);
                configuration.setVelocityCurve (1);
                break;

            case 1:
                configuration.setPadThresholdPush1 (20);
                break;

            case 2:
                configuration.setVelocityCurve (1);
                break;

            default:
                break;
        }
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay1 (final ITextDisplay display)
    {
        final PushConfiguration configuration = this.surface.getConfiguration ();

        if (this.page == Page.INFO)
        {
            final String firmware = "Firmware: " + this.surface.getMajorVersion () + "." + this.surface.getMinorVersion ();
            final String startup = "Startup: " + Views.getViewName (configuration.getStartupView ());

            final String session;
            if (configuration.isScenesClipViewSelected ())
                session = "Session: Scenes";
            else if (configuration.isFlipSession ())
                session = "Session: Flipped";
            else
                session = "Session: Clips";

            display.setBlock (0, 0, "Config Info");
            display.setBlock (0, 1, firmware);
            display.setBlock (0, 2, startup);
            display.setBlock (0, 3, session);
            return;
        }

        if (this.page == Page.PRO)
        {
            final String threshold = this.surface.getSelectedPadThreshold ();
            final String curve = this.surface.getSelectedVelocityCurve ();

            final String [] trackNames =
            {
                "Trk: Page",
                "Trk: Step",
                "Trk: Swap"
            };
            final String [] sceneNames =
            {
                "Scn: Page",
                "Scn: Step"
            };

            final int trackOption = configuration.getCursorKeysTrackOption ();
            final int sceneOption = configuration.getCursorKeysSceneOption ();

            final String trackLabel = trackNames[Math.max (0, Math.min (trackNames.length - 1, trackOption))];
            final String sceneLabel = sceneNames[Math.max (0, Math.min (sceneNames.length - 1, sceneOption))];

            final int ribbonMode = configuration.getRibbonMode ();
            final String ribbonLabel;
            switch (ribbonMode)
            {
                case PushConfiguration.RIBBON_MODE_PITCH:
                    ribbonLabel = "Rib: Pitch";
                    break;
                case PushConfiguration.RIBBON_MODE_CC:
                    ribbonLabel = "Rib: CC";
                    break;
                case PushConfiguration.RIBBON_MODE_CC_PB:
                    ribbonLabel = "Rib: CC/PB";
                    break;
                case PushConfiguration.RIBBON_MODE_PB_CC:
                    ribbonLabel = "Rib: PB/CC";
                    break;
                case PushConfiguration.RIBBON_MODE_FADER:
                    ribbonLabel = "Rib: Fader";
                    break;
                case PushConfiguration.RIBBON_MODE_LAST_TOUCHED:
                    ribbonLabel = "Rib: Last";
                    break;
                default:
                    ribbonLabel = "Rib: ?";
                    break;
            }

            final int ribbonNR = configuration.getRibbonNoteRepeat ();
            final String ribbonNRLabel;
            switch (ribbonNR)
            {
                case PushConfiguration.NOTE_REPEAT_PERIOD:
                    ribbonNRLabel = "NR: Period";
                    break;
                case PushConfiguration.NOTE_REPEAT_LENGTH:
                    ribbonNRLabel = "NR: Length";
                    break;
                case PushConfiguration.NOTE_REPEAT_OFF:
                default:
                    ribbonNRLabel = "NR: Off";
                    break;
            }

            display.setBlock (0, 0, "Threshold").setBlock (1, 0, threshold);
            display.setBlock (0, 1, "Curve").setBlock (1, 1, curve);
            display.setBlock (0, 2, trackLabel);
            display.setBlock (0, 3, sceneLabel);

            display.setBlock (1, 2, ribbonLabel);
            display.setBlock (1, 3, ribbonNRLabel);

            final int lastVelocity = this.surface.getLastPadVelocity ();
            display.setBlock (2, 0, "Pad Vel");
            display.setBlock (2, 1, Push1Display.formatValue (lastVelocity, 127));

            display.setBlock (3, 0, "Pro");
            return;
        }

        display.setBlock (0, 0, "Pad Threshold").setBlock (1, 0, this.surface.getSelectedPadThreshold ());
        display.setBlock (0, 1, "Velocity Curve").setBlock (1, 1, this.surface.getSelectedVelocityCurve ());
        display.setBlock (0, 3, "Firmware: " + this.surface.getMajorVersion () + "." + this.surface.getMinorVersion ());

        final int lastVelocityNoob = this.surface.getLastPadVelocity ();
        display.setBlock (2, 0, "Pad Vel");
        display.setBlock (2, 1, Push1Display.formatValue (lastVelocityNoob, 127));

        display.setBlock (3, 0, "Noob");

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