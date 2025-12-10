// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;


/**
 * Command to select the session view.
 *
 * @author Jürgen Moßgraber
 */
public class SelectSessionViewCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    private boolean isTemporary;


    /**
     * Constructor.
     *
     * @param model The model
     * @param surface The surface
     */
    public SelectSessionViewCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
    }


    /**
     * Activate temporary display of session view.
     */
    public void setTemporary ()
    {
        this.isTemporary = true;
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        final ViewManager viewManager = this.surface.getViewManager ();

        if (event == ButtonEvent.DOWN && this.surface.isShiftPressed ())
        {
            final PushConfiguration configuration = this.surface.getConfiguration ();

            int index;
            if (configuration.isScenesClipViewSelected ())
                index = 2;
            else if (configuration.isFlipSession ())
                index = 1;
            else
                index = 0;

            final int newIndex = (index + 1) % 3;

            String label;
            switch (newIndex)
            {
                case 0:
                    configuration.setFlipSession (false);
                    label = "SESSION VIEW: Clips";
                    break;

                case 1:
                    configuration.setFlipSession (true);
                    label = "SESSION VIEW: Flipped";
                    break;

                case 2:
                default:
                    configuration.setSceneView ();
                    label = "SESSION VIEW: Scenes";
                    break;
            }

            this.surface.getDisplay ().notify (label);
            return;
        }

        if (event == ButtonEvent.DOWN)
        {
            this.isTemporary = false;

            // Switch to the preferred session view
            final ModeManager modeManager = this.surface.getModeManager ();
            if (Views.isSessionView (viewManager.getActiveID ()))
            {
                if (modeManager.isActive (Modes.SESSION_VIEW_SELECT))
                    modeManager.restore ();
                else
                    modeManager.setTemporary (Modes.SESSION_VIEW_SELECT);
                return;
            }

            final PushConfiguration configuration = this.surface.getConfiguration ();
            viewManager.setActive (configuration.isScenesClipViewSelected () ? Views.SCENE_PLAY : Views.SESSION);
            return;
        }

        if (event == ButtonEvent.UP && this.isTemporary)
            viewManager.restore ();
    }
}
