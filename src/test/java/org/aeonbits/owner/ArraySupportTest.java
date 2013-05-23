/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Separator;
import org.aeonbits.owner.Config.Tokenizer;
import org.aeonbits.owner.Config.TokenizerClass;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * TODO: remark for Luigi, refactor this: split this class... I put the comments to identify where to split
 *
 * @author Luigi R. Viggiano
 * @author Dmytro Chyzhykov
 */
public class ArraySupportTest {

    private ArrayConfig cfg;
    private ArrayConfigWithSeparatorAnnotationOnClassLevel cfgSeparatorAnnotationOnClassLevel;
    private ArrayConfigWithTokenizerAnnotationOnClassLevel cfgArrayConfigWithTokenizerAnnotationOnClassLevel;

    @Before
    public void before() {
        cfg = ConfigFactory.create(ArrayConfig.class);
        cfgSeparatorAnnotationOnClassLevel = ConfigFactory.create(ArrayConfigWithSeparatorAnnotationOnClassLevel.class);
        cfgArrayConfigWithTokenizerAnnotationOnClassLevel = ConfigFactory
                .create(ArrayConfigWithTokenizerAnnotationOnClassLevel.class);
    }

    public static interface ArrayConfig extends Config {
        public String[] fruit();
        public String[] emptyProperty();
        public String[] missedProperty();
        public Integer[] integers();
        public Integer[] emptyIntegers();

        @Key("integers")
        public int[] primitiveIntArray();

        @Key("emptyIntegers")
        public int[] primitiveEmptyIntegers();

        public static class UnsupportedType {}
        public UnsupportedType[] unsupported();
    }

    @Test
    public void itShouldReadStringArray() throws Exception {
        assertThat(cfg.fruit(), is(new String[]{"apple", "pear", "orange"}));
    }

    @Test
    public void itShouldReturnNullForMissedStringArray() throws Exception {
        assertThat(cfg.missedProperty(), is(nullValue()));
    }

    @Test
    public void itShouldReturnEmptyStringArray() throws Exception {
        assertThat(cfg.emptyProperty(), is(new String[]{}));
    }

    @Test
    public void itShouldReturnIntegerArray() throws Exception {
        assertThat(cfg.integers(), is(new Integer[]{1, 2, 3}));
    }

    @Test
    public void itShouldReturnEmptyIntegerArray() throws Exception {
        assertThat(cfg.emptyIntegers(), is(new Integer[]{}));
    }

    @Test
    public void itShouldReturnIntArray() throws Exception {
        assertThat(cfg.primitiveIntArray(), is(new int[]{1, 2, 3}));
    }

    @Test
    public void itShouldReturnEmptyIntArray() throws Exception {
        assertThat(cfg.primitiveEmptyIntegers(), is(new int[]{}));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUnsupportedArrayType() throws Exception {
        cfg.unsupported();
    }

    /*------------------------------------------------------------------------------------------------------------------
     * ArrayConfigWithSeparatorAnnotationOnClassLevel
     *----------------------------------------------------------------------------------------------------------------*/

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
        assertThat(cfgSeparatorAnnotationOnClassLevel.commaSeparated(), is(new int[]{1, 2, 3, 4}));
    }

    @Test
    public void testSeparatorAnnotationOnClassLevelAndNoOverridingOnMethodLevel() {
        assertThat(cfgSeparatorAnnotationOnClassLevel.semicolonSeparated(), is(new int[]{1, 2, 3, 4}));
    }

    @Test
    public void testTokenClassAnnotationOnMethodLevelOverridingSeparatorOnClassLevel() {
        assertThat(cfgSeparatorAnnotationOnClassLevel.dashSeparated(), is(new int[]{1, 2, 3, 4}));
    }


    /*------------------------------------------------------------------------------------------------------------------
     * ArrayConfigWithTokenizerAnnotationOnClassLevel
     *----------------------------------------------------------------------------------------------------------------*/

    @TokenizerClass(CustomDashTokenizer.class)
    public static interface ArrayConfigWithTokenizerAnnotationOnClassLevel extends Config {

        @TokenizerClass(CustomCommaTokenizer.class) //should override the class-level @TokenizerClass
        @DefaultValue("1,2,3,4")
        public int[] commaSeparated();

        @Separator(";")  // overrides class level @TokenizerClass
        @DefaultValue("1; 2; 3; 4")
        public int[] semicolonSeparated();

        @DefaultValue("1-2-3-4")
        public int[] dashSeparated(); // class level @TokenizerClass applies
    }

    @Test
    public void testTokenizerClassAnnotationOnMethodLevelOverridingTokenizerClassAnnotationOnClassLevel() {
        assertThat(cfgArrayConfigWithTokenizerAnnotationOnClassLevel.commaSeparated(), is(new int[]{1, 2, 3, 4}));
    }

    @Test
    public void testSeparatorAnnotationOnMethodLevelOverridingTokenizerClassAnnotationOnClassLevel() {
        assertThat(cfgArrayConfigWithTokenizerAnnotationOnClassLevel.semicolonSeparated(), is(new int[]{1, 2, 3, 4}));
    }

    @Test
    public void testTokenizerClassAnnotationOnClassLevelAndNoOverridingOnMethodLevel() {
        assertThat(cfgArrayConfigWithTokenizerAnnotationOnClassLevel.dashSeparated(), is(new int[]{1, 2, 3, 4}));
    }


    /*------------------------------------------------------------------------------------------------------------------
     * Custom Tokenizers, used by above tests
     *----------------------------------------------------------------------------------------------------------------*/

    public static class CustomDashTokenizer implements Tokenizer {
        @Override
        public String[] tokens(String values) {
            return values.split("-", -1);
        }
    }

    public static class CustomCommaTokenizer implements Tokenizer {
        @Override
        public String[] tokens(String values) {
            return values.split(",", -1);
        }
    }


}
