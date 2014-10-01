package com.matriksdata.owner.zookeeper.loader;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.KeeperException;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Author: Koray Sariteke
 */
public class ZookeeperLoaderTest {
    @Config.Sources("zk:dummyProp.zk")
    public static interface ZooConfigWithSource extends Config {
        String thanks();
        List<String> greetings();
    }

    @Test
    public void shouldLoadPropertiesFromZookeeperSource() throws Exception {
        System.setProperty("zookeeper.host", "127.0.0.1");
        System.setProperty("zookeeper.port", "65403");
        System.setProperty("zookeeper.node.root", "/test/properties");

        //Start dummy zookeeper server
        TestingServer server = new TestingServer(65403);
        server.start();
        CuratorFramework zkClient = CuratorFrameworkFactory.newClient(server.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        zkClient.start();

        String thanksPath = ZKPaths.makePath("/test/properties/dummyProp.zk", "thanks");
        try {
            zkClient.setData().forPath(thanksPath, "welcome".getBytes());
        } catch (KeeperException.NoNodeException e) {
            zkClient.create().creatingParentsIfNeeded().forPath(thanksPath, "welcome".getBytes());
        }

        String greetingsPath = ZKPaths.makePath("/test/properties/dummyProp.zk", "greetings");
        try {
            zkClient.setData().forPath(greetingsPath, "hi,bonjour,hiya,hi!".getBytes());
        } catch (KeeperException.NoNodeException e) {
            zkClient.create().creatingParentsIfNeeded().forPath(greetingsPath, "hi,bonjour,hiya,hi!".getBytes());
        }

        zkClient.getChildren();

        ConfigFactory.registerLoader(new ZookeeperLoader());
        ZooConfigWithSource sample = ConfigFactory.create(ZooConfigWithSource.class);

        assertEquals("welcome", sample.thanks());
        assertEquals(true, sample.greetings().containsAll(Arrays.asList("hi", "bonjour", "hiya", "hi!")));
    }
}
