/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import org.junit.Assume;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Luigi R. Viggiano
 * @author Matteo Baccan
 */
public class ReflectionTest {

    @Test
    public void testAvailableWithNonExistentClass() {
        boolean available = Reflection.isClassAvailable("foo.bar.baz.FooBar");
        assertFalse(available);
    }

    @Test
    public void testAvailableWithExistentClass(){
        boolean available = Reflection.isClassAvailable("java.lang.String");
        assertTrue(available);
    }

    @Test
    public void testForNameWithExistentClass() {
        assertSame(String.class, Reflection.forName("java.lang.String"));
    }

    @Test
    public void testForNameWithNonExistentClass() {
        assertNull(Reflection.forName("foo.bar.baz.FooBar"));
    }

    /**
     * Plays the role of a {@code Config} interface proxied by OWNER: it exposes both an
     * abstract method and a default method.
     */
    public interface Sample {
        Integer three();

        default Integer sum(Integer a, Integer b) {
            return a + b;
        }
    }

    @Test
    public void isDefaultShouldReturnTrueForInterfaceDefaultMethods() throws Exception {
        assertTrue(Reflection.isDefault(sumMethod()));
    }

    @Test
    public void isDefaultShouldReturnFalseForAbstractInterfaceMethods() throws Exception {
        assertFalse(Reflection.isDefault(Sample.class.getMethod("three")));
    }

    @Test
    public void invokeDefaultMethodShouldInvokeDefaultImplementationBypassingTheProxyHandler() throws Throwable {
        // findSpecial() accepts a special caller different from the lookup class only since
        // Java 9; on a real Java 8 runtime this branch is never taken by production code.
        Assume.assumeFalse(isJava8Runtime());

        Object result = Reflection.invokeDefaultMethod(newProxy(), sumMethod(), new Object[] {2, 3}, false);

        assertEquals(Integer.valueOf(5), result);
    }

    @Test
    public void invokeDefaultMethodShouldUseTheLookupConstructorHackWhenRunningOnJava8() throws Throwable {
        if (isJava8Runtime()) {
            // On a real Java 8 runtime the private Lookup(Class, int) constructor is usable
            // and the default method implementation gets invoked.
            Object result = Reflection.invokeDefaultMethod(newProxy(), sumMethod(), new Object[] {3, 4}, true);
            assertEquals(Integer.valueOf(7), result);
        } else {
            try {
                Reflection.invokeDefaultMethod(newProxy(), sumMethod(), new Object[] {3, 4}, true);
                fail("the MethodHandles.Lookup constructor hack is unusable on Java 9+, a failure was expected");
            } catch (IllegalAccessException expected) {
                // JDK 9-13: the constructor is still there, but a Lookup created with
                // PRIVATE-only modes is not allowed to unreflectSpecial() a public member.
            } catch (NullPointerException expected) {
                // JDK 14+: the Lookup(Class, int) constructor has been removed, so
                // Reflection.Lookup.lookupConstructor() returns null.
            } catch (ExceptionInInitializerError expected) {
                // JDK 9-15 with --illegal-access=deny: setAccessible(true) fails while
                // initializing the nested Lookup class.
            } catch (NoClassDefFoundError expected) {
                // Any subsequent access after the nested Lookup class failed to initialize.
            }
        }
    }

    private static Method sumMethod() throws NoSuchMethodException {
        return Sample.class.getMethod("sum", Integer.class, Integer.class);
    }

    /**
     * Returns a {@link Proxy} implementing {@link Sample} whose handler always fails: default
     * methods must be dispatched to the interface implementation via {@code invokespecial}
     * semantics, never through the proxy handler (that would loop forever in OWNER).
     */
    private static Sample newProxy() {
        return (Sample) Proxy.newProxyInstance(
                ReflectionTest.class.getClassLoader(),
                new Class<?>[] {Sample.class},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        throw new UnsupportedOperationException(
                                "default methods must not be dispatched to the proxy handler: " + method);
                    }
                });
    }

    /**
     * Tells whether the tests are running on a real Java 8 runtime, using the very same check
     * performed by {@link Reflection} at class initialization.
     */
    private static boolean isJava8Runtime() {
        return ManagementFactory.getRuntimeMXBean().getSpecVersion().startsWith("1.8");
    }

}
