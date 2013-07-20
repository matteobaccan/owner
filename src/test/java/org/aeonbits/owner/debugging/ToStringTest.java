/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.debugging;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class ToStringTest {

    interface MyConfig extends Config {
        @Key("max.threads")
        @DefaultValue("25")
        int maxThreads();

        @Key("max.folders")
        @DefaultValue("99")
        int maxFolders();

        @Key("default.name")
        @DefaultValue("untitled")
        String defaultName();
    }

    @Test
    public void toStringTest() {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        assertEquals("{default.name=untitled, max.folders=99, max.threads=25}", cfg.toString());
    }
}
