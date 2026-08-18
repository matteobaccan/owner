/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * Converter interface specifies how to convert an input string coming from a property value to a target object returned
 * by the Config method.
 *
 * @param <T> the type of the class that should be returned from the conversion.
 * @author Luigi R. Viggiano
 * @since 1.0.4
 */
@FunctionalInterface
public interface Converter<T> extends Serializable {

    /**
     * Converts the given input into an Object of type T.
     * If the method returns null, null will be returned by the Config object.
     * <p>
     * <b>A converter named by a class is instantiated for every call</b>, so one written that way should
     * have no internal state. A converter <b>registered as an instance</b> —
     * {@link Factory#setTypeConverter(Class, Converter)}, since 2.0.0 — is the object you handed over, and
     * lives as long as the factory does: it is then yours to make thread safe, and it travels with the
     * configuration when the configuration is serialized, which is why this interface is
     * {@link Serializable} as {@link org.aeonbits.owner.loaders.Loader} and
     * {@link org.aeonbits.owner.handlers.ValueHandler} are.
     * </p>
     *
     * @param method the method invoked on the <code>{@link Config} object</code>
     * @param input  the property value specified as input text to be converted to the T return type
     * @return the object of type T converted from the input string.
     * @since 1.0.4
     */
    T convert(Method method, String input);

}
