/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.loaders.Loader;

import java.util.Map;
import java.util.Properties;

/**
 * Interface for factory implementation used to instantiate {@link Config} instances.
 *
 * @author Luigi R. Viggiano
 * @since 1.0.5
 */
public interface Factory {

    /**
     * Creates a {@link Config} instance from the specified interface
     *
     * @param clazz   the interface extending from {@link Config} that you want to instantiate.
     * @param imports additional variables to be used to resolve the properties.
     * @param <T>     type of the interface.
     * @return an object implementing the given interface, which maps methods to property values.
     * @since 1.0.5
     */
    <T extends Config> T create(Class<? extends T> clazz, Map<?, ?>... imports);

    /**
     * Returns the value for a given property.
     *
     * @param key the key for the property
     * @return the value for the property, or <code>null</code> if the property is not set.
     * @since 1.0.5
     */
    String getProperty(String key);

    /**
     * Set a property in the ConfigFactory. Those properties will be used to expand variables specified in the `@Source`
     * annotation, or by the ConfigFactory to configure its own behavior.
     *
     * @param key   the key for the property.
     * @param value the value for the property.
     * @return the old value.
     * @since 1.0.5
     */
    String setProperty(String key, String value);

    /**
     * Clears the value for the property having the given key. This means, that the given property is removed.
     *
     * @param key the key for the property to remove.
     * @return the old value for the given key, or <code>null</code> if the property was not set.
     * @since 1.0.5
     */
    String clearProperty(String key);

    /**
     * Those properties will be used to expand variables specified in the `@Source` annotation, or by the ConfigFactory
     * to configure its own behavior.
     *
     * @return the properties in the ConfigFactory
     * @since 1.0.5
     */
    Properties getProperties();

    /**
     * Those properties will be used to expand variables specified in the `@Source` annotation, or by the ConfigFactory
     * to configure its own behavior.
     *
     * @param properties the properties to set in the config Factory.
     * @since 1.0.5
     */
    void setProperties(Properties properties);

    /**
     * Registers a loader to enables additional file formats.
     *
     * @param loader the loader to register.
     * @throws NullPointerException if specified loader is <code>null</code>.
     * @since 1.0.5
     */
    void registerLoader(Loader loader);

    /**
     * Sets a converter for the given type. Setting a converter via this method will override any default converters
     * but not {@link Config.ConverterClass} annotations.
     *
     * @param type the type for which to set a converter.
     * @param converter the converter class to use for the specified type.
     * @since 1.0.10
     */
    void setTypeConverter(Class<?> type, Class<? extends Converter<?>> converter);

    /**
     * Removes a converter for the given type.
     * @param type the type for which to remove the converter.
     * @since 1.0.10
     */
    void removeTypeConverter(Class<?> type);
}
