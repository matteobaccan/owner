/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.Assert.assertTrue;

public class PropertiesLoaderTest {

    private PropertiesLoader loader = new PropertiesLoader();

    private static final String UTF_KEY = "цвет";
    private static final String UTF_VALUE= "синий";
    private static final String UTF_KEY_VALUE = String.format("%s:%s", UTF_KEY, UTF_VALUE);
    private InputStream keyValueStream;

    @Before
    public void before() throws IOException {
        keyValueStream = new ByteArrayInputStream(UTF_KEY_VALUE.getBytes("UTF-8"));
    }

    @After
    public void after() throws IOException {
        keyValueStream.close();
    }

    @Test
    public void testLoadingCyrillicInUTF8() throws IOException {
        Properties result = new Properties();
        loader.load(result, keyValueStream);

        assertTrue(result.containsKey(UTF_KEY));
        assertTrue(result.getProperty(UTF_KEY).equals(UTF_VALUE));
    }
}
