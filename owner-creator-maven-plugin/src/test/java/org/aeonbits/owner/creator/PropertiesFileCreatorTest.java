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

import org.aeonbits.owner.Config;
import org.aeonbits.owner.plugin.annotations.Description;
import org.aeonbits.owner.plugin.annotations.Group;
import org.aeonbits.owner.plugin.annotations.GroupOrder;
import org.aeonbits.owner.plugin.annotations.NoProperty;
import org.aeonbits.owner.plugin.annotations.ValorizedAs;
import org.junit.Test;


/**
 * @author Luca Taddeo
 */
public class PropertiesFileCreatorTest {

    @GroupOrder({"First", "Second", "Third"})
    interface MyConfig extends Config {

        @Group("Second")
        @Key("valuekey")
        @DefaultValue("value")
        String value();

        @Group({"First", "FirstInside"})
        @Description("Test comment with\nnew line.")
        @ValorizedAs("pippo")
        @DefaultValue("value2")
        String value2();

        @NoProperty
        @DefaultValue("value3")
        String value3();

        @Group("Third")
        @DefaultValue("value4")
        String value4();

        @Group("First")
        @DefaultValue("value5")
        String value5();

        @Deprecated
        @DefaultValue("value6")
        String value6();
    }

    /**
     * Test of parse method, of class PropertiesFileCreator.
     * @throws java.lang.Exception
     */
    @Test
    public void testParse() throws Exception {
        PropertiesFileCreator creator = new PropertiesFileCreator();
        
        File file = new File("./target/text.properties");
        file.getParentFile().mkdirs();

        PrintWriter output = new PrintWriter(file);
        try {
            creator.parse(MyConfig.class, output, "test");
        } finally {
            output.close();
        }
    }

}
