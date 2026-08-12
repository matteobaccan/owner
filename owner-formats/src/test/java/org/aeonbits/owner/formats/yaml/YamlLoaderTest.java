/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.yaml;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The subset of YAML this reads, one rule at a time, and each of the constructs it refuses.
 * <p>
 * The refusals matter as much as the readings: a subset that guessed at what it does not support would
 * change the meaning of a file rather than decline it, and a configuration whose meaning changed quietly
 * is the failure this whole format was hardest to justify against.
 * </p>
 *
 * @author Matteo Baccan
 */
public class YamlLoaderTest {

    private static Properties read(String... lines) throws IOException {
        File file = Files.createTempFile("owner-yaml", ".yaml").toFile();
        file.deleteOnExit();
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(String.join("\n", lines).getBytes(UTF_8));
        }
        Properties result = new Properties();
        new YamlLoader().load(result, file.toURI());
        return result;
    }

    private static String refused(String... lines) {
        try {
            read(lines);
            return null;
        } catch (IOException expected) {
            return expected.getMessage();
        }
    }

    // ---------------------------------------------------------------- what it answers for

    @Test
    public void itAnswersForBothNamesTheFormatGoesBy() throws URISyntaxException {
        assertTrue(new YamlLoader().accept(new URI("file:/app/config.yaml")));
        assertTrue(new YamlLoader().accept(new URI("file:/app/config.yml")));
        assertTrue(new YamlLoader().accept(new URI("file:/app/CONFIG.YAML")));
        assertFalse(new YamlLoader().accept(new URI("file:/app/config.json")));
    }

    @Test
    public void itLooksForBothNamesWhenNoSourcesAreDeclared() {
        assertEquals(2, new YamlLoader().defaultSpecsFor("classpath:MyConfig").length);
        assertEquals("classpath:MyConfig.yaml", new YamlLoader().defaultSpecsFor("classpath:MyConfig")[0]);
        assertEquals("classpath:MyConfig.yml", new YamlLoader().defaultSpecsFor("classpath:MyConfig")[1]);
    }

    // ---------------------------------------------------------------- structure

    @Test
    public void indentationBecomesTheKey() throws IOException {
        Properties props = read("server:", "  host: localhost", "  port: 8080");

        assertEquals("localhost", props.getProperty("server.host"));
        assertEquals("8080", props.getProperty("server.port"));
    }

    @Test
    public void itNestsAsDeepAsTheDocument() throws IOException {
        assertEquals("1", read("a:", "  b:", "    c:", "      d: 1").getProperty("a.b.c.d"));
    }

    @Test
    public void aSequenceIsNumberedFromZero() throws IOException {
        Properties props = read("hosts:", "  - alpha", "  - beta");

        assertEquals("alpha", props.getProperty("hosts[0]"));
        assertEquals("beta", props.getProperty("hosts[1]"));
    }

    /** The line every YAML configuration has, and the one worth being sure of. */
    @Test
    public void aMappingMayOpenOnTheSameLineAsItsDash() throws IOException {
        Properties props = read("servers:",
                "  - host: alpha",
                "    port: 1",
                "  - host: beta");

        assertEquals("alpha", props.getProperty("servers[0].host"));
        assertEquals("1", props.getProperty("servers[0].port"));
        assertEquals("beta", props.getProperty("servers[1].host"));
        assertNull(props.getProperty("servers[1].port"));
    }

    @Test
    public void aSequenceOfSequencesIsNumberedTwice() throws IOException {
        Properties props = read("pairs:", "  -", "    - a", "    - b");

        assertEquals("a", props.getProperty("pairs[0][0]"));
        assertEquals("b", props.getProperty("pairs[0][1]"));
    }

    @Test
    public void keysAtTheTopHaveNoPrefix() throws IOException {
        Properties props = read("name: owner", "server:", "  host: localhost");

        assertEquals("owner", props.getProperty("name"));
        assertEquals("localhost", props.getProperty("server.host"));
    }

    @Test
    public void aLeadingDocumentMarkerAndATrailingOneAreAllowed() throws IOException {
        assertEquals("1", read("---", "a: 1", "...").getProperty("a"));
    }

    // ---------------------------------------------------------------- scalars

    @Test
    public void aScalarIsKeptExactlyAsWritten() throws IOException {
        Properties props = read("a: 007", "b: 1e3", "c: yes", "d: no", "e: 2026-08-11");

        assertEquals("007", props.getProperty("a"));
        assertEquals("1e3", props.getProperty("b"));
        assertEquals("the Norway problem does not arise: the interface decides the type", "yes",
                props.getProperty("c"));
        assertEquals("no", props.getProperty("d"));
        assertEquals("2026-08-11", props.getProperty("e"));
    }

    @Test
    public void quotesDelimitAndAreNotPartOfTheValue() throws IOException {
        Properties props = read("a: \"one two\"", "b: 'one two'", "c: \"a\\nb\"", "d: 'it''s'",
                "e: \"\\u00e8\"");

        assertEquals("one two", props.getProperty("a"));
        assertEquals("one two", props.getProperty("b"));
        assertEquals("a\nb", props.getProperty("c"));
        assertEquals("a single quote has one escape and this is it", "it's", props.getProperty("d"));
        assertEquals("è", props.getProperty("e"));
    }

    @Test
    public void aCommentEndsAValueOnlyWhenItFollowsASpace() throws IOException {
        Properties props = read("# a whole line", "a: 1 # and the rest", "b: abc#123",
                "c: \"a # b\"");

        assertEquals("1", props.getProperty("a"));
        assertEquals("a hash inside a word is part of it", "abc#123", props.getProperty("b"));
        assertEquals("a # b", props.getProperty("c"));
    }

    @Test
    public void aColonInsideAValueIsNotASeparator() throws IOException {
        assertEquals("http://example.org/a", read("url: http://example.org/a").getProperty("url"));
    }

    // ---------------------------------------------------------------- block scalars

    @Test
    public void aLiteralBlockKeepsItsLineBreaks() throws IOException {
        Properties props = read("key: |", "  first", "  second", "other: 1");

        assertEquals("first\nsecond\n", props.getProperty("key"));
        assertEquals("and the block ends where the indentation does", "1", props.getProperty("other"));
    }

    @Test
    public void aFoldedBlockTurnsLineBreaksIntoSpaces() throws IOException {
        assertEquals("first second\n", read("key: >", "  first", "  second").getProperty("key"));
    }

    @Test
    public void theChompingIndicatorsDecideWhatHappensAtTheEnd() throws IOException {
        assertEquals("a", read("key: |-", "  a").getProperty("key"));
        assertEquals("a\n", read("key: |", "  a").getProperty("key"));
        assertEquals("a\n\n", read("key: |+", "  a").getProperty("key"));
    }

    @Test
    public void aBlockScalarKeepsWhatWouldOtherwiseBeSyntax() throws IOException {
        Properties props = read("script: |", "  - not a sequence", "  key: not a mapping", "  # not a comment");

        assertEquals("- not a sequence\nkey: not a mapping\n# not a comment\n", props.getProperty("script"));
    }

    // ---------------------------------------------------------------- flow style

    @Test
    public void aFlowSequenceIsASequence() throws IOException {
        Properties props = read("ports: [80, 443]");

        assertEquals("80", props.getProperty("ports[0]"));
        assertEquals("443", props.getProperty("ports[1]"));
    }

    @Test
    public void aFlowMappingIsAMapping() throws IOException {
        Properties props = read("env: {A: 1, B: two}");

        assertEquals("1", props.getProperty("env.A"));
        assertEquals("two", props.getProperty("env.B"));
    }

    /** A JSON document is valid YAML, which is the reason flow style is in the subset at all. */
    @Test
    public void whatIsWrittenAsJsonIsRead() throws IOException {
        Properties props = read("servers: [{\"host\": \"alpha\"}, {\"host\": \"beta\"}]");

        assertEquals("alpha", props.getProperty("servers[0].host"));
        assertEquals("beta", props.getProperty("servers[1].host"));
    }

    @Test
    public void aCommaInsideQuotesDoesNotSeparate() throws IOException {
        Properties props = read("a: [\"one, two\", three]");

        assertEquals("one, two", props.getProperty("a[0]"));
        assertEquals("three", props.getProperty("a[1]"));
    }

    // ---------------------------------------------------------------- absent values

    @Test
    public void thereAreThreeWaysToWriteNothingAndNoneOfThemWritesAKey() throws IOException {
        Properties props = read("a:", "b: ~", "c: null", "d: 1");

        assertFalse(props.containsKey("a"));
        assertFalse(props.containsKey("b"));
        assertFalse(props.containsKey("c"));
        assertEquals("and none of them stops what follows being read", "1", props.getProperty("d"));
    }

    @Test
    public void anEmptyFlowSequenceIsAnEmptyValue() throws IOException {
        Properties props = read("a: []", "b: {}");

        assertEquals("", props.getProperty("a"));
        assertFalse(props.containsKey("b"));
    }

    // ---------------------------------------------------------------- what it refuses

    @Test
    public void anAnchorOrAnAliasIsRefused() {
        assertTrue(String.valueOf(refused("a: &base 1")), refused("a: &base 1").contains("anchor"));
        assertTrue(String.valueOf(refused("a: *base")), refused("a: *base").contains("anchor"));
    }

    @Test
    public void aMergeKeyIsRefused() {
        String message = refused("a:", "  <<: *base", "  b: 1");

        assertTrue(String.valueOf(message), message.contains("merge key"));
        assertTrue("and it says what to do instead: " + message, message.contains("MERGE"));
    }

    @Test
    public void aTagIsRefused() {
        assertTrue(String.valueOf(refused("a: !!str 1")), refused("a: !!str 1").contains("tag"));
    }

    @Test
    public void aComplexKeyIsRefused() {
        assertTrue(String.valueOf(refused("? a", ": 1")), refused("? a", ": 1").contains("complex key"));
    }

    @Test
    public void aSecondDocumentIsRefused() {
        String message = refused("a: 1", "---", "b: 2");

        assertTrue(String.valueOf(message), message.contains("second document"));
        assertTrue(String.valueOf(message), message.contains("Line 2"));
    }

    /**
     * The one that has to be an error rather than a guess: a value continued on the next line looks
     * exactly like a nested block, and reading it either way silently would be wrong half the time.
     */
    @Test
    public void aValueContinuedOnTheNextLineIsRefused() {
        String message = refused("description: some long", "  text");

        assertTrue(String.valueOf(message), message.contains("'|'"));
        assertTrue(String.valueOf(message), message.contains("'>'"));
    }

    @Test
    public void aTabUsedForIndentationIsRefused() {
        String message = refused("a:", "\tb: 1");

        assertTrue(String.valueOf(message), message.contains("tab"));
        assertTrue(String.valueOf(message), message.contains("Line 2"));
    }

    @Test
    public void aNameGivenTwiceInTheSameBlockIsRefused() {
        assertTrue(String.valueOf(refused("a: 1", "a: 2")), refused("a: 1", "a: 2").contains("twice"));
    }

    @Test
    public void theSameNameUnderTwoBlocksIsNotARepeat() throws IOException {
        Properties props = read("a:", "  host: 1", "b:", "  host: 2");

        assertEquals("1", props.getProperty("a.host"));
        assertEquals("2", props.getProperty("b.host"));
    }

    @Test
    public void aNameWithNoColonIsRefused() {
        assertTrue(String.valueOf(refused("lonely")), refused("lonely").contains("colon"));
    }

    @Test
    public void anUnclosedFlowCollectionIsRefused() {
        assertTrue(String.valueOf(refused("a: [1, 2")), refused("a: [1, 2").contains("never closed"));
    }

    @Test
    public void aDocumentThatBeginsIndentedIsRefused() {
        assertTrue(String.valueOf(refused("  a: 1")), refused("  a: 1").contains("indented"));
    }

    @Test
    public void everyComplaintSaysWhichLineItIsOn() {
        String message = refused("a: 1", "b: 2", "c: !!str 3");

        assertTrue(String.valueOf(message), message.contains("Line 3"));
    }

    @Test
    public void anEmptyDocumentIsAnEmptyConfiguration() throws IOException {
        assertTrue(read("").isEmpty());
        assertTrue(read("# nothing but a comment").isEmpty());
    }

    @Test
    public void aByteOrderMarkIsNotPartOfTheDocument() throws IOException {
        assertEquals("1", read("\uFEFFa: 1").getProperty("a"));
    }
    // ------------------------------------------------- the refusals nothing reached yet

    /**
     * The lines below are each the only way into a complaint this parser can make. They were written
     * against the coverage report rather than from imagination: a refusal that has never been executed is
     * a refusal nobody has read, and the message is the whole of what it does.
     */

    // Two complaints in YamlParser have no test and are left without one deliberately, both being
    // shadowed by a more specific check that fires first: "this line is outside everything above it"
    // (a block at column 0 consumes the rest of the file, and a '...' marker does not end it) and
    // "indented further than the one above it" (the value-on-the-previous-line check gets there first).
    // They may well be unreachable. Contorting a document into them would prove nothing about either.

    @Test
    public void aSequenceItemWhereANameWasExpectedIsRefused() {
        assertTrue(refused("a: 1", "- b").contains("sequence item where a name was expected"));
    }

    @Test
    public void anEmptyNameIsRefused() {
        assertTrue(refused("\"\": 1").contains("a name here is empty"));
    }

    @Test
    public void anItemIndentedFurtherThanTheOneAboveItIsRefused() {
        assertTrue(refused("a:", "  - one", "      - two").contains("indented further than the item above"));
    }

    @Test
    public void aBlockScalarWithSomethingOtherThanPlusOrMinusIsRefused() {
        assertTrue(refused("a: >x", "  text").contains("only '+' to keep the blank lines"));
    }

    @Test
    public void aFlowMappingEntryWithoutAColonIsRefused() {
        assertTrue(refused("a: {b}").contains("needs a colon"));
    }

    // ------------------------------------------------------- the escapes in a quoted scalar

    @Test
    public void everyEscapeADoubleQuotedScalarKnows() throws IOException {
        Properties read = read(
                "newline: \"a\\nb\"",
                "tab: \"a\\tb\"",
                "carriageReturn: \"a\\rb\"",
                "backspace: \"a\\bb\"",
                "formFeed: \"a\\fb\"",
                "nul: \"a\\0b\"",
                "quote: \"a\\\"b\"",
                "backslash: \"a\\\\b\"");
        assertEquals("a\nb", read.getProperty("newline"));
        assertEquals("a\tb", read.getProperty("tab"));
        assertEquals("a\rb", read.getProperty("carriageReturn"));
        assertEquals("a\bb", read.getProperty("backspace"));
        assertEquals("a\fb", read.getProperty("formFeed"));
        assertEquals("a\0b", read.getProperty("nul"));
        assertEquals("a\"b", read.getProperty("quote"));
        assertEquals("a\\b", read.getProperty("backslash"));
    }

    @Test
    public void anEscapeThisParserDoesNotKnowIsKeptAsWritten() throws IOException {
        // not a refusal on purpose: YAML has escapes we do not read, and handing the text back unchanged
        // is the reading least likely to be wrong about what the author meant
        assertEquals("a\\qb", read("a: \"a\\qb\"").getProperty("a"));
    }

    @Test
    public void aUnicodeEscapeIsResolved() throws IOException {
        assertEquals("\u00E9\u20AC", read("a: \"\\u00E9\\u20AC\"").getProperty("a"));
    }

    @Test
    public void aUnicodeEscapeCutShortIsRefused() {
        assertTrue(refused("a: \"\\u00\"").contains("needs four hexadecimal digits"));
    }

    @Test
    public void aUnicodeEscapeThatIsNotHexadecimalIsRefused() {
        assertTrue(refused("a: \"\\uZZZZ\"").contains("is not four hexadecimal digits"));
    }
}
