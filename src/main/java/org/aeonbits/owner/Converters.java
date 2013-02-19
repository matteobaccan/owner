/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static java.lang.reflect.Modifier.isStatic;
import static org.aeonbits.owner.Util.expandUserHome;

/**
 * Converter class from {@link java.lang.String} to property types.
 *
 * @author Luigi R. Viggiano
 */
enum Converters {
    PROPERTY_EDITOR {
        @Override
        Object convert(Class<?> targetType, String text) {
            PropertyEditor editor = PropertyEditorManager.findEditor(targetType);
            if (editor != null) {
                editor.setAsText(text);
                return editor.getValue();
            }
            return null;
        }
    },

    FILE {
        @Override
        Object convert(Class<?> targetType, String text) {
            if (targetType == File.class)
                return new File(expandUserHome(text));
            return null;
        }
    },

    CLASS_WITH_STRING_CONSTRUCTOR {
        @Override
        Object convert(Class<?> targetType, String text) {
            try {
                Constructor<?> constructor = targetType.getConstructor(String.class);
                return constructor.newInstance(text);
            } catch (Exception e) {
                return null;
            }
        }
    },

    CLASS_WITH_OBJECT_CONSTRUCTOR {
        @Override
        Object convert(Class<?> targetType, String text) {
            try {
                Constructor<?> constructor = targetType.getConstructor(Object.class);
                return constructor.newInstance(text);
            } catch (Exception e) {
                return null;
            }
        }
    },

    CLASS_WITH_VALUE_OF_METHOD {
        @Override
        Object convert(Class<?> targetType, String text) {
            try {
                Method method = targetType.getMethod("valueOf", String.class);
                if (isStatic(method.getModifiers()))
                    return method.invoke(null, text);
                return null;
            } catch (Exception e) {
                return null;
            }
        }
    },

    CLASS {
        @Override
        Object convert(Class<?> targetType, String text) {
            try {
                return Class.forName(text);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    },

    UNSUPPORTED {
        @Override
        Object convert(Class<?> targetType, String text) {
            throw new UnsupportedOperationException(String.format("Cannot convert '%s' to %s", text,
                    targetType.getCanonicalName()));
        }
    };

    abstract Object convert(Class<?> targetType, String text);

    static Object unsupported(Class<?> targetType, String text) {
        return UNSUPPORTED.convert(targetType, text);
    }
}
