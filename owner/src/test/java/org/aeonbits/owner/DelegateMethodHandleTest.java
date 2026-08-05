/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Matteo Baccan
 */
public class DelegateMethodHandleTest {

    public static class Target {
        public String greet(String name) {
            return "hello " + name;
        }

        public String failing() {
            throw new UnsupportedOperationException("boom");
        }
    }

    public static class Twin {
        public String greet(String name) {
            return null;
        }
    }

    public static class DifferentName {
        public String salute(String name) {
            return null;
        }
    }

    public static class DifferentReturnType {
        public Object greet(String name) {
            return null;
        }
    }

    public static class DifferentParameterTypes {
        public String greet(CharSequence name) {
            return null;
        }
    }

    private static Method method(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return clazz.getDeclaredMethod(name, parameterTypes);
    }

    @Test
    public void testInvokeReturnsTargetMethodResult() throws Throwable {
        DelegateMethodHandle handle = new DelegateMethodHandle(new Target(), method(Target.class, "greet", String.class));
        assertEquals("hello world", handle.invoke(new Object[] {"world"}));
    }

    @Test
    public void testInvokeRethrowsTargetException() throws Throwable {
        DelegateMethodHandle handle = new DelegateMethodHandle(new Target(), method(Target.class, "failing"));
        try {
            handle.invoke(null);
            fail("an exception is expected");
        } catch (UnsupportedOperationException e) {
            assertSame(UnsupportedOperationException.class, e.getClass());
            assertEquals("boom", e.getMessage());
        }
    }

    @Test
    public void testMatchesWhenNameReturnTypeAndParametersAreEqual() throws Throwable {
        DelegateMethodHandle handle = new DelegateMethodHandle(new Target(), method(Target.class, "greet", String.class));
        assertTrue(handle.matches(method(Twin.class, "greet", String.class)));
    }

    @Test
    public void testDoesNotMatchWhenNameDiffers() throws Throwable {
        DelegateMethodHandle handle = new DelegateMethodHandle(new Target(), method(Target.class, "greet", String.class));
        assertFalse(handle.matches(method(DifferentName.class, "salute", String.class)));
    }

    @Test
    public void testDoesNotMatchWhenReturnTypeDiffers() throws Throwable {
        DelegateMethodHandle handle = new DelegateMethodHandle(new Target(), method(Target.class, "greet", String.class));
        assertFalse(handle.matches(method(DifferentReturnType.class, "greet", String.class)));
    }

    @Test
    public void testDoesNotMatchWhenParameterTypesDiffer() throws Throwable {
        DelegateMethodHandle handle = new DelegateMethodHandle(new Target(), method(Target.class, "greet", String.class));
        assertFalse(handle.matches(method(DifferentParameterTypes.class, "greet", CharSequence.class)));
    }

}
