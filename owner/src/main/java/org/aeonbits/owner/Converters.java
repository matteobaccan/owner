/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.CollectionConverterClass;
import org.aeonbits.owner.Config.ConverterClass;
import org.aeonbits.owner.util.DurationParser;

import java.beans.PropertyEditor;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.beans.PropertyEditorManager.findEditor;
import static java.lang.Boolean.getBoolean;
import static java.lang.reflect.Modifier.isStatic;
import static org.aeonbits.owner.Converters.SpecialValue.NULL;
import static org.aeonbits.owner.Converters.SpecialValue.SKIP;
import static org.aeonbits.owner.util.Util.expandUserHome;
import static org.aeonbits.owner.util.Util.unreachableButCompilerNeedsThis;
import static org.aeonbits.owner.util.Util.unsupported;
import static org.aeonbits.owner.util.Reflection.isClassAvailable;

/**
 * Converter class from {@link java.lang.String} to property types.
 *
 * @author Luigi R. Viggiano
 */
enum Converters {

    /**
     * Must come first: the converter takes over the raw property value before {@link #ARRAY} or
     * {@link #COLLECTION} get a chance to tokenize it, which is the whole point of the annotation.
     */
    METHOD_WITH_COLLECTION_CONVERTER_CLASS_ANNOTATION {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            CollectionConverterClass annotation = targetMethod.getAnnotation(CollectionConverterClass.class);
            if (annotation == null) return SKIP;
            if (!Collection.class.isAssignableFrom(targetType))
                throw unsupported(
                        "@CollectionConverterClass can only be used on a method returning a Collection, but '%s' "
                                + "returns %s; use @ConverterClass instead",
                        targetMethod.getName(), targetType.getName());
            return convertWithConverterClass(targetMethod, text, annotation.value());
        }
    },

    ARRAY {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            if (!targetType.isArray()) return SKIP;

            Class<?> type = targetType.getComponentType();

            if (text.trim().isEmpty())
                return Array.newInstance(type, 0);

            Tokenizer tokenizer = TokenizerResolver.resolveTokenizer(targetMethod);
            String[] chunks = tokenizer.tokens(text);

            Converters converter = doConvert(targetMethod, type, chunks[0], key).getConverter();
            Object result = Array.newInstance(type, chunks.length);

            for (int i = 0; i < chunks.length; i++) {
                String chunk = chunks[i];
                Object value = converter.tryConvert(targetMethod, type, chunk, key);
                // the converter is chosen once from the first chunk: the remaining ones may still
                // fail, and their special values must not end up as elements of the resulting array
                if (value == SKIP)
                    throw unsupportedConversion(key, type, chunk);
                set(targetMethod, result, i, type, value == NULL ? null : value);
            }

            return result;
        }

        /**
         * Stores one converted element, turning the {@link IllegalArgumentException} thrown by
         * {@link Array#set} into something that says what to do about it. It means the converter produced
         * something that is not an element of the collection, which is what happens when a
         * {@link ConverterClass} meant to build the whole collection is used on a method returning one:
         * that annotation converts one element at a time.
         */
        private void set(Method targetMethod, Object array, int index, Class<?> type, Object value) {
            try {
                Array.set(array, index, value);
            } catch (IllegalArgumentException e) {
                if (targetMethod.getAnnotation(ConverterClass.class) == null)
                    throw e;
                throw unsupported(e,
                        "The @ConverterClass of '%s' returned a %s, which is not an element of type %s: "
                                + "@ConverterClass converts one element at a time, so it has to return one. "
                                + "Use @CollectionConverterClass to convert the whole value at once instead",
                        targetMethod.getName(),
                        value == null ? "null" : value.getClass().getName(),
                        type.getName());
            }
        }
    },

    COLLECTION {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            if (!Collection.class.isAssignableFrom(targetType)) return SKIP;

            Class<?> type = elementType(targetMethod);
            Object stub = Array.newInstance(type, 0);
            Object[] array = (Object[]) ARRAY.tryConvert(targetMethod, stub.getClass(), text, key);

            Collection<Object> result = instantiateCollection(targetMethod, targetType);
            result.addAll(Arrays.asList(array));
            return result;
        }
    },

    METHOD_WITH_CONVERTER_CLASS_ANNOTATION {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            ConverterClass annotation = targetMethod.getAnnotation(ConverterClass.class);
            if (annotation == null) return SKIP;

            Class<? extends Converter> converterClass = annotation.value();
            return convertWithConverterClass(targetMethod, text, converterClass);
        }
    },

    METHOD_WITH_REGISTERED_CONVERTER {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            if (!converterRegistry.containsKey(targetType)) return SKIP;

            Class<? extends Converter> converterClass = converterRegistry.get(targetType);
            return convertWithConverterClass(targetMethod, text, converterClass);
        }
    },

    PROPERTY_EDITOR {
        private final boolean isPropertyEditorAvailable =
                isClassAvailable("java.beans.PropertyEditorManager");

        private final boolean isPropertyEditorDisabled =
                getBoolean("org.aeonbits.owner.property.editor.disabled");

        private final boolean canUsePropertyEditors = isPropertyEditorAvailable && !isPropertyEditorDisabled;

        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            if (!canUsePropertyEditors)
                return SKIP;

            PropertyEditor editor = findEditor(targetType);
            if (editor == null) return SKIP;
            try {
                editor.setAsText(text);
                return editor.getValue();
            } catch (Exception e) {
                throw unsupportedConversion(e, key, targetType, text);
            }
        }
    },

    /*
     * This is needed for cases like when the PropertyEditor classes are not available
     */
    PRIMITIVE {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            if (!targetType.isPrimitive()) return SKIP;
            // a value that does not parse must fail the same way here as it does through a PropertyEditor:
            // this converter only runs where the editors are missing or disabled, and the caller should not
            // have to tell the two situations apart from the exception it gets
            try {
                if (targetType == Byte.TYPE) return Byte.parseByte(text);
                if (targetType == Short.TYPE) return Short.parseShort(text);
                if (targetType == Integer.TYPE) return Integer.parseInt(text);
                if (targetType == Long.TYPE) return Long.parseLong(text);
                if (targetType == Boolean.TYPE) return Boolean.parseBoolean(text);
                if (targetType == Float.TYPE) return Float.parseFloat(text);
                if (targetType == Double.TYPE) return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                throw unsupportedConversion(e, key, targetType, text);
            }
            return SKIP;
        }
    },

    FILE {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            if (targetType != File.class) return SKIP;
            return new File(expandUserHome(text));
        }
    },

    /** Same treatment as {@link #FILE}, so that the two ways of naming a path behave alike. */
    PATH {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            if (targetType != Path.class) return SKIP;
            return Paths.get(expandUserHome(text));
        }
    },

    /**
     * A timeout is the commonest typed setting there is after a number and a string, and
     * {@link Duration} is the type the JDK has for it — so it is converted out of the box, as
     * {@link #FILE} and {@link #PATH} are, rather than asking for a
     * {@link ConverterClass @ConverterClass(DurationConverter.class)} on every method that returns one.
     * <p>
     * Coming after {@link #METHOD_WITH_CONVERTER_CLASS_ANNOTATION} and
     * {@link #METHOD_WITH_REGISTERED_CONVERTER}, it is the default and not the rule: a converter named
     * on the method, or registered for the type, still takes precedence.
     * </p>
     *
     * @since 2.0.0
     */
    DURATION {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            if (targetType != Duration.class) return SKIP;
            requireExplicitTimeUnit(text, key);
            try {
                return DurationParser.parse(text);
            } catch (RuntimeException e) {
                // the converter reports an IllegalArgumentException; every other type reports the
                // same UnsupportedOperationException naming the value, the type and the key
                throw unsupportedConversion(e, key, targetType, text);
            }
        }

        /**
         * A bare number is read as milliseconds by {@link DurationParser}, which is a reasonable
         * thing to opt into and a poor thing to have happen silently: whoever writes
         * <code>timeout=30</code> means seconds far more often than milliseconds, and would get a
         * service that gives up thirty milliseconds in, with nothing said. The unit is therefore
         * required on this path — the automatic one — while the annotation keeps accepting a bare
         * number, so nothing written against the previous behaviour changes.
         */
        private void requireExplicitTimeUnit(String text, String key) {
            String trimmed = text.trim();
            if (trimmed.isEmpty() || DurationParser.isIso8601(trimmed))
                return;
            if (!Character.isLetter(trimmed.charAt(trimmed.length() - 1)))
                throw unsupported(
                        "Cannot convert '%s' to a Duration for property '%s': the time unit is missing. "
                                + "Write it explicitly — '%s ms', '%s s', '%s m', '%s h' or '%s d' — or use an "
                                + "ISO-8601 duration such as 'PT30S'. A bare number would be read as "
                                + "milliseconds, which is rarely what is meant.",
                        text, key, trimmed, trimmed, trimmed, trimmed, trimmed);
        }
    },

    CLASS {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            if (targetType != Class.class) return SKIP;
            try {
                return Class.forName(text);
            } catch (ClassNotFoundException ex) {
                throw unsupportedConversion(ex, key, targetType, text);
            }
        }
    },

    CLASS_WITH_STRING_CONSTRUCTOR {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            try {
                Constructor<?> constructor = targetType.getConstructor(String.class);
                return constructor.newInstance(text);
            } catch (Exception e) {
                return SKIP;
            }
        }
    },

    CLASS_WITH_VALUE_OF_METHOD {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            try {
                Method method = targetType.getMethod("valueOf", String.class);
                if (isStatic(method.getModifiers()))
                    return method.invoke(null, text);
                return SKIP;
            } catch (Exception e) {
                return SKIP;
            }
        }
    },

    CLASS_WITH_OBJECT_CONSTRUCTOR {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            try {
                Constructor<?> constructor = targetType.getConstructor(Object.class);
                return constructor.newInstance(text);
            } catch (Exception e) {
                return SKIP;
            }
        }
    },

    UNSUPPORTED {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key) {
            throw unsupportedConversion(key, targetType, text);
        }
    };

    /**
     * The type of one element of the array or collection the method reads.
     * <p>
     * An <code>Optional&lt;Collection&lt;E&gt;&gt;</code> keeps the collection one level down and the
     * element type is <code>E</code> either way, so what is asked for is the type the value converts to
     * rather than the return type. A raw collection yields {@link String}.
     * </p>
     *
     * @param targetMethod the method being resolved.
     * @return the element type.
     */
    static Class<?> elementType(Method targetMethod) {
        Type type = OptionalSupport.valueType(targetMethod);
        if (type instanceof GenericArrayType)
            return erase(((GenericArrayType) type).getGenericComponentType());
        if (type instanceof Class && ((Class<?>) type).isArray())
            return ((Class<?>) type).getComponentType();
        if (type instanceof ParameterizedType)
            return erase(((ParameterizedType) type).getActualTypeArguments()[0]);
        return String.class;
    }

    private static Class<?> erase(Type type) {
        if (type instanceof Class)
            return (Class<?>) type;
        if (type instanceof ParameterizedType)
            return (Class<?>) ((ParameterizedType) type).getRawType();
        return String.class;
    }

    /**
     * An empty collection of the kind the method asks for: the very type when it names a class, and a
     * reasonable one for each of the interfaces. Shared with whoever else has elements to put in a
     * collection the caller chose — reading a list from indexed keys, for one.
     *
     * @param targetMethod the method being resolved, which carries the element type.
     * @param targetType   the collection type asked for.
     * @return an empty, modifiable collection.
     */
    @SuppressWarnings("unchecked")
    static Collection<Object> instantiateCollection(Method targetMethod, Class<?> targetType) {
        Class<?> element = elementType(targetMethod);
        if (element.isEnum() && targetType.equals(EnumSet.class))
            return (Collection<Object>) instantiateEnumSet((Class<? extends Enum>) element);
        if (!targetType.isInterface())
            return instantiateCollectionFromClass(targetType);
        if (SortedSet.class.isAssignableFrom(targetType))
            return new TreeSet<>();
        if (Set.class.isAssignableFrom(targetType))
            return new LinkedHashSet<>();
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> instantiateCollectionFromClass(Class<?> targetType) {
        try {
            return (Collection<Object>) targetType.newInstance();
        } catch (Exception e) {
            throw unsupported(e, "Cannot instantiate collection of type '%s'", targetType.getCanonicalName());
        }
    }

    private static <E extends Enum<E>> Collection<?> instantiateEnumSet(Class<E> targetType) {
        try {
            return EnumSet.noneOf(targetType);
        } catch (Exception e) {
            throw unsupported(e, "Cannot instantiate enumset of type '%s'", targetType.getCanonicalName());
        }
    }

    private static Object convertWithConverterClass(
            Method targetMethod, String text, Class<? extends Converter> converterClass) {
        Converter<?> converter;
        try {
            converter = converterClass.newInstance();
        } catch (InstantiationException e) {
            throw unsupported(e, "Converter class %s can't be instantiated: %s",
                    converterClass.getCanonicalName(), e.getMessage());
        } catch (IllegalAccessException e) {
            throw unsupported(e, "Converter class %s can't be accessed: %s",
                    converterClass.getCanonicalName(), e.getMessage());
        }
        Object result = converter.convert(targetMethod, text);
        if (result == null) return NULL;
        return result;
    }

    private static final Map<Class<?>, Class<? extends Converter<?>>> converterRegistry =
            new ConcurrentHashMap<Class<?>, Class<? extends Converter<?>>>();

    /**
     * @param targetMethod the method being resolved.
     * @param targetType   the type to convert to.
     * @param text         the value to convert.
     * @param key          the key the value comes from, used to say which property is at fault when the
     *                     conversion fails. It is handed down rather than derived from the method, since the
     *                     key depends on the factory that created the Config object: see {@link KeyPrefix}.
     * @return the converted value, or {@link SpecialValue#SKIP} to leave the conversion to the next converter.
     */
    abstract Object tryConvert(Method targetMethod, Class<?> targetType, String text, String key);

    static void setTypeConverter(Class<?> type, Class<? extends Converter<?>> converter) {
        converterRegistry.put(type, converter);
    }

    public static void removeTypeConverter(Class<?> type) {
        converterRegistry.remove(type);
    }

    static Object convert(Method targetMethod, Class<?> targetType, String text, String key) {
        return doConvert(targetMethod, targetType, text, key).getConvertedValue();
    }

    private static ConversionResult doConvert(
            Method targetMethod, Class<?> targetType, String text, String key) {
        for (Converters converter : values()) {
            Object convertedValue = converter.tryConvert(targetMethod, targetType, text, key);
            if (convertedValue != SKIP)
                return new ConversionResult(converter, convertedValue);
        }
        return unreachableButCompilerNeedsThis();
    }

    private static UnsupportedOperationException unsupportedConversion(
            Exception cause, String key, Class<?> targetType, String text) {
        return unsupported(cause, CANNOT_CONVERT_MESSAGE, text, targetType.getCanonicalName(), key);
    }

    private static UnsupportedOperationException unsupportedConversion(
            String key, Class<?> targetType, String text) {
        return unsupported(CANNOT_CONVERT_MESSAGE, text, targetType.getCanonicalName(), key);
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

    enum SpecialValue {
        /**
         * The NULL object: when tryConvert returns this object, the conversion result is null.
         */
        NULL,

        /**
         * The SKIP object: when tryConvert returns this object the conversion is skipped in favour of the next one.
         */
        SKIP
    }

    /**
     * The value and the target type say what went wrong, the key says where to go and fix it: a file with
     * fifty properties in it makes the difference between a one line message and a manual search.
     */
    static final String CANNOT_CONVERT_MESSAGE = "Cannot convert '%s' to %s for property '%s'";
}
