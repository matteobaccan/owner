/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.variableexpansion;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Tests properties resolution.
 *
 * @author Luigi R. Viggiano
 */
public class PropertyNotSetTest {

    public interface MyConfig extends Config {
        @DefaultValue("Hello ${world}.")
        String foo();
    }

    @Test
    public void testPropertyNotSet() {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        assertEquals("Hello .", cfg.foo());
    }

    @Test
    public void testPropertySet() {
        Map<String, String> ctx = new HashMap<String, String>() {{
            put("world", "Earth");
        }};
        MyConfig cfg = ConfigFactory.create(MyConfig.class, ctx);
        assertEquals("Hello Earth.", cfg.foo());
    }

    @Test
    public void testPropertyChanged() {
        Map<String, String> ctx = new HashMap<String, String>() {{
            put("world", "Earth");
        }};
        MyConfig cfg = ConfigFactory.create(MyConfig.class, ctx);

        ctx.put("world", "Mars");
        assertEquals("Hello Earth.", cfg.foo());
    }
}
