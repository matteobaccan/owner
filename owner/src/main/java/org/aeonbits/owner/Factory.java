/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.handlers.ValueHandler;
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
     * <p>
     * Imports are merged into a {@link java.util.Properties} instance, whose contract only admits {@link String}
     * keys and values. Entries with a null or non-String key or value are therefore rejected, rather than being
     * accepted and then silently ignored when the properties are read.</p>
     *
     * @param clazz   the interface extending from {@link Config} that you want to instantiate.
     * @param imports additional variables to be used to resolve the properties.
     * @param <T>     type of the interface.
     * @return an object implementing the given interface, which maps methods to property values.
     * @throws IllegalArgumentException if any of the imports contains a null or non-String key or value.
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
     * <p>
     * The properties understood by the factory itself are, all of them since 2.0.0:
     * </p>
     * <ul>
     *     <li><code>owner.key.prefix</code>: a literal prepended to the key of every property, for the
     *     interfaces that do not declare a {@link Config.Prefix} of their own.</li>
     *     <li><code>owner.key.prefix.from.package</code>: <code>true</code> derives that prefix from the
     *     package of the interface declaring the method, followed by a dot.</li>
     *     <li><code>owner.strict</code>: <code>true</code> turns this library's warnings into refusals, so
     *     that a configuration which would have carried on with its default values does not get created at
     *     all. The default is <code>false</code>, which is how OWNER has always behaved. What it covers is
     *     not a list of its own — it is the warnings, and those already leave the legitimate cases alone: a
     *     source that is merely absent stays silent under it too, since
     *     {@link Config.LoadType#FIRST} expects misses by design.</li>
     *     <li><code>owner.declared.only</code>: <code>true</code> restricts every {@link Accessible} view
     *     and {@link Object#toString()} to the properties each interface declares, for the interfaces that
     *     do not carry {@link Config.DeclaredOnly} — the ones you did not write. An interface that carries
     *     the annotation decides for itself, in both directions.</li>
     * </ul>
     * <p>
     * They are read when a Config object is created, and the object keeps them for the rest of its life.
     * </p>
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
     * <p>
     * A registered loader comes <b>first</b>, both when a source is being matched to a loader and among the
     * file names looked for when a configuration declares no {@link Config.Sources}. It therefore takes
     * precedence over the built-in loaders and can take over a source one of them would have accepted.
     * </p>
     * <p>
     * Registering is one of the two ways in, and it is the explicit one: a loader declared in
     * <code>META-INF/services/org.aeonbits.owner.loaders.Loader</code> is found on the classpath without
     * this call. See {@link Loader} for the difference, which matters in a container.
     * </p>
     *
     * @param loader the loader to register.
     * @throws IllegalArgumentException if the specified loader is <code>null</code>.
     * @since 1.0.5
     */
    void registerLoader(Loader loader);

    /**
     * Registers a handler a value can name, as <code>${$name::payload}</code>, instead of holding its own
     * text.
     * <p>
     * A handler is registered under {@link ValueHandler#name() its own name}, and registering one under a
     * name already taken replaces it - which is what makes a key rotation an ordinary thing to do.
     * </p>
     * <p>
     * Registration is the <b>only</b> way in: unlike a {@link Loader}, a handler is not discovered on the
     * classpath. It is also what settles where a handler's own configuration comes from, since the caller
     * constructs the instance and hands it over already holding its passphrase, token or endpoint.
     * </p>
     * <p>
     * A configuration keeps the handlers the factory held when it was created, so register before
     * {@link #create(Class, Map[])}.
     * </p>
     *
     * @param handler the handler to register.
     * @throws IllegalArgumentException if the handler is <code>null</code>, or its name is null, empty or
     *                                  contains whitespace or any of <code>$ : { }</code>.
     * @since 2.0.0
     */
    void registerValueHandler(ValueHandler handler);

    /**
     * Sets a converter for the given type. Setting a converter via this method will override any default converters
     * but not {@link Config.ConverterClass} annotations.
     * <p>
     * <b>The converter belongs to this factory</b>, and to the configurations it creates. That is new in
     * 2.0.0: the registry used to be shared by every factory in the JVM, so this method — an instance
     * method — changed how values converted everywhere, and {@link #removeTypeConverter} on one factory
     * took the converter away from all the others.
     * </p>
     * <p>
     * It is read <b>when a value is converted</b> and not when the configuration is created, so registering
     * a converter changes what a Config object that already exists answers, and removing it changes it
     * back. That part is unchanged and is what the method is for.
     * </p>
     *
     * @param type the type for which to set a converter.
     * @param converter the converter class to use for the specified type.
     * @since 1.0.10
     */
    void setTypeConverter(Class<?> type, Class<? extends Converter<?>> converter);

    /**
     * Removes a converter for the given type, from this factory: see {@link #setTypeConverter}.
     *
     * @param type the type for which to remove the converter.
     * @since 1.0.10
     */
    void removeTypeConverter(Class<?> type);
}
