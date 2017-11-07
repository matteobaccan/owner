/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Luigi R. Viggiano
 */
class Java8SupportImpl implements Reflection.Java8Support {
    private boolean isJava8;

    Java8SupportImpl() {
        String version = ManagementFactory.getRuntimeMXBean().getSpecVersion();
        isJava8 = version.startsWith("1.8");
    }

    @Override
    public boolean isDefault(Method method) {
        return method.isDefault();
    }


    @Override
    public Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
        final Class<?> declaringClass = method.getDeclaringClass();

        if (isJava8) {
            return Lookup.in(declaringClass)
                    .unreflectSpecial(method, declaringClass)
                    .bindTo(proxy)
                    .invokeWithArguments(args);
        } else {
            MethodType rt = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
            return MethodHandles.lookup()
                    .findSpecial(declaringClass, method.getName(), rt, declaringClass)
                    .bindTo(proxy)
                    .invokeWithArguments(args);
        }
    }

    private static class Lookup {
        private static final Constructor<MethodHandles.Lookup> LOOKUP_CONSTRUCTOR = lookupConstructor();

        private static Constructor<MethodHandles.Lookup> lookupConstructor() {
            try {
                Constructor<MethodHandles.Lookup> ctor =
                        MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
                ctor.setAccessible(true);
                return ctor;
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        private static MethodHandles.Lookup in(Class<?> requestedLookupClass)
                throws IllegalAccessException, InvocationTargetException, InstantiationException {
            return LOOKUP_CONSTRUCTOR.newInstance(requestedLookupClass, MethodHandles.Lookup.PRIVATE);
        }
    }
}
