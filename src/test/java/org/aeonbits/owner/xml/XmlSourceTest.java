package org.aeonbits.owner.xml;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author luigi
 */
public class XmlSourceTest {

    public static interface ServerConfig extends Config {

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

    @Test
    public void testXmlReading() {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
        assertEquals(80, cfg.httpPort());
        assertEquals("localhost", cfg.httpHostname());
        assertEquals(22, cfg.sshPort());
        assertEquals("127.0.0.1", cfg.sshAddress());
        assertEquals(60, cfg.aliveInterval());
        assertEquals("admin", cfg.sshUser());
    }

}
