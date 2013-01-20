/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package com.github.owner;


import com.github.owner.Config.DefaultValue;
import com.github.owner.Config.Key;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Properties;

import static java.lang.String.format;

/**
 * This {@link InvocationHandler} receives method calls from the proxy instantiated by {@link ConfigFactory} and maps
 * it to a property value from a property file, or a {@link DefaultValue} specified in method annotation.
 * The {@link Key} annotation can be used to override default mapping between method names and property names.
 *
 * Automatic conversion is handled between the property value and the return type expected by the method of the proxy.
 *
 * @author Luigi R. Viggiano
 */
class PropertyInvocationHandler implements InvocationHandler {
    private final Properties properties;

    public PropertyInvocationHandler(Properties properties) {
        this.properties = properties;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String key = key(method);
        String defaultValue = defaultValue(method);
        String value = properties.getProperty(key, defaultValue);
        if (value == null)
            return null;
        return convert(method.getReturnType(), format(value, args)); // TODO: variable expansion here would be nice
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
