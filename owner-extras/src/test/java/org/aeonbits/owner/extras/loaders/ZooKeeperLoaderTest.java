/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

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
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.aeonbits.owner.Config.Sources;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Koray Sariteke
 * @author Luigi R. Viggiano
 */
public class ZooKeeperLoaderTest {
    private TestingServer server;
    private Factory configFactory;

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
        ZooKeeperConfig sample = configFactory.create(ZooKeeperConfig.class);
        assertEquals("welcome", sample.thanks());
        assertTrue(sample.greetings().containsAll(asList("hi", "bonjour", "hiya", "hi!")));
        assertNull(sample.notAvailable());
    }

    @Test
    public void shouldThrowInterruptedIOExceptionWhenThreadIsInterrupted() throws Exception {
        ZooKeeperLoader loader = new ZooKeeperLoader();
        CuratorFramework client = mock(CuratorFramework.class);
        when(client.blockUntilConnected(anyInt(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("simulated interrupt"));
        try (MockedStatic<CuratorFrameworkFactory> factory = mockStatic(CuratorFrameworkFactory.class)) {
            factory.when(() -> CuratorFrameworkFactory.newClient(anyString(), any(RetryPolicy.class)))
                    .thenReturn(client);
            loader.load(new Properties(), new URI("zookeeper://127.0.0.1:65404/test"));
            fail("an InterruptedIOException was expected");
        } catch (InterruptedIOException e) {
            assertTrue(e.getCause() instanceof InterruptedException);
        } finally {
            // the loader must restore the interrupted status; this assertion also clears the flag
            assertTrue(Thread.interrupted());
        }
        verify(client).close();
    }

    @Test
    public void shouldUseHostOnlyConnectStringAndDisallowRetriesWhenPortIsNotSpecified() throws Exception {
        // getClient lives in ZooKeeperReader since 2.0.0, with everything else that names Curator
        Method getClient = ZooKeeperReader.class.getDeclaredMethod("getClient", URI.class);
        getClient.setAccessible(true);
        CuratorFramework client = (CuratorFramework) getClient.invoke(null, new URI("zookeeper://127.0.0.1/test"));
        try {
            RetryPolicy retryPolicy = client.getZookeeperClient().getRetryPolicy();
            assertFalse(retryPolicy.allowRetry(0, 0L, null));
        } finally {
            client.close();
        }
    }

    @Test
    public void whenPathIsWrongEverythingIsNull() throws Exception {
        ZooKeeperWrongPathConfig config = configFactory.create(ZooKeeperWrongPathConfig.class);
        assertNull(config.notAvailable());
        assertNull(config.greetings());
        assertNull(config.thanks());
    }

    @Before
    public void before() throws Exception {
        server = new TestingServer(65403);
        server.start();

        String connectString = server.getConnectString();

        CuratorFramework client = CuratorFrameworkFactory.newClient(connectString, 50, 50, new RetryPolicy() {
            @Override
            public boolean allowRetry(int retryCount, long elapsedTimeMs, RetrySleeper sleeper) {
                return false;
            }
        });
        try {
            client.start();
            client.blockUntilConnected(30, SECONDS);
            String basePath = "/test";
            setDataInZookeperServer(client, basePath, "thanks", "welcome");
            setDataInZookeperServer(client, basePath, "greetings", "hi,bonjour,hiya,hi!");
        } finally {
            client.close();
        }

        configFactory = ConfigFactory.newInstance();
        configFactory.registerLoader(new ZooKeeperLoader());
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
