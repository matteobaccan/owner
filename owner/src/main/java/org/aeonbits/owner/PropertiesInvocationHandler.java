/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.aeonbits.owner.Config.DisableableFeature.PARAMETER_FORMATTING;
import static org.aeonbits.owner.Config.DisableableFeature.VARIABLE_EXPANSION;
import static org.aeonbits.owner.Converters.convert;
import static org.aeonbits.owner.PropertiesManager.Delegate;
import static org.aeonbits.owner.PropertiesMapper.key;
import static org.aeonbits.owner.Util.isFeatureDisabled;

/**
 * This {@link InvocationHandler} receives method calls from the delegate instantiated by {@link ConfigFactory} and maps
 * it to a property value from a property file, or a {@link Config.DefaultValue} specified in method annotation.
 * <p/>
 * The {@link Config.Key} annotation can be used to override default mapping between method names and property names.
 * <p/>
 * Automatic conversion is handled between the property value and the return type expected by the method of the
 * delegate.
 *
 * @author Luigi R. Viggiano
 */
class PropertiesInvocationHandler implements InvocationHandler, Serializable {

    private static final Method[] DELEGATES = findDelegates();
    private final StrSubstitutor substitutor;
    final PropertiesManager propertiesManager;

    PropertiesInvocationHandler(PropertiesManager manager) {
        this.propertiesManager = manager;
        this.substitutor = new StrSubstitutor(manager.load());
    }

    public Object invoke(Object proxy, Method invokedMethod, Object... args) throws Throwable {
        propertiesManager.syncReloadCheck();
        Method delegate = getDelegateMethod(invokedMethod);
        if (delegate != null)
            return delegate(delegate, args);

        return resolveProperty(invokedMethod, args);
    }

    private Object delegate(Method delegate, Object[] args) throws Throwable {
        try {
            return delegate.invoke(propertiesManager, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private Method getDelegateMethod(Method invokedMethod) {
        for (Method delegate : DELEGATES)
            if (equals(invokedMethod, delegate))
                return delegate;
        return null;
    }

    private boolean equals(Method a, Method b) {
        return a.getName().equals(b.getName())
                && a.getReturnType().equals(b.getReturnType())
                && Arrays.equals(a.getParameterTypes(), b.getParameterTypes());
    }

    private Object resolveProperty(Method method, Object... args) {
        String key = key(method);
        String value = propertiesManager.getProperty(key);
        if (value == null)
            return null;
        Object result = convert(method, method.getReturnType(), format(method, expandVariables(method, value), args));
        if (result == Converters.NULL) return null;
        return result;
    }

    private String format(Method method, String format, Object... args) {
        if (isFeatureDisabled(method, PARAMETER_FORMATTING))
            return format;
        return String.format(format, args);
    }

    private String expandVariables(Method method, String value) {
        if (isFeatureDisabled(method, VARIABLE_EXPANSION))
            return value;
        return substitutor.replace(value);
    }

    private static Method[] findDelegates() {
        List<Method> result = new LinkedList<Method>();
        Method[] methods = PropertiesManager.class.getMethods();
        for (Method m : methods)
            if (m.getAnnotation(Delegate.class) != null)
                result.add(m);
        return result.toArray(new Method[result.size()]);
    }

    public <T extends Config> void setProxy(T proxy) {
        propertiesManager.setProxy(proxy);
    }

}
