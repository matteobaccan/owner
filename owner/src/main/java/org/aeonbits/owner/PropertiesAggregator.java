/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.CollectionConverterClass;
import org.aeonbits.owner.Config.ConverterClass;
import org.aeonbits.owner.Config.DefaultValue;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

import static org.aeonbits.owner.Converters.convert;
import static org.aeonbits.owner.util.Util.unsupported;

/**
 * Builds a {@link Map} out of the properties sharing a common prefix, which is how a group of related keys is
 * read as a whole:
 * <pre>
 *     something.foo=1
 *     something.bar=2
 * </pre>
 * <pre>
 *     Map&lt;String, Integer&gt; something();     // {foo=1, bar=2}
 * </pre>
 * The prefix is the key the method resolves to, followed by a dot, so it is chosen with {@link Config.Key} and
 * {@link Config.Prefix} like any other key. Both sides of the entry go through the regular type conversion, so
 * the keys and the values are not limited to strings.
 *
 * @author Matteo Baccan
 */
final class PropertiesAggregator {

    /**
     * What sits between the key a method resolves to and the name of an entry below it. Package visible
     * because whoever has to recognise the properties belonging to a group needs the same rule: masking a
     * {@link Config.Sensitive} group, for one.
     */
    static final String SEPARATOR = ".";

    /** Don't let anyone instantiate this class */
    private PropertiesAggregator() {}

    /**
     * The prefix under which the entries of a group live: the key the method resolves to, and the separator.
     *
     * @param key the key the method resolves to.
     * @return the prefix every property of that group starts with.
     */
    static String prefixOf(String key) {
        return key + SEPARATOR;
    }

    /**
     * Tells whether the given method reads a group of properties rather than a single one, which is the case
     * for a method returning a {@link Map} and taking over neither of the conversion annotations: an explicit
     * converter is handed the raw value of one property, and takes precedence.
     *
     * @param method the method to inspect.
     * @return <code>true</code> if the method aggregates a group of properties.
     */
    static boolean aggregates(Method method) {
        return Map.class.isAssignableFrom(method.getReturnType())
                && method.getAnnotation(ConverterClass.class) == null
                && method.getAnnotation(CollectionConverterClass.class) == null;
    }

    /**
     * Collects every property whose name starts with <code>prefix.</code> into a map, converting the part of the
     * name that follows the prefix into the key type and the value into the value type.
     *
     * @param method  the method being resolved, which provides the map types and the conversion annotations.
     * @param prefix  the key the method resolves to; the properties collected are the ones below it.
     * @param manager the properties to read from.
     * @param value   the processing applied to each value, i.e. what the single-value path applies to one.
     * @return the map, empty when no property matches; never <code>null</code>.
     */
    static Object aggregate(Method method, String prefix, PropertiesManager manager, UnaryOperator<String> value) {
        if (method.getAnnotation(DefaultValue.class) != null)
            throw unsupported(
                    "@DefaultValue cannot be used on '%s', which reads the group of properties under '%s' "
                            + "rather than a single one; give a default to the individual properties instead",
                    method.getName(), prefix);

        Class<?> keyType = typeArgument(method, 0);
        Class<?> valueType = typeArgument(method, 1);
        String start = prefixOf(prefix);

        Map<Object, Object> result = instantiate(method.getReturnType());
        for (String name : manager.propertyNames()) {
            if (!name.startsWith(start) || name.length() == start.length())
                continue;
            String entryKey = name.substring(start.length());
            // the key to report is the property the entry comes from, not the group: telling somebody that
            // 'servers' failed to convert leaves them looking through the whole group by hand
            result.put(convert(method, keyType, entryKey, name),
                    convert(method, valueType, value.apply(manager.getProperty(name)), name));
        }
        return result;
    }

    /**
     * Returns the class behind the type argument at the given position of the map, defaulting to
     * <code>String</code> for a raw <code>Map</code> and for anything that is not a plain class, such as a
     * wildcard or a type variable.
     */
    private static Class<?> typeArgument(Method method, int position) {
        Type returnType = method.getGenericReturnType();
        if (!(returnType instanceof ParameterizedType))
            return String.class;

        Type[] arguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (position >= arguments.length)
            return String.class;

        Type argument = arguments[position];
        if (argument instanceof Class)
            return (Class<?>) argument;
        if (argument instanceof ParameterizedType)
            return (Class<?>) ((ParameterizedType) argument).getRawType();
        return String.class;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> instantiate(Class<?> targetType) {
        if (!targetType.isInterface()) {
            try {
                return (Map<Object, Object>) targetType.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw unsupported(e, "Cannot instantiate map of type '%s'", targetType.getCanonicalName());
            }
        }
        if (SortedMap.class.isAssignableFrom(targetType))
            return new TreeMap<>();
        return new LinkedHashMap<>();
    }
}
