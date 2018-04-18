/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Mutable;
import org.aeonbits.owner.Reloadable;
import org.aeonbits.owner.TestConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

import static org.aeonbits.owner.util.UtilTest.fileFromURI;
import static org.aeonbits.owner.util.UtilTest.ignoreAndReturnNull;
import static org.aeonbits.owner.util.UtilTest.save;
import static org.aeonbits.owner.event.PropertyChangeMatcher.matches;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
public class EventListenerOnReloadTest implements TestConstants {
    private static final String SPEC = "file:" + RESOURCES_DIR + "/EventListenerOnReloadTest.properties";
    private File target;
    @Mock
    private TransactionalPropertyChangeListener propertyChangeListener;
    @Mock
    private TransactionalReloadListener reloadListener;
    private MyConfig cfg;

    @Before
    public void before() throws URISyntaxException {
        target = fileFromURI(SPEC);
        target.delete();
        cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(propertyChangeListener);
        cfg.addReloadListener(reloadListener);
    }

    @After
    public void after() {
        target.delete();
    }

    @Sources(SPEC)
    interface MyConfig extends Mutable, Reloadable {
        @DefaultValue("5")
        Integer someInteger();

        @DefaultValue("foobar")
        String someString();

        @DefaultValue("3.14")
        Double someDouble();

        String nullsByDefault();
    }

    @Test
    public void testPropertyChangeListenerOnReloadWhenRollbackBatchException() throws Throwable {

        save(target, new Properties() {{
            setProperty("someInteger", "5");
            setProperty("someString", "bazbar");
            setProperty("someDouble", "2.718");
            setProperty("nullsByDefault", "NotNullNow");
        }});

        doNothing().doNothing().doThrow(new RollbackBatchException())
                .when(propertyChangeListener).beforePropertyChange(any(PropertyChangeEvent.class));

        cfg.reload();

        assertEquals(new Integer(5), cfg.someInteger());
        assertEquals("foobar", cfg.someString());
        assertEquals(new Double("3.14"), cfg.someDouble());
        assertNull(cfg.nullsByDefault());
    }


    @Test
    public void testPropertyChangeListenerOnReloadWhenRollbackOperationException() throws Throwable {

        save(target, new Properties() {{
            setProperty("someString", "bazbar");
            setProperty("someDouble", "2.718");
            setProperty("nullsByDefault", "NotNullNow");
        }});

        PropertyChangeEvent eventToRollback = new PropertyChangeEvent(cfg, "someString", "foobar", "bazbar");

        doThrow(new RollbackOperationException())
                .when(propertyChangeListener).beforePropertyChange(argThat(matches(eventToRollback)));

        cfg.reload();

        assertEquals(new Integer(5), cfg.someInteger());
        assertEquals("foobar", cfg.someString());
        assertEquals(new Double("2.718"), cfg.someDouble());
        assertEquals("NotNullNow", cfg.nullsByDefault());
    }

    @Test
    public void testPropertyChangeListenerOnReloadWhenNoChangesHaveBeenMade() throws Throwable {
        cfg.reload();
        verifyZeroInteractions(propertyChangeListener);
    }

    @Test
    public void testPropertyChangeListenerOnReloadWhenChangeHappen() throws Throwable {
        save(target, new Properties() {{
            setProperty("someInteger", "5");
            setProperty("someString", "bazbar");
            setProperty("someDouble", "2.718");
            setProperty("nullsByDefault", "NotNullNow");
        }});

        cfg.reload();

        verify(propertyChangeListener, times(3)).beforePropertyChange(any(PropertyChangeEvent.class));
        verify(propertyChangeListener, times(3)).propertyChange(any(PropertyChangeEvent.class));

        PropertyChangeEvent someStringChange = new PropertyChangeEvent(cfg, "someString", "foobar", "bazbar");
        PropertyChangeEvent someDoubleChange = new PropertyChangeEvent(cfg, "someDouble", "3.14", "2.718");
        PropertyChangeEvent nullByDefaultChange = new PropertyChangeEvent(cfg, "nullsByDefault", null, "NotNullNow");

        InOrder inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(someStringChange)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(someStringChange)));

        inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(someDoubleChange)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(someDoubleChange)));

        inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(nullByDefaultChange)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(nullByDefaultChange)));

        inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(someStringChange)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(someDoubleChange)));

        inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(someDoubleChange)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(someStringChange)));

        inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(someStringChange)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(nullByDefaultChange)));

        inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(someDoubleChange)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(nullByDefaultChange)));

        inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(nullByDefaultChange)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(someStringChange)));

        inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(nullByDefaultChange)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(someDoubleChange)));

        verifyNoMoreInteractions(propertyChangeListener);
    }

    @Test
    public void testReloadListenerIsInvokedOnReload() throws IOException, RollbackBatchException {
        save(target, new Properties() {{
            setProperty("someInteger", "5");
            setProperty("someString", "bazbar");
            setProperty("someDouble", "2.718");
            setProperty("nullsByDefault", "NotNullNow");
        }});

        cfg.reload();

        InOrder inOrder = inOrder(reloadListener);
        inOrder.verify(reloadListener, times(1)).beforeReload(any(ReloadEvent.class));
        inOrder.verify(reloadListener, times(1)).reloadPerformed(any(ReloadEvent.class));
        verifyNoMoreInteractions(reloadListener);

        assertEquals(new Integer(5), cfg.someInteger());
        assertEquals("bazbar", cfg.someString());
        assertEquals(new Double("2.718"), cfg.someDouble());
        assertEquals("NotNullNow", cfg.nullsByDefault());

    }

    @Test
    public void testReloadWhenRollbackBatchExceptionIsThrown() throws Throwable {
        save(target, new Properties() {{
            setProperty("someInteger", "5");
            setProperty("someString", "bazbar");
            setProperty("someDouble", "2.718");
            setProperty("nullsByDefault", "NotNullNow");
        }});

        doThrow(RollbackBatchException.class).when(reloadListener).beforeReload(any(ReloadEvent.class));

        cfg.reload();

        assertEquals(new Integer(5), cfg.someInteger());
        assertEquals("foobar", cfg.someString());
        assertEquals(new Double("3.14"), cfg.someDouble());
        assertNull(cfg.nullsByDefault());
    }

    @Test
    public void testReloadEventIsNotModifiable() throws Throwable {
        save(target, new Properties() {{
            setProperty("someInteger", "5");
            setProperty("someString", "bazbar");
            setProperty("someDouble", "2.718");
            setProperty("nullsByDefault", "NotNullNow");
        }});

        final ReloadEvent[] beforeEvent = new ReloadEvent[1];
        final ReloadEvent[] afterEvent = new ReloadEvent[1];

        cfg.addReloadListener(new TransactionalReloadListener() {
            public void beforeReload(ReloadEvent event) throws RollbackBatchException {
                beforeEvent[0] = event;
            }

            public void reloadPerformed(ReloadEvent event) {
                afterEvent[0] = event;
            }
        });

        cfg.reload();

        assertNotNull(beforeEvent[0]);
        assertNotNull(afterEvent[0]);
        assertSame(beforeEvent[0], afterEvent[0]);

        try {
            beforeEvent[0].getEvents().clear();
            fail("it should return an unmodifiable collection");
        } catch (UnsupportedOperationException x) {
            ignoreAndReturnNull();
        }

        try {
            beforeEvent[0].getOldProperties().clear();
            fail("it should return an unmodifiable collection");
        } catch (UnsupportedOperationException x) {
            ignoreAndReturnNull();
        }

        try {
            beforeEvent[0].getNewProperties().clear();
            fail("it should return an unmodifiable collection");
        } catch (UnsupportedOperationException x) {
            ignoreAndReturnNull();
        }
    }

    @Test
    public void testFullReloadCycle() throws IOException {
        final boolean[] reloadPerformed = new boolean[] {false};
        cfg.addReloadListener(new TransactionalReloadListener() {

            public void beforeReload(ReloadEvent event) throws RollbackBatchException {
                String notAllowedValue = "42";
                String newSomeInteger = event.getNewProperties().getProperty("someInteger");
                if (notAllowedValue.equals(newSomeInteger))
                    throw new RollbackBatchException("42 is not allowed for property 'someInteger'");
            }

            public void reloadPerformed(ReloadEvent event) {
                reloadPerformed[0] = true;
            }

        });

        save(target, new Properties() {{
            setProperty("someInteger", "41");
            setProperty("someString", "bazbar");
            setProperty("someDouble", "2.718");
            setProperty("nullsByDefault", "NotNullNow");
        }});

        cfg.reload();

        assertTrue(reloadPerformed[0]);
        assertEquals(new Integer(41), cfg.someInteger());
        assertEquals("bazbar", cfg.someString());
        assertEquals(new Double("2.718"), cfg.someDouble());
        assertNotNull(cfg.nullsByDefault());

        reloadPerformed[0] = false;

        save(target, new Properties() {{
            setProperty("someInteger", "42");
            setProperty("someString", "blahblah");
            setProperty("someDouble", "1.234");
        }});

        cfg.reload();

        assertFalse(reloadPerformed[0]);
        assertEquals(new Integer(41), cfg.someInteger());
        assertEquals("bazbar", cfg.someString());
        assertEquals(new Double("2.718"), cfg.someDouble());
        assertNotNull(cfg.nullsByDefault());

    }


    @Test
    public void testFullPropertyChangeCycleCycle() throws IOException {
        final boolean[] reloadPerformed = new boolean[] {false};

        cfg.addPropertyChangeListener("someInteger",
                new TransactionalPropertyChangeListener() {
            public void beforePropertyChange(PropertyChangeEvent event)
                    throws RollbackOperationException, RollbackBatchException {
                String notAllowedValue = "88";
                String makesEverythingToRollback = "42";

                String newSomeInteger = (String)event.getNewValue();
                if (notAllowedValue.equals(newSomeInteger))
                    throw new RollbackOperationException("88 is not allowed for property 'someInteger', " +
                            "the single property someInteger is rolled back");

                if (makesEverythingToRollback.equals(newSomeInteger))
                    throw new RollbackBatchException("42 is not allowed for property 'someInteger', " +
                            "the whole event is rolled back");

            }

            public void propertyChange(PropertyChangeEvent evt) {
                reloadPerformed[0] = true;
            }
        });

        save(target, new Properties() {{
            setProperty("someInteger", "41");
            setProperty("someString", "bazbar");
            setProperty("someDouble", "2.718");
            setProperty("nullsByDefault", "NotNullNow");
        }});

        cfg.reload();

        assertTrue(reloadPerformed[0]);
        assertEquals(new Integer(41), cfg.someInteger());
        assertEquals("bazbar", cfg.someString());
        assertEquals(new Double("2.718"), cfg.someDouble());
        assertNotNull(cfg.nullsByDefault());

        reloadPerformed[0] = false;

        cfg.setProperty("someInteger", "55");
        assertTrue(reloadPerformed[0]);
        assertEquals(new Integer(55), cfg.someInteger());

        reloadPerformed[0] = false;


        cfg.setProperty("someInteger", "88");
        // 88 is rolled back.
        assertFalse(reloadPerformed[0]);
        assertEquals(new Integer(55), cfg.someInteger());

        reloadPerformed[0] = false;

        save(target, new Properties() {{
            setProperty("someInteger", "42");
            setProperty("someString", "blahblah");
            setProperty("someDouble", "1.234");
        }});

        cfg.reload();

        assertFalse(reloadPerformed[0]);
        assertEquals(new Integer(55), cfg.someInteger());
        assertEquals("bazbar", cfg.someString());
        assertEquals(new Double("2.718"), cfg.someDouble());
        assertNotNull(cfg.nullsByDefault());


        reloadPerformed[0] = false;

        save(target, new Properties() {{
            setProperty("someInteger", "88");
            setProperty("someString", "this is not rolled back");
            setProperty("someDouble", "1.2345");
        }});

        cfg.reload();

        assertFalse(reloadPerformed[0]);
        // only someInteger=88 is rolled back
        assertEquals(new Integer(55), cfg.someInteger());
        assertEquals("this is not rolled back", cfg.someString());
        assertEquals(new Double("1.2345"), cfg.someDouble());
        assertNull(cfg.nullsByDefault());

    }

}
