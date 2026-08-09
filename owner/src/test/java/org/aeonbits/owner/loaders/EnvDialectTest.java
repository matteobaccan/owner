/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.aeonbits.owner.loaders.EnvDialect.BareNames.ERROR;
import static org.aeonbits.owner.loaders.EnvDialect.BareNames.FROM_ENVIRONMENT;
import static org.aeonbits.owner.loaders.EnvDialect.BareNames.IGNORE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link EnvDialect}: the presets say what they claim to say, and adjusting one rule leaves the
 * others alone.
 *
 * @author Matteo Baccan
 */
public class EnvDialectTest {

    @Test
    public void testDockerDoesNothingToAValue() {
        EnvDialect docker = EnvDialect.DOCKER;
        assertFalse(docker.isQuotesStripped());
        assertFalse(docker.isEscapesExpanded());
        assertFalse(docker.isExportPrefixStripped());
        assertFalse(docker.isInlineComments());
        assertFalse(docker.isMultilineValues());
        assertFalse(docker.isLineContinuation());
        assertEquals(FROM_ENVIRONMENT, docker.bareNames());
    }

    @Test
    public void testDotenvUnwrapsAndUnescapes() {
        EnvDialect dotenv = EnvDialect.DOTENV;
        assertTrue(dotenv.isQuotesStripped());
        assertTrue(dotenv.isEscapesExpanded());
        assertTrue(dotenv.isExportPrefixStripped());
        assertTrue(dotenv.isInlineComments());
        assertTrue(dotenv.isMultilineValues());
        assertFalse(dotenv.isLineContinuation());
        assertEquals(IGNORE, dotenv.bareNames());
    }

    /** Compose parts from docker on quotes, and from dotenv on {@code export} and on values spanning lines. */
    @Test
    public void testComposeSitsBetweenTheOtherTwo() {
        EnvDialect compose = EnvDialect.COMPOSE;
        assertTrue(compose.isQuotesStripped());
        assertTrue(compose.isEscapesExpanded());
        assertFalse(compose.isExportPrefixStripped());
        assertTrue(compose.isInlineComments());
        assertFalse(compose.isMultilineValues());
        assertEquals(FROM_ENVIRONMENT, compose.bareNames());
    }

    @Test
    public void testTheDialectsAreReachableByName() {
        assertSame(EnvDialect.DOCKER, EnvDialect.named("docker"));
        assertSame(EnvDialect.DOTENV, EnvDialect.named("dotenv"));
        assertSame(EnvDialect.COMPOSE, EnvDialect.named("compose"));
    }

    @Test
    public void testANameIsMatchedWhateverItsCaseAndSpacing() {
        assertSame(EnvDialect.DOTENV, EnvDialect.named("  DotEnv "));
    }

    @Test
    public void testAnUnknownNameIsRefused() {
        try {
            EnvDialect.named("hocon");
            fail("an unknown dialect name should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("hocon"));
        }
    }

    @Test
    public void testANullNameIsRefused() {
        try {
            EnvDialect.named(null);
            fail("a null dialect name should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }
    }

    @Test
    public void testAdjustingOneRuleLeavesTheOthersAlone() {
        EnvDialect adjusted = EnvDialect.DOTENV.withQuotesStripped(false);
        assertFalse(adjusted.isQuotesStripped());
        assertTrue(adjusted.isEscapesExpanded());
        assertTrue(adjusted.isExportPrefixStripped());
        assertTrue(adjusted.isInlineComments());
        assertTrue(adjusted.isMultilineValues());
        assertEquals(IGNORE, adjusted.bareNames());
    }

    @Test
    public void testAdjustingReturnsANewDialectAndLeavesTheOriginal() {
        EnvDialect adjusted = EnvDialect.DOCKER.withQuotesStripped(true);
        assertTrue(adjusted.isQuotesStripped());
        assertFalse("the shared constant must not have been changed", EnvDialect.DOCKER.isQuotesStripped());
    }

    @Test
    public void testEveryRuleCanBeAdjusted() {
        EnvDialect all = EnvDialect.DOCKER
                .withQuotesStripped(true)
                .withEscapesExpanded(true)
                .withExportPrefixStripped(true)
                .withInlineComments(true)
                .withMultilineValues(true)
                .withLineContinuation(true)
                .withBareNames(ERROR);
        assertTrue(all.isQuotesStripped());
        assertTrue(all.isEscapesExpanded());
        assertTrue(all.isExportPrefixStripped());
        assertTrue(all.isInlineComments());
        assertTrue(all.isMultilineValues());
        assertTrue(all.isLineContinuation());
        assertEquals(ERROR, all.bareNames());
    }

    /** The name records where a dialect came from, so a message can say it, and adjusting does not change it. */
    @Test
    public void testTheNameSurvivesAnAdjustment() {
        assertEquals("dotenv", EnvDialect.DOTENV.withQuotesStripped(false).name());
    }

    @Test
    public void testANullBareNamesPolicyIsRefused() {
        try {
            EnvDialect.DOCKER.withBareNames(null);
            fail("a null policy should be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("null"));
        }
    }

    @Test
    public void testADialectEqualsItself() {
        assertEquals(EnvDialect.DOCKER, EnvDialect.DOCKER);
    }

    @Test
    public void testTwoDialectsWithTheSameRulesAreEqual() {
        assertEquals(EnvDialect.DOCKER, EnvDialect.DOCKER.withQuotesStripped(true).withQuotesStripped(false));
        assertEquals(EnvDialect.DOCKER.hashCode(),
                EnvDialect.DOCKER.withQuotesStripped(true).withQuotesStripped(false).hashCode());
    }

    /** One rule at a time, so that no comparison is forgotten in {@code equals}. */
    @Test
    public void testDialectsDifferingInOneRuleAreNotEqual() {
        EnvDialect docker = EnvDialect.DOCKER;
        assertNotEquals(docker, docker.withQuotesStripped(true));
        assertNotEquals(docker, docker.withEscapesExpanded(true));
        assertNotEquals(docker, docker.withExportPrefixStripped(true));
        assertNotEquals(docker, docker.withInlineComments(true));
        assertNotEquals(docker, docker.withMultilineValues(true));
        assertNotEquals(docker, docker.withLineContinuation(true));
        assertNotEquals(docker, docker.withBareNames(IGNORE));
        assertNotEquals(docker, EnvDialect.DOTENV);
    }

    @Test
    public void testTwoDialectsWithTheSameRulesButDifferentOriginsAreNotEqual() {
        // compose is dotenv without the export prefix and without values spanning lines: made to match rule by
        // rule, the two still say where they came from
        assertNotEquals(EnvDialect.COMPOSE,
                EnvDialect.DOTENV.withExportPrefixStripped(false).withMultilineValues(false)
                        .withBareNames(FROM_ENVIRONMENT));
    }

    @Test
    public void testADialectIsNotEqualToOtherThings() {
        assertNotEquals(EnvDialect.DOCKER, "docker");
        assertNotEquals(EnvDialect.DOCKER, null);
    }

    @Test
    public void testHashCodeTellsEveryRuleApart() {
        EnvDialect none = EnvDialect.DOCKER;
        EnvDialect all = none
                .withQuotesStripped(true)
                .withEscapesExpanded(true)
                .withExportPrefixStripped(true)
                .withInlineComments(true)
                .withMultilineValues(true)
                .withLineContinuation(true)
                .withBareNames(ERROR);
        assertNotEquals(none.hashCode(), all.hashCode());
        assertEquals(all.hashCode(), all.withQuotesStripped(true).hashCode());
    }

    @Test
    public void testToStringNamesTheRules() {
        String text = EnvDialect.DOTENV.toString();
        assertTrue(text, text.contains("dotenv"));
        assertTrue(text, text.contains("quotesStripped=true"));
        assertTrue(text, text.contains("bareNames=IGNORE"));
    }

    /** {@link Loader} is {@link java.io.Serializable}, so what a loader holds has to travel with it. */
    @Test
    public void testADialectSurvivesSerialization() throws IOException, ClassNotFoundException {
        EnvDialect original = EnvDialect.DOTENV.withLineContinuation(true).withBareNames(ERROR);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeObject(original);
        out.close();

        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        EnvDialect restored = (EnvDialect) in.readObject();
        in.close();

        assertEquals(original, restored);
        assertEquals(original.toString(), restored.toString());
    }

    @Test
    public void testALoaderSurvivesSerialization() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeObject(new DotEnvLoader(EnvDialect.DOTENV));
        out.close();

        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        Object restored = in.readObject();
        in.close();

        assertTrue(restored instanceof DotEnvLoader);
    }
}
