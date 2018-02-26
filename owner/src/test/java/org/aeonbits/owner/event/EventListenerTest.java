/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.LoadersManagerForTest;
import org.aeonbits.owner.Mutable;
import org.aeonbits.owner.PropertiesManagerForTest;
import org.aeonbits.owner.VariablesExpanderForTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.beans.PropertyChangeEvent;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.aeonbits.owner.event.PropertyChangeMatcher.matches;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
public class EventListenerTest {

    @Mock
    private TransactionalPropertyChangeListener propertyChangeListener;

    @Mock
    private Properties props;

    @Mock
    private ScheduledExecutorService scheduler;

    @Mock
    private VariablesExpanderForTest expander;

    private LoadersManagerForTest loaders = new LoadersManagerForTest();

    @Mock
    private ReloadListener reloadListener;
    private PropertiesManagerForTest propertiesManager;

    interface MyConfig extends Mutable {
        @DefaultValue("13")
        String primeNumber();
    }

    @Test
    public void testSetProperty() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(propertyChangeListener);

        assertEquals("13", cfg.primeNumber());

        cfg.setProperty("primeNumber", "17");
        assertEquals("17", cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", "17");
        InOrder inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(expectedEvent)));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSetPropertyWhenValuesAreEqual() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(propertyChangeListener);
        assertEquals("13", cfg.primeNumber());

        cfg.setProperty("primeNumber", "13");
        assertEquals("13", cfg.primeNumber());

        verifyZeroInteractions(propertyChangeListener);
    }

    @Test
    public void testSetPropertyThrowingRollbackOperationException() throws Throwable {
        doThrow(new RollbackOperationException()).when(propertyChangeListener).beforePropertyChange(any
                (PropertyChangeEvent.class));
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(propertyChangeListener);

        assertEquals("13", cfg.primeNumber());

        cfg.setProperty("primeNumber", "17");       // is rolled back!
        assertEquals("13", cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", "17");
        InOrder inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(propertyChangeListener, never()).propertyChange(argThat(matches(expectedEvent)));
        inOrder.verifyNoMoreInteractions();
    }


    @Test
    public void testRemoveProperty() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(propertyChangeListener);

        assertEquals("13", cfg.primeNumber());

        cfg.removeProperty("primeNumber");
        assertNull(cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", null);

        InOrder inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(expectedEvent)));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testRemovePropertyThrowingRollbackOperationException() throws Throwable {
        doThrow(new RollbackOperationException()).when(propertyChangeListener).beforePropertyChange(any
                (PropertyChangeEvent.class));

        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(propertyChangeListener);

        assertEquals("13", cfg.primeNumber());

        cfg.removeProperty("primeNumber");  // rolled back!
        thingsAreRolledBack(cfg);
    }

    @Test
    public void testRemovePropertyChangeListener() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(propertyChangeListener);

        assertEquals("13", cfg.primeNumber());

        cfg.setProperty("primeNumber", "17");
        assertEquals("17", cfg.primeNumber());

        cfg.removePropertyChangeListener(propertyChangeListener);

        cfg.setProperty("primeNumber", "3");
        assertEquals("3", cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", "17");
        InOrder inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(expectedEvent)));
        inOrder.verifyNoMoreInteractions();
    }


    @Test
    public void testClear() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(propertyChangeListener);

        cfg.clear();

        assertNull(cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", null);

        InOrder inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(expectedEvent)));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testClearOnRollbackOperationException() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(propertyChangeListener);

        doThrow(new RollbackOperationException()).when(propertyChangeListener).beforePropertyChange(any
                (PropertyChangeEvent.class));

        cfg.clear();

        thingsAreRolledBack(cfg);
    }

    private void thingsAreRolledBack(MyConfig cfg) throws RollbackOperationException, RollbackBatchException {

        assertEquals("13", cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", null);

        InOrder inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(propertyChangeListener, never()).propertyChange(argThat(matches(expectedEvent)));
        inOrder.verifyNoMoreInteractions();
    }

    interface Server extends Mutable {

        @DefaultValue("localhost")
        String hostname();

        @DefaultValue("8080")
        int port();

        @DefaultValue("http")
        String protocol();
    }

    @Test
    public void testClearOnRollbackBatchException() throws Throwable {
        Server cfg = ConfigFactory.create(Server.class);
        cfg.addPropertyChangeListener(propertyChangeListener);

        doNothing().doNothing().doThrow(new RollbackBatchException())
                .when(propertyChangeListener).beforePropertyChange(any(PropertyChangeEvent.class));

        cfg.clear();

        assertEquals("localhost", cfg.hostname());
        assertEquals(8080, cfg.port());
        assertEquals("http", cfg.protocol());

        verify(propertyChangeListener, times(3)).beforePropertyChange(any(PropertyChangeEvent.class));
        verify(propertyChangeListener, never()).propertyChange(any(PropertyChangeEvent.class));
    }


    @Test
    public void testLoadInputStream() throws Throwable {
        Server server = ConfigFactory.create(Server.class);
        server.addPropertyChangeListener(propertyChangeListener);
        String properties = getPropertiesTextForLoad();
        server.load(new ByteArrayInputStream(properties.getBytes()));
        verifyLoad(server);
    }

    @Test
    public void testLoadReader() throws Throwable {
        Server server = ConfigFactory.create(Server.class);
        server.addPropertyChangeListener(propertyChangeListener);
        String properties = getPropertiesTextForLoad();
        server.load(new StringReader(properties));
        verifyLoad(server);
    }

    private String getPropertiesTextForLoad() {
        return "hostname = foobar\n" +
                "port = 80\n" +
                "protocol = http\n";
    }

    @Test
    public void testPartialClearOnRollbackOperationException() throws Throwable {
        Server server = ConfigFactory.create(Server.class);

        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                PropertyChangeEvent evt = (PropertyChangeEvent) invocation.getArguments()[0];
                if (evt.getPropertyName().equals("port"))
                    throw new RollbackOperationException();
                return null;
            }
        }).when(propertyChangeListener).beforePropertyChange(any(PropertyChangeEvent.class));

        server.addPropertyChangeListener(propertyChangeListener);

        server.clear();

        assertNull(server.hostname());
        assertNull(server.protocol());
        assertEquals(8080, server.port());

        PropertyChangeEvent hostnameChangeEvent = new PropertyChangeEvent(server, "hostname", "localhost", null);
        PropertyChangeEvent protocolChangeEvent = new PropertyChangeEvent(server, "protocol", "http", null);

        verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(hostnameChangeEvent)));
        verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(protocolChangeEvent)));
        verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(hostnameChangeEvent)));
        verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(protocolChangeEvent)));

        InOrder inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(hostnameChangeEvent)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(hostnameChangeEvent)));

        inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(protocolChangeEvent)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(protocolChangeEvent)));

    }

    @Test
    public void testLoadReaderOnRollbackBatchException() throws Throwable {
        Server server = prepareLoadForRollbackBatch();
        String properties = getPropertiesAsText();
        server.load(new StringReader(properties));
        verifyLoadIsRolledBackCompletely(server);
    }

    @Test
    public void testLoadInputStreamOnRollbackBatchException() throws Throwable {
        Server server = prepareLoadForRollbackBatch();
        String properties = getPropertiesAsText();
        server.load(new ByteArrayInputStream(properties.getBytes()));
        verifyLoadIsRolledBackCompletely(server);
    }

    private void verifyLoadIsRolledBackCompletely(Server server) throws RollbackBatchException,
            RollbackOperationException {
        assertEquals("localhost", server.hostname());
        assertEquals(8080, server.port());
        assertEquals("http", server.protocol());
        verify(propertyChangeListener, times(3)).beforePropertyChange(any(PropertyChangeEvent.class));
        verify(propertyChangeListener, never()).propertyChange(any(PropertyChangeEvent.class));
    }

    private String getPropertiesAsText() {
        return "hostname = foobar\n" +
                "port = 80\n" +
                "protocol = ftp\n";
    }

    private Server prepareLoadForRollbackBatch() throws RollbackOperationException, RollbackBatchException {
        Server server = ConfigFactory.create(Server.class);
        server.addPropertyChangeListener(propertyChangeListener);

        doNothing().doNothing().doThrow(new RollbackBatchException())
                .when(propertyChangeListener).beforePropertyChange(any(PropertyChangeEvent.class));
        return server;
    }

    private void verifyLoad(Server server) throws RollbackOperationException, RollbackBatchException {
        assertEquals("foobar", server.hostname());
        assertEquals(80, server.port());
        assertEquals("http", server.protocol());

        PropertyChangeEvent hostnameChangeEvent = new PropertyChangeEvent(server, "hostname", "localhost", "foobar");
        PropertyChangeEvent portChangeEvent = new PropertyChangeEvent(server, "port", "8080", "80");

        verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(hostnameChangeEvent)));
        verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(portChangeEvent)));
        verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(hostnameChangeEvent)));
        verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(portChangeEvent)));

        InOrder inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(hostnameChangeEvent)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(hostnameChangeEvent)));

        inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(portChangeEvent)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(portChangeEvent)));
    }

    @Test
    public void testAddPropertyChangeListenerWithPropertyName() throws Throwable {

        Server cfg = ConfigFactory.create(Server.class);
        cfg.addPropertyChangeListener("hostname", propertyChangeListener);

        cfg.setProperty("protocol", "ssh");
        cfg.setProperty("hostname", "google.com");
        cfg.setProperty("port", "22");

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "hostname", "localhost", "google.com");
        InOrder inOrder = inOrder(propertyChangeListener);
        inOrder.verify(propertyChangeListener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(propertyChangeListener, times(1)).propertyChange(argThat(matches(expectedEvent)));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testRemovePropertyChangeListenerWithPropertyName() throws Throwable {
        Server cfg = ConfigFactory.create(Server.class);
        cfg.addPropertyChangeListener("hostname", propertyChangeListener);
        cfg.removePropertyChangeListener(propertyChangeListener);

        cfg.setProperty("protocol", "ssh");
        cfg.setProperty("hostname", "google.com");
        cfg.setProperty("port", "22");

        verifyZeroInteractions(propertyChangeListener);
    }

    @Before
    public void before() {
        propertiesManager = new PropertiesManagerForTest(Server.class, props, scheduler,
                new VariablesExpanderForTest(new Properties()), loaders);
    }

    @Test
    public void testRemoveReloadListenerNull() throws Throwable {
        propertiesManager.addReloadListener(reloadListener);
        propertiesManager.removeReloadListener(null); // no nullpex should happen
        assertEquals(1, propertiesManager.getReloadListeners().size());
    }

    @Test
    public void testRemoveReloadListenerThatIsNotThere() throws Throwable {
        assertThat(propertiesManager.getReloadListeners(), is(empty()));
        propertiesManager.removeReloadListener(reloadListener);
        assertThat(propertiesManager.getReloadListeners(), is(empty()));
    }

    @Test
    public void testRemovePropertyChangeListenerNull() throws Throwable {
        propertiesManager.addPropertyChangeListener(propertyChangeListener);
        propertiesManager.removePropertyChangeListener(null);
        assertEquals(1, propertiesManager.getPropertyChangeListeners().size());
    }

    @Test
    public void testRemovePropertyChangeThatIsNotThere() throws Throwable {
        assertThat(propertiesManager.getPropertyChangeListeners(), is(empty()));
        propertiesManager.removePropertyChangeListener(propertyChangeListener);
        assertThat(propertiesManager.getPropertyChangeListeners(), is(empty()));
    }

    @Test
    public void testAddPropertyChangeListenerNull() throws Throwable {
        propertiesManager.addPropertyChangeListener(null);
        assertTrue(propertiesManager.getPropertyChangeListeners().isEmpty());
    }

    @Test
    public void testAddReloadListenerNull() throws Throwable {
        propertiesManager.addReloadListener(null);
        assertTrue(propertiesManager.getReloadListeners().isEmpty());
    }

    @Test
    public void testAddPropertyChangeListenerNullSomething() throws Throwable {
        propertiesManager.addPropertyChangeListener(null, propertyChangeListener);
        assertTrue(propertiesManager.getPropertyChangeListeners().isEmpty());
    }

    @Test
    public void testAddPropertyChangeListenerSomethingNull() throws Throwable {
        propertiesManager.addPropertyChangeListener("something", null);
        assertTrue(propertiesManager.getPropertyChangeListeners().isEmpty());
    }

    @Test
    public void testAddPropertyChangeListenerNullNull() throws Throwable {
        propertiesManager.addPropertyChangeListener(null, null);
        assertTrue(propertiesManager.getPropertyChangeListeners().isEmpty());
    }

}
