/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.xml;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.Factory;
import org.aeonbits.owner.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import static org.aeonbits.owner.ConfigFactory.newInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * @author Luigi R. Viggiano
 */
public class XmlSourceTest implements TestConstants {

    private Factory factory;

    public static interface ServerConfig extends Config, Accessible {

        @Key("server.http.port")
        int httpPort();

        @Key("server.http.hostname")
        String httpHostname();

        @Key("server.ssh.port")
        int sshPort();

        @Key("server.ssh.address")
        String sshAddress();

        @Key("server.ssh.alive.interval")
        int aliveInterval();

        @Key("server.ssh.user")
        String sshUser();
    }

    @Before
    public void before() {
        factory = newInstance();
    }

    @Test
    public void testXmlReading() {
        ServerConfig cfg = factory.create(ServerConfig.class);
        assertEquals(80, cfg.httpPort());
        assertEquals("localhost", cfg.httpHostname());
        assertEquals(22, cfg.sshPort());
        assertEquals("127.0.0.1", cfg.sshAddress());
        assertEquals(60, cfg.aliveInterval());
        assertEquals("admin", cfg.sshUser());
    }

    @Test
    public void testStoreToXML() throws IOException {
        ServerConfig cfg = factory.create(ServerConfig.class);
        File target = new File(RESOURCES_DIR + "/XmlSourceTest$ServerConfig.properties.xml");
        target.getParentFile().mkdirs();
        cfg.storeToXML(new FileOutputStream(target), "this is an example");

        Properties props = new Properties();
        props.loadFromXML(new FileInputStream(target));

        assertEquals(String.valueOf(cfg.httpPort()), props.getProperty("server.http.port"));
        assertEquals(cfg.httpHostname(), props.getProperty("server.http.hostname"));
        assertEquals(String.valueOf(cfg.sshPort()), props.getProperty("server.ssh.port"));
        assertEquals(cfg.sshAddress(), props.getProperty("server.ssh.address"));
        assertEquals(String.valueOf(cfg.aliveInterval()), props.getProperty("server.ssh.alive.interval"));
        assertEquals(cfg.sshUser(), props.getProperty("server.ssh.user"));
    }

    static interface ServerConfigJavaFormat extends ServerConfig {
    }

    @Test
    public void testXmlReadingJavaFormat() {
        ServerConfigJavaFormat cfg = factory.create(ServerConfigJavaFormat.class);
        assertEquals(8080, cfg.httpPort());
        assertEquals("foobar", cfg.httpHostname());
        assertEquals(2222, cfg.sshPort());
        assertEquals("10.0.0.1", cfg.sshAddress());
        assertEquals(30, cfg.aliveInterval());
        assertEquals("root", cfg.sshUser());
    }

    @Test(expected = FactoryConfigurationError.class)
    public void testSAXParserMisconfigured() {
        System.setProperty("javax.xml.parsers.SAXParserFactory", "foo.bar.baz");
        try {
            factory.create(ServerConfigJavaFormat.class);
        } finally {
            System.getProperties().remove("javax.xml.parsers.SAXParserFactory");
        }
    }

    static interface ServerConfigInvalid extends ServerConfig {
    }

    @Test
    public void testServerConfigInvalid() throws Throwable {
        ServerConfigInvalid cfg = factory.create(ServerConfigInvalid.class);
        assertNull(cfg.httpHostname());
    }

    @Test
    public void testParserConfigurationException() throws ParserConfigurationException, SAXException {
        SAXParserFactory saxFactory = mock(SAXParserFactory.class);
        ParserConfigurationException expected = new ParserConfigurationException();
        doThrow(expected).when(saxFactory).newSAXParser();

        SAXParserFactoryForTest.setDelegate(saxFactory);

        System.setProperty("javax.xml.parsers.SAXParserFactory", SAXParserFactoryForTest.class.getName());
        try {
            factory.create(ServerConfigJavaFormat.class);
            fail("exception is expected");
        } catch (IllegalArgumentException ex) {
            assertSame(expected, ex.getCause());
        } finally {
            System.getProperties().remove("javax.xml.parsers.SAXParserFactory");
        }
    }

}
