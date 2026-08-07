/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.EncryptedValue;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Prefix;

import java.lang.reflect.Method;
import java.util.Properties;

import static org.aeonbits.owner.Config.DisableableFeature.PREFIX;
import static org.aeonbits.owner.util.Util.isFeatureDisabled;

/**
 * Maps methods to properties keys and defaultValues. Maps a class to default property values.
 *
 * @author Luigi R. Viggiano
 */
final class PropertiesMapper {

    /** Don't let anyone instantiate this class */
    private PropertiesMapper() {}

    static boolean isEncryptedValue( Method method ) {
        return ( method.getAnnotation( EncryptedValue.class ) ) != null;
    }

    /**
     * Returns the key the given method resolves to.
     * <p>
     * The key is never derived anywhere else: whoever needs one receives it, since it depends on the
     * {@link KeyPrefix} of the factory that created the Config object and not on the method alone.
     * </p>
     *
     * @param method       the method to resolve.
     * @param globalPrefix the prefix configured on the factory, {@link KeyPrefix#NONE} when there is none.
     * @return the key.
     */
    static String key(Method method, KeyPrefix globalPrefix) {
        Key key = method.getAnnotation(Key.class);
        String name = (key == null) ? method.getName() : key.value();
        return prefix(method, globalPrefix) + name;
    }

    /**
     * Returns the prefix to prepend to the key of the given method, which is the one declared by the
     * interface where the method is declared. Since {@link Method#getDeclaringClass()} already points to
     * that interface, the prefix of a sub-interface never leaks onto the methods it inherits, and a
     * super-interface keeps its own prefix at any depth of the hierarchy.
     * <p>
     * A {@link Prefix} written on the interface wins over the one configured on the factory: it is the
     * explicit statement of the two, and letting them concatenate would push the keys of everybody who
     * already uses the annotation one level deeper. {@code @DisableFeature(PREFIX)} switches off both.
     * </p>
     */
    private static String prefix(Method method, KeyPrefix globalPrefix) {
        if (isFeatureDisabled(method, PREFIX))
            return "";
        Prefix prefix = method.getDeclaringClass().getAnnotation(Prefix.class);
        if (prefix != null)
            return prefix.value();
        return globalPrefix.of(method.getDeclaringClass());
    }

    static String defaultValue(Method method) {
        DefaultValue defaultValue = method.getAnnotation(DefaultValue.class);
        return defaultValue != null ? defaultValue.value() : null;
    }

    /**
     * Returns the default value to use in place of an empty one, or <code>null</code> when the method did not
     * ask for it with {@link DefaultValue#useOnEmpty()}, which is the case by default.
     */
    static String defaultValueOnEmpty(Method method) {
        DefaultValue defaultValue = method.getAnnotation(DefaultValue.class);
        return defaultValue != null && defaultValue.useOnEmpty() ? defaultValue.value() : null;
    }

    static void defaults(Properties properties, Class<? extends Config> clazz, KeyPrefix globalPrefix) {
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            String key = key(method, globalPrefix);
            String value = defaultValue(method);
            if (value != null)
                properties.put(key, value);
        }
    }

}
