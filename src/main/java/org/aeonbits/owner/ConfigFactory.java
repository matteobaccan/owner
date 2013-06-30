/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.lang.reflect.InvocationHandler;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import static java.lang.reflect.Proxy.newProxyInstance;
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

    // TODO: extract this into a class HotReloadScheduler that allows scheduling only once per class,
    // and keeps a reference to the command scheduled and - possibly - allows the user to dispose the scheduled command.
    private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
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
        Class<?>[] interfaces = new Class<?>[]{clazz};
        VariablesExpander expander = new VariablesExpander(props);
        PropertiesManager manager = new PropertiesManager(clazz, new Properties(), scheduler, expander, imports);
        InvocationHandler handler = new PropertiesInvocationHandler(manager);
        return (T) newProxyInstance(clazz.getClassLoader(), interfaces, handler);
    }

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

    public static Properties getProperties() {
        return props;
    }

    public static void setProperties(Properties properties) {
        if (properties == null)
            props = new Properties();
        else
            props = properties;
    }

    public static String getProperty(String key) {
        checkKey(key);
        return props.getProperty(key);
    }

    public static String clearProperty(String key) {
        checkKey(key);
        return (String) props.remove(key);
    }

}
