/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.Properties;

import static java.util.Collections.unmodifiableList;

/**
 * A semantic event which indicates that a reload occurred.
 *
 * @author Luigi R. Viggiano
 * @see ReloadListener
 * @since 1.0.4
 */
public class ReloadEvent extends Event {

    private final List<PropertyChangeEvent> events;
    private final Properties oldProperties;
    private final Properties newProperties;

    /**
     * Constructs a prototypical Event.
     *
     * @param source        The object on which the Event initially occurred.
     * @param events        The {@link PropertyChangeEvent change events} regarding which properties have been modified
     *                      during the reload.
     * @param oldProperties the properties before the reload.
     * @param newProperties the properties after the reload.
     * @throws IllegalArgumentException if source is null.
     */
    public ReloadEvent(Object source, List<PropertyChangeEvent> events, Properties oldProperties,
                       Properties newProperties) {
        super(source);
        this.events = unmodifiableList(events);
        this.oldProperties = new UnmodifiableProperties(oldProperties);
        this.newProperties = new UnmodifiableProperties(newProperties);
    }

    /**
     * Returns The {@link PropertyChangeEvent change events} regarding which properties have been modified during the
     * reload.
     *
     * @return The {@link PropertyChangeEvent change events} regarding which properties have been modified during the
     *         reload.
     */
    public List<PropertyChangeEvent> getEvents() {
        return events;
    }

    /**
     * Returns the properties before the reload.
     *
     * @return the properties before the reload.
     */
    public Properties getOldProperties() {
        return oldProperties;
    }

    /**
     * Returns the properties after the reload.
     *
     * @return the properties after the reload.
     */
    public Properties getNewProperties() {
        return newProperties;
    }

}
