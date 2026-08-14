/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

import org.aeonbits.owner.loaders.Loader;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

import static org.aeonbits.owner.util.Util.hideCredentials;
import static org.aeonbits.owner.util.Util.unsupported;

/**
 * A {@link Loader loader} that reads the properties from the children of a ZooKeeper node, addressed with
 * the <code>zookeeper</code> scheme:
 *
 * <pre>
 *     &#64;Sources("zookeeper://zookeeper.example.com:2181/config/myapp")
 * </pre>
 *
 * <p>
 * The name of each child is the key and its data, read as a string, is the value. The port may be omitted,
 * in which case the client's default is used. Connecting is given thirty seconds before it gives up, which
 * the <code>owner.zookeeper.connection.timeout.seconds</code> System Property changes.
 * </p>
 *
 * <p>
 * <b>It needs <a href="https://curator.apache.org">Apache Curator</a>, which this artifact declares as an
 * optional dependency and therefore does not bring along.</b> Nothing here names Curator: every call that
 * does lives in {@link ZooKeeperReader}, which is not touched until a <code>zookeeper:</code> source is
 * actually read. So this loader can be discovered and instantiated on a class path without Curator, a
 * configuration that reads any other source is unaffected, and only a configuration that names a
 * <code>zookeeper:</code> source is told - by name, and with what to add - that the dependency is missing.
 * See {@link ZooKeeperReader} for why that separation must not be undone.
 * </p>
 *
 * @author Koray Sariteke
 * @author Luigi R. Viggiano
 */
public class ZooKeeperLoader implements Loader {
    /**
     * A loader is built with no arguments, both when it is registered by hand and when
     * {@link java.util.ServiceLoader} finds it on the class path. Declared rather than left implicit
     * so that the requirement is visible to whoever changes this class.
     */
    public ZooKeeperLoader() {
    }


    private static final long serialVersionUID = -8541229366311874254L;

    private static final String SCHEME = "zookeeper";

    /**
     * Accepts a source whose scheme is <code>zookeeper</code>, and no other.
     * <p>
     * Written out rather than inherited with <code>{&#64;inheritDoc}</code>: this artifact sees
     * {@link org.aeonbits.owner.loaders.Loader} as a jar and not as source, so there is no comment there
     * for javadoc to copy - which JDK 25 is the first to say out loud.
     * </p>
     * <p>
     * The constant is on the left of the comparison because a URI need not have a scheme: a source written
     * without one, and the empty URI that a blank <code>file:</code> produces, both answer <code>null</code>
     * here, and every registered loader is asked about every source.
     * </p>
     *
     * @param uri the source being offered to this loader.
     * @return true when the scheme is <code>zookeeper</code>.
     */
    @Override
    public boolean accept(URI uri) {
        return SCHEME.equals(uri.getScheme());
    }

    @Override
    public void load(Properties result, URI uri) throws IOException {
        try {
            ZooKeeperReader.read(result, uri);
        } catch (NoClassDefFoundError curatorIsNotOnTheClassPath) {
            // said here rather than left to propagate: a NoClassDefFoundError names the class that could
            // not be found, which is Curator's, and not the thing to do about it
            throw unsupported(curatorIsNotOnTheClassPath,
                    "Reading %s needs Apache Curator, which is an optional dependency of owner-extras and is "
                            + "not on the class path. Add org.apache.curator:curator-framework to read a "
                            + "zookeeper: source.", hideCredentials(uri));
        }
    }

    @Override
    public String defaultSpecFor(String urlPrefix) {
        return null;
    }
}
