/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.test.TestingServer;
import org.apache.curator.utils.ZKPaths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import sun.net.www.protocol.zookeeper.NoRetryPolicy;

/**
 * @author Koray Sariteke
 * @author Luigi R. Viggiano
 */
public class ZooKeeperLoaderTest {
    private TestingServer server;

    @Sources("zookeeper://127.0.0.1:65403/test")
    public static interface ZooKeeperConfig extends Config {
        String thanks();
        List<String> greetings();
        String notAvailable();
    }

    @Sources("zookeeper://127.0.0.1:65403/wrong")
    public static interface ZooKeeperWrongPathConfig extends Config {
        String thanks();
        List<String> greetings();
        String notAvailable();
    }

    @Test
    public void shouldLoadPropertiesFromZookeeperSource() throws Exception {
        ZooKeeperConfig sample = ConfigFactory.create(ZooKeeperConfig.class);
        assertEquals("welcome", sample.thanks());
        assertTrue(sample.greetings().containsAll(asList("hi", "bonjour", "hiya", "hi!")));
        assertNull(sample.notAvailable());
    }

    @Test
    public void whenPathIsWrongEverythingIsNull() throws Exception {
        ZooKeeperWrongPathConfig config = ConfigFactory.create(ZooKeeperWrongPathConfig.class);
        assertNull(config.notAvailable());
        assertNull(config.greetings());
        assertNull(config.thanks());
    }

    @Before
    public void before() throws Exception {
        server = new TestingServer(65403);
        server.start();

        String connectString = server.getConnectString();

        CuratorFramework client = CuratorFrameworkFactory.newClient(connectString, 50, 50, new NoRetryPolicy());
        try {
            client.start();
            client.blockUntilConnected(30, SECONDS);
            String basePath = "/test";
            setDataInZookeperServer(client, basePath, "thanks", "welcome");
            setDataInZookeperServer(client, basePath, "greetings", "hi,bonjour,hiya,hi!");
        } finally {
            client.close();
        }

    }

    private void setDataInZookeperServer(CuratorFramework client,
                                         String basePath, String property, String value) throws Exception {
        String path = ZKPaths.makePath(basePath, property);
        client.create().creatingParentsIfNeeded().forPath(path, value.getBytes());
    }

    @After
    public void after() throws IOException {
        server.stop();
    }
}
