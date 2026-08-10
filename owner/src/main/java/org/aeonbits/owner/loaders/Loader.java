/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Properties;

/**
 * Defines the interface of a generic Properties loader.
 *
 * <h2>Getting a loader into a factory</h2>
 * <p>
 * Two ways, and the difference between them is who is being explicit.
 * </p>
 * <p>
 * <b>Registering it</b> is the explicit way, and it works everywhere:
 * </p>
 * <pre>
 *     ConfigFactory.registerLoader(new YamlLoader());
 * </pre>
 * <p>
 * <b>Being discovered</b> is the implicit way. A loader listed in
 * <code>META-INF/services/org.aeonbits.owner.loaders.Loader</code> is found by
 * {@link java.util.ServiceLoader} when a factory is created, which requires it to be a public class with a
 * public no-argument constructor - that is <code>ServiceLoader</code>'s rule, not OWNER's. Being discovered
 * <b>enables</b> it: the loader answers for its formats immediately, and its
 * {@link #defaultSpecFor(String) default spec} joins the sources looked for when an interface carries no
 * {@code @Sources}. That is what Spring Boot, MicroProfile and Gestalt all do.
 * </p>
 *
 * <h3>Which class loader does the looking</h3>
 * <p>
 * The context class loader of the thread creating the factory, falling back on the one that loaded OWNER
 * when there is no context class loader. It is right more often than the alternative, and it is not right
 * always:
 * </p>
 * <ul>
 *   <li>In an <b>application server</b>, with OWNER in the shared libraries and the loader in a web
 *   application's <code>WEB-INF/lib</code>, only the context class loader can see it - a parent never sees
 *   a child's jars.</li>
 *   <li>On a <b>thread that a container or a pool set up</b>, the context class loader may point somewhere
 *   that knows nothing of the application, and the loader is not found although it is on the classpath.</li>
 *   <li>Under <b>OSGi</b> there is no class path to search and <code>ServiceLoader</code> needs help from
 *   the container to work at all.</li>
 * </ul>
 * <p>
 * In each of those, register the loader explicitly. That path depends on nothing.
 * </p>
 *
 * <h3>What a loader that is not found looks like</h3>
 * <p>
 * Not like an error. {@link PropertiesLoader} accepts every URL it can resolve and is consulted last, so a
 * <code>app.yaml</code> whose loader was not discovered is not left unread - it is read <b>as a properties
 * file</b>, and the configuration comes back holding almost nothing, with nothing said. When a format seems
 * to be ignored, <code>org.aeonbits.owner.level = CONFIG</code> makes the library name the loaders it
 * discovered, which tells a loader that is absent from a loader that is broken.
 * </p>
 *
 * <h3>Where a loader sits among the others</h3>
 * <p>
 * A registered loader comes before a discovered one, a discovered one before the built-in loaders, and
 * {@link PropertiesLoader} last. For the sources looked for in the absence of {@code @Sources} the order is
 * deliberately not the same: <b>a discovered loader comes last there</b>, so that a jar arriving on the
 * classpath cannot make a stray <code>MyConfig.yaml</code> take precedence over the
 * <code>MyConfig.properties</code> an application already loads. Among several discovered loaders the order
 * is the class path's, which is not guaranteed to be the same on two machines: two loaders claiming one
 * format is a situation to avoid rather than to order.
 * </p>
 *
 * <h3>Options on a source</h3>
 * <p>
 * The query belongs to the protocol and the fragment belongs to OWNER. A loader reads what was written on a
 * single source through {@link SourceOptions}, and <b>refuses an option it does not recognise</b> rather
 * than ignoring it.
 * </p>
 *
 * <h3>Serialization</h3>
 * <p>
 * A loader is {@link Serializable} because a <code>Config</code> object holds the ones that were in force
 * when it was created. A configuration serialized where a loader was present and read back where it is not
 * fails to deserialize; that was already true of a registered loader and discovery makes it likelier.
 * </p>
 *
 * @author Luigi R. Viggiano
 * @since 1.0.5
 */
public interface Loader extends Serializable {

    /**
     * Indicates whether this Loader accepts the URI, guessing the content type from it.
     *
     * @since 1.1.0
     * @param uri   the URI
     * @return true, if the loader is able to handle the content of the URI.
     */
    boolean accept(URI uri);

    /**
     * Loads the given {@link URI uri} into the given {@link Properties result}
     *
     * @since 1.1.0
     * @param result    the resulting properties where to load the {@link URI uri}
     * @param uri     the {@link URI} from where to load the properties.
     * @throws java.io.IOException if there is some I/O error during the load.
     */
    void load(Properties result, URI uri) throws IOException;

    /**
     * Returns the default URI specification for a given URI resource, that can be handled by this loader.
     *
     * @param uriPrefix the prefix identifying the URI resource.
     * @return the default URI specification for a given URI resource, that can be handled by this loader.
     */
    String defaultSpecFor(String uriPrefix);
}
