/*
 * Copyright (c) 2013, Luigi R. Viggiano
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
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.aeonbits.owner.Config.DisableableFeature.PARAMETER_FORMATTING;
import static org.aeonbits.owner.Config.DisableableFeature.VARIABLE_EXPANSION;
import static org.aeonbits.owner.Converters.convert;
import static org.aeonbits.owner.PropertiesMapper.key;
import static org.aeonbits.owner.Util.isFeatureDisabled;

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
    private final StrSubstitutor substitutor;
    private final PropertiesManager propertiesManager;



    PropertiesInvocationHandler(PropertiesManager manager) {
        this.propertiesManager = manager;
        this.substitutor = new StrSubstitutor(manager.load());
    }

    @Override
    public Object invoke(Object proxy, Method method, Object... args) throws Throwable {
        for (DelegatedMethods delegated : DelegatedMethods.values())
            if (delegated.matches(method))
                return delegated.invoke(propertiesManager, args);
        return resolveProperty(method, args);
    }

    private Object resolveProperty(Method method, Object... args) {
        String key = key(method);
        String value = propertiesManager.getProperty(key);
        if (value == null)
            return null;
        return convert(method, method.getReturnType(), format(method, expandVariables(method, value), args));
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

}
