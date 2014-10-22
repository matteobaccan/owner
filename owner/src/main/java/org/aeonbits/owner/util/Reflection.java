/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import java.lang.reflect.Method;
import java.lang.annotation.Annotation;

/**
 * @author Luigi R. Viggiano
 */
public class Reflection {

    // Suppresses default LOOKUP_CONSTRUCTOR, ensuring non-instantiability.
    private Reflection() {
    }

    public static boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static interface Java8Support {
        public boolean isDefault(Method method);

        public Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable;
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

    /**
     * Method to recursively find an annotation over an interface hierarchy.
     *
     * @param   clazz the class to inspect to find the annotation.
     * @param   annotationClass the annotation class to look for.
     * @return  the Annotation if found, or null otherwise.
     */
    public static Annotation getAnnotation(Class<?> clazz, Class<? extends Annotation> annotationClass)
    {
        Annotation annotation = clazz.getAnnotation(annotationClass);
        if (annotation != null)
            return annotation;

        for (Class<?> interfaceClass : clazz.getInterfaces())
        {
            annotation = getAnnotation(interfaceClass, annotationClass);
            if (annotation != null)
                return annotation;
        }

        return null;
    }

}
