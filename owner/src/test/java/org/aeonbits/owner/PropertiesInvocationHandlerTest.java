/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.util.Reflection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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

    // The owner-java8 module (providing Java8SupportImpl) is not on this module's test classpath, so
    // Reflection.isDefault() always returns false here: the only way to exercise the default-method
    // paths of PropertiesInvocationHandler deterministically is to stub the Reflection static methods.
    @Test
    public void testInvokeDelegatesDefaultMethodsToJava8Support() throws Throwable {
        Method method = MyConfig.class.getDeclaredMethod("list", PrintStream.class);
        try (MockedStatic<Reflection> reflection = mockStatic(Reflection.class)) {
            reflection.when(() -> Reflection.isDefault(method)).thenReturn(true);
            reflection.when(() -> Reflection.invokeDefaultMethod(proxy, method, new Object[] {printStream}))
                    .thenReturn("default method result");
            Object result = handler.invoke(proxy, method, printStream);
            assertEquals("default method result", result);
        }
    }

    @Test
    public void testValidateMandatoryPropertiesSkipsDefaultMethods() throws Throwable {
        Method method = MandatoryConfig.class.getDeclaredMethod("mandatoryValue");
        try (MockedStatic<Reflection> reflection = mockStatic(Reflection.class)) {
            reflection.when(() -> Reflection.isDefault(method)).thenReturn(true);
            handler.validateMandatoryProperties(MandatoryConfig.class); // must not throw: default methods are skipped
        }
    }

    @Test(expected = MissingMandatoryPropertyException.class)
    public void testValidateMandatoryPropertiesFailsOnMissingAbstractProperty() {
        handler.validateMandatoryProperties(MandatoryConfig.class);
    }

    @Test
    public void testFormatIsSkippedWhenArgsIsEmpty() throws Throwable {
        properties.setProperty("value.with.percent", "100%");
        Object result = handler.invoke(proxy, FormatConfig.class.getDeclaredMethod("valueWithPercent"));
        assertEquals("100%", result);
    }

    @Test
    public void testFormatIsSkippedWhenArgsIsNull() throws Throwable {
        // JDK dynamic proxies pass a null args array for zero-arg methods
        properties.setProperty("value.with.percent", "100%");
        Object result = handler.invoke(proxy, FormatConfig.class.getDeclaredMethod("valueWithPercent"),
                (Object[]) null);
        assertEquals("100%", result);
    }

    @Test
    public void testFormatAppliesArguments() throws Throwable {
        properties.setProperty("valid.format", "Hello %s!");
        Object result = handler.invoke(proxy, FormatConfig.class.getDeclaredMethod("validFormat", String.class),
                "world");
        assertEquals("Hello world!", result);
    }

    @Test
    public void testFormatReturnsRawValueWhenFormatIsIllegal() throws Throwable {
        // '%d' does not accept a String argument: String.format() fails and the raw value must be returned
        properties.setProperty("invalid.format", "100%d");
        Object result = handler.invoke(proxy, FormatConfig.class.getDeclaredMethod("invalidFormat", String.class),
                "unused");
        assertEquals("100%d", result);
    }

    public interface MyConfig extends Config, Accessible {
        void list(PrintStream out);
        void list(PrintWriter out);
    }

    public interface MandatoryConfig extends Config {
        @Config.Mandatory
        String mandatoryValue();
    }

    public interface FormatConfig extends Config {
        @Config.Key("value.with.percent")
        String valueWithPercent();

        @Config.Key("valid.format")
        String validFormat(String arg);

        @Config.Key("invalid.format")
        String invalidFormat(String arg);
    }

}
