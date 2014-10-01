/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Factory;
import org.apache.curator.RetryPolicy;
import org.apache.curator.RetrySleeper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.test.TestingServer;
import org.apache.curator.utils.ZKPaths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static java.util.Arrays.asList;
import static org.aeonbits.owner.Config.Sources;
import static org.junit.Assert.*;

/**
 * @author Koray Sariteke
 * @author Luigi R. Viggiano
 */
public class ZookeeperLoaderTest {
    private TestingServer server;
    private Factory configFactory;

    @Sources("zookeper://127.0.0.1:65403/test")
    public static interface ZookeperConfig extends Config {
        String thanks();
        List<String> greetings();
        String notAvailable();
    }

    @Sources("zookeper://127.0.0.1:65403/wrong")
    public static interface ZookeeperWrongPathConfig extends Config {
        String thanks();
        List<String> greetings();
        String notAvailable();
    }

    @Test
    public void shouldLoadPropertiesFromZookeeperSource() throws Exception {
        ZookeperConfig sample = configFactory.create(ZookeperConfig.class);
        assertEquals("welcome", sample.thanks());
        assertTrue(sample.greetings().containsAll(asList("hi", "bonjour", "hiya", "hi!")));
        assertNull(sample.notAvailable());
    }

    @Test
    public void whenPathIsWrongEverythingIsNull() throws Exception {
        ZookeeperWrongPathConfig config = configFactory.create(ZookeeperWrongPathConfig.class);
        assertNull(config.notAvailable());
        assertNull(config.greetings());
        assertNull(config.thanks());
    }

    @Before
    public void before() throws Exception {
        server = new TestingServer(65403);
        server.start();

        String connectString = server.getConnectString();

        CuratorFramework client = CuratorFrameworkFactory.newClient(connectString, 50, 50, noRetryPolicy());
        try {
            client.start();
            String basePath = "/test";
            setDataInZookeperServer(client, basePath, "thanks", "welcome");
            setDataInZookeperServer(client, basePath, "greetings", "hi,bonjour,hiya,hi!");
        } finally {
            client.close();
        }

        configFactory = ConfigFactory.newInstance();
        configFactory.registerLoader(new ZookeeperLoader());
    }

    private RetryPolicy noRetryPolicy() {
        return new RetryPolicy() {
            public boolean allowRetry(int retryCount, long elapsedTimeMs, RetrySleeper sleeper) {
                return false;
            }
        };
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
