/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.handlers.ValueHandler;
import org.aeonbits.owner.loaders.Loader;

import javax.management.DynamicMBean;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static java.lang.reflect.Proxy.newProxyInstance;
import static org.aeonbits.owner.util.Reflection.isClassAvailable;

/**
 * Default implementation for {@link Factory}.
 *
 * @author Luigi R. Viggiano
 */
class DefaultFactory implements Factory {

    private static final boolean JMX_AVAILABLE = isClassAvailable("javax.management.DynamicMBean");
    private final ScheduledExecutorService scheduler;
    private Properties props;
    final LoadersManager loadersManager;
    final HandlersManager handlersManager;
    final ConvertersManager convertersManager;

    DefaultFactory(ScheduledExecutorService scheduler, Properties props) {
        this.scheduler = scheduler;
        this.props = props;
        this.loadersManager = new LoadersManager();
        this.handlersManager = new HandlersManager();
        this.convertersManager = new ConvertersManager();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Config> T create(Class<? extends T> clazz, Map<?, ?>... imports) {
        validateImports(imports);
        Class<?>[] interfaces = interfaces(clazz);
        VariablesExpander expander = new VariablesExpander(props, handlersManager);
        // read once, here: from now on the Config object keeps the prefix it was born with
        boolean strict = Boolean.parseBoolean(props.getProperty(PropertiesManager.STRICT));
        boolean declaredOnly = Boolean.parseBoolean(props.getProperty(PropertiesManager.DECLARED_ONLY));
        PropertiesManager manager = new PropertiesManager(clazz, new Properties(), scheduler, expander, loadersManager,
                handlersManager, convertersManager, KeyPrefix.from(props), strict, declaredOnly,
                Includes.tokenIn(props), imports);
        Object jmxSupport = getJMXSupport(clazz, manager);
        PropertiesInvocationHandler handler = new PropertiesInvocationHandler(clazz, manager, jmxSupport);
        handler.validateMandatoryProperties();
        T proxy = (T) newProxyInstance(clazz.getClassLoader(), interfaces, handler);
        handler.setProxy(proxy);
        // after the proxy exists, and it has to be: a validation constraint is checked against a value, and
        // a value is what a mapping method answers with. The mandatory check above needs no proxy and runs
        // first on purpose - a property that is not there at all is a better thing to be told than the same
        // property failing a @NotNull
        handler.validateConstraints(proxy);
        return proxy;
    }

    /**
     * Rejects imports that {@link Properties} cannot represent.
     * <p>
     * Imports are merged into a {@link Properties} instance, whose contract only admits {@link String} keys and
     * values. Since {@link Properties} extends {@code Hashtable<Object, Object>}, entries of any other type are
     * accepted by {@code putAll} but become invisible to {@code getProperty}: a non-String value makes the property
     * resolve to {@code null} - shadowing any {@link Config.DefaultValue} - and a non-String key is silently
     * ignored. Failing here turns both silent misbehaviours into an immediate, explicit error.</p>
     *
     * @param imports the imports to validate, may be empty.
     * @throws IllegalArgumentException if any entry has a null or non-String key or value.
     */
    private static void validateImports(Map<?, ?>... imports) {
        for (Map<?, ?> map : imports) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (key == null)
                    throw new IllegalArgumentException("An import contains a null key");
                if (!(key instanceof String))
                    throw new IllegalArgumentException(String.format(
                            "An import contains a non-string key: '%s' of type %s", key, key.getClass().getName()));
                if (value == null)
                    throw new IllegalArgumentException(String.format(
                            "An import contains a null value for key: '%s'", key));
                if (!(value instanceof String))
                    throw new IllegalArgumentException(String.format(
                            "An import contains a non-string value for key: '%s' of type %s",
                            key, value.getClass().getName()));
            }
        }
    }

    @Override
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

    @Override
    public Properties getProperties() {
        return props;
    }

    @Override
    public void setProperties(Properties properties) {
        if (properties == null)
            props = new Properties();
        else
            props = properties;
    }

    @Override
    public void registerLoader(Loader loader) {
        loadersManager.registerLoader(loader);
    }

    @Override
    public void registerValueHandler(ValueHandler handler) {
        handlersManager.registerValueHandler(handler);
    }

    @Override
    public void setTypeConverter(Class<?> type, Class<? extends Converter<?>> converter) {
        convertersManager.setTypeConverter(type, converter);
    }

    @Override
    public void setTypeConverter(Class<?> type, Converter<?> converter) {
        convertersManager.setTypeConverter(type, converter);
    }

    @Override
    public void removeTypeConverter(Class<?> type){
        convertersManager.removeTypeConverter(type);
    }

    @Override
    public String getProperty(String key) {
        checkKey(key);
        return props.getProperty(key);
    }

    @Override
    public String clearProperty(String key) {
        checkKey(key);
        return (String) props.remove(key);
    }

    private Object getJMXSupport(Class<?> clazz, PropertiesManager manager) {
        if (JMX_AVAILABLE)
            return new JMXSupport(clazz, manager);
        return null;
    }

    private <T extends Config> Class<?>[] interfaces(Class<? extends T> clazz) {
        if (JMX_AVAILABLE)
            return new Class<?>[]{clazz, DynamicMBean.class};
        else
            return new Class<?>[]{clazz};
    }

}
