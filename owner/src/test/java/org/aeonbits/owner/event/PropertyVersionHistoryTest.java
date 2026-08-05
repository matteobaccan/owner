/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.event;

import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Mutable;
import org.junit.Test;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

/**
 * OWNER does not store a version history of property values, but it exposes the previous value
 * on every change, so keeping a history is straightforward. This test documents the two ways to
 * get the old value of a key when it is updated.
 *
 * @author Matteo Baccan
 */
public class PropertyVersionHistoryTest {

    interface MyConfig extends Mutable {
        @DefaultValue("apple")
        String fruit();
    }

    @Test
    public void setPropertyReturnsThePreviousValue() {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);

        String old = cfg.setProperty("fruit", "orange");

        assertEquals("apple", old);
        assertEquals("orange", cfg.fruit());
    }

    @Test
    public void propertyChangeListenerCanRecordTheHistoryOfAKey() {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);

        final List<String> history = new ArrayList<>();
        cfg.addPropertyChangeListener("fruit", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                history.add((String) event.getOldValue());
            }
        });

        cfg.setProperty("fruit", "orange");
        cfg.setProperty("fruit", "banana");

        assertEquals(asList("apple", "orange"), history);
        assertEquals("banana", cfg.fruit());
    }

}
