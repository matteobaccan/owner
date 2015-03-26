/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.typeconversion.arrays;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Separator;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Luigi R. Viggiano
 */
public class SeparatorAnnotationOnClassLevelTest {
    private ArrayConfigWithSeparatorAnnotationOnClassLevel cfg;

    @Before
    public void before() {
        cfg = ConfigFactory.create(ArrayConfigWithSeparatorAnnotationOnClassLevel.class);
    }

    @Separator(";")
    public static interface ArrayConfigWithSeparatorAnnotationOnClassLevel extends Config {

        @Separator(",")                   //should override the class-level @Separator
        @DefaultValue("1, 2, 3, 4")
        public int[] commaSeparated();

        @DefaultValue("1; 2; 3; 4")
        public int[] semicolonSeparated();  //should take the class level @Separator

        @TokenizerClass(CustomDashTokenizer.class) //should take the class level @Separator
        @DefaultValue("1-2-3-4")
        public int[] dashSeparated();
    }

    @Test
    public void testSeparatorAnnotationOnMethodOverridingSeparatorAnnotationOnClassLevel() {
        assertThat(cfg.commaSeparated(), is(new int[]{1, 2, 3, 4}));
    }

    @Test
    public void testSeparatorAnnotationOnClassLevelAndNoOverridingOnMethodLevel() {
        assertThat(cfg.semicolonSeparated(), is(new int[]{1, 2, 3, 4}));
    }

    @Test
    public void testTokenClassAnnotationOnMethodLevelOverridingSeparatorOnClassLevel() {
        assertThat(cfg.dashSeparated(), is(new int[]{1, 2, 3, 4}));
    }
}
