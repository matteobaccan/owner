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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.beans.PropertyChangeEvent;
import java.io.ByteArrayInputStream;
import java.io.StringReader;

import static org.aeonbits.owner.event.PropertyChangeMatcher.matches;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
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
public class PropertyChangeListenerTest {

    @Mock
    TransactionalPropertyChangeListener listener;

    interface MyConfig extends Mutable {
        @DefaultValue("13")
        String primeNumber();
    }

    @Test
    public void testSetProperty() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);

        assertEquals("13", cfg.primeNumber());

        cfg.setProperty("primeNumber", "17");
        assertEquals("17", cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", "17");
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(expectedEvent)));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSetPropertyWhenValuesAreEqual() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);
        assertEquals("13", cfg.primeNumber());

        cfg.setProperty("primeNumber", "13");
        assertEquals("13", cfg.primeNumber());

        verifyZeroInteractions(listener);
    }

    @Test
    public void testSetPropertyThrowingRollbackOperationException() throws Throwable {
        doThrow(new RollbackOperationException()).when(listener).beforePropertyChange(any(PropertyChangeEvent.class));
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);

        assertEquals("13", cfg.primeNumber());

        cfg.setProperty("primeNumber", "17");       // is rolled back!
        assertEquals("13", cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", "17");
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(listener, never()).propertyChange(argThat(matches(expectedEvent)));
        inOrder.verifyNoMoreInteractions();
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
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testRemovePropertyThrowingRollbackOperationException() throws Throwable {
        doThrow(new RollbackOperationException()).when(listener).beforePropertyChange(any(PropertyChangeEvent.class));

        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);

        assertEquals("13", cfg.primeNumber());

        cfg.removeProperty("primeNumber");  // rolled back!
        thingsAreRolledBack(cfg);
    }

    @Test
    public void testRemovePropertyChangeListener() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);

        assertEquals("13", cfg.primeNumber());

        cfg.setProperty("primeNumber", "17");
        assertEquals("17", cfg.primeNumber());

        cfg.removePropertyChangeListener(listener);

        cfg.setProperty("primeNumber", "17");
        assertEquals("3", cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", "17");
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(expectedEvent)));
        inOrder.verifyNoMoreInteractions();
    }


    @Test
    public void testClear() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);

        cfg.clear();

        assertNull(cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", null);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(expectedEvent)));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testClearOnRollbackOperationException() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);

        doThrow(new RollbackOperationException()).when(listener).beforePropertyChange(any(PropertyChangeEvent.class));

        cfg.clear();

        thingsAreRolledBack(cfg);
    }

    @Test
    public void testClearOnRollbackBatchException() throws Throwable {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        cfg.addPropertyChangeListener(listener);

        doThrow(new RollbackBatchException()).when(listener).beforePropertyChange(any(PropertyChangeEvent.class));

        cfg.clear();

        thingsAreRolledBack(cfg);
    }

    private void thingsAreRolledBack(MyConfig cfg) throws RollbackOperationException, RollbackBatchException {

        assertEquals("13", cfg.primeNumber());

        PropertyChangeEvent expectedEvent = new PropertyChangeEvent(cfg, "primeNumber", "13", null);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(expectedEvent)));
        inOrder.verify(listener, never()).propertyChange(argThat(matches(expectedEvent)));
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
    public void testLoadInputStream() throws Throwable {
        Server server = ConfigFactory.create(Server.class);
        server.addPropertyChangeListener(listener);
        String properties = getPropertiesTextForLoad();
        server.load(new ByteArrayInputStream(properties.getBytes()));
        verifyLoad(server);
    }

    @Test
    public void testLoadReader() throws Throwable {
        Server server = ConfigFactory.create(Server.class);
        server.addPropertyChangeListener(listener);
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
                PropertyChangeEvent evt = (PropertyChangeEvent)invocation.getArguments()[0];
                if (evt.getPropertyName().equals("port"))
                    throw new RollbackOperationException();
                return null;
            }
        }).when(listener).beforePropertyChange(any(PropertyChangeEvent.class));

        server.addPropertyChangeListener(listener);

        server.clear();

        assertNull(server.hostname());
        assertNull(server.protocol());
        assertEquals(8080, server.port());

        PropertyChangeEvent hostnameChangeEvent = new PropertyChangeEvent(server, "hostname", "localhost", null);
        PropertyChangeEvent protocolChangeEvent = new PropertyChangeEvent(server, "protocol", "http", null);

        verify(listener, times(1)).beforePropertyChange(argThat(matches(hostnameChangeEvent)));
        verify(listener, times(1)).beforePropertyChange(argThat(matches(protocolChangeEvent)));
        verify(listener, times(1)).propertyChange(argThat(matches(hostnameChangeEvent)));
        verify(listener, times(1)).propertyChange(argThat(matches(protocolChangeEvent)));

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(hostnameChangeEvent)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(hostnameChangeEvent)));

        inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(protocolChangeEvent)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(protocolChangeEvent)));

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

    private void verifyLoadIsRolledBackCompletely(Server server) {
        assertEquals("localhost", server.hostname());
        assertEquals(8080, server.port());
        assertEquals("http", server.protocol());
    }

    private String getPropertiesAsText() {
        return "hostname = foobar\n" +
                    "port = 80\n" +
                    "protocol = ftp\n";
    }

    private Server prepareLoadForRollbackBatch() throws RollbackOperationException, RollbackBatchException {
        Server server = ConfigFactory.create(Server.class);
        server.addPropertyChangeListener(listener);

        doNothing().doNothing().doThrow(new RollbackBatchException())
                .when(listener).beforePropertyChange(any(PropertyChangeEvent.class));
        return server;
    }

    private void verifyLoad(Server server) throws RollbackOperationException, RollbackBatchException {
        assertEquals("foobar", server.hostname());
        assertEquals(80, server.port());
        assertEquals("http", server.protocol());

        PropertyChangeEvent hostnameChangeEvent = new PropertyChangeEvent(server, "hostname", "localhost", "foobar");
        PropertyChangeEvent portChangeEvent = new PropertyChangeEvent(server, "port", "8080", "80");

        verify(listener, times(1)).beforePropertyChange(argThat(matches(hostnameChangeEvent)));
        verify(listener, times(1)).beforePropertyChange(argThat(matches(portChangeEvent)));
        verify(listener, times(1)).propertyChange(argThat(matches(hostnameChangeEvent)));
        verify(listener, times(1)).propertyChange(argThat(matches(portChangeEvent)));

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(hostnameChangeEvent)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(hostnameChangeEvent)));

        inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).beforePropertyChange(argThat(matches(portChangeEvent)));
        inOrder.verify(listener, times(1)).propertyChange(argThat(matches(portChangeEvent)));
    }


}
