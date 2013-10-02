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

import static java.lang.reflect.Proxy.newProxyInstance;

/**
 * Default implementation for {@link ConfigFactoryInstance}.
 *
 * @author Luigi R. Viggiano
 */
class ConfigFactoryInstanceImpl implements ConfigFactoryInstance {
    private final ScheduledExecutorService scheduler;
    private Properties props;

    ConfigFactoryInstanceImpl(ScheduledExecutorService scheduler, Properties props) {
        this.scheduler = scheduler;
        this.props = props;
    }

    @SuppressWarnings("unchecked")
    public <T extends Config> T create(Class<? extends T> clazz, Map<?, ?>... imports) {
        Class<?>[] interfaces = new Class<?>[] {clazz};
        VariablesExpander expander = new VariablesExpander(props);
        LoadersManager loaders = newLoadersManager();
        PropertiesManager manager = new PropertiesManager(clazz, new Properties(), scheduler, expander, loaders,
                imports);
        PropertiesInvocationHandler handler = new PropertiesInvocationHandler(manager);
        T proxy = (T) newProxyInstance(clazz.getClassLoader(), interfaces, handler);
        handler.setProxy(proxy);
        return proxy;
    }

    LoadersManager newLoadersManager() {
        return new LoadersManager();
    }

    public String setProperty(String key, String value) {
        checkKey(key);
        return (String) props.setProperty(key, value);
    }

    private void checkKey(String key) {
        if (key == null)
            throw new NullPointerException("key can't be null");
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

    public String getProperty(String key) {
        checkKey(key);
        return props.getProperty(key);
    }

    public String clearProperty(String key) {
        checkKey(key);
        return (String) props.remove(key);
    }

}
