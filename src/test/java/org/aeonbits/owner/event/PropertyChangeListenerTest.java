/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Mutable;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.beans.PropertyChangeEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
public class PropertyChangeListenerTest {

    @Mock PropertyChangeListener listener;


    interface MyConfig extends Mutable {
        @DefaultValue("13")
        String primeNumber();
    }

    @Test
    public void testAddPropertyChangeListener() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);

        assertEquals("13", cfg.primeNumber());

        cfg.setProperty("primeNumber", "17");
        assertEquals("17", cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", "17");
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(expectedEvent)));
    }

    private Matcher<PropertyChangeEvent> matches(final PropertyChangeEvent expectedEvent) {
        return new ArgumentMatcher<PropertyChangeEvent>() {
            @Override
            public boolean matches(Object argument) {
                PropertyChangeEvent arg = (PropertyChangeEvent) argument;
                return expectedEvent.getSource() == arg.getSource() &&
                       eq(expectedEvent.getOldValue(), arg.getOldValue()) &&
                       eq(expectedEvent.getNewValue(), arg.getNewValue()) &&
                       eq(expectedEvent.getPropertyName(), arg.getPropertyName());
            }

            private boolean eq(Object expected, Object actual) {
                if (expected == actual) return true;
                if (expected == null) return actual == null;
                return expected.equals(actual);
            }
        };
    }

    @Test
    public void testRemoveProperty() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);

        assertEquals("13", cfg.primeNumber());

        cfg.removeProperty("primeNumber");
        assertNull(cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", null);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(expectedEvent)));
    }

    @Test
    public void testRemovePropertyChangeListener() throws  Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);

        assertEquals("13", cfg.primeNumber());

        cfg.setProperty("primeNumber", "17");
        assertEquals("17", cfg.primeNumber());

        cfg.removePropertyChangeListener(listener);

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", "17");
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(expectedEvent)));
    }

}
