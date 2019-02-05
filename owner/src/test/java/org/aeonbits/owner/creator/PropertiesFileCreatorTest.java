/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.creator;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.GroupOrder;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Luca Taddeo
 */
public class PropertiesFileCreatorTest {

    @GroupOrder({"second", "first"})
    interface MyConfig extends Config {

        @Group({"first"})
        @Key("valuekey")
        @DefaultValue("value")
        String value();

        @DefaultValue("value2")
        String value2();

        @Group({"second"})
        @DefaultValue("value3")
        String value3();

        @Group({"second", "sub"})
        @DefaultValue("value4")
        String value4();
    }
    
    /**
     * Test of parse method, of class PropertiesFileCreator.
     */
    @Test
    public void testParse() {

        try {
            PropertiesFileCreator creator = new PropertiesFileCreator();

            assertTrue(creator.parse(MyConfig.class, "./text.properties", "test", "project test"));
        } catch (Exception ex) {
            Logger.getLogger(PropertiesFileCreatorTest.class.getName()).log(Level.SEVERE, null, ex);
            fail();
        }
    }

}
