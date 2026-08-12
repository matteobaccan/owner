/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.utils.ZKPaths;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.util.Properties;

import static java.lang.Integer.parseInt;
import static java.lang.System.getProperty;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Everything in this loader that touches <a href="https://curator.apache.org">Apache Curator</a>, and the
 * only class here that names it.
 *
 * <p>
 * <b>The separation is load-bearing and it is not a matter of taste.</b> {@link ZooKeeperLoader} is found by
 * {@link java.util.ServiceLoader}, which instantiates every loader it discovers before anybody asks for one,
 * and Curator is an optional dependency that most users of <code>owner-extras</code> will not have. A class
 * is loaded without resolving the classes its method bodies mention, so <code>ZooKeeperLoader</code> loads
 * and instantiates with Curator absent - and this class, which cannot, is not touched until a
 * <code>zookeeper:</code> source is actually read. That is what makes the failure land on the one
 * configuration that asked for it instead of on every configuration in the application.
 * </p>
 *
 * <p>
 * So no reference to Curator may move into <code>ZooKeeperLoader</code>, in a method body, a signature or a
 * field. <code>ZooKeeperLoaderIsolationTest</code> reads the compiled class and fails if one does, because
 * nothing else would: with Curator on the class path, which is how the suite runs, inlining this class back
 * into the loader passes every other test.
 * </p>
 *
 * @author Koray Sariteke
 * @author Luigi R. Viggiano
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class ZooKeeperReader {

    private static final String ZOOKEEPER_CONNECTION_TIMEOUT_SECONDS = "owner.zookeeper.connection.timeout.seconds";

    private ZooKeeperReader() {
    }

    /**
     * Reads the children of the node named by the URI into the given properties.
     *
     * @param result the properties to fill.
     * @param uri    the <code>zookeeper:</code> source.
     * @throws IOException if the node cannot be read, and {@link InterruptedIOException} if the thread was
     *                     interrupted while connecting.
     */
    static void read(Properties result, URI uri) throws IOException {
        CuratorFramework client = getClient(uri);
        try {
            connect(client);

            String basePath = uri.getPath();

            for (String key : client.getChildren().forPath(basePath))
                result.put(key, getValue(client, basePath, key));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw (IOException) new InterruptedIOException().initCause(e);
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            client.close();
        }
    }

    private static String getValue(CuratorFramework client, String basePath, String key) throws Exception {
        return new String(client.getData().forPath(ZKPaths.makePath(basePath, key)));
    }

    private static void connect(CuratorFramework client) throws InterruptedException {
        client.start();
        int timeout = parseInt(getProperty(ZOOKEEPER_CONNECTION_TIMEOUT_SECONDS, "30"));
        client.blockUntilConnected(timeout, SECONDS);
    }

    private static CuratorFramework getClient(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();

        String connectString = (port == -1) ? host : host + ":" + port;
        return CuratorFrameworkFactory.newClient(connectString,
                (retryCount, elapsedTimeMs, sleeper) -> false);
    }
}
