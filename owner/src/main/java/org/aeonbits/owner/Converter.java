/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.lang.reflect.Method;

/**
 * Converter interface specifies how to convert an input string coming from a property value to a target object returned
 * by the Config method.
 *
 * @param <T> the type of the class that should be returned from the conversion.
 * @author Luigi R. Viggiano
 * @since 1.0.4
 */
public interface Converter<T> {

    /**
     * Converts the given input into an Object of type T.
     * If the method returns null, null will be returned by the Config object.
     * The converter is instantiated for every call, so it shouldn't have any internal state.
     *
     * @param method the method invoked on the <code>{@link Config} object</code>
     * @param input  the property value specified as input text to be converted to the T return type
     * @return the object of type T converted from the input string.
     * @since 1.0.4
     */
    T convert(Method method, String input);

}
