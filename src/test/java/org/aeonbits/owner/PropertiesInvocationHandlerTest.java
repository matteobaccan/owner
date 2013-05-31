/*
 * Copyright (c) 2013, Luigi R. Viggiano
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
import org.mockito.runners.MockitoJUnitRunner;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Properties;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
public class PropertiesInvocationHandlerTest {
    @Mock
    private Properties properties;
    @Mock
    private PrintStream printStream;
    @Mock
    private PrintWriter printWriter;
    @Mock
    private Object proxy;
    private PropertiesInvocationHandler handler;

    @Before
    public void before() {
        PropertiesLoader loader = new PropertiesLoader(null) {
            @Override
            Properties load() {
                return properties;
            }
        };
        handler = new PropertiesInvocationHandler(loader);
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

    public interface MyConfig extends Config {
        void list(PrintStream out);
        void list(PrintWriter out);
    }

}
