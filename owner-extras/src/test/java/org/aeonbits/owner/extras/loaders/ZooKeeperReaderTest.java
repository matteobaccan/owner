/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.GetChildrenBuilder;
import org.apache.curator.test.TestingServer;
import org.apache.curator.utils.ZKPaths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ZooKeeperReader}.
 *
 * @author Koray Sariteke
 * @author Luigi R. Viggiano
 * @author Matteo Baccan
 */
public class ZooKeeperReaderTest {

    private TestingServer server;

    @Before
    public void setUp() throws Exception {
        server = new TestingServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void shouldReadPropertiesFromZooKeeper() throws Exception {
        String connectString = server.getConnectString();
        CuratorFramework client = CuratorFrameworkFactory.newClient(connectString, 50, 50,
                (retryCount, elapsedTimeMs, sleeper) -> false);
        try {
            client.start();
            client.blockUntilConnected(30, SECONDS);
            String basePath = "/appConfig";
            client.create().creatingParentsIfNeeded().forPath(ZKPaths.makePath(basePath, "db.host"), "localhost".getBytes());
            client.create().creatingParentsIfNeeded().forPath(ZKPaths.makePath(basePath, "db.port"), "5432".getBytes());
        } finally {
            client.close();
        }

        Properties result = new Properties();
        URI uri = new URI("zookeeper://" + connectString + "/appConfig");
        ZooKeeperReader.read(result, uri);

        assertEquals("localhost", result.getProperty("db.host"));
        assertEquals("5432", result.getProperty("db.port"));
    }

    @Test
    public void shouldUseCustomTimeoutPropertyWhenReading() throws Exception {
        String connectString = server.getConnectString();
        CuratorFramework client = CuratorFrameworkFactory.newClient(connectString, 50, 50,
                (retryCount, elapsedTimeMs, sleeper) -> false);
        try {
            client.start();
            client.blockUntilConnected(30, SECONDS);
            client.create().creatingParentsIfNeeded().forPath("/customTimeout/key", "value".getBytes());
        } finally {
            client.close();
        }

        System.setProperty("owner.zookeeper.connection.timeout.seconds", "5");
        try {
            Properties result = new Properties();
            URI uri = new URI("zookeeper://" + connectString + "/customTimeout");
            ZooKeeperReader.read(result, uri);
            assertEquals("value", result.getProperty("key"));
        } finally {
            System.clearProperty("owner.zookeeper.connection.timeout.seconds");
        }
    }

    @Test
    public void shouldThrowInterruptedIOExceptionWhenInterruptedDuringConnect() throws Exception {
        CuratorFramework client = mock(CuratorFramework.class);
        when(client.blockUntilConnected(anyInt(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("simulated interrupt"));

        try (MockedStatic<CuratorFrameworkFactory> factory = mockStatic(CuratorFrameworkFactory.class)) {
            factory.when(() -> CuratorFrameworkFactory.newClient(anyString(), any(RetryPolicy.class)))
                    .thenReturn(client);

            Properties result = new Properties();
            URI uri = new URI("zookeeper://127.0.0.1:2181/test");

            try {
                ZooKeeperReader.read(result, uri);
                fail("Expected InterruptedIOException");
            } catch (InterruptedIOException e) {
                assertTrue(e.getCause() instanceof InterruptedException);
            } finally {
                assertTrue("Interrupted status should be restored", Thread.interrupted());
            }

            verify(client).close();
        }
    }

    @Test
    public void shouldWrapGeneralExceptionsInIOExceptionAndCloseClient() throws Exception {
        CuratorFramework client = mock(CuratorFramework.class);
        GetChildrenBuilder childrenBuilder = mock(GetChildrenBuilder.class);
        when(client.getChildren()).thenReturn(childrenBuilder);
        when(childrenBuilder.forPath(anyString())).thenThrow(new RuntimeException("ZK connection error"));

        try (MockedStatic<CuratorFrameworkFactory> factory = mockStatic(CuratorFrameworkFactory.class)) {
            factory.when(() -> CuratorFrameworkFactory.newClient(anyString(), any(RetryPolicy.class)))
                    .thenReturn(client);

            Properties result = new Properties();
            URI uri = new URI("zookeeper://127.0.0.1:2181/test");

            try {
                ZooKeeperReader.read(result, uri);
                fail("Expected IOException");
            } catch (IOException e) {
                assertNotNull(e.getCause());
                assertEquals("ZK connection error", e.getCause().getMessage());
            }

            verify(client).close();
        }
    }

    @Test
    public void shouldConstructClientWithHostAndPortOrHostOnly() throws Exception {
        Properties result = new Properties();
        URI uriWithPort = new URI("zookeeper://127.0.0.1:2181/path");
        URI uriWithoutPort = new URI("zookeeper://127.0.0.1/path");

        CuratorFramework clientWithPort = mock(CuratorFramework.class);
        CuratorFramework clientWithoutPort = mock(CuratorFramework.class);

        GetChildrenBuilder builderWithPort = mock(GetChildrenBuilder.class);
        GetChildrenBuilder builderWithoutPort = mock(GetChildrenBuilder.class);

        when(clientWithPort.getChildren()).thenReturn(builderWithPort);
        when(clientWithoutPort.getChildren()).thenReturn(builderWithoutPort);
        when(builderWithPort.forPath(anyString())).thenReturn(java.util.Collections.emptyList());
        when(builderWithoutPort.forPath(anyString())).thenReturn(java.util.Collections.emptyList());

        try (MockedStatic<CuratorFrameworkFactory> factory = mockStatic(CuratorFrameworkFactory.class)) {
            factory.when(() -> CuratorFrameworkFactory.newClient(org.mockito.ArgumentMatchers.eq("127.0.0.1:2181"), any(RetryPolicy.class)))
                    .thenReturn(clientWithPort);
            factory.when(() -> CuratorFrameworkFactory.newClient(org.mockito.ArgumentMatchers.eq("127.0.0.1"), any(RetryPolicy.class)))
                    .thenReturn(clientWithoutPort);

            ZooKeeperReader.read(result, uriWithPort);
            ZooKeeperReader.read(result, uriWithoutPort);

            verify(clientWithPort).close();
            verify(clientWithoutPort).close();
        }
    }

    @Test
    public void testPrivateConstructor() throws Exception {
        Constructor<ZooKeeperReader> constructor = ZooKeeperReader.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        ZooKeeperReader instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
