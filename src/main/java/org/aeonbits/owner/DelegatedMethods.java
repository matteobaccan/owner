/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Arrays;

enum DelegatedMethods {
    LIST_PRINT_STREAM(getMethod(Listable.class, "list", PrintStream.class)) {
        @Override
        public Object delegate(PropertiesManager propsMgr, Object... args) {
            propsMgr.list((PrintStream) args[0]);
            return null;
        }
    },

    LIST_PRINT_WRITER(getMethod(Listable.class, "list", PrintWriter.class)) {
        @Override
        public Object delegate(PropertiesManager propsMgr, Object... args) {
            propsMgr.list((PrintWriter) args[0]);
            return null;
        }
    },

    RELOAD(getMethod(Reloadable.class, "reload")) {
        @Override
        public Object delegate(PropertiesManager propsMgr, Object... args) {
            propsMgr.reload();
            return null;
        }
    },

    SET_PROPERTY(getMethod(Modifiable.class, "setProperty", String.class, String.class)) {
        @Override
        public Object delegate(PropertiesManager propsMgr, Object[] args) {
            return propsMgr.setProperty((String) args[0], (String) args[1]);
        }
    },

    REMOVE_PROPERTY(getMethod(Modifiable.class, "removeProperty", String.class)) {
        @Override
        public Object delegate(PropertiesManager propsMgr, Object[] args) {
            return propsMgr.removeProperty((String) args[0]);
        }
    };

    private final Method delegableMethod;

    DelegatedMethods(Method delegableMethod) {
        this.delegableMethod = delegableMethod;
    }

    boolean matches(Method invokedMethod) {
        return delegableMethod.getName().equals(invokedMethod.getName())
                && Arrays.equals(delegableMethod.getParameterTypes(), invokedMethod.getParameterTypes());
    }

    private static Method getMethod(Class<?> aClass, String name, Class<?>... args) {
        try {
            return aClass.getMethod(name, args);
        } catch (NoSuchMethodException e) {
            // this shouldn't happen, btw we handle the case in which the delegate method is not available...
            // so, it's fine.
            return null;
        }
    }

    abstract Object delegate(PropertiesManager propsMgr, Object[] args);

    static DelegatedMethods[] delegableMethods() {
        return values();
    }
}