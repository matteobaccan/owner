/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Luigi R. Viggiano
 * @author Dmytro Chyzhykov
 */
public class ArraySupportTest {

    private ArrayConfig cfg;

    @Before
    public void before() {
        cfg = ConfigFactory.create(ArrayConfig.class);
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
}
