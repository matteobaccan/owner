/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import java.util.Map;

/**
 * <p>Allows a <code>Config</code> object to say where each of its properties came from.</p>
 *
 * <p>Example:</p>
 *
 * <pre>
 *     &#64;LoadPolicy(LoadType.MERGE)
 *     &#64;Sources({"system:env", "file:config/app.properties"})
 *     public interface MyConfig extends Config, Traceable {
 *         int someProperty();
 *     }
 *
 *     MyConfig cfg = ConfigFactory.create(MyConfig.class);
 *     cfg.originOf("someProperty");         // file:config/app.properties
 * </pre>
 *
 * <p>A configuration merged out of several sources answers each property with one value, and which of the
 * sources that value came from is not something the merged properties can still say: they are one map, and a
 * value read from a file is indistinguishable from one that came from the environment or from a
 * {@link Config.DefaultValue}. This interface keeps that distinction available.</p>
 *
 * <p>The use it was asked for is saving a configuration back. With
 * {@link Config.LoadType#MERGE} over <code>system:env</code> and a file,
 * {@link Accessible#store(java.io.OutputStream, String)} writes out the whole environment along with the
 * three properties that belong to the application. Knowing the origin of each key is what makes it possible
 * to write back only what came from the file:</p>
 *
 * <pre>
 *     public interface MyConfig extends Config, Accessible, Traceable { ... }
 *
 *     Properties mine = new Properties();
 *     for (Map.Entry&lt;String, Origin&gt; entry : cfg.origins().entrySet())
 *         if ("file:config/app.properties".equals(entry.getValue().source()))
 *             mine.setProperty(entry.getKey(), cfg.getProperty(entry.getKey()));
 *     mine.store(out, null);
 * </pre>
 *
 * <p>The origins follow the properties: a {@link Reloadable#reload()} recomputes them, a
 * {@link Mutable#setProperty(String, String)} makes the property one that was written at run time, and
 * removing a property removes its origin with it.</p>
 *
 * @author Matteo Baccan
 * @see Origin
 * @since 2.0.0
 */
public interface Traceable extends Config {

    /**
     * Returns where the given property came from.
     *
     * @param key the property name, which is the whole key, prefix included.
     * @return the origin, or <code>null</code> when no property of that name is there.
     * @since 2.0.0
     */
    Origin originOf(String key);

    /**
     * Returns where every property came from.
     * <p>
     * A snapshot, taken under the same lock that {@link Accessible#propertyNames()} takes: the map handed
     * back is the caller's own and does not change underneath while a reload runs.
     * </p>
     *
     * @return the origin of each property, by property name; empty when there are none, never
     *         <code>null</code>.
     * @since 2.0.0
     */
    Map<String, Origin> origins();
}
