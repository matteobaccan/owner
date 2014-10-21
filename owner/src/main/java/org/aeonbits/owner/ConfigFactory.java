/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.loaders.Loader;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

/**
 * A static factory class to instantiate {@link Config} instances.
 * <p>
 * By default a {@link Config} sub-interface is associated to a property having the same package name and class name as
 * the interface itself.</p>
 * <p>
 * Method names are mapped to property names contained in the property files.</p>
 * <p>
 * This is a singleton static class, to be used as convenience when only a single factory is needed inside an
 * application. It exposes the {@link #newInstance()} method to create new instances of {@link Factory} objects.
 * </p>
 * @author Luigi R. Viggiano
 */
public final class ConfigFactory {

    static final Factory INSTANCE = newInstance();

    /** Don't let anyone instantiate this class */
    private ConfigFactory() {}

    /**
     * Returns a new instance of a config Factory object.
     *
     * @return a new instance of a config Factory object.
     */
    public static Factory newInstance() {
        ScheduledExecutorService scheduler = newSingleThreadScheduledExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread result = new Thread(r);
                result.setDaemon(true);
                return result;
            }
        });
        Properties props = new Properties();
        return new DefaultFactory(scheduler, props);
    }

    /**
     * Creates a {@link Config} instance from the specified interface
     *
     * @param clazz   the interface extending from {@link Config} that you want to instantiate.
     * @param imports additional variables to be used to resolve the properties.
     * @param <T>     type of the interface.
     * @return an object implementing the given interface, which maps methods to property values.
     */
    public static <T extends Config> T create(Class<? extends T> clazz, Map<?, ?>... imports) {
        return INSTANCE.create(clazz, imports);
    }

    /**
     * Creates a {@link Config} instance from the specified interface
     *
     * @param clazz   the interface extending from {@link Config} that you want to instantiate.
     * @param imports additional variables to be used to resolve the properties.
     * @param <T>     type of the interface.
     * @return an object implementing the given interface, which maps methods to property values.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Config> T validate(Class<? extends T> clazz, Map<?, ?>... imports) {
        T config = create(clazz, imports);
        for (Method method : clazz.getDeclaredMethods()) {
            Object value = findValue(PropertiesMapper.key(method), imports);
            if (value == null && PropertiesMapper.required(method)) {
                usage(clazz.getDeclaredMethods(), imports);
            }
        }

        return config;
    }

    private static void usage(final Method[] methods, final Map<?, ?>[] maps) {
        StringBuilder usage = new StringBuilder("Missing keys were found:\n");
        for (Method method : methods) {
            usage.append(" - ").append(PropertiesMapper.key(method));
            if(PropertiesMapper.required(method)) {
                usage.append("*");
            }
            final Object description = PropertiesMapper.description(method);
            if (description != null) {
                usage.append("  --  ").append(description);
            }
            usage.append("\n");

            final String defaultValue = PropertiesMapper.defaultValue(method);
            if (defaultValue != null) {
                usage.append("\t- Default:  ")
                       .append(defaultValue)
                       .append("\n");
            }
            Object value = findValue(PropertiesMapper.key(method), maps);
            if (value != null) {
                usage.append("\t- Current:  ")
                       .append(value)
                       .append("\n");
            }
        }
        usage.append("( *  Required )");

        throw new ValidationException(usage.toString());
    }

    private static Object findValue(final String key, final Map<?, ?>[] maps) {
        for (Map<?, ?> map : maps) {
            Object o = map.get(key);
            if (o != null) {
                return o;
            }
        }
        return null;
    }

    /**
     * Set a property in the ConfigFactory. Those properties will be used to expand variables specified in the `@Source`
     * annotation, or by the ConfigFactory to configure its own behavior.
     *
     * @param key   the key for the property.
     * @param value the value for the property.
     * @return the old value.
     * @since 1.0.4
     */
    public static String setProperty(String key, String value) {
        return INSTANCE.setProperty(key, value);
    }

    /**
     * Those properties will be used to expand variables specified in the `@Source` annotation, or by the ConfigFactory
     * to configure its own behavior.
     *
     * @return the properties in the ConfigFactory
     * @since 1.0.4
     */
    public static Properties getProperties() {
        return INSTANCE.getProperties();
    }

    /**
     * Those properties will be used to expand variables specified in the `@Source` annotation, or by the ConfigFactory
     * to configure its own behavior.
     *
     * @param properties the properties to set in the config Factory.
     * @since 1.0.4
     */
    public static void setProperties(Properties properties) {
        INSTANCE.setProperties(properties);
    }

    /**
     * Returns the value for a given property.
     *
     * @param key the key for the property
     * @return the value for the property, or <tt>null</tt> if the property is not set.
     * @since 1.0.4
     */
    public static String getProperty(String key) {
        return INSTANCE.getProperty(key);
    }

    /**
     * Clears the value for the property having the given key. This means, that the given property is removed.
     *
     * @param key the key for the property to remove.
     * @return the old value for the given key, or <tt>null</tt> if the property was not set.
     * @since 1.0.4
     */
    public static String clearProperty(String key) {
        return INSTANCE.clearProperty(key);
    }

    /**
     * Registers a loader to enables additional file formats.
     *
     * @param loader the loader to register.
     * @throws NullPointerException if specified loader is <tt>null</tt>.
     * @since 1.0.5
     */
    public static void registerLoader(Loader loader) {
        INSTANCE.registerLoader(loader);
    }

    public static class ValidationException extends RuntimeException {

        public ValidationException(final String message) {
            super(message);
        }
    }
}
