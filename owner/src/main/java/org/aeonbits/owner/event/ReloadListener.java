/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

import java.util.EventListener;

/**
 * The listener interface for receiving reload events. The class that is interested in processing a reload event
 * implements this interface, and the object created with that class is registered with a component, using the
 * component's <code>addReloadListener</code> method. When the reload event occurs, that object's
 * <code>reloadPerformed</code> method is invoked.
 *
 * @author Luigi R. Viggiano
 * @see ReloadEvent
 * @since 1.0.4
 */
public interface ReloadListener extends EventListener {

    /**
     * This method is invoked after the property are reloaded.
     * When this method is invoked we can assume that the changes are effective.
     *
     * @param event the {@link ReloadEvent event} of property reload.
     */
    void reloadPerformed(ReloadEvent event);

}
