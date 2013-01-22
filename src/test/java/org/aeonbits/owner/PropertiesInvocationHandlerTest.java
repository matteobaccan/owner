/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.PrintStream;
import java.util.Properties;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
    private Object proxy;

    @Test
    public void testListPrintStream() throws Throwable {
        PropertiesInvocationHandler handler = new PropertiesInvocationHandler(properties);
        handler.invoke(proxy, Config.class.getDeclaredMethod("list", PrintStream.class ), printStream);
        verify(properties).list(eq(printStream));
        verifyNoMoreInteractions(proxy);
    }
}
