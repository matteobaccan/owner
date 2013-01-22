/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;


import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Key;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Properties;

import static java.lang.String.format;

/**
 * This {@link InvocationHandler} receives method calls from the delegate instantiated by {@link ConfigFactory} and maps it
 * to a property value from a property file, or a {@link DefaultValue} specified in method annotation. The {@link Key}
 * annotation can be used to override default mapping between method names and property names.
 * <p/>
 * Automatic conversion is handled between the property value and the return type expected by the method of the delegate.
 *
 * @author Luigi R. Viggiano
 */
class PropertiesInvocationHandler implements InvocationHandler {
    private final Properties properties;
    private static Method listPrintStream;
    private static Method listPrintWriter;

    static {
        try {
            Class<Properties> propertiesClass = Properties.class;
            listPrintStream = propertiesClass.getMethod("list", PrintStream.class);
            listPrintWriter = propertiesClass.getMethod("list", PrintWriter.class);
        } catch (NoSuchMethodException e) {
            // this shouldn't happen, btw we handle the case in which the delegate method is not available...
            // so, it's fine.
        }
    }

    public PropertiesInvocationHandler(Properties properties) {
        this.properties = properties;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object... args) throws Throwable {
        Method proxyMethod;
        if (null != (proxyMethod = proxyMethod(method)))
            return delegate(properties, proxyMethod, args);
        return resolveProperty(method, args);
    }

    private Object resolveProperty(Method method, Object... args) {
        String key = key(method);
        String defaultValue = defaultValue(method);
        String value = properties.getProperty(key, defaultValue);
        if (value == null)
            return null;
        return convert(method.getReturnType(), format(value, args)); // TODO: variable expansion here would be nice
    }

    private Object delegate(Object target, Method method, Object... args) throws InvocationTargetException,
            IllegalAccessException {
        return method.invoke(target, args);
    }

    private Method proxyMethod(Method method) {
        if (matches(listPrintStream, method))
            return listPrintStream;
        if (matches(listPrintWriter, method))
            return listPrintWriter;
        return null;
    }

    private boolean matches(Method proxied, Method proxy) {
        return proxied != null && proxied.getName().equals(proxy.getName())
                && Arrays.equals(proxied.getParameterTypes(), proxy.getParameterTypes());
    }

    private String key(Method method) {
        Key key = method.getAnnotation(Key.class);
        return (key == null) ? method.getName() : key.value();
    }

    private Object convert(Class<?> targetType, String text) {
        PropertyEditor editor = PropertyEditorManager.findEditor(targetType);
        editor.setAsText(text);
        return editor.getValue();
    }

    private String defaultValue(Method method) {
        DefaultValue defaultValue = method.getAnnotation(DefaultValue.class);
        return defaultValue != null ? defaultValue.value() : null;
    }
}
