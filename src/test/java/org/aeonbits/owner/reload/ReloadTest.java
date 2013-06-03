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
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class ReloadTest {
    private static final String spec = "file:target/test-resources/ReloadableConfig.properties";

    @Sources(spec)
    public interface ReloadableConfig extends Config, Reloadable {
        int someValue();
    }

    @Test
    public void testReload() throws Throwable {
        save(new Properties() {{
            setProperty("someValue", "10");
        }});

        ReloadableConfig cfg = ConfigFactory.create(ReloadableConfig.class);
        assertEquals(10, cfg.someValue());

        save(new Properties() {{
            setProperty("someValue", "20");
        }});

        cfg.reload();
        assertEquals(20, cfg.someValue());
    }

    private void save(Properties p) throws Throwable {
        File target = new File(new URL(spec).getFile());
        target.getParentFile().mkdirs();
        p.store(new FileWriter(target), "foobar");
    }


}
