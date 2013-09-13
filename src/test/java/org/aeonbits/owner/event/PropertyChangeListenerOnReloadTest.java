/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Mutable;
import org.aeonbits.owner.TestConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import static org.aeonbits.owner.UtilTest.save;
import static org.aeonbits.owner.event.PropertyChangeMatcher.matches;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
public class PropertyChangeListenerOnReloadTest implements TestConstants {
    private static final String spec = "file:" + RESOURCES_DIR + "/PropertyChangeListenerOnReloadTest.properties";
    private File target;
    @Mock
    private TransactionalPropertyChangeListener listener;
    private MyConfig cfg;

    @Sources(spec)
    @HotReload(1)
    interface MyConfig extends Mutable {
        @DefaultValue("5")
        Integer someInteger();

        @DefaultValue("foobar")
        String someString();

        @DefaultValue("3.14")
        Double someDouble();

        String nullsByDefault();
    }

    @Before
    public void before() throws MalformedURLException {
        target = new File(new URL(spec).getFile());
        cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);
    }

    @After
    public void after() {
        target.delete();
    }


    @Test
    public void testReloadWhenNoChangesHaveBeenMade() throws IOException, RollbackBatchException,
            RollbackOperationException {

        cfg.reload();
        verifyZeroInteractions(listener);
    }

    @Test
    public void testReloadWhenChangeHappen() throws IOException, RollbackBatchException, RollbackOperationException {
        save(target, new Properties() {{
            setProperty("someInteger", "5");
            setProperty("someString", "bazbar");
            setProperty("someDouble", "2.718");
            setProperty("nullByDefault", "NotNullNow");
        }});

        cfg.reload();

        verify(listener, times(3)).beforePropertyChange(any(PropertyChangeEvent.class));
        verify(listener, times(3)).propertyChange(any(PropertyChangeEvent.class));

        PropertyChangeEvent someStringChange = new PropertyChangeEvent(cfg, "someString", "foobar", "bazbar");
        PropertyChangeEvent someDoubleChange = new PropertyChangeEvent(cfg, "someDouble", "3.14", "2.718");
        PropertyChangeEvent nullByDefaultChange = new PropertyChangeEvent(cfg, "nullByDefault", null, "NotNullNow");

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(someStringChange)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(someStringChange)));

        inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(someDoubleChange)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(someDoubleChange)));

        inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(nullByDefaultChange)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(nullByDefaultChange)));

        inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(someStringChange)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(someDoubleChange)));

        inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(someDoubleChange)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(someStringChange)));

        inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(someStringChange)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(nullByDefaultChange)));

        inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(someDoubleChange)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(nullByDefaultChange)));

        inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(nullByDefaultChange)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(someStringChange)));

        inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(nullByDefaultChange)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(someDoubleChange)));

        verifyNoMoreInteractions(listener);
    }

}
