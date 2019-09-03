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

    public static boolean isClassAvailable(String className) {
        return forName(className) != null;
    }

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


    public static boolean isDefault(Method method) {
        return JAVA_8_SUPPORT.isDefault(method);
    }

    public static Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
        return JAVA_8_SUPPORT.invokeDefaultMethod(proxy, method, args);
    }

}
