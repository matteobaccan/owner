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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Luigi R. Viggiano
 * @author Dmytro Chyzhykov
 */
public class ArraySupportTest {

    private ArrayConfig cfg;

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

    // it's private, it cannot be instantiated by the OWNER library
    private static class NonInstantiableTokenizer extends CustomCommaTokenizer implements Tokenizer {
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

        public static class UnsupportedType {
        }

        public UnsupportedType[] unsupported();

        @Separator(";")
        @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
        public int[] fibonacci();

        @TokenizerClass(CustomDashTokenizer.class)
        @DefaultValue("foo-bar-baz")
        public String[] withSeparatorClass();

        @Separator(";")
        @TokenizerClass(CustomDashTokenizer.class)
        @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
        public int[] conflictingAnnotationsOnMethodLeve(); // should throw an exception when invoked: cannot use
                                                           // both @Separator and @Tokenizer on method level

        @TokenizerClass(NonInstantiableTokenizer.class)
        @DefaultValue("1,2,3")
        public int[] nonInstantiableTokenizer(); // throws an exception since the Tokenizer class is declared as private
    }

    @Separator(";")
    public static interface ArrayConfigWithSeparatorAnnotationOnClassLevel extends Config {

        @Separator(",")                   //should override the class-level @Separator
        @DefaultValue("1, 2, 3, 4")
        public int commaSeparated();

        @DefaultValue("1; 2; 3; 4")
        public int semicolonSeparated();  //should take the class level @Separator


        @TokenizerClass(CustomDashTokenizer.class) //should take the class level @Separator
        @DefaultValue("1-2-3-4")
        public int dashSeparated();
    }

    @TokenizerClass(CustomDashTokenizer.class)
    public static interface ArrayConfigWithTokenizerAnnotationOnClassLevel extends Config {

        @TokenizerClass(CustomCommaTokenizer.class) //should override the class-level @TokenizerClass
        @DefaultValue("1,2,3,4")
        public int commaSeparated();

        @Separator(";")  // overrides class level @TokenizerClass
        @DefaultValue("1; 2; 3; 4")
        public int semicolonSeparated();

        @DefaultValue("1-2-3-4")
        public int dashSeparated(); // class level @TokenizerClass applies
    }


    @TokenizerClass(CustomDashTokenizer.class) // should throw an exception during Config.create():  @Tokenizer
    @Separator(";")                            // and @Separator annotations cannot be used together on class level.
    public static interface ConflictingAnnotationsOnClassLevel extends Config {
    }


    @Before
    public void before() {
        cfg = ConfigFactory.create(ArrayConfig.class);
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

    @Test(expected = UnsupportedOperationException.class)
    public void testConflictingAnnotationsOnMethodLevel() throws Exception {
        cfg.conflictingAnnotationsOnMethodLeve();
    }

    @Test
    public void testSeparatorAnnotation() throws Exception {
        assertThat(cfg.fibonacci(), is(new int[]{0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55}));
    }

    @Test
    public void testTokenizerClass() throws Exception {
        assertThat(cfg.withSeparatorClass(), is(new String[]{"foo", "bar", "baz"}));
    }

    @Test
    public void testNonInstantiableTokenizer() throws Exception {
        try {
            cfg.nonInstantiableTokenizer();
            fail("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getCause() instanceof IllegalAccessException); // since NonInstantiableTokenizer is private.
        }
    }

}
