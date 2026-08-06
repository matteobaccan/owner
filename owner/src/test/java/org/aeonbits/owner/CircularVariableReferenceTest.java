/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A property whose value leads back to the property itself cannot be resolved. Following it used to exhaust the
 * stack; it is now reported as the configuration error it is, naming the chain that closes the loop.
 *
 * @author Matteo Baccan
 */
public class CircularVariableReferenceTest {

    private static String circularMessage(final Properties values, String source) {
        try {
            String result = new StrSubstitutor(values).replace(source);
            fail("IllegalArgumentException is expected, got '" + result + "'");
            return null;
        } catch (IllegalArgumentException e) {
            assertTrue("unexpected message: " + e.getMessage(),
                    e.getMessage().startsWith("Circular variable reference: "));
            return e.getMessage();
        }
    }

    @Test
    public void aPropertyReferringToItself() {
        assertEquals("Circular variable reference: ${a} -> ${a}",
                circularMessage(new Properties() {{ setProperty("a", "${a}"); }}, "${a}"));
    }

    @Test
    public void aDefaultValueDoesNotRescueACircularReference() {
        assertEquals("Circular variable reference: ${a:d} -> ${a:d}",
                circularMessage(new Properties() {{ setProperty("a", "${a:d}"); }}, "${a}"));
    }

    @Test
    public void twoPropertiesReferringToEachOther() {
        assertEquals("Circular variable reference: ${a} -> ${b} -> ${a}",
                circularMessage(new Properties() {{
                    setProperty("a", "${b}");
                    setProperty("b", "${a}");
                }}, "${a}"));
    }

    @Test
    public void aLongerLoopNamesEveryStepOfIt() {
        assertEquals("Circular variable reference: ${b} -> ${c} -> ${b}",
                circularMessage(new Properties() {{
                    setProperty("a", "${b}");
                    setProperty("b", "${c}");
                    setProperty("c", "${b}");
                }}, "${a}"));
    }

    @Test
    public void aCircularNestedKey() {
        circularMessage(new Properties() {{
            setProperty("env", "${servers.${env}.name}");
            setProperty("servers.dev.name", "Development");
        }}, "${env}");
    }

    /** The idiom imported from shells and from Spring, which reads as if it terminated. It does not. */
    interface SelfDefaultingConfig extends Config {
        @Key("db.host")
        @DefaultValue("${db.host:localhost}")
        String host();
    }

    @Test
    public void theSelfDefaultingIdiomIsReportedRatherThanFollowed() {
        try {
            ConfigFactory.create(SelfDefaultingConfig.class).host();
            fail("IllegalArgumentException is expected");
        } catch (IllegalArgumentException e) {
            assertEquals("Circular variable reference: ${db.host:localhost} -> ${db.host:localhost}",
                    e.getMessage());
        }
    }

    // -- what must NOT be mistaken for a loop --------------------------------------------------------

    @Test
    public void theSameVariableUsedTwiceIsNotALoop() {
        Properties values = new Properties() {{
            setProperty("x", "a");
        }};
        assertEquals("a-a", new StrSubstitutor(values).replace("${x}-${x}"));
    }

    @Test
    public void aChainOfDistinctVariablesIsNotALoop() {
        Properties values = new Properties() {{
            setProperty("story", "The ${animal} jumped over the ${target}.");
            setProperty("animal", "quick ${color} fox");
            setProperty("color", "brown");
            setProperty("target", "lazy dog");
        }};
        assertEquals("The quick brown fox jumped over the lazy dog.",
                new StrSubstitutor(values).replace("${story}"));
    }

    @Test
    public void aNestedKeyMentioningTheSameVariableTwiceIsNotALoop() {
        Properties values = new Properties() {{
            setProperty("env", "dev");
            setProperty("servers.dev.host", "devhost");
            setProperty("servers.dev.port", "6000");
        }};
        assertEquals("devhost:6000",
                new StrSubstitutor(values).replace("${servers.${env}.host}:${servers.${env}.port}"));
    }

    @Test
    public void aValueEqualToItsOwnKeyIsNotALoop() {
        Properties values = new Properties() {{
            setProperty("a", "a");
        }};
        assertEquals("a", new StrSubstitutor(values).replace("${a}"));
    }
}
