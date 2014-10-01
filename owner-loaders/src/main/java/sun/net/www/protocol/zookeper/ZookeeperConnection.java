/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package sun.net.www.protocol.zookeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.ZKPaths;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Koray Sariteke
 * @author Luigi R. Viggiano
 */
public class ZookeeperConnection extends URLConnection {
    private final String basePath;
    private final CuratorFramework client;

    /**
     * Constructs a URL connection to the specified URL. A connection to
     * the object referenced by the URL is not created.
     *
     * @param url the specified URL.
     * @throws java.net.MalformedURLException if the URL is malformed.j
     */
    protected ZookeeperConnection(URL url) throws MalformedURLException {
        super(url);
        String host = url.getHost();
        int port = url.getPort();
        basePath = url.getPath();
        String connectString = (port == -1) ? host : host + ":" + port;
        client = CuratorFrameworkFactory.newClient(connectString, new ExponentialBackoffRetry(1000, 3));
    }

    @Override
    public void connect() {
        client.start();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        return new ZookeeperStream(client, basePath);
    }

    public static class ZookeeperStream extends InputStream {

        private final CuratorFramework client;
        private final String basePath;

        public ZookeeperStream(CuratorFramework client, String basePath) {
            this.client = client;
            this.basePath = basePath;
        }

        public Map<String, String> pairs() throws IOException {
            Map<String, String> pairsMap = new HashMap<String, String>();
            try {
                for (String key : client.getChildren().forPath(basePath))
                    pairsMap.put(key, new String(client.getData().forPath(ZKPaths.makePath(basePath, key))));
                return pairsMap;
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }

        @Override
        public void close() throws IOException {
            client.close();
        }

        @Override
        public int read() throws IOException {
            throw new IOException("not supported operation");
        }

        @Override
        public int read(byte[] b) throws IOException {
            throw new IOException("not supported operation");
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            throw new IOException("not supported operation");
        }

        @Override
        public long skip(long n) throws IOException {
            throw new IOException("not supported operation");
        }

        @Override
        public int available() throws IOException {
            throw new IOException("not supported operation");
        }
    }
}
