/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loadstrategies;

import static org.aeonbits.owner.Config.LoadType.MERGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

/**
 * @author Luigi R. Viggiano
 */
public class MergeLoadStrategyTest {
    @Test
    public void testPropertyMerge() {
        MergeConfig cfg = ConfigFactory.create(MergeConfig.class);
        assertEquals("first", cfg.foo());
        assertEquals("second", cfg.bar());
        assertEquals("first", cfg.foo());
        assertEquals("third", cfg.qux());
        assertNull(cfg.quux());
        assertEquals("theDefaultValue", cfg.fubar());
    }

    @Sources({"classpath:org/aeonbits/owner/first.properties",
              "classpath:foo/bar/thisDoesntExists.properties",
              "classpath:org/aeonbits/owner/second.properties",
              "file:${user.dir}/src/test/resources/foo/bar/thisDoesntExists.properties",
              "file:${user.dir}/src/test/resources/org/aeonbits/owner/third.properties"})
    @LoadPolicy(MERGE)
    public static interface MergeConfig extends Config {
        @DefaultValue("this should be ignored")
        String foo();
        @DefaultValue("this should be ignored")
        String bar();
        @DefaultValue("this should be ignored")
        String baz();
        @DefaultValue("this should be ignored")
        String qux();

        String quux(); // this should return null;
        @DefaultValue("theDefaultValue")
        String fubar();
    }

    @Sources("httpz://foo.bar.baz")
    @LoadPolicy(MERGE)
    interface InvalidURLConfig extends Config {

    }

    @Test(expected = UnsupportedOperationException.class)
    public void testWhenURLIsInvalid() {
        ConfigFactory.create(InvalidURLConfig.class);
    }

}
