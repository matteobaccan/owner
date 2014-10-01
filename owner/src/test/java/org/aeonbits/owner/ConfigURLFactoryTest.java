/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Luigi R. Viggiano
 */
public class ConfigURLFactoryTest {
    @Test
    public void shouldReturnAnURL() throws MalformedURLException {
        ConfigURLFactory h = new ConfigURLFactory(this.getClass().getClassLoader(), new VariablesExpander(new Properties()));
        URL url = h.newURL("classpath:test.properties");
        assertNotNull(url);
    }

    @Test
    public void testshouldReturnNullForZookeeper() throws Exception {
        ConfigURLFactory factory =new ConfigURLFactory(this.getClass().getClassLoader(), new VariablesExpander(new Properties()));
        URL url = factory.newURL("zk:test.zk");
        assertNull(url);
    }


    @Test
    public void testshouldReturnURLForZookeeper() throws Exception {
        System.setProperty("zookeeper.host", "127.0.0.1");
        System.setProperty("zookeeper.port", "65403");
        System.setProperty("zookeeper.node.root", "/test/properties");

        ConfigURLFactory factory =new ConfigURLFactory(this.getClass().getClassLoader(), new VariablesExpander(new Properties()));
        URL url = factory.newURL("zk:test.zk");
        assertNotNull(url);
    }
}
