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

import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.aeonbits.owner.Util.prohibitInstantiation;

/**
 * Factory class to instantiate {@link Config} instances. By default a {link Config} sub-interface is associated to a
 * property having the same package name and class name as the interface itself.
 * <p/>
 * Method names are mapped to property names contained in the property files.
 *
 * @author Luigi R. Viggiano
 */
public abstract class ConfigFactory {

    private static final ScheduledExecutorService scheduler = newSingleThreadScheduledExecutor(new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread result = new Thread(r);
            result.setDaemon(true);
            return result;
        }
    });
    private static Properties props = new Properties();

    ConfigFactory() {
        prohibitInstantiation();
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
    public static <T extends Config> T create(Class<? extends T> clazz, Map<?, ?>... imports) {
        Class<?>[] interfaces = new Class<?>[] {clazz};
        VariablesExpander expander = new VariablesExpander(props);
        LoadersManager loaders = new LoadersManager();
        PropertiesManager manager = new PropertiesManager(clazz, new Properties(), scheduler, expander, loaders,
                imports);
        PropertiesInvocationHandler handler = new PropertiesInvocationHandler(manager);
        T proxy = (T) newProxyInstance(clazz.getClassLoader(), interfaces, handler);
        handler.setProxy(proxy);
        return proxy;
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
        checkKey(key);
        return (String) props.setProperty(key, value);
    }

    private static void checkKey(String key) {
        if (key == null)
            throw new NullPointerException("key can't be null");
        if (key.isEmpty())
            throw new IllegalArgumentException("key can't be empty");
    }

    /**
     * Those properties will be used to expand variables specified in the `@Source` annotation, or by the ConfigFactory
     * to configure its own behavior.
     *
     * @return the properties in the ConfigFactory
     * @since 1.0.4
     */
    public static Properties getProperties() {
        return props;
    }

    /**
     * Those properties will be used to expand variables specified in the `@Source` annotation, or by the ConfigFactory
     * to configure its own behavior.
     *
     * @param properties the properties to set in the config Factory.
     * @since 1.0.4
     */
    public static void setProperties(Properties properties) {
        if (properties == null)
            props = new Properties();
        else
            props = properties;
    }

    /**
     * Returns the value for a given property.
     *
     * @param key the key for the property
     * @return the value for the property, or <tt>null</tt> if the property is not set.
     * @since 1.0.4
     */
    public static String getProperty(String key) {
        checkKey(key);
        return props.getProperty(key);
    }

    /**
     * Clears the value for the property having the given key. This means, that the given property is removed.
     *
     * @param key the key for the property to remove.
     * @return the old value for the given key, or <tt>null</tt> if the property was not set.
     * @since 1.0.4
     */
    public static String clearProperty(String key) {
        checkKey(key);
        return (String) props.remove(key);
    }

}
