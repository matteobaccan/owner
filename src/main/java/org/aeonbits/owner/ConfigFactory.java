/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

/**
 * Factory class to instantiate {@link Config} instances.
 * By default a {link Config} sub-interface is associated to a property having the same package name and class name as
 * the interface itself.
 * <p/>
 * Method names are mapped to property names contained in the property files.
 *
 * This is a singleton static class, to be used as convenience when only a single factory is needed inside an
 * application.
 *
 * @author Luigi R. Viggiano
 */
public final class ConfigFactory {

    private static final AbstractConfigFactory instance = newInstance();

    /** Don't let anyone instantiate this class */
    private ConfigFactory() {}

    private static AbstractConfigFactory newInstance() {
        ScheduledExecutorService scheduler = newSingleThreadScheduledExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread result = new Thread(r);
                result.setDaemon(true);
                return result;
            }
        });
        Properties props = new Properties();
        return new DefaultConfigFactory(scheduler, props);
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
        return instance.create(clazz, imports);
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
        return instance.setProperty(key, value);
    }

    /**
     * Those properties will be used to expand variables specified in the `@Source` annotation, or by the ConfigFactory
     * to configure its own behavior.
     *
     * @return the properties in the ConfigFactory
     * @since 1.0.4
     */
    public static Properties getProperties() {
        return instance.getProperties();
    }

    /**
     * Those properties will be used to expand variables specified in the `@Source` annotation, or by the ConfigFactory
     * to configure its own behavior.
     *
     * @param properties the properties to set in the config Factory.
     * @since 1.0.4
     */
    public static void setProperties(Properties properties) {
        instance.setProperties(properties);
    }

    /**
     * Returns the value for a given property.
     *
     * @param key the key for the property
     * @return the value for the property, or <tt>null</tt> if the property is not set.
     * @since 1.0.4
     */
    public static String getProperty(String key) {
        return instance.getProperty(key);
    }

    /**
     * Clears the value for the property having the given key. This means, that the given property is removed.
     *
     * @param key the key for the property to remove.
     * @return the old value for the given key, or <tt>null</tt> if the property was not set.
     * @since 1.0.4
     */
    public static String clearProperty(String key) {
        return instance.clearProperty(key);
    }

}
