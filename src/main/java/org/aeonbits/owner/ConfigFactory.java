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

    ConfigFactory() {
        prohibitInstantiation();
    }

    /**
     * Creates a {@link Config} instance from the specified interface
     *
     * @param clazz     the interface extending from {@link Config} that you want to instantiate.
     * @param imports   additional variables to be used to resolve the properties.
     * @param <T>       type of the interface.
     * @return  an object implementing the given interface, which maps methods to property values.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Config> T create(Class<? extends T> clazz, Map<?, ?>... imports) {
        Class<?>[] interfaces = new Class<?>[]{clazz};
        PropertiesManager manager = new PropertiesManager(clazz, new Properties(), imports);
        InvocationHandler handler = new PropertiesInvocationHandler(manager);
        return (T) newProxyInstance(clazz.getClassLoader(), interfaces, handler);
    }
}
