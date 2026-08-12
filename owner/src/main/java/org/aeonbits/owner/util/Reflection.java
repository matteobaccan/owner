/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
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
public final class Reflection {

    private static final boolean IS_JAVA_8 =
            ManagementFactory.getRuntimeMXBean().getSpecVersion().startsWith("1.8");

    /** {@link MethodHandles}<code>.privateLookupIn(Class, Lookup)</code>, absent on Java 8. */
    private static final Method PRIVATE_LOOKUP_IN = privateLookupInMethod();

    private static Method privateLookupInMethod() {
        try {
            return MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
        } catch (NoSuchMethodException notOnJava8) {
            return null;
        }
    }

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

    /**
     * Tells whether the given method is an interface <code>default</code> method.
     *
     * @param method the method to inspect.
     * @return <code>true</code> if the method is a default method; <code>false</code> otherwise.
     */
    public static boolean isDefault(Method method) {
        return method.isDefault();
    }

    /**
     * Invokes a <code>default</code> method on the given proxy, dispatching to the interface
     * implementation instead of going through the proxy invocation handler.
     *
     * @param proxy  the proxy instance the method is invoked on.
     * @param method the default method to invoke.
     * @param args   the arguments to pass to the method.
     * @return the value returned by the invoked default method.
     * @throws Throwable anything thrown by the invoked method.
     */
    public static Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
        return invokeDefaultMethod(proxy, method, args, IS_JAVA_8);
    }

    // package-private so tests can exercise both invocation strategies on any JVM
    static Object invokeDefaultMethod(Object proxy, Method method, Object[] args, boolean isJava8)
            throws Throwable {
        final Class<?> declaringClass = method.getDeclaringClass();

        if (isJava8) {
            // on Java 8 findSpecial() rejects a special caller different from the lookup class,
            // so a private-access Lookup on the declaring interface must be created via the
            // private Lookup(Class, int) constructor
            return Lookup.in(declaringClass)
                    .unreflectSpecial(method, declaringClass)
                    .bindTo(proxy)
                    .invokeWithArguments(args);
        }

        MethodHandles.Lookup lookup = privateLookupIn(declaringClass);
        if (lookup != null)
            return lookup.unreflectSpecial(method, declaringClass)
                    .bindTo(proxy)
                    .invokeWithArguments(args);

        MethodType rt = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
        return MethodHandles.lookup()
                .findSpecial(declaringClass, method.getName(), rt, declaringClass)
                .bindTo(proxy)
                .invokeWithArguments(args);
    }

    /**
     * A lookup with private access on the interface that declares the method, which is what it takes to
     * invoke a <code>default</code> method on an interface that is not <code>public</code>.
     * <p>
     * The lookup taken below belongs to this class, in <code>org.aeonbits.owner.util</code>, and
     * <code>findSpecial</code> checks the declaring interface against it: a configuration interface that
     * is package-private anywhere else is not accessible from here, and the call failed with
     * <em>symbolic reference class is not accessible</em> at the first invocation. Java 8 never had the
     * problem, since the constructor hack above builds the lookup <em>on the declaring interface</em>;
     * this is the same thing said in the supported way, and it exists from Java 9 on.
     * </p>
     * <p>
     * Called by reflection because the bytecode targets Java 8, where the method does not exist. A
     * <code>null</code> answer means it is not there, or that it refused — a module that does not open
     * the package to us — and the caller falls back on what was done before, which is enough for the
     * public interfaces that used to be the only ones that worked.
     * </p>
     *
     * @param declaringClass the interface declaring the default method.
     * @return a lookup with private access on it, or <code>null</code> if there can be none.
     */
    private static MethodHandles.Lookup privateLookupIn(Class<?> declaringClass) {
        if (PRIVATE_LOOKUP_IN == null) return null;
        try {
            return (MethodHandles.Lookup)
                    PRIVATE_LOOKUP_IN.invoke(null, declaringClass, MethodHandles.lookup());
        // the two are one answer here: the reflective call being refused, and the call itself refusing.
        // Either way there is no such lookup and the caller has somewhere else to go
        } catch (IllegalAccessException | InvocationTargetException thereIsNoLookingThere) {
            return null;
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
