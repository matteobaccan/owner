package org.aeonbits.owner;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class XmlConfigTest {

    @Test
    public void testXmlPropertyValue() {

        XmlProperties xmlProperties = ConfigFactory.create(XmlProperties.class);
        assertEquals("Value of the property", xmlProperties.xproperty());
    }

}
