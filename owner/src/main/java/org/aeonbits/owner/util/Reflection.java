/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import java.lang.reflect.Method;

/**
 * @author Luigi R. Viggiano
 */
public final class Reflection {

    // Suppresses default constructor, ensuring no one instantiate this class.
    private Reflection() {}

    /**
     * Checks whether a class with the given name is available on the classpath.
     *
     * @param className the fully qualified class name.
     * @return <code>true</code> if the class can be loaded, <code>false</code> otherwise.
     */
    public static boolean isClassAvailable(String className) {
        return forName(className) != null;
    }

    /**
     * Loads the class with the given name, returning <code>null</code> when it is not available.
     *
     * @param className the fully qualified class name.
     * @return the {@link Class} object, or <code>null</code> if the class cannot be found.
     */
    public static Class<?> forName(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    interface Java8Support {
        boolean isDefault(Method method);

        Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable;
    }

    private static final Java8Support JAVA_8_SUPPORT = getJava8Support();

    private static Java8Support getJava8Support() {
        try {
            return (Java8Support) Class.forName("org.aeonbits.owner.util.Java8SupportImpl").newInstance();
        } catch (Exception e) {
            return java8NotSupported();
        }
    }

    private static Java8Support java8NotSupported() {
        return new Java8Support() {
            public boolean isDefault(Method method) {
                return false;
            }

            public Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
                return null;
            }
        };
    }


    /**
     * Tells whether the given method is a Java 8 <code>default</code> method.
     *
     * @param method the method to inspect.
     * @return <code>true</code> if the method is a default method; <code>false</code> otherwise, or when
     *         running on a JVM without default method support.
     */
    public static boolean isDefault(Method method) {
        return JAVA_8_SUPPORT.isDefault(method);
    }

    /**
     * Invokes a Java 8 <code>default</code> method on the given proxy.
     *
     * @param proxy  the proxy instance the method is invoked on.
     * @param method the default method to invoke.
     * @param args   the arguments to pass to the method.
     * @return the value returned by the invoked default method.
     * @throws Throwable anything thrown by the invoked method.
     */
    public static Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
        return JAVA_8_SUPPORT.invokeDefaultMethod(proxy, method, args);
    }

}
