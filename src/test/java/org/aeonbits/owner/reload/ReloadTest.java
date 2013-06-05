/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.reload;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Reloadable;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import static org.aeonbits.owner.UtilTest.save;
import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class ReloadTest {
    private static final String spec = "file:target/test-resources/ReloadableConfig.properties";
    private static File target;

    @BeforeClass
    public static void beforeClass() throws MalformedURLException {
        target = new File(new URL(spec).getFile());
    }

    @Before
    public void before() throws Throwable {
        save(target, new Properties() {{
            setProperty("minimumAge", "18");
        }});
    }

    @Sources(spec)
    public interface ReloadableConfig extends Config, Reloadable {
        Integer minimumAge();
    }

    @Test
    public void testReload() throws Throwable {

        ReloadableConfig cfg = ConfigFactory.create(ReloadableConfig.class);

        assertEquals(Integer.valueOf(18), cfg.minimumAge());

        save(target, new Properties() {{
            setProperty("minimumAge", "21");
        }});

        cfg.reload();
        assertEquals(Integer.valueOf(21), cfg.minimumAge());
    }

    public interface ReloadImportConfig extends Config, Reloadable {
        Integer minimumAge();
    }

    @Test
    public void testReloadWithImportedProperties() throws Throwable {
        Properties props = new Properties() {{
           setProperty("minimumAge", "18");
        }};

        ReloadImportConfig cfg = ConfigFactory.create(ReloadImportConfig.class, props);
        assertEquals(Integer.valueOf(18), cfg.minimumAge());

        props.setProperty("minimumAge", "21"); // changing props doesn't reflect to cfg immediately
        assertEquals(Integer.valueOf(18), cfg.minimumAge());

        cfg.reload(); // the config gets reloaded, so the change in props gets reflected
        assertEquals(Integer.valueOf(21), cfg.minimumAge());
    }

    @After
    public void after() throws Throwable {
        target.delete();
    }

}
