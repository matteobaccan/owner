/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.typeconversion.arrays;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Luigi R. Viggiano
 */
public class ConflictingAnnotationsOnMethodLevelTest {
    private ConflictingAnnotationsOnMethodLevelConfig cfg;

    @Before
    public void before() {
        cfg = ConfigFactory.create(ConflictingAnnotationsOnMethodLevelConfig.class);
    }

    public static interface ConflictingAnnotationsOnMethodLevelConfig extends Config {
        // should throw an exception when invoked: cannot use both @Separator and @Tokenizer on method level
        @Separator(";")
        @TokenizerClass(CustomDashTokenizer.class)
        @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
        public int[] conflictingAnnotationsOnMethodLevel();
    }

    @Test
    public void testConflictingAnnotationsOnMethodLevel() throws Exception {
        try {
            cfg.conflictingAnnotationsOnMethodLevel();
            fail("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException ex) {
            assertThat(ex.getMessage(),
                    equalTo("You cannot specify @Separator and @TokenizerClass both together on method level for " +
                            "'public abstract int[] org.aeonbits.owner.typeconversion.arrays" +
                            ".ConflictingAnnotationsOnMethodLevelTest$ConflictingAnnotationsOnMethodLevelConfig" +
                            ".conflictingAnnotationsOnMethodLevel()'"));
        }
    }
}
