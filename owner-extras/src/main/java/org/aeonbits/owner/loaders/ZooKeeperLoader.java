/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import org.apache.curator.RetryPolicy;
import org.apache.curator.RetrySleeper;
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
 * @author Koray Sariteke
 * @author Luigi R. Viggiano
 *
 */
public class ZooKeeperLoader implements Loader {

    private static final String SCHEME = "zookeeper";
    private static final String ZOOKEEPER_CONNECTION_TIMEOUT_SECONDS = "owner.zookeeper.connection.timeout.seconds";


    public boolean accept(URI uri) {
        return uri.getScheme().equals(SCHEME);
    }

    public void load(Properties result, URI uri) throws IOException {
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

    private String getValue(CuratorFramework client, String basePath, String key) throws Exception {
        return new String(client.getData().forPath(ZKPaths.makePath(basePath, key)));
    }

    private void connect(CuratorFramework client) throws InterruptedException {
        client.start();
        int timeout = parseInt(getProperty(ZOOKEEPER_CONNECTION_TIMEOUT_SECONDS, "30"));
        client.blockUntilConnected(timeout, SECONDS);
    }

    private CuratorFramework getClient(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();

        String connectString = (port == -1) ? host : host + ":" + port;
        return CuratorFrameworkFactory.newClient(connectString, new RetryPolicy() {
            public boolean allowRetry(int retryCount, long elapsedTimeMs, RetrySleeper sleeper) {
                return false;
            }
        });
    }

    public String defaultSpecFor(String urlPrefix) {
        return null;
    }
}
