/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.autoreload;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Reloadable;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import static org.aeonbits.owner.UtilTest.save;
import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class AutoReloadTest {

    private static final String spec = "file:target/test-resources/AutoReloadConfig.properties";
    private static File target;

    @BeforeClass
    public static void beforeClass() throws MalformedURLException {
        target = new File(new URL(spec).getFile());
    }

    @Sources(spec)
    @HotReload
    interface AutoReloadConfig extends Config, Reloadable {
        Integer someValue();
    }

    @Test
    public void testAutoReload() throws IOException, InterruptedException {
        save(target, new Properties() {{
            setProperty("someValue", "10");
        }});

        AutoReloadConfig cfg = ConfigFactory.create(AutoReloadConfig.class);
        assertEquals(Integer.valueOf(10), cfg.someValue());

        Thread.sleep(5);

        save(target, new Properties() {{
            setProperty("someValue", "20");
        }});

        assertEquals(Integer.valueOf(20), cfg.someValue());
    }
}
