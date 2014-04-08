/*
 * Copyright (c) 2013-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Utility class to have a cache singleton for Config instances.
 *
 * @author Luigi R. Viggiano
 * @since 1.0.6
 */
public final class ConfigCache {
    private static ConcurrentMap<Object, Config> CACHE = new ConcurrentHashMap<Object, Config>();

    /** Don't let anyone instantiate this class */
    private ConfigCache() {}

    /**
     * Gets from the cache or create, an instance of the given class using the given imports.
     * The factory used to create new instances is the static {@link ConfigFactory#INSTANCE}.
     *
     * @param clazz     the interface extending from {@link Config} that you want to instantiate.
     * @param imports   additional variables to be used to resolve the properties.
     * @param <T>       type of the interface.
     * @return an object implementing the given interface, that can be taken from the cache, which maps methods
     *         to property values.
     */
    public static <T extends Config> T getOrCreate(Class<? extends T> clazz, Map<?, ?>... imports) {
        return getOrCreate(ConfigFactory.INSTANCE, clazz, clazz, imports);
    }

    /**
     * Gets from the cache or create, an instance of the given class using the given imports.
     *
     * @param factory   the factory to use to eventually create the instance.
     * @param clazz     the interface extending from {@link Config} that you want to instantiate.
     * @param imports   additional variables to be used to resolve the properties.
     * @param <T>       type of the interface.
     * @return an object implementing the given interface, that can be taken from the cache, which maps methods
     *         to property values.
     */
    public static <T extends Config> T getOrCreate(Factory factory, Class<? extends T> clazz, Map<?, ?>... imports) {
        return getOrCreate(factory, clazz, clazz, imports);
    }

    /**
     * Gets from the cache or create, an instance of the given class using the given imports.
     *
     * @param key       the key object to be used to identify the instance in the cache.
     * @param clazz     the interface extending from {@link Config} that you want to instantiate.
     * @param imports   additional variables to be used to resolve the properties.
     * @param <T>       type of the interface.
     * @return an object implementing the given interface, that can be taken from the cache, which maps methods
     *         to property values.
     */
    public static <T extends Config> T getOrCreate(Object key, Class<? extends T> clazz, Map<?, ?>... imports) {
        return getOrCreate(ConfigFactory.INSTANCE, key, clazz, imports);
    }

    /**
     * Gets from the cache or create, an instance of the given class using the given imports.
     *
     * @param factory   the factory to use to eventually create the instance.
     * @param key       the key object to be used to identify the instance in the cache.
     * @param clazz     the interface extending from {@link Config} that you want to instantiate.
     * @param imports   additional variables to be used to resolve the properties.
     * @param <T>       type of the interface.
     * @return an object implementing the given interface, that can be taken from the cache, which maps methods
     *         to property values.
     */
    public static <T extends Config> T getOrCreate(Factory factory, Object key,
                                                   Class<? extends T> clazz, Map<?, ?>[] imports) {
        T existing = get(key);
        if (existing != null) return existing;
        T created = factory.create(clazz, imports);
        T raced = add(key, created);
        return raced != null ? raced : created;
    }

    /**
     * Gets from the cache the {@link Config} instance identified by the given key.
     *
     * @param key       the key object to be used to identify the instance in the cache.
     * @param <T>       type of the interface.
     * @return the {@link Config} object from the cache if exists, or <tt>null</tt> if it doesn't.
     */
    public static <T extends Config> T get(Object key) {
        return (T) CACHE.get(key);
    }

    /**
     * Adds a {@link Config} object into the cache.
     *
     * @param key       the key object to be used to identify the instance in the cache.
     * @param instance  the instance of the {@link Config} object to be stored into the cache.
     * @param <T>       type of the interface.
     * @return the previous value associated with the specified key, or
     *         <tt>null</tt> if there was no mapping for the key.
     */
    public static <T extends Config> T add(Object key, T instance) {
        return (T) CACHE.putIfAbsent(key, instance);
    }

    /**
     * Removes all of the cached instances.
     * The cache will be empty after this call returns.
     */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * Removes the cached instance for the given key if it is present.
     *
     * <p>Returns previous instance associated to the given key in the cache,
     * or <tt>null</tt> if the cache contained no instance for the given key.
     *
     * <p>The cache will not contain the instance for the specified key once the
     * call returns.
     *
     * @param   key key whose instance is to be removed from the cache.
     * @return the previous instance associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no instance for <tt>key</tt>.
     */
    public static <T extends Config> T remove(Object key) {
        return (T) CACHE.remove(key);
    }
}
