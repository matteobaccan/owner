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
import java.lang.reflect.Constructor;

/**
 * @author luigi
 */
public enum Converters {
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

    CLASS_WITH_STRING_CONSTRUCTOR {
        @Override
        Object convert(Class<?> targetType, String text) {
            try {
                Constructor<?> constructor = targetType.getConstructor(String.class);
                return constructor.newInstance(text);
            } catch (ReflectiveOperationException ex) {
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
            } catch (ReflectiveOperationException ex) {
                return null;
            }
        }
    },

    CLASS_CONVERTER {
        @Override
        Object convert(Class<?> targetType, String text) {
            try {
                return Class.forName(text);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    };

    abstract Object convert(Class<?> targetType, String text);

    public static Object unsupported(Class<?> targetType, String text) {
        throw new UnsupportedOperationException(String.format("Cannot convert '%s' to %s", text,
                targetType.getCanonicalName()));
    }
}
