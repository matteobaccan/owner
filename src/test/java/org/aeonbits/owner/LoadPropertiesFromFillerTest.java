package org.aeonbits.owner;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class LoadPropertiesFromFillerTest {

    private static ConfigURLStreamHandler handler;
    private Properties checkProperties;

    @BeforeClass
    public static void init() {
        handler = new ConfigURLStreamHandler(
                CreatePropertiesFillerTest.class.getClassLoader(),
                new SystemVariablesExpander());
    }

    @Before
    public void setUp() {
        checkProperties = new Properties();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWhenPropertiesAreNull() throws Exception {
        URL propertyFile = new URL(null,
                "classpath:org/aeonbits/owner/first.properties", handler);
        PropertiesFiller filler = PropertiesFiller.create(propertyFile);
        filler.load(null);
    }

    @Test
    public void testWhenResourceIsAFlatPropertyFile() throws IOException {
        URL propertyFile = new URL(null,
                "classpath:org/aeonbits/owner/first.properties", handler);
        testAndVerify(PropertiesFiller.create(propertyFile));
    }

    @Test
    public void testWhenResourceIsAnXmlPropertyFile() throws IOException {
        URL propertyFile = new URL(null,
                "classpath:org/aeonbits/owner/XmlProperties.xml", handler);
        testAndVerify(PropertiesFiller.create(propertyFile));
    }

    private void testAndVerify(PropertiesFiller filler) throws IOException {
        try {
            filler.load(checkProperties);
        } finally {
            filler.close();
        }
        assertTrue(checkProperties.size() > 0);
    }
}
