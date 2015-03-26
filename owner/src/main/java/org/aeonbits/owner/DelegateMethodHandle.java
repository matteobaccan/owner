/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @author Luigi R. Viggiano
 */
class DelegateMethodHandle {
    private final Object target;
    private final Method method;

    public DelegateMethodHandle(Object target, Method method) {
        this.target = target;
        this.method = method;
    }

    public Object invoke(Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    public boolean matches(Method invokedMethod) {
        return invokedMethod.getName().equals(method.getName())
                && invokedMethod.getReturnType().equals(method.getReturnType())
                && Arrays.equals(invokedMethod.getParameterTypes(), method.getParameterTypes());
    }

}
