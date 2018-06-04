package org.aeonbits.owner.variableexpansion;

import static org.aeonbits.owner.Config.DisableableFeature.VARIABLE_EXPANSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

/**
 * @author aknopov
 */
public class ParametrizedKeyTest
{
    private static final String DEV_SETUP = "dev";
    private static final String UAT_SETUP = "uat";

    @Config.Sources("classpath:org/aeonbits/owner/variableexpansion/KeyExpansionExample.xml")
    public interface MyConfig extends Config {
        @Key("servers.%s.name")
        String name(String setup);

        @Key("servers.%s.hostname")
        String hostname(String setup);

        @Key("servers.%s.port")
        Integer port(String setup);

        @Key("servers.%s.user")
        String user(String setup);

        @DisableFeature(VARIABLE_EXPANSION)
        @Key("servers.%s.password")
        String password(String setup);
    }

    @Test
    public void testKeyParametrization() {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);

        assertEquals("DEV", cfg.name(DEV_SETUP));
        assertEquals("devhost", cfg.hostname(DEV_SETUP));
        assertEquals(new Integer(6000), cfg.port(DEV_SETUP));
        assertEquals("myuser1", cfg.user(DEV_SETUP));
        assertNull(cfg.password(DEV_SETUP)); // expansion is disabled on method level

        assertEquals("UAT", cfg.name(UAT_SETUP));
        assertEquals("uathost", cfg.hostname(UAT_SETUP));
        assertEquals(new Integer(60020), cfg.port(UAT_SETUP));
        assertEquals("myuser2", cfg.user(UAT_SETUP));
        assertNull(cfg.password(UAT_SETUP)); // expansion is disabled on method level
    }
}
