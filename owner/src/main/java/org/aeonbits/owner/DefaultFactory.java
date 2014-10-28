/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import static java.lang.reflect.Proxy.newProxyInstance;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import org.aeonbits.owner.loaders.Loader;

/**
 * Default implementation for {@link Factory}.
 *
 * @author Luigi R. Viggiano
 */
class DefaultFactory implements Factory {

    private final ScheduledExecutorService scheduler;
    private Properties props;
    final LoadersManager loadersManager;

    DefaultFactory(ScheduledExecutorService scheduler, Properties props) {
        this.scheduler = scheduler;
        this.props = props;
        this.loadersManager = new LoadersManager();
    }

    @SuppressWarnings("unchecked")
    public <T extends Config> T create(Class<? extends T> clazz, Map<?, ?>... imports) {
        Class<?>[] interfaces = new Class<?>[] {clazz};
        VariablesExpander expander = new VariablesExpander(props);
        PropertiesManager manager = new PropertiesManager(clazz, new Properties(), scheduler, expander, loadersManager,
                imports);
        PropertiesInvocationHandler handler = new PropertiesInvocationHandler(manager, clazz);
        T proxy = (T) newProxyInstance(clazz.getClassLoader(), interfaces, handler);
        handler.setProxy(proxy);
        return proxy;
    }

    public String setProperty(String key, String value) {
        checkKey(key);
        return (String) props.setProperty(key, value);
    }

    private void checkKey(String key) {
        if (key == null)
            throw new IllegalArgumentException("key can't be null");
        if (key.isEmpty())
            throw new IllegalArgumentException("key can't be empty");
    }

    public Properties getProperties() {
        return props;
    }

    public void setProperties(Properties properties) {
        if (properties == null)
            props = new Properties();
        else
            props = properties;
    }

    public void registerLoader(Loader loader) {
        loadersManager.registerLoader(loader);
    }

    public String getProperty(String key) {
        checkKey(key);
        return props.getProperty(key);
    }

    public String clearProperty(String key) {
        checkKey(key);
        return (String) props.remove(key);
    }

}
