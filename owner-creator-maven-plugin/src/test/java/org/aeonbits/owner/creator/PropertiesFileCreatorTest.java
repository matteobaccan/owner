/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.creator;

import java.io.File;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aeonbits.owner.Config;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Luca Taddeo
 */
public class PropertiesFileCreatorTest {

    interface MyConfig extends Config {

        @Key("valuekey")
        @DefaultValue("value")
        String value();

        @DefaultValue("value2")
        String value2();

        @DefaultValue("value3")
        String value3();

        @DefaultValue("value4")
        String value4();
    }

    /**
     * Test of parse method, of class PropertiesFileCreator.
     */
    @Test
    public void testParse() throws Exception {
        PropertiesFileCreator creator = new PropertiesFileCreator();

        File file = new File("./target/text.properties");
        file.getParentFile().mkdirs();

        PrintWriter output = new PrintWriter(file);
        try {
            creator.parse(MyConfig.class, output, "test", "project test");
        } finally {
            output.close();
        }
    }

}
