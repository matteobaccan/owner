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
import java.io.FileWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class ReloadTest {
    private static final String spec = "file:target/test-resources/ReloadableConfig.properties";
    private static File target;
    private ReloadableConfig reloadableConfig;

    @BeforeClass
    public static void beforeClass() throws MalformedURLException {
        target = new File(new URL(spec).getFile());
    }

    @Before
    public void before() throws Throwable {
        synchronized (target) {
            save(new Properties() {{
                setProperty("someValue", "10");
            }});

            reloadableConfig = ConfigFactory.create(ReloadableConfig.class);
        }
    }

    @Sources(spec)
    public interface ReloadableConfig extends Config, Reloadable {
        Integer someValue();
    }

    @Test
    public void testReload() throws Throwable {
        assertEquals(Integer.valueOf(10), reloadableConfig.someValue());

        synchronized (target) {
            save(new Properties() {{
                setProperty("someValue", "20");
            }});

            reloadableConfig.reload();
        }
        assertEquals(Integer.valueOf(20), reloadableConfig.someValue());
    }

    private void save(Properties p) throws Throwable {
        synchronized (target) {
            target.getParentFile().mkdirs();
            p.store(new FileWriter(target), "foobar");
        }
    }

    @After
    public void after() throws Throwable {
        synchronized (target) {
            target.delete();
        }
    }

}
