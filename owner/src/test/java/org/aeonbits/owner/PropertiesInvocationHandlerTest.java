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

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
public class PropertiesInvocationHandlerTest {
    // A @Spy on java.util.Properties breaks on JDK 16+: Mockito cannot copy the JDK-internal
    // 'map' field (java.base is not open), leaving the spy's storage null. Delegating to a real
    // instance avoids relying on the mock's internal state.
    private final Properties properties = mock(Properties.class, delegatesTo(new Properties()));
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
        PropertiesManager loader = new PropertiesManager(Dummy.class, properties, scheduler, expander, loaders);
        handler = new PropertiesInvocationHandler(loader, null);
    }

    @Test
    public void testListPrintStream() throws Throwable {
        handler.invoke(proxy, MyConfig.class.getDeclaredMethod("list", PrintStream.class), printStream);
        verify(properties).list(eq(printStream));
    }

    @Test
    public void testListPrintWriter() throws Throwable {
        handler.invoke(proxy, MyConfig.class.getDeclaredMethod("list", PrintWriter.class), printWriter);
        verify(properties).list(eq(printWriter));
    }

    public interface MyConfig extends Config, Accessible {
        void list(PrintStream out);
        void list(PrintWriter out);
    }

}
