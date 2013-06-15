/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

/**
 * A semantic event which indicates that a reload occurred.
 *
 * @author Luigi R. Viggiano
 * @see ReloadListener
 * @since 1.0.4
 */
public class ReloadEvent extends Event {
    /**
     * Constructs a prototypical Event.
     *
     * @param source The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public ReloadEvent(Object source) {
        super(source);
    }
}
