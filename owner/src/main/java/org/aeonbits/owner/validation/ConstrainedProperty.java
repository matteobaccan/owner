/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.validation;

import java.lang.reflect.Method;

/**
 * One property to be checked: the method that carries the constraints, the key it resolved to, the value it
 * answered with, and the configuration object it belongs to.
 *
 * <p>
 * <b>The value is already read.</b> A {@link ConfigValidator} is handed what the method returned rather than
 * being left to call it, because deciding <em>which</em> methods may be called is the library's business and
 * not the validator's: a method taking arguments has no key until the call, a <code>default</code> method is
 * user code, a nested section is a view and not a value. Those decisions are made once, where the knowledge
 * is, and what arrives here is only what a validator can safely be given.
 * </p>
 *
 * <p>
 * The {@link #config() configuration object} comes along because Bean Validation's
 * <code>validateReturnValue</code> asks for the object as well as the method, and for a nested section it is
 * not the object the {@link org.aeonbits.owner.Factory} returned but the section's own.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public final class ConstrainedProperty {

    private final Object config;
    private final Method method;
    private final String key;
    private final Object value;

    /**
     * Builds a property to be checked.
     *
     * @param config the configuration object the method was called on: the one a
     *               {@link org.aeonbits.owner.Factory} returned, or a nested section of it.
     * @param method the method carrying the constraints.
     * @param key    the property key the method resolved to, prefixes and nesting path included.
     * @param value  the value the method answered with.
     */
    public ConstrainedProperty(Object config, Method method, String key, Object value) {
        this.config = config;
        this.method = method;
        this.key = key;
        this.value = value;
    }

    /**
     * The configuration object the method belongs to.
     *
     * @return the configuration object, never <code>null</code>.
     */
    public Object config() {
        return config;
    }

    /**
     * The method carrying the constraints.
     *
     * @return the method, never <code>null</code>.
     */
    public Method method() {
        return method;
    }

    /**
     * The key this property resolved to, which is what a violation should be reported against: it is the
     * line the reader has to go and change, while the method name is only where it is declared.
     *
     * @return the property key, never <code>null</code>.
     */
    public String key() {
        return key;
    }

    /**
     * The value the method answered with, converted to the return type as any caller would receive it - an
     * {@link java.util.Optional} included, since a validation provider knows how to look inside one.
     *
     * @return the value, possibly <code>null</code>.
     */
    public Object value() {
        return value;
    }

    /** A description for a log line: the key and the method, and never the value. */
    @Override
    public String toString() {
        return "'" + key + "' (" + method.getName() + "())";
    }
}
