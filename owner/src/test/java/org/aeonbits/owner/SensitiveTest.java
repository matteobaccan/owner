/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Prefix;
import org.aeonbits.owner.Config.Sensitive;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@link Sensitive} annotation: the value of a sensitive property is replaced by
 * {@link Sensitive#MASK} in the output meant to be read by a human, and left alone everywhere else.
 *
 * @author Matteo Baccan
 */
public class SensitiveTest {

    private static final String PASSWORD = "hunter2";

    interface MyConfig extends Accessible {
        String username();

        @Sensitive
        String password();

        @Sensitive
        @Key("db.secret")
        String secret();
    }

    private static MyConfig config() {
        return ConfigFactory.create(MyConfig.class, new Properties() {{
            setProperty("username", "matteo");
            setProperty("password", PASSWORD);
            setProperty("db.secret", "s3cr3t");
        }});
    }

    // -------------------------------------------------------------------------------------------------
    // what is masked
    // -------------------------------------------------------------------------------------------------

    @Test
    public void listToAPrintStreamMasksTheSensitiveValues() throws UnsupportedEncodingException {
        String output = list(config());

        assertFalse("the password was printed", output.contains(PASSWORD));
        assertTrue(output.contains("password=" + Sensitive.MASK));
        assertTrue(output.contains("db.secret=" + Sensitive.MASK));
        assertTrue("everything else is printed as it is", output.contains("username=matteo"));
    }

    @Test
    public void listToAPrintWriterMasksTheSensitiveValues() {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        config().list(out);
        out.flush();

        String output = writer.toString();
        assertFalse("the password was printed", output.contains(PASSWORD));
        assertTrue(output.contains("password=" + Sensitive.MASK));
        assertTrue(output.contains("username=matteo"));
    }

    @Test
    public void toStringMasksTheSensitiveValues() {
        String output = config().toString();

        assertFalse("the password was printed", output.contains(PASSWORD));
        assertTrue(output.contains(Sensitive.MASK));
        assertTrue(output.contains("matteo"));
    }

    // -------------------------------------------------------------------------------------------------
    // what is not masked: masking is not encryption, and a Config has to stay readable and writable
    // -------------------------------------------------------------------------------------------------

    @Test
    public void theMethodReturnsTheRealValue() {
        assertEquals(PASSWORD, config().password());
    }

    @Test
    public void getPropertyReturnsTheRealValue() {
        assertEquals(PASSWORD, config().getProperty("password"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void fillReturnsTheRealValue() {
        Map<String, String> map = new HashMap<>();
        config().fill(map);
        assertEquals(PASSWORD, map.get("password"));
    }

    /**
     * The one that would do real damage: a masked <code>store</code> writes the mask into the file, and the
     * next time the configuration is read the password is gone for good.
     */
    @Test
    public void storeWritesTheRealValue() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        config().store(out, "a comment");

        String stored = out.toString("ISO-8859-1");
        assertTrue("the password must survive a save", stored.contains("password=" + PASSWORD));
        assertFalse(stored.contains(Sensitive.MASK));
    }

    @Test
    public void storeToXMLWritesTheRealValue() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        config().storeToXML(out, "a comment");

        String stored = out.toString("UTF-8");
        assertTrue("the password must survive a save", stored.contains(PASSWORD));
        assertFalse(stored.contains(Sensitive.MASK));
    }

    // -------------------------------------------------------------------------------------------------
    // where the annotation can be written
    // -------------------------------------------------------------------------------------------------

    @Sensitive
    interface AllSensitive extends Accessible {
        String first();

        String second();
    }

    @Test
    public void theAnnotationOnTheInterfaceMasksEveryProperty() throws UnsupportedEncodingException {
        AllSensitive cfg = ConfigFactory.create(AllSensitive.class, new Properties() {{
            setProperty("first", "one");
            setProperty("second", "two");
        }});

        String output = list(cfg);
        assertFalse(output.contains("one"));
        assertFalse(output.contains("two"));
        assertEquals(2, countOccurrences(output, Sensitive.MASK));
    }

    @Prefix("db.")
    interface WithPrefix extends Accessible {
        @Sensitive
        String password();
    }

    @Test
    public void theMaskedKeyIsTheOneThePropertyIsReadWith() throws UnsupportedEncodingException {
        WithPrefix cfg = ConfigFactory.create(WithPrefix.class, new Properties() {{
            setProperty("db.password", PASSWORD);
        }});

        String output = list(cfg);
        assertFalse(output.contains(PASSWORD));
        assertTrue(output.contains("db.password=" + Sensitive.MASK));
    }

    // -------------------------------------------------------------------------------------------------
    // the limits of what can be worked out in advance
    // -------------------------------------------------------------------------------------------------

    interface WithParameters extends Accessible {
        @Sensitive
        @Key("secret.%s")
        String secret(String which);
    }

    /**
     * The keys to mask are worked out when the Config object is created, and the key of a method taking
     * parameters is not known until it is called. Documented as a limitation rather than silently producing
     * a mask under a key that matches nothing.
     */
    @Test
    public void aKeyThatDependsOnTheArgumentsCannotBeMasked() throws UnsupportedEncodingException {
        WithParameters cfg = ConfigFactory.create(WithParameters.class, new Properties() {{
            setProperty("secret.db", PASSWORD);
        }});

        assertTrue(list(cfg).contains(PASSWORD));
    }

    // -------------------------------------------------------------------------------------------------
    // nothing changes when nothing is sensitive
    // -------------------------------------------------------------------------------------------------

    interface NothingSensitive extends Accessible {
        String value();
    }

    @Test
    public void aConfigWithoutSensitivePropertiesIsPrintedAsBefore() throws UnsupportedEncodingException {
        NothingSensitive cfg = ConfigFactory.create(NothingSensitive.class, new Properties() {{
            setProperty("value", "printed");
        }});

        assertTrue(list(cfg).contains("value=printed"));
        assertTrue(cfg.toString().contains("printed"));
    }

    private static String list(Accessible cfg) throws UnsupportedEncodingException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, "UTF-8");
        cfg.list(out);
        out.flush();
        return bytes.toString("UTF-8");
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + token.length()))
            count++;
        return count;
    }
}
