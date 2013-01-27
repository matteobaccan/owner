package org.aeonbits.owner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.net.URL;

import org.junit.BeforeClass;
import org.junit.Test;

public class CreatePropertiesFillerTest {

    private static ConfigURLStreamHandler handler;

    @BeforeClass
    public static void init() {
        handler = new ConfigURLStreamHandler(
                CreatePropertiesFillerTest.class.getClassLoader(),
                new SystemVariablesExpander());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCreatePropertiesFiller() throws IOException {
        new PropertiesFiller().close();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateXmlPropertiesFiller() throws IOException {
        new XmlPropertiesFiller().close();
    }

    @Test
    public void testWhenURLIsNull() throws Exception {
        PropertiesFiller filler = PropertiesFiller.create(null);
        assertNull(filler);
    }

    @Test
    public void testWhenResourceIsMissing() throws Exception {
        URL propertyFile = new URL(null,
                "classpath:org/aeonbits/owner/fake.properties", handler);
        PropertiesFiller filler = PropertiesFiller.create(propertyFile);
        assertNull(filler);
    }

    @Test
    public void testWhenResourceIsAFlatPropertyFile() throws IOException {
        URL propertyFile = new URL(null,
                "classpath:org/aeonbits/owner/first.properties", handler);
        PropertiesFiller filler = PropertiesFiller.create(propertyFile);
        assertNotNull(filler);
        assertEquals(PropertiesFiller.class, filler.getClass());
    }

    @Test
    public void testWhenResourceIsAnXmlPropertyFile() throws IOException {
        URL propertyFile = new URL(null,
                "classpath:org/aeonbits/owner/XmlProperties.xml", handler);
        PropertiesFiller filler = PropertiesFiller.create(propertyFile);
        assertNotNull(filler);
        assertEquals(XmlPropertiesFiller.class, filler.getClass());
    }
}
