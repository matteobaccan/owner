/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.ConverterClass;
import org.aeonbits.owner.loaders.PropertyKeys;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiConsumer;

import static org.aeonbits.owner.util.Util.unsupported;

/**
 * Reads a group of related properties as a configuration object of its own:
 * <pre>
 *     server.host=localhost
 *     server.port=8080
 * </pre>
 * <pre>
 *     public interface AppConfig extends Config {
 *         ServerConfig server();
 *     }
 *
 *     public interface ServerConfig extends Config {
 *         String host();
 *         int port();
 *     }
 * </pre>
 *
 * <p>
 * <b>The segment of the key is the accessor</b>, not the type it returns, so {@link Config.Key} names it and
 * two methods can return the same interface without colliding — <code>primary()</code> and
 * <code>backup()</code> read <code>primary.host</code> and <code>backup.host</code>. This is what the four
 * other libraries that bind an interface do as well; the alternative, taking the name of the type, would make
 * the second accessor impossible to write.
 * </p>
 *
 * <p>
 * <b>The nested object loads nothing of its own.</b> It is a view over the properties its parent has already
 * resolved, sharing the same {@link PropertiesManager}: one set of {@code @Sources}, one hot reload, one set
 * of listeners, one mutable state for the whole tree. A {@code @Sources} written on a nested interface is
 * therefore not read — the object it describes is not loaded, it is looked up.
 * </p>
 *
 * <p>
 * <b>The objects are built when the configuration is created</b>, once, and kept: the same accessor called
 * twice returns the same object, {@link Config.Mandatory} inside a nested interface is checked at creation
 * like everywhere else, and a cycle in the types is refused there rather than exhausting the stack at the
 * first call. Only an accessor taking arguments is resolved later, since its key is not known until it is
 * called.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class NestedProperties {

    /**
     * What sits between the key an accessor resolves to and the keys of the object below it. The same
     * separator the {@link PropertiesAggregator} groups by, and the one the loaders flatten with: a nested
     * source and a nested interface have to meet on the same key or neither is any use.
     */
    static final String SEPARATOR = String.valueOf(PropertyKeys.NESTING);

    /** Don't let anyone instantiate this class */
    private NestedProperties() {}

    /**
     * Tells whether the given method reads a nested configuration object, by reflection alone: its return
     * type is an interface extending {@link Config}.
     * <p>
     * Extending {@code Config} is required rather than inferred from "it is an interface we cannot convert",
     * so that nothing is captured by accident: a method returning some other interface goes on failing to
     * convert, as it always did, instead of quietly turning into a configuration object whose every method
     * returns <code>null</code>.
     * </p>
     * <p>
     * A method naming a converter is excluded, as it is from the {@link Map} grouping and from the indexed
     * lists: an explicit converter is handed the raw value of one property and takes precedence.
     * </p>
     *
     * @param method the method to inspect.
     * @return <code>true</code> if it reads a nested configuration object.
     */
    static boolean nests(Method method) {
        Class<?> type = OptionalSupport.valueClass(method);
        return type.isInterface()
                && Config.class.isAssignableFrom(type)
                && method.getAnnotation(ConverterClass.class) == null;
    }

    /**
     * Tells whether the given method reads a <b>list</b> of nested configuration objects, written one
     * section per index: <code>servers[0].host</code>, <code>servers[1].host</code>.
     *
     * @param method the method to inspect, already known to read a list.
     * @return <code>true</code> if the elements of that list are configuration objects.
     */
    static boolean nestsElements(Method method) {
        Class<?> element = Converters.elementType(method);
        return element.isInterface() && Config.class.isAssignableFrom(element);
    }

    /**
     * Tells whether a method that reads a group — one returning a {@link Map} — reads a group of nested
     * configuration objects rather than of values: <code>servers.alpha.host</code> rather than
     * <code>servers.alpha</code>.
     *
     * @param method the method to inspect, already known to read a group.
     * @return <code>true</code> if the values of that map are configuration objects.
     */
    static boolean nestsValues(Method method) {
        Class<?> value = PropertiesAggregator.typeArgument(method, 1);
        return value.isInterface() && Config.class.isAssignableFrom(value);
    }

    /**
     * The names of the sections lying directly below a prefix, which is what the keys of a group of nested
     * objects are: given <code>servers.alpha.host</code> and <code>servers.beta.port</code> below
     * <code>servers.</code>, they are <code>alpha</code> and <code>beta</code>.
     * <p>
     * Sorted, and not in the order the properties happen to come out in: a group read twice has to be the
     * same group in the same order, and a {@link java.util.Properties} makes no promise about either.
     * </p>
     * <p>
     * A key with nothing below it — <code>servers.alpha</code> on its own — names no section and is left
     * out, the same boundary an indexed list draws between an element that <b>is</b> a value and one that
     * <b>holds</b> some.
     * </p>
     */
    static SortedSet<String> sectionNamesUnder(String prefix, PropertiesManager manager) {
        SortedSet<String> names = new TreeSet<>();
        for (String name : manager.propertyNames()) {
            if (!name.startsWith(prefix))
                continue;
            int separator = name.indexOf(SEPARATOR, prefix.length());
            if (separator > prefix.length() && separator < name.length() - SEPARATOR.length())
                names.add(name.substring(prefix.length(), separator));
        }
        return names;
    }

    /**
     * One nested object of a group or of a list, at a path the properties decided: kept by path, so that the
     * same element read twice is the same object.
     */
    static Object sectionAt(Class<?> type, String path, PropertiesInvocationHandler parent,
                            List<Class<?>> ancestors) {
        List<Class<?>> chain = new ArrayList<>(ancestors);
        chain.add(type);
        return parent.nestedAt(type, path, chain);
    }

    /**
     * Builds the list of nested objects an accessor reads, one for each index found below its key.
     * <p>
     * The elements are built when the list is asked for rather than when the configuration is created, there
     * being no way to know how many of them there are until the properties are read; they are kept
     * afterwards, so the same element of the same list is the same object at every call.
     * </p>
     * <p>
     * <b>No cycle is refused here</b>, and the difference from {@link #create} is not an oversight: a type
     * holding a list of itself describes a tree, and it is the properties that say how deep it goes. The
     * recursion ends where the keys end — an element with no <code>[0]</code> below it has an empty list —
     * so there is nothing to refuse and something useful to allow.
     * </p>
     *
     * @return the list, or <code>null</code> when no index is written below the key, so that the caller
     *         carries on with the ordinary lookup.
     */
    static Object list(Method accessor, String key, PropertiesInvocationHandler parent,
                       List<Class<?>> ancestors) {
        List<String> paths = IndexedProperties.sectionsOf(key, parent.propertiesManager);
        if (paths.isEmpty())
            return null;

        Class<?> element = Converters.elementType(accessor);
        Object array = Array.newInstance(element, paths.size());
        for (int i = 0; i < paths.size(); i++)
            Array.set(array, i, sectionAt(element, paths.get(i), parent, ancestors));

        Class<?> target = OptionalSupport.valueClass(accessor);
        if (target.isArray())
            return array;

        Collection<Object> result = Converters.instantiateCollection(accessor, target);
        for (int i = 0; i < paths.size(); i++)
            result.add(Array.get(array, i));
        return result;
    }

    /**
     * The prefix the object hangs from, given the key its accessor resolved to.
     * <p>
     * The separator is added unless the key already ends with one, which is what lets
     * <code>@Prefix("server.")</code> be written the way the annotation documents it. An <b>empty</b> key —
     * <code>@Key("")</code> — yields an empty path on purpose: the nested object then reads the keys of the
     * object holding it, without a segment of its own, which is the same escape hatch SmallRye spells
     * <code>@WithParentName</code>.
     * </p>
     */
    static String pathOf(String key) {
        if (key.isEmpty() || key.endsWith(SEPARATOR))
            return key;
        return key + SEPARATOR;
    }

    /** Tells whether any property at all lives below the given path, which is what makes an Optional present. */
    static boolean anythingUnder(String path, PropertiesManager manager) {
        for (String name : manager.propertyNames())
            if (name.startsWith(path) && name.length() > path.length())
                return true;
        return false;
    }

    /**
     * Builds the nested object of every accessor of the given interface that takes no arguments, in
     * declaration order.
     *
     * @param parent    the object holding them, which they share the properties of.
     * @param ancestors the interfaces from the root down to and including this one, used to refuse a cycle.
     * @return the objects by accessor, empty when the interface nests nothing.
     */
    static Map<Method, Object> childrenOf(PropertiesInvocationHandler parent, List<Class<?>> ancestors) {
        Map<Method, Object> children = new LinkedHashMap<>();
        for (Method method : parent.configClass().getMethods())
            if (nests(method) && method.getParameterTypes().length == 0)
                children.put(method, build(method, parent.keyOf(method), parent, ancestors, true));
        return children;
    }

    /**
     * Builds one nested object.
     *
     * @param accessor  the method returning it, which names it in the message of a cycle.
     * @param key       the key that accessor resolved to.
     * @param parent    the object holding it.
     * @param ancestors the interfaces from the root down to and including the parent.
     * @return the proxy, an instance of the interface the accessor returns.
     */
    static Object create(Method accessor, String key, PropertiesInvocationHandler parent,
                         List<Class<?>> ancestors) {
        return build(accessor, key, parent, ancestors, false);
    }

    /**
     * @param whenCreated <code>true</code> for the objects built with the configuration, whose path was
     *                    known in advance and whose defaults are therefore already among the properties;
     *                    <code>false</code> for one built at the first call, which is kept by path.
     */
    private static Object build(Method accessor, String key, PropertiesInvocationHandler parent,
                                List<Class<?>> ancestors, boolean whenCreated) {
        Class<?> type = OptionalSupport.valueClass(accessor);
        if (ancestors.contains(type))
            throw cycle(accessor, type, ancestors);

        List<Class<?>> chain = new ArrayList<>(ancestors);
        chain.add(type);
        String path = pathOf(key);
        return whenCreated ? parent.nest(type, path, chain, true) : parent.nestedAt(type, path, chain);
    }

    /**
     * Walks every nested interface reachable from the given one, depth first, handing each the prefix it
     * resolves its keys with.
     * <p>
     * Whatever is read off the methods of a configuration interface has to be read off the nested ones too,
     * under the composed key — the default values, the keys to mask, the decryptors — and every one of those
     * happens before the objects themselves exist. This is the one walk they all share, so that a rule
     * cannot hold at the root and quietly stop one level down.
     * </p>
     * <p>
     * An accessor taking arguments is not walked: its key is a different one at every call, and there is no
     * single prefix to register anything under. A cycle is stopped rather than reported, because this runs
     * while the configuration is being built and {@link #create} refuses it a moment later, with a message.
     * </p>
     *
     * @param clazz  the interface to start from.
     * @param prefix the prefix that interface resolves its own keys with.
     * @param action what to do with each nested interface found.
     */
    static void forEachNested(Class<?> clazz, KeyPrefix prefix, BiConsumer<Class<?>, KeyPrefix> action) {
        forEachNested(clazz, prefix, action, singletonChain(clazz));
    }

    private static void forEachNested(Class<?> clazz, KeyPrefix prefix, BiConsumer<Class<?>, KeyPrefix> action,
                                      List<Class<?>> ancestors) {
        for (Method method : clazz.getMethods()) {
            if (!nests(method) || method.getParameterTypes().length > 0)
                continue;
            Class<?> type = OptionalSupport.valueClass(method);
            if (ancestors.contains(type))
                continue;

            KeyPrefix nested = KeyPrefix.nestedIn(pathOf(PropertiesMapper.key(method, prefix)));
            action.accept(type, nested);

            List<Class<?>> chain = new ArrayList<>(ancestors);
            chain.add(type);
            forEachNested(type, nested, action, chain);
        }
    }

    /**
     * Walks every nested interface reachable through a section whose <b>path the properties decide</b> — an
     * element of a list, a value of a map, the answer of an accessor taking arguments — handing each the
     * prefix the whole group lives under.
     * <p>
     * These are the ones {@link #forEachNested} cannot reach, and they have to be reached all the same:
     * what a method declares has to be read whether or not anybody can say in advance which key it will
     * answer for. What can be done with them differs, though. A {@link Config.EncryptedValue} is registered
     * against the <b>method</b>, so not knowing the key costs nothing. A {@link Config.Sensitive} is
     * registered against the key, and there is none — so what is masked is the whole group, which is the
     * rule this library already applies where a group and a key inside it disagree: a secret printed
     * because nobody could name it in advance is the mistake that costs something, and a value masked that
     * need not have been is read as over-caution and no more.
     * </p>
     *
     * @param clazz  the interface to start from.
     * @param prefix the prefix that interface resolves its own keys with.
     * @param action what to do with each interface found, and the prefix its group lives under.
     */
    static void forEachNestedGroup(Class<?> clazz, KeyPrefix prefix, BiConsumer<Class<?>, String> action) {
        walkGroups(clazz, prefix, null, action, singletonChain(clazz));
    }

    /**
     * @param groupPrefix <code>null</code> while the walk is still on paths that are known — where it is
     *                    only looking for the accessors that open a group — and the prefix of the group
     *                    once it is inside one, since everything below an unknown segment is unknown too.
     */
    private static void walkGroups(Class<?> clazz, KeyPrefix prefix, String groupPrefix,
                                   BiConsumer<Class<?>, String> action, List<Class<?>> ancestors) {
        for (Method method : clazz.getMethods()) {
            Class<?> opensGroup = groupElementOf(method);
            if (opensGroup != null) {
                String below = groupPrefix != null ? groupPrefix : groupPrefixOf(method, prefix);
                if (below != null)
                    enterGroup(opensGroup, below, action, ancestors);
                continue;
            }
            if (!nests(method) || method.getParameterTypes().length > 0)
                continue;

            Class<?> type = OptionalSupport.valueClass(method);
            if (ancestors.contains(type))
                continue;
            if (groupPrefix != null) {
                enterGroup(type, groupPrefix, action, ancestors);
                continue;
            }
            List<Class<?>> chain = new ArrayList<>(ancestors);
            chain.add(type);
            walkGroups(type, KeyPrefix.nestedIn(pathOf(PropertiesMapper.key(method, prefix))), null,
                    action, chain);
        }
    }

    private static void enterGroup(Class<?> type, String groupPrefix, BiConsumer<Class<?>, String> action,
                                   List<Class<?>> ancestors) {
        if (ancestors.contains(type))
            return;
        action.accept(type, groupPrefix);

        List<Class<?>> chain = new ArrayList<>(ancestors);
        chain.add(type);
        walkGroups(type, KeyPrefix.NONE, groupPrefix, action, chain);
    }

    /**
     * The interface behind a method that reads a group of sections, or <code>null</code> when the method
     * reads something else: the element type of a list of sections, the value type of a map of them, or the
     * type an accessor taking arguments answers with.
     */
    private static Class<?> groupElementOf(Method method) {
        if (IndexedProperties.readsAList(method) && nestsElements(method))
            return Converters.elementType(method);
        if (PropertiesAggregator.aggregates(method) && nestsValues(method))
            return PropertiesAggregator.typeArgument(method, 1);
        if (nests(method) && method.getParameterTypes().length > 0)
            return OptionalSupport.valueClass(method);
        return null;
    }

    /**
     * The interface a method reads a section as, whichever of the four shapes it uses: a plain accessor, a
     * list of sections, a map of them, or an accessor taking arguments. <code>null</code> when the method
     * reads no section at all.
     */
    private static Class<?> sectionTypeOf(Method method) {
        if (nests(method))
            return OptionalSupport.valueClass(method);
        return groupElementOf(method);
    }

    /**
     * The interfaces whose methods are addressed <b>by key</b>, and which a nested interface may therefore
     * not extend.
     *
     * @see #rejectKeyedSection(Method)
     */
    private static final Class<?>[] KEYED = {Accessible.class, Mutable.class, Traceable.class};

    /**
     * Refuses a section declared as an {@link Accessible}, a {@link Mutable} or a {@link Traceable}.
     * <p>
     * Those interfaces are addressed by key, and <b>a section has no key space of its own</b>: it shares
     * one {@link PropertiesManager} with the whole configuration, which is what makes it a view rather
     * than a copy, and what makes a reload reach it. Left to inherit them, a section would answer
     * <code>getProperty("host")</code> with the <code>host</code> of the root rather than with its own
     * <code>server.host</code> — a different property, silently — and <code>clear()</code> called on a
     * section would empty the entire configuration.
     * </p>
     * <p>
     * It is refused rather than scoped because refusing is the decision that can be taken back: reading a
     * section's keys relative to the section is a feature this library may well grow, in the shape
     * Typesafe Config gives it with <code>getConfig</code> and Commons Configuration with
     * <code>subset</code>, and allowing it later breaks nobody. Shipping the root's answers under the
     * section's name and correcting them afterwards would break everybody who had come to rely on them.
     * </p>
     * <p>
     * {@link Reloadable} is deliberately <b>not</b> in the list: it acts on the configuration as a whole,
     * there is exactly one of those, and so it means the same thing called from anywhere in the tree.
     * </p>
     *
     * @param accessor the method reading the section.
     * @throws UnsupportedOperationException if the section is declared as one of {@link #KEYED}.
     */
    /**
     * Walks the configuration interface and everything it nests, at any depth and through all four shapes,
     * refusing every section declared as one of {@link #KEYED}.
     * <p>
     * It is a walk of its own rather than a line added to the check of the mandatory properties, because
     * that one descends into the plain sections only: a section reachable solely as the element of a list
     * is never among its children, and a rule with a hole in it is not a rule. Interfaces already visited
     * are skipped, which also makes it terminate on a configuration that nests itself.
     * </p>
     *
     * @param configClass the interface to walk.
     */
    static void rejectKeyedSections(Class<?> configClass) {
        rejectKeyedSections(configClass, new ArrayList<Class<?>>());
    }

    private static void rejectKeyedSections(Class<?> configClass, List<Class<?>> visited) {
        if (visited.contains(configClass))
            return;
        visited.add(configClass);
        for (Method method : configClass.getMethods()) {
            rejectKeyedSection(method);
            Class<?> section = sectionTypeOf(method);
            if (section != null)
                rejectKeyedSections(section, visited);
        }
    }

    static void rejectKeyedSection(Method accessor) {
        Class<?> section = sectionTypeOf(accessor);
        if (section == null)
            return;
        for (Class<?> keyed : KEYED)
            if (keyed.isAssignableFrom(section))
                throw unsupported("Method '%s' reads the nested configuration object '%s', which extends "
                                + "%s. A section is a view over the properties of the whole configuration "
                                + "and has no key space of its own, so getProperty(\"x\") on it would read "
                                + "'x' and not '%s.x', and clear() on it would empty everything. Read the "
                                + "properties by name from the configuration object the factory created, "
                                + "where the keys are the ones written in the file, and leave '%s' with its "
                                + "mapping methods. Reloadable may be extended: there is one configuration, "
                                + "so reload() means the same thing from anywhere in it.",
                        accessor.getName(), section.getSimpleName(), keyed.getSimpleName(),
                        accessor.getName(), section.getSimpleName());
    }

    /**
     * The prefix every key of a group starts with: the key of the accessor and the character that opens an
     * index for a list, the separator for a map, and everything before the first placeholder for an
     * accessor taking arguments.
     *
     * @return the prefix, or <code>null</code> when there is none worth having — a key that begins with its
     *         placeholder gives an empty prefix, and masking everything is not what anybody asked for.
     */
    private static String groupPrefixOf(Method method, KeyPrefix prefix) {
        String key = PropertiesMapper.key(method, prefix);
        if (method.getParameterTypes().length > 0) {
            int placeholder = key.indexOf('%');
            key = placeholder < 0 ? key : key.substring(0, placeholder);
            return key.isEmpty() ? null : key;
        }
        if (key.isEmpty())
            return null;
        return IndexedProperties.readsAList(method) ? key + PropertyKeys.INDEX_OPEN : pathOf(key);
    }

    private static List<Class<?>> singletonChain(Class<?> clazz) {
        List<Class<?>> chain = new ArrayList<>();
        chain.add(clazz);
        return chain;
    }

    /**
     * A configuration object holding itself, at any depth, cannot be built: every one of them would have to
     * hold another. It is refused where it is discovered — when the configuration is created — rather than
     * left to exhaust the stack, and the message names the way back round so that the offending accessor can
     * be found without drawing the graph by hand.
     */
    private static UnsupportedOperationException cycle(Method accessor, Class<?> type,
                                                       List<Class<?>> ancestors) {
        StringBuilder chain = new StringBuilder();
        for (Class<?> ancestor : ancestors.subList(ancestors.indexOf(type), ancestors.size()))
            chain.append(ancestor.getSimpleName()).append(" -> ");
        chain.append(type.getSimpleName());
        return unsupported(
                "The nested configuration interfaces of '%s' form a cycle: %s, closed by '%s()'. Every one of "
                        + "these objects would have to hold another, so there is no configuration to build. "
                        + "Break the cycle, or read the repeated part through a key of its own.",
                ancestors.get(0).getName(), chain, accessor.getName());
    }
}
