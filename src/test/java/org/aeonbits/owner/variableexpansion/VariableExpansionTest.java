/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.variableexpansion;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author luigi
 */
public class VariableExpansionTest {
    /**
     * @author Luigi R. Viggiano
     */
    @Sources({"file:${user.dir}/src/test/resources/test.properties"})
    public static interface SampleConfigWithExpansion extends Config {
        public String favoriteColor();
    }

    @Test
    public void testPropertyWithExpansion() {
        SampleConfigWithExpansion config = ConfigFactory.create
                (SampleConfigWithExpansion.class);
        assertEquals("pink", config.favoriteColor());
    }

}
