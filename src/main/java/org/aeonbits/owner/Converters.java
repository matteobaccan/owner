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
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import static java.lang.String.format;
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
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
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
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            if (targetType == File.class)
                return new File(expandUserHome(text));
            return null;
        }
    },

    CLASS_WITH_STRING_CONSTRUCTOR {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
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
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
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
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
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
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            try {
                return Class.forName(text);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    },

    ARRAY {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            if (!targetType.isArray())
                return null;

            Class<?> type = targetType.getComponentType();

            if (text.trim().isEmpty())
                return Array.newInstance(type, 0);

            String separator = ","; // TODO: allow the user to specify his own, via annotation
            String[] chunks = text.split(separator, -1);

            Converters converter = doConvert(targetMethod, type, chunks[0]).getConverter();
            Object result = Array.newInstance(type, chunks.length);

            for (int i = 0; i < chunks.length; i++) {
                String chunk = chunks[i].trim();
                Object value = converter.tryConvert(targetMethod, type, chunk);
                Array.set(result, i, value);
            }

            return result;
        }
    },

    COLLECTION {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            if (!targetType.isAssignableFrom(Collection.class)) {
                return null;
            }

            ParameterizedType parameterizedType = (ParameterizedType) targetMethod.getGenericReturnType();
            Class<?> type = (Class<?>) parameterizedType.getActualTypeArguments()[0];

            Object stub = Array.newInstance(type, 0);

            Object[] array = (Object[]) ARRAY.tryConvert(targetMethod, stub.getClass(), text);
            Collection<Object> list = Arrays.asList(array);

            Collection<Object> result = instantiateCollection(targetType);
            result.addAll(list);

            return result;
        }

        private <T> Collection<T> instantiateCollection(Class<? extends T> targetType) {
            if (targetType.isInterface()) {
                return instantiateCollectionFromInterface(targetType);
            }

            return instantiateCollectionFromClass(targetType);
        }

        private <T> Collection<T> instantiateCollectionFromClass(Class<? extends T> targetType) {
            try {
                return (Collection<T>) targetType.newInstance();
            } catch (InstantiationException e) {
                throw new UnsupportedOperationException("Could not instantiate collection type:" + targetType.getCanonicalName());
            } catch (IllegalAccessException e) {
                throw new UnsupportedOperationException("IllegalAccessException trying to instantiate collection type:" + targetType.getCanonicalName());
            }
        }

        private <T> Collection<T> instantiateCollectionFromInterface(Class<? extends T> targetType) {
            if (targetType.isAssignableFrom(Collection.class)) {
                return new HashSet<T>();
            }

            return null;
        }

    },

    UNSUPPORTED {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            throw unsupported(targetType, text);
        }
    };

    abstract Object tryConvert(Method targetMethod, Class<?> targetType, String text);

    static Object convert(Method targetMethod, Class<?> targetType, String text) {
        return doConvert(targetMethod, targetType, text).getConvertedValue();
    }

    private static ConversionResult doConvert(Method targetMethod, Class<?> targetType, String text) {
        for (Converters converter : values()) {
            Object convertedValue = converter.tryConvert(targetMethod, targetType, text);
            if (convertedValue != null)
                return new ConversionResult(converter, convertedValue);
        }
        throw unsupported(targetType, text); // this line is unreachable, but compiler needs it.
    }

    static final UnsupportedOperationException unsupported(Class<?> targetType, String text) {
        return new UnsupportedOperationException(
                format("Cannot convert '%s' to %s", text, targetType.getCanonicalName()));
    }

    private static class ConversionResult {
        private final Converters converter;
        private final Object convertedValue;

        public ConversionResult(Converters converter, Object convertedValue) {
            this.converter = converter;
            this.convertedValue = convertedValue;
        }

        public Converters getConverter() {
            return converter;
        }

        public Object getConvertedValue() {
            return convertedValue;
        }
    }
}
