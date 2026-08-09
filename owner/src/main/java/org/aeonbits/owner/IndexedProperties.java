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
import org.aeonbits.owner.loaders.PropertyKeys;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

import static org.aeonbits.owner.Converters.convert;
import static org.aeonbits.owner.Converters.elementType;
import static org.aeonbits.owner.Converters.instantiateCollection;
import static org.aeonbits.owner.util.Util.unsupported;

/**
 * Reads a list out of the properties written one element per key:
 * <pre>
 *     servers[0]=alpha
 *     servers[1]=beta
 * </pre>
 * <pre>
 *     List&lt;String&gt; servers();     // [alpha, beta]
 * </pre>
 *
 * <p>
 * The point of it is not the notation but what the notation makes possible. A list held in a single value
 * has to be split on something — a comma, by default — and a value containing that something cannot then be
 * expressed at all: <code>["a,b"]</code> and <code>["a","b"]</code> are the same string. An element that
 * has a key of its own is one element whatever it contains, which is why <b>the separator does not apply
 * here</b>: <code>servers[0]=a,b</code> is a single element. It is also the only way a list from a JSON or
 * a YAML source can survive being flattened into properties.
 * </p>
 *
 * <p>
 * <b>Square brackets, and not <code>servers.0</code></b>, because the dot is taken: a method returning a
 * {@link java.util.Map} already reads everything under <code>servers.</code> as a group, so the same layout
 * of keys would mean two things according only to the return type — and a map whose keys are numbers would
 * become indistinguishable from a list.
 * </p>
 *
 * <p>
 * <b>If there is one indexed key, that is the list</b>, and the plain key is not consulted. Which also
 * settles what would otherwise be a question about {@link Config.DefaultValue}: defaults are merged into
 * the same properties when the configuration is loaded, so afterwards a value that came from a file cannot
 * be told from one that came from an annotation, and a rule that had to tell them apart could not be
 * implemented.
 * </p>
 *
 * <p>
 * <b>The indices must start at 0 and run consecutively.</b> A gap is refused rather than closed up or
 * filled with a null, because an element quietly dropped is not something the reader can notice: the list
 * is simply shorter than the file says, and every index after the gap has moved. The three libraries that
 * have this feature do three different things here, so there is no convention to follow — see
 * <code>FORMATS.md</code> for which does what and why this is the choice.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class IndexedProperties {

    private static final String OPEN = String.valueOf(PropertyKeys.INDEX_OPEN);
    private static final char CLOSE = PropertyKeys.INDEX_CLOSE;

    /** Don't let anyone instantiate this class */
    private IndexedProperties() {}

    /**
     * Tells whether the method could read a list this way, by reflection alone and without looking at any
     * property. Cheap on purpose: it runs on every call of every method, and only when it says yes is it
     * worth walking the property names.
     * <p>
     * A method naming a converter is excluded, both kinds: an explicit converter is handed the raw value of
     * one property and takes precedence, exactly as it does over the {@link Map} grouping.
     * </p>
     *
     * @param method the method to inspect.
     * @return <code>true</code> if it returns an array or a {@link Collection} and names no converter.
     */
    static boolean readsAList(Method method) {
        Class<?> type = OptionalSupport.valueClass(method);
        return (type.isArray() || Collection.class.isAssignableFrom(type))
                && method.getAnnotation(ConverterClass.class) == null
                && method.getAnnotation(CollectionConverterClass.class) == null;
    }

    /**
     * Collects <code>key[0]</code>, <code>key[1]</code> and so on into the array or collection the method
     * asks for.
     *
     * @param method  the method being resolved, which provides the element type.
     * @param key     the key the method resolves to; the elements are the properties indexed below it.
     * @param manager the properties to read from.
     * @param value   the processing applied to each value, i.e. what the single-value path applies to one.
     * @return the list, or <code>null</code> when no indexed property is present, so that the caller
     *         carries on with the ordinary single-value lookup.
     */
    static Object collect(Method method, String key, PropertiesManager manager, UnaryOperator<String> value) {
        TreeMap<Integer, String> names = indexedNames(key, manager);
        if (names.isEmpty())
            return null;

        List<String> ordered = inOrder(names, key);
        Class<?> elementType = elementType(method);

        Object array = Array.newInstance(elementType, ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            String name = ordered.get(i);
            // the key reported on a failure is the property the element came from, not the list: telling
            // somebody that 'servers' would not convert leaves them counting brackets by hand
            Array.set(array, i, convert(method, elementType, value.apply(manager.getProperty(name)), name));
        }

        Class<?> targetType = OptionalSupport.valueClass(method);
        if (targetType.isArray())
            return array;

        Collection<Object> result = instantiateCollection(method, targetType);
        for (int i = 0; i < ordered.size(); i++)
            result.add(Array.get(array, i));
        return result;
    }

    /** The properties named <code>key[n]</code> exactly, by index. Sorted, so a file's order does not count. */
    private static TreeMap<Integer, String> indexedNames(String key, PropertiesManager manager) {
        String start = key + OPEN;
        TreeMap<Integer, String> found = new TreeMap<>();
        for (String name : manager.propertyNames()) {
            Integer index = indexIn(name, start);
            if (index != null)
                found.put(index, name);
        }
        return found;
    }

    /**
     * The index in <code>key[n]</code>, or <code>null</code> when the name is not one of those.
     * <p>
     * The closing bracket has to be the last character, which is what keeps <code>servers[0].host</code>
     * out: that names something below an element rather than an element, and belongs to nested
     * configuration interfaces rather than here.
     * </p>
     */
    private static Integer indexIn(String name, String start) {
        if (!name.startsWith(start) || name.charAt(name.length() - 1) != CLOSE)
            return null;
        String digits = name.substring(start.length(), name.length() - 1);
        if (digits.isEmpty())
            return null;
        for (int i = 0; i < digits.length(); i++)
            if (digits.charAt(i) < '0' || digits.charAt(i) > '9')
                return null;
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException tooManyDigitsToBeAnIndex) {
            return null;
        }
    }

    /** Checks the sequence and returns the names in index order. */
    private static List<String> inOrder(TreeMap<Integer, String> names, String key) {
        List<String> ordered = new ArrayList<>(names.size());
        int expected = 0;
        for (Integer index : names.navigableKeySet()) {
            if (index != expected)
                throw gap(key, expected, names.get(index));
            ordered.add(names.get(index));
            expected++;
        }
        return ordered;
    }

    private static UnsupportedOperationException gap(String key, int expected, String found) {
        if (expected == 0)
            return unsupported(
                    "The indexed properties of '%s' start at '%s' rather than at '%s[0]'. Elements are "
                            + "numbered from zero, and a list that began anywhere else would quietly be a "
                            + "different length from the one the file describes.",
                    key, found, key);
        return unsupported(
                "The indexed properties of '%s' skip from '%s[%d]' to '%s'. Reading the list across the gap "
                        + "would silently shorten it and move every element after the gap to a different "
                        + "position, so it is refused instead: number the elements from zero without gaps.",
                key, key, expected - 1, found);
    }
}
