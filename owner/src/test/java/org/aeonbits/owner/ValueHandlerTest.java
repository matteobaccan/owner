/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.handlers.ValueHandler;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The envelope of a <code>${$name::payload}</code> marker: what the library reads, what it hands over, and
 * what it refuses.
 * <p>
 * The cipher is not here. What is tested is that the payload arrives whole, that the answer replaces the
 * marker everywhere a value can be read, and that the two refusals happen - an unregistered name, and a
 * marker on a method that also declares {@code @EncryptedValue}.
 * </p>
 *
 * @author Matteo Baccan
 */
public class ValueHandlerTest {

    /** Answers with the payload upside down, which is enough to tell "resolved" from "not resolved". */
    static class ReversingHandler implements ValueHandler {
        private final String name;

        ReversingHandler() {
            this("reverse");
        }

        ReversingHandler(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String resolve(String payload) {
            return new StringBuilder(payload).reverse().toString();
        }
    }

    public interface WithMarker extends Accessible {
        @DefaultValue("${$reverse::terces}")
        String password();

        @DefaultValue("jdbc:h2:mem:test?password=${password}&x=1")
        String url();
    }

    @Test
    public void aMarkerIsResolvedByTheHandlerItNames() {
        ConfigFactory.registerValueHandler(new ReversingHandler());
        WithMarker cfg = ConfigFactory.create(WithMarker.class);
        assertEquals("secret", cfg.password());
    }

    @Test
    public void aValueReferringToAMarkerGetsTheAnswerAndNotTheText() {
        ConfigFactory.registerValueHandler(new ReversingHandler());
        WithMarker cfg = ConfigFactory.create(WithMarker.class);
        assertEquals("jdbc:h2:mem:test?password=secret&x=1", cfg.url());
    }

    @Test
    public void fillGetsTheAnswerToo() {
        ConfigFactory.registerValueHandler(new ReversingHandler());
        WithMarker cfg = ConfigFactory.create(WithMarker.class);
        Map<String, String> filled = new HashMap<>();
        cfg.fill(filled);
        assertEquals("secret", filled.get("password"));
    }

    @Test
    public void storeWritesTheMarkerBackAndNotTheSecret() throws Exception {
        ConfigFactory.registerValueHandler(new ReversingHandler());
        WithMarker cfg = ConfigFactory.create(WithMarker.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cfg.store(out, null);
        String written = out.toString("ISO-8859-1");
        assertTrue(written, written.contains("terces"));
        assertFalse(written, written.contains("=secret"));
    }

    public interface WithBase64LikePayload extends Config {
        @DefaultValue("${$reverse::=x/+nH7k}")
        String value();
    }

    @Test
    public void thePayloadArrivesWholeIncludingTheCharactersBase64Uses() {
        ConfigFactory.registerValueHandler(new ReversingHandler());
        assertEquals("k7Hn+/x=", ConfigFactory.create(WithBase64LikePayload.class).value());
    }

    public interface WithColonsInThePayload extends Config {
        @DefaultValue("${$reverse::2v:ppa/atad/terces}")
        String value();
    }

    @Test
    public void colonsInThePayloadSurvive() {
        ConfigFactory.registerValueHandler(new ReversingHandler());
        assertEquals("secret/data/app:v2", ConfigFactory.create(WithColonsInThePayload.class).value());
    }

    public interface WithAMarkerInsideALargerValue extends Config {
        @DefaultValue("before-${$reverse::terces}-after")
        String value();
    }

    @Test
    public void aMarkerSubstitutesInline() {
        ConfigFactory.registerValueHandler(new ReversingHandler());
        assertEquals("before-secret-after", ConfigFactory.create(WithAMarkerInsideALargerValue.class).value());
    }

    public interface WithUnknownHandler extends Config {
        @DefaultValue("${$nobody-registered-this::payload}")
        String value();
    }

    @Test
    public void anUnregisteredNameIsAnErrorAndNotTheEmptyString() {
        try {
            ConfigFactory.create(WithUnknownHandler.class).value();
            fail("a marker naming a handler nobody registered should not resolve");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("nobody-registered-this"));
            // the payload does not travel into the message: it is the part that may be a secret
            assertFalse(expected.getMessage(), expected.getMessage().contains("payload"));
        }
    }

    /**
     * What a handler answers is not read again. A secret that happens to contain <code>${</code> is a
     * secret, and expanding it would let a value from outside the configuration name keys inside it.
     */
    public interface WithAnAnswerThatLooksLikeAVariable extends Config {
        @DefaultValue("shhh")
        String elsewhere();

        @DefaultValue("${$echo::${elsewhere}}")
        String looksLikeATemplate();
    }

    /**
     * Answers with something that looks like a variable, to prove it is not read as one.
     * <p>
     * Static, like every handler here, and that is not a style choice: a handler registered on the
     * factory is held by every configuration the factory creates and travels in its serialized form, so
     * an anonymous class - which captures the enclosing instance - makes this test class part of the
     * graph and every later test that serializes a Config object fail. Written as an anonymous class
     * first, and found by the macOS leg of the build, which runs the tests in another order.
     * </p>
     */
    static class EchoingHandler implements ValueHandler {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String resolve(String payload) {
            return "${elsewhere}";
        }
    }

    @Test
    public void whatAHandlerAnswersIsNotExpandedAgain() {
        ConfigFactory.registerValueHandler(new EchoingHandler());
        assertEquals("${elsewhere}",
                ConfigFactory.create(WithAnAnswerThatLooksLikeAVariable.class).looksLikeATemplate());
    }

    public interface WithBoth extends Config {
        @DefaultValue("${$reverse::terces}")
        @EncryptedValue
        String password();
    }

    @Test
    public void aMarkerAndAnEncryptedValueOnOneMethodAreRefused() {
        ConfigFactory.registerValueHandler(new ReversingHandler());
        try {
            ConfigFactory.create(WithBoth.class);
            fail("@EncryptedValue over a marker says two contradictory things and should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("@EncryptedValue"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("reverse"));
        }
    }

    /** Answers to the same name as {@link ReversingHandler} and says something else. */
    static class RotatedHandler implements ValueHandler {
        @Override
        public String name() {
            return "reverse";
        }

        @Override
        public String resolve(String payload) {
            return "rotated";
        }
    }

    @Test
    public void registeringTheSameNameAgainReplacesIt() {
        ConfigFactory.registerValueHandler(new ReversingHandler());
        ConfigFactory.registerValueHandler(new RotatedHandler());
        assertEquals("rotated", ConfigFactory.create(WithMarker.class).password());
        // put back what the other tests expect, since the factory is a singleton for the whole JVM
        ConfigFactory.registerValueHandler(new ReversingHandler());
    }

    @Test
    public void aNullHandlerIsRefused() {
        try {
            ConfigFactory.registerValueHandler(null);
            fail("null is not a handler");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("can't be null"));
        }
    }

    @Test
    public void aNameThatCouldNotBeWrittenInAMarkerIsRefusedAtRegistration() {
        for (String unreachable : new String[]{"", "with space", "a:b", "a$b", "a{b", "a}b"}) {
            try {
                ConfigFactory.registerValueHandler(new ReversingHandler(unreachable));
                fail("'" + unreachable + "' cannot be written in a marker and should be refused");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("name"));
            }
        }
    }

    /**
     * The reading of the envelope, without a factory. <code>isMarker</code> is what decides that an
     * expression is not a key at all, so its edges are worth pinning down.
     */
    @Test
    public void whatCountsAsAMarker() {
        assertTrue(HandlersManager.isMarker("$aes-gcm::payload"));
        assertTrue(HandlersManager.isMarker("$a::"));
        assertTrue("a payload may hold colons", HandlersManager.isMarker("$vault::secret:v2"));

        assertFalse("an ordinary key is not one", HandlersManager.isMarker("some.key"));
        assertFalse("a default value is not one", HandlersManager.isMarker("some.key:default"));
        assertFalse("nothing before the ::", HandlersManager.isMarker("$::payload"));
        assertFalse("no :: at all", HandlersManager.isMarker("$aes-gcm:payload"));
        assertFalse("does not begin with $", HandlersManager.isMarker("aes-gcm::payload"));
        assertFalse("empty", HandlersManager.isMarker(""));
    }

    @Test
    public void theNameStopsAtTheFirstSeparator() {
        assertEquals("aes-gcm", HandlersManager.nameOf("$aes-gcm::a::b"));
    }

    /** A substitution with no handlers registered refuses a marker, and says what is registered. */
    @Test
    public void withNothingRegisteredTheMessageSaysSo() {
        StrSubstitutor substitutor = new StrSubstitutor(new Properties(), false, new HandlersManager());
        try {
            substitutor.replace("${$aes-gcm::whatever}");
            fail("a marker with no handlers registered should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("nothing"));
        }
    }
}
