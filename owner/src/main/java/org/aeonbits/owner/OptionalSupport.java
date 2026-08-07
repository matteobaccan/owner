/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Reads through an {@link Optional} return type, so that the rest of the library can go on reasoning about
 * the type a property value is converted to without caring whether it is wrapped.
 * <p>
 * A method declared as <code>Optional&lt;Integer&gt; port()</code> converts its value to an
 * <code>Integer</code> exactly like <code>Integer port()</code> does: the wrapper says what happens when the
 * property is <b>absent</b>, not how the value is read.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class OptionalSupport {

    /** Don't let anyone instantiate this class */
    private OptionalSupport() {}

    /**
     * Tells whether the given method wraps its value in an {@link Optional}.
     *
     * @param method the method to inspect.
     * @return <code>true</code> if the method returns an {@link Optional}.
     */
    static boolean isOptional(Method method) {
        return method.getReturnType() == Optional.class;
    }

    /**
     * Returns the generic type the property value is converted to: what the {@link Optional} wraps when there
     * is one, the return type of the method otherwise.
     * <p>
     * A raw <code>Optional</code>, or one with a wildcard, carries no type to convert to and falls back on
     * {@link String}, which is the same default a raw collection takes.
     * </p>
     */
    static Type valueType(Method method) {
        Type returnType = method.getGenericReturnType();
        if (!isOptional(method))
            return returnType;
        if (returnType instanceof ParameterizedType)
            return ((ParameterizedType) returnType).getActualTypeArguments()[0];
        return String.class;
    }

    /**
     * Returns the class {@link #valueType(Method)} erases to, which is what the converters dispatch on.
     * <p>
     * A method that does not return an {@link Optional} answers with its own return type, untouched: the
     * erasure below only has to reconstruct what the reflection API hands over as a generic type, and
     * {@link Method#getReturnType()} already is the erased one.
     * </p>
     */
    static Class<?> valueClass(Method method) {
        if (!isOptional(method))
            return method.getReturnType();
        return erase(valueType(method));
    }

    private static Class<?> erase(Type type) {
        if (type instanceof Class)
            return (Class<?>) type;
        if (type instanceof ParameterizedType)
            return (Class<?>) ((ParameterizedType) type).getRawType();
        if (type instanceof GenericArrayType)
            return Array.newInstance(erase(((GenericArrayType) type).getGenericComponentType()), 0).getClass();
        // a type variable or a wildcard erases to nothing usable: same default a raw Optional takes
        return String.class;
    }
}
