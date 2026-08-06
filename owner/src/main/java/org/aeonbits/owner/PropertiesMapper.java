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

    static String key(Method method) {
        Key key = method.getAnnotation(Key.class);
        String name = (key == null) ? method.getName() : key.value();
        return prefix(method) + name;
    }

    /**
     * Returns the prefix to prepend to the key of the given method, which is the one declared by the
     * interface where the method is declared. Since {@link Method#getDeclaringClass()} already points to
     * that interface, the prefix of a sub-interface never leaks onto the methods it inherits, and a
     * super-interface keeps its own prefix at any depth of the hierarchy.
     */
    private static String prefix(Method method) {
        if (isFeatureDisabled(method, PREFIX))
            return "";
        Prefix prefix = method.getDeclaringClass().getAnnotation(Prefix.class);
        return (prefix == null) ? "" : prefix.value();
    }

    static String defaultValue(Method method) {
        DefaultValue defaultValue = method.getAnnotation(DefaultValue.class);
        return defaultValue != null ? defaultValue.value() : null;
    }

    static void defaults(Properties properties, Class<? extends Config> clazz) {
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            String key = key(method);
            String value = defaultValue(method);
            if (value != null)
                properties.put(key, value);
        }
    }

}
