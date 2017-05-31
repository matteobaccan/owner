/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

/**
 * @author Koray Sariteke
 * @author Luigi R. Viggiano
 */
public class YAMLLoaderTest {

    @Sources("file:src/test/resources/config.yaml")
    public static interface YAMLConfig extends Config {
    	@Key("thanks")
        String thanks();
    	@Key("greetings")
        List<String> greetings();
    	@Key("notAvailable")
        String notAvailable();
    }

    @Sources("file:src/test/resources/config2.yaml")
    public static interface YAMLWrongPathConfig extends Config {
    	@Key("thanks")
        String thanks();
    	@Key("greetings")
        List<String> greetings();
    	@Key("notAvailable")
        String notAvailable();
    }

    @Test
    public void shouldLoadPropertiesFromYAMLSource() throws Exception {
    	YAMLConfig sample = ConfigFactory.create(YAMLConfig.class);
        assertEquals("welcome", sample.thanks());
        assertTrue(sample.greetings().containsAll(asList("hi", "bonjour", "hiya", "hi!")));
        assertNull(sample.notAvailable());
    }

    @Test
    public void whenPathIsWrongEverythingIsNull() throws Exception {
    	YAMLWrongPathConfig config = ConfigFactory.create(YAMLWrongPathConfig.class);
        assertNull(config.notAvailable());
        assertNull(config.greetings());
        assertNull(config.thanks());
    }

}
