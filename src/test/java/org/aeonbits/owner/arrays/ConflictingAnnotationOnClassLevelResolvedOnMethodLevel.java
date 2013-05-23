/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.arrays;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Separator;
import org.aeonbits.owner.Config.TokenizerClass;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author Luigi R. Viggiano
 */
public class ConflictingAnnotationOnClassLevelResolvedOnMethodLevel {

    private ConflictingAnnotationsResolved cfg;

    // as we know specifying both @TokenizerClass and Separator on the same level (class level) generates a conflict
    @TokenizerClass(CustomCommaTokenizer.class)
    @Separator(";")
    public static interface ConflictingAnnotationsResolved extends Config {

        @Separator(",") // but since @Separator on method level takes precedence, the conflict is resolved.
        @DefaultValue("1, 2, 3, 4")
        public int[] commaSeparated();
    }

    @Before
    public void before() {
        cfg = ConfigFactory.create(ConflictingAnnotationsResolved.class);
    }

    @Test
    public void testSeparatorAnnotationOnMethodLevelResolveTheConflictOnClassLevel() {
        assertThat(cfg.commaSeparated(), is(new int[]{1, 2, 3, 4}));
    }

}
