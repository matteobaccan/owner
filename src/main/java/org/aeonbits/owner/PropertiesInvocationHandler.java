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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Properties;

import static java.lang.String.format;
import static org.aeonbits.owner.Converters.unsupported;
import static org.aeonbits.owner.PropertiesMapper.key;

/**
 * This {@link InvocationHandler} receives method calls from the delegate instantiated by {@link ConfigFactory} and maps
 * it to a property value from a property file, or a {@link DefaultValue} specified in method annotation.
 * <p/>
 * The {@link Key} annotation can be used to override default mapping between method names and property names.
 * <p/>
 * Automatic conversion is handled between the property value and the return type expected by the method of the
 * delegate.
 *
 * @author Luigi R. Viggiano
 */
class PropertiesInvocationHandler implements InvocationHandler {
    private final Properties properties;
    private final StrSubstitutor substitutor;
    private static final Method listPrintStream = getMethod(Properties.class, "list", PrintStream.class);
    private static final Method listPrintWriter = getMethod(Properties.class, "list", PrintWriter.class);

    private static Method getMethod(Class<?> aClass, String name, Class<?>... args) {
        try {
            return aClass.getMethod(name, args);
        } catch (NoSuchMethodException e) {
            // this shouldn't happen, btw we handle the case in which the delegate method is not available...
            // so, it's fine.
            return null;
        }
    }

    PropertiesInvocationHandler(Properties properties) {
        this.properties = properties;
        this.substitutor = new StrSubstitutor(properties);
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
        String value = properties.getProperty(key);
        if (value == null)
            return null;
        return convert(method.getReturnType(), format(substitutor.replace(value), args));
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

    private Object convert(Class<?> targetType, String text) {
        for (Converters converter : Converters.values()) {
            Object converted = converter.convert(targetType, text);
            if (converted != null)
                return converted;
        }
        return unsupported(targetType, text);
    }
}
