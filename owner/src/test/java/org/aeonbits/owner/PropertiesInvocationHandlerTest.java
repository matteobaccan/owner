/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertSame;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
public class PropertiesInvocationHandlerTest {
    private RecordingProperties properties;
    @Mock private PrintStream printStream;
    @Mock private PrintWriter printWriter;
    @Mock private Object proxy;
    private PropertiesInvocationHandler handler;
    @Mock private ScheduledExecutorService scheduler;
    private LoadersManager loaders = new LoadersManagerForTest();
    private final VariablesExpander expander = new VariablesExpander(new Properties());


    interface Dummy extends Config {}

    @Before
    public void before() {
        properties = new RecordingProperties();
        PropertiesManager loader = new PropertiesManager(Dummy.class, properties, scheduler, expander, loaders);
        handler = new PropertiesInvocationHandler(loader, null);
    }

    @Test
    public void testListPrintStream() throws Throwable {
        handler.invoke(proxy, MyConfig.class.getDeclaredMethod("list", PrintStream.class), printStream);
        assertSame(printStream, properties.printStream);
    }

    @Test
    public void testListPrintWriter() throws Throwable {
        handler.invoke(proxy, MyConfig.class.getDeclaredMethod("list", PrintWriter.class), printWriter);
        assertSame(printWriter, properties.printWriter);
    }

    public interface MyConfig extends Config, Accessible {
        void list(PrintStream out);
        void list(PrintWriter out);
    }

    private static class RecordingProperties extends Properties {
        private PrintStream printStream;
        private PrintWriter printWriter;

        @Override
        public void list(PrintStream out) {
            printStream = out;
        }

        @Override
        public void list(PrintWriter out) {
            printWriter = out;
        }
    }

}
