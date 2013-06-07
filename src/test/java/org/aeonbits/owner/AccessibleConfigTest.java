package org.aeonbits.owner;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author luigi
 */
public class AccessibleConfigTest {
    public static interface AccessibleConfig extends Config, Accessible {
        @DefaultValue("Bohemian Rapsody - Queen")
        String favoriteSong();

        @Key("salutation.text")
        @DefaultValue("Good Morning")
        String salutation();
    }

    @Test
    public void testListPrintStream() throws IOException {
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        PropertiesManager manager = new PropertiesManager(AccessibleConfig.class, new Properties());
        manager.load().list(new PrintStream(expected, true));

        AccessibleConfig config = ConfigFactory.create(AccessibleConfig.class);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        config.list(new PrintStream(result, true));

        assertEquals(expected.toString(), result.toString());
    }

    @Test
    public void testListPrintWriter() throws IOException {
        StringWriter expected = new StringWriter();
        PropertiesManager manager = new PropertiesManager(AccessibleConfig.class, new Properties());
        manager.load().list(new PrintWriter(expected, true));

        AccessibleConfig config = ConfigFactory.create(AccessibleConfig.class);
        StringWriter result = new StringWriter();
        config.list(new PrintWriter(result, true));

        assertEquals(expected.toString(), result.toString());
    }

}
