/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.importedprops;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.io.File;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class SystemPropertiesAndEnvTest {
    interface SystemEnvProperties extends Config {
        @Key("file.separator")
        String fileSeparator();

        @Key("java.home")
        String javaHome();

        @Key("HOME")
        String home();

        @Key("USER")
        String user();

        void list(PrintStream out);
    }

    @Test
    public void testSystemEnvProperties() {
        SystemEnvProperties cfg = ConfigFactory.create(SystemEnvProperties
                .class, System.getProperties(), System.getenv());
        assertEquals(File.separator, cfg.fileSeparator());
        assertEquals(System.getProperty("java.home"), cfg.javaHome());
        assertEquals(System.getenv().get("HOME"), cfg.home());
        assertEquals(System.getenv().get("USER"), cfg.user());
    }
}
