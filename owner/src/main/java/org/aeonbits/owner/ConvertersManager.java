/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.util.Util;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The converters registered on a {@link Factory}, by the type each of them converts.
 * <p>
 * <b>They belong to the factory</b>, which they did not until 2.0.0: the registry was a static field of
 * {@link Converters}, so {@link Factory#setTypeConverter} — an instance method — wrote a map shared by
 * every factory in the JVM, and <code>removeTypeConverter</code> on one took the converter away from all
 * the others. A configuration created by {@code ConfigFactory.newInstance()} is isolated in its
 * properties, its loaders, its value handlers, its prefix and its strictness; conversion was the one
 * thing that leaked.
 * </p>
 * <p>
 * The registry is read <b>when a value is converted</b> and not when the configuration is created, which
 * is the one part of the old behaviour that was deliberate: registering a converter changes what an
 * existing Config object answers, and removing it changes it back. That is asserted by
 * {@code ConverterRegistryTest} and is older than this class.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
class ConvertersManager implements Serializable {

    /**
     * A configuration travels through serialization with its manager, and its manager now holds this: a
     * Config object that came back from a stream has to convert its values the way it did before, and a
     * registry left behind would mean the same property answering differently on the other side.
     * {@link HandlersManager} is serializable for the same reason.
     */
    private static final long serialVersionUID = 7395218374195329215L;

    /** Registered by class: built again for every conversion, which is what the annotations do too. */
    private final ConcurrentMap<Class<?>, Class<? extends Converter<?>>> classes = new ConcurrentHashMap<>();

    /** Registered as an object: handed back as it is, which is the only shape a container can supply. */
    private final ConcurrentMap<Class<?>, Converter<?>> instances = new ConcurrentHashMap<>();

    void setTypeConverter(Class<?> type, Class<? extends Converter<?>> converter) {
        instances.remove(type);
        classes.put(type, converter);
    }

    void setTypeConverter(Class<?> type, Converter<?> converter) {
        classes.remove(type);
        instances.put(type, converter);
    }

    void removeTypeConverter(Class<?> type) {
        classes.remove(type);
        instances.remove(type);
    }

    /**
     * The converter registered for the given type, or <code>null</code> when there is none.
     * <p>
     * One registered as an object is returned as it is, once and for every conversion; one registered as a
     * class is built here, every time, which is what {@link Config.ConverterClass} does as well and what the
     * javadoc of {@link Converter} has always promised.
     * </p>
     */
    Converter<?> converterFor(Class<?> type) {
        Converter<?> instance = instances.get(type);
        if (instance != null)
            return instance;
        Class<? extends Converter<?>> declared = classes.get(type);
        return declared == null ? null : Util.newInstance(declared);
    }
}
