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
import java.nio.file.Files;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The corners of {@link YamlParser} that {@link YamlLoaderTest} leaves alone.
 * <p>
 * That class reads the subset one rule at a time. This one takes the paths a file only reaches by being
 * <b>wrong in a particular way</b>, or by being written on a platform other than the one the test suite
 * runs on — the branches that exist so that a bad file is declined rather than half-read, and that
 * therefore nothing exercises until somebody writes a bad file.
 * </p>
 *
 * @author Matteo Baccan
 */
public class YamlParserEdgesTest {

    private static Properties read(String... lines) throws IOException {
        return readJoinedWith("\n", lines);
    }

    private static Properties readJoinedWith(String newline, String... lines) throws IOException {
        File file = Files.createTempFile("owner-yaml-edges", ".yaml").toFile();
        file.deleteOnExit();
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(String.join(newline, lines).getBytes(UTF_8));
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

    // ---------------------------------------------------------------- the file as it arrives

    /**
     * A document written on Windows. The reader strips the carriage return itself rather than leaving it
     * on the end of every value, which is the difference between <code>localhost</code> and
     * <code>localhost\r</code> — a value that looks right everywhere it is printed and matches nothing.
     */
    @Test
    public void aDocumentWithWindowsLineEndingsReadsTheSame() throws IOException {
        Properties windows = readJoinedWith("\r\n", "server:", "  host: localhost", "  port: 9090");
        assertEquals("localhost", windows.getProperty("server.host"));
        assertEquals("9090", windows.getProperty("server.port"));
    }

    // ---------------------------------------------------------------- document start & indentation

    /**
     * A document whose first line is indented is refused, because nothing says what it is indented under.
     */
    @Test
    public void aDocumentThatBeginsIndentedIsRefused() {
        String said = refused("  name: owner");
        assertNotNull(said);
        assertTrue(said, said.contains("the document begins indented"));
    }

    /**
     * A tab used as indentation is refused rather than counted.
     */
    @Test
    public void tabsUsedAsIndentationAreRefused() {
        String said = refused("\thost: localhost");
        assertNotNull(said);
        assertTrue(said, said.contains("a tab is used to indent this line"));
    }

    // ---------------------------------------------------------------- indentation that says nothing

    /**
     * A line indented under a name that already has a value. YAML has a way of spelling a value that
     * spans lines — <code>|</code> and <code>&gt;</code> — and the message names both, because that is
     * what the author was reaching for and the two do different things.
     */
    @Test
    public void aLineIndentedUnderAValueIsRefused() {
        String said = refused("name: owner", "    continued here");
        assertNotNull(said);
        assertTrue(said, said.contains("already has a value on it"));
        assertTrue(said, said.contains("'|'"));
        assertTrue(said, said.contains("'>'"));
    }

    /**
     * A different message for a different mistake: an indent that is deeper than the block it lands in
     * but shallower than the one it came after, so it belongs to neither. Nothing on the line above it
     * has a value, the nested block having already closed, which is what tells the two messages apart.
     */
    @Test
    public void aLineAtAnIndentNoBlockHasIsRefused() {
        String said = refused("a:", "  b:", "    c: 1", "   d: 2");
        assertNotNull(said);
        assertTrue(said, said.contains("indented further than the one above it"));
        assertTrue(said, said.contains("nothing here opens a block"));
    }

    /** The same inside a sequence, where the message is the sequence's own. */
    @Test
    public void aLineIndentedUnderASequenceItemIsRefused() {
        String said = refused("hosts:", "  - alpha", "      continued here");
        assertNotNull(said);
        assertTrue(said, said.contains("indented further than the item above it"));
    }

    /**
     * A line that is level with everything and belongs to nothing — reached by closing a block and then
     * writing at an indent no open block has.
     */
    @Test
    public void aLineOutsideEverythingAboveItIsRefused() {
        // a document that *is* a sequence ends at the first line that is not an item, and then there is
        // nothing left open for that line to belong to
        String said = refused("- alpha", "- beta", "name: owner");
        assertNotNull(said);
        assertTrue(said, said.contains("outside everything above it"));
    }

    /**
     * An item with nothing on it and nothing under it. The message is the one the caller passes in, which
     * is how the same check serves a mapping and a sequence with a sentence each.
     */
    @Test
    public void anItemWithNothingUnderItIsRefused() {
        String said = refused("hosts:", "  -");
        assertNotNull(said);
        assertTrue(said, said.contains("needs something indented under it"));
    }

    // ---------------------------------------------------------------- where a block stops

    /**
     * A document marker ends the block it is written under, rather than being read as a value of it. The
     * second document is then refused by the check at the end — one configuration is one document — but
     * the block has to stop cleanly first, or the marker would arrive as a key.
     */
    @Test
    public void aDocumentMarkerEndsTheBlockAboveIt() {
        String underAMapping = refused("server:", "  host: localhost", "---", "other: document");
        assertNotNull(underAMapping);
        assertTrue(underAMapping, underAMapping.contains("a second document begins here"));

        String underASequence = refused("hosts:", "  - alpha", "---", "other: document");
        assertNotNull(underASequence);
        assertTrue(underASequence, underASequence.contains("a second document begins here"));
    }

    /**
     * A sequence ends at the first line that is not an item, and the mapping it sits in carries on. Not a
     * refusal: it is how a list is followed by another key.
     */
    @Test
    public void aSequenceEndsAtTheFirstLineThatIsNotAnItem() throws IOException {
        Properties p = read("hosts:", "  - alpha", "  - beta", "port: 9090");
        assertEquals("alpha", p.getProperty("hosts[0]"));
        assertEquals("beta", p.getProperty("hosts[1]"));
        assertEquals("9090", p.getProperty("port"));
    }

    // ---------------------------------------------------------------- block scalars

    /**
     * A block scalar ends when the indentation drops back, and the blank lines at the end of it are not
     * part of the value — which is what makes a file with a trailing empty line mean the same as one
     * without.
     */
    @Test
    public void aBlockScalarStopsWhenTheIndentationDropsAndLosesItsTrailingBlanks() throws IOException {
        Properties p = read(
                "banner: |",
                "    first",
                "    second",
                "",
                "",
                "port: 9090");
        assertEquals("first\nsecond\n", p.getProperty("banner"));
        assertEquals("9090", p.getProperty("port"));
    }

    /**
     * A folded scalar joins its lines with a space, except across a blank one, where the break is kept.
     * That is the whole of what folding means and it needs three lines to show.
     */
    @Test
    public void aFoldedScalarKeepsTheBreakAcrossABlankLine() throws IOException {
        Properties p = read(
                "text: >",
                "    one",
                "    two",
                "",
                "    four");
        // the blank line contributes a break of its own, so there are two: the one that ends "two"
        // and the one that ends the blank. That is the folding rule and not an accident
        assertEquals("one two\n\nfour\n", p.getProperty("text"));
    }

    // ---------------------------------------------------------------- null, three ways

    /**
     * The three spellings of nothing. All of them write no key at all, so a
     * <code>@DefaultValue</code> stands — which is stated on the site and is the behaviour that has to
     * hold for each of the three separately.
     */
    @Test
    public void allThreeSpellingsOfNullWriteNoKey() throws IOException {
        Properties p = read("a:", "b: ~", "c: null", "d: value");
        assertNull(p.getProperty("a"));
        assertNull(p.getProperty("b"));
        assertNull(p.getProperty("c"));
        assertEquals("value", p.getProperty("d"));
    }

    // ---------------------------------------------------------------- flow style

    /** A comma after the last item of a flow list, which is legal in JSON5 and in no version of YAML. */
    @Test
    public void aTrailingCommaInAFlowListIsRefused() {
        String said = refused("hosts: [alpha, beta,]");
        assertNotNull(said);
        assertTrue(said, said.contains("comma after the last item"));
        assertTrue(said, said.contains("list"));
    }

    /** The same in a flow mapping, where the message says block rather than list. */
    @Test
    public void aTrailingCommaInAFlowMappingIsRefused() {
        String said = refused("server: {host: alpha, port: 80,}");
        assertNotNull(said);
        assertTrue(said, said.contains("comma after the last item"));
    }

    /** A flow collection nested in a flow collection: the depth counter is what keeps the commas apart. */
    @Test
    public void aFlowCollectionInsideAFlowCollectionIsReadByDepth() throws IOException {
        Properties p = read("grid: [[a, b], [c, d]]");
        assertEquals("a", p.getProperty("grid[0][0]"));
        assertEquals("b", p.getProperty("grid[0][1]"));
        assertEquals("c", p.getProperty("grid[1][0]"));
        assertEquals("d", p.getProperty("grid[1][1]"));
    }

    // ---------------------------------------------------------------- unsupported features & mapping errors

    /**
     * YAML features that this subset does not read — merge keys, complex keys, anchors/aliases, and tags —
     * are refused by name with an explanation.
     */
    @Test
    public void unsupportedYamlFeaturesAreRefused() {
        String mergeKey = refused("<<: *defaults");
        assertNotNull(mergeKey);
        assertTrue(mergeKey, mergeKey.contains("a merge key is not read"));

        String complexKey = refused("? key: value");
        assertNotNull(complexKey);
        assertTrue(complexKey, complexKey.contains("a complex key is not read"));

        String anchor = refused("&anchor value");
        assertNotNull(anchor);
        assertTrue(anchor, anchor.contains("an anchor or an alias is not read"));

        String alias = refused("*alias");
        assertNotNull(alias);
        assertTrue(alias, alias.contains("an anchor or an alias is not read"));

        String tag = refused("!tag value");
        assertNotNull(tag);
        assertTrue(tag, tag.contains("a tag is not read"));
    }

    /**
     * Mapping syntax errors — empty keys, duplicate keys, missing colons, or a sequence item where a key
     * name was expected — are refused with descriptive error messages.
     */
    @Test
    public void mappingSyntaxErrorsAreRefused() {
        String emptyKey = refused(": value");
        assertNotNull(emptyKey);
        assertTrue(emptyKey, emptyKey.contains("a name here is empty"));

        String duplicateKey = refused("key: 1", "key: 2");
        assertNotNull(duplicateKey);
        assertTrue(duplicateKey, duplicateKey.contains("the name 'key' is given twice in the same block"));

        String missingColon = refused("key value");
        assertNotNull(missingColon);
        assertTrue(missingColon, missingColon.contains("a name here needs a colon after it"));

        String seqInMapping = refused("key: 1", "- item");
        assertNotNull(seqInMapping);
        assertTrue(seqInMapping, seqInMapping.contains("a sequence item where a name was expected"));
    }

    // ---------------------------------------------------------------- block scalars & chomping

    /**
     * Block scalar chomping indicators: '+' keeps trailing newlines, '-' strips them, and invalid chomping
     * indicators are refused.
     */
    @Test
    public void blockScalarChompingModifiersWork() throws IOException {
        Properties keep = read(
                "text: |+",
                "    line1",
                "    line2",
                "",
                "");
        assertEquals("line1\nline2\n\n", keep.getProperty("text"));

        Properties strip = read(
                "text: |-",
                "    line1",
                "    line2",
                "",
                "");
        assertEquals("line1\nline2", strip.getProperty("text"));

        String invalid = refused("text: |*");
        assertNotNull(invalid);
        assertTrue(invalid, invalid.contains("only '+' to keep the blank lines at the end and '-' to strip them"));
    }

    // ---------------------------------------------------------------- quoting and comments

    /**
     * Quoted scalar escape sequences in double quotes and single quotes, as well as malformed unicode escapes.
     */
    @Test
    public void escapesInDoubleAndSingleQuotedStrings() throws IOException {
        Properties p = read(
                "escaped: \"\\n\\t\\r\\b\\f\\0\\\"\\\\\\u0041\\z\"",
                "single: 'It''s single'");
        assertEquals("\n\t\r\b\f\0\"\\A\\z", p.getProperty("escaped"));
        assertEquals("It's single", p.getProperty("single"));

        String shortHex = refused("val: \"\\u004\"");
        assertNotNull(shortHex);
        assertTrue(shortHex, shortHex.contains("a \\u escape needs four hexadecimal digits"));

        String invalidHex = refused("val: \"\\u000G\"");
        assertNotNull(invalidHex);
        assertTrue(invalidHex, invalidHex.contains("'000G' is not four hexadecimal digits"));
    }

    /**
     * Flow style error cases: unclosed collections and missing colons inside flow mappings.
     */
    @Test
    public void flowStyleErrorCasesAreRefused() {
        String unclosed = refused("val: [1, 2");
        assertNotNull(unclosed);
        assertTrue(unclosed, unclosed.contains("this collection is never closed"));

        String missingColon = refused("val: {a}");
        assertNotNull(missingColon);
        assertTrue(missingColon, missingColon.contains("'a' needs a colon: it is inside braces"));
    }

    /**
     * A quoted scalar whose closing quote is missing is not a quoted scalar: it is read as the plain text
     * it is, quote included, rather than being silently completed.
     */
    @Test
    public void aScalarWithNoClosingQuoteIsReadAsPlainText() throws IOException {
        Properties p = read("name: \"unfinished");
        assertEquals("\"unfinished", p.getProperty("name"));
    }

    /**
     * A <code>#</code> begins a comment only after a space, so a value may hold one — which is what a
     * colour, a fragment or an anchor in a URL needs.
     */
    @Test
    public void aHashIsOnlyACommentAfterASpace() throws IOException {
        Properties p = read("colour: '#ff0000'", "url: http://host/page#section", "port: 80 # the http one");
        assertEquals("#ff0000", p.getProperty("colour"));
        assertEquals("http://host/page#section", p.getProperty("url"));
        assertEquals("80", p.getProperty("port"));
    }

    /**
     * A colon separates a key from a value only when a space follows it, so a value may hold one and so
     * may a URL — the case this rule exists for.
     */
    @Test
    public void aColonSeparatesOnlyWhenASpaceFollowsIt() throws IOException {
        Properties p = read("url: http://host:8080/path", "ratio: 16:9");
        assertEquals("http://host:8080/path", p.getProperty("url"));
        assertEquals("16:9", p.getProperty("ratio"));
    }
}
