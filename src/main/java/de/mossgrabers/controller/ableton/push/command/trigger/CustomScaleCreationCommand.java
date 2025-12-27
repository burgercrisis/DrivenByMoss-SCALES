// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;

/**
 * Command to create custom scales directly on Push hardware.
 *
 * @author Jürgen Moßgraber
 */
public class CustomScaleCreationCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration> {
    /**
     * Constructor.
     *
     * @param model   The model
     * @param surface The surface
     */
    public CustomScaleCreationCommand(final IModel model, final PushControlSurface surface) {
        super(model, surface);
    }

    /** {@inheritDoc} */
    @Override
    public void execute(final ButtonEvent event, final int velocity) {
        if (event != ButtonEvent.DOWN)
            return;

        final ModeManager modeManager = this.surface.getModeManager();
        if (modeManager.isActive(Modes.SCALE_CREATION)) {
            modeManager.restore();
            this.model.getHost().showNotification("Scale Creation Mode Exited");
        } else {
            modeManager.setTemporary(Modes.SCALE_CREATION);
            this.model.getHost().showNotification("Scale Creation Mode");
        }
    }
}
