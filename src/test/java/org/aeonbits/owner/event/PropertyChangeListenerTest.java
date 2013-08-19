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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.beans.PropertyChangeEvent;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
@Ignore
public class PropertyChangeListenerTest {

    @Mock PropertyChangeListener listener;


    interface MyConfig extends Mutable {
        @DefaultValue("13")
        int primeNumber();
    }

    @Test
    public void testAddPropertyChangeListener() throws RollbackPropertyChangeException, RollbackReloadException {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);

        assertEquals(13, cfg.primeNumber());

        cfg.setProperty("primeNumber", "17");
        assertEquals(17, cfg.primeNumber());


        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", "17");
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(eq(expectedEvent));
        inOrder.verify(listener, times(1)).propertyChange(eq(expectedEvent));
    }


    @Ignore
    @Test
    public void testRemovePropertyChangeListener() {

    }
}
