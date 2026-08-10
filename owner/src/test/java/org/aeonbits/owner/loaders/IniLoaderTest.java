/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The rules of {@link IniLoader}, one at a time. There is no INI standard, so every one of these is a
 * decision rather than a fact, and {@code FORMATS.md} records what the field does about each.
 *
 * @author Matteo Baccan
 */
public class IniLoaderTest {

    // ---------------------------------------------------------------- what it answers for

    @Test
    public void itAnswersForBothNamesTheFormatGoesBy() throws URISyntaxException {
        assertTrue(new IniLoader().accept(new URI("file:/app/config.ini")));
        assertTrue(new IniLoader().accept(new URI("file:/app/config.cfg")));
        assertTrue(new IniLoader().accept(new URI("file:/app/CONFIG.INI")));
        assertTrue(new IniLoader().accept(new URI("http://host/app.ini?v=2#dialect=git")));
        assertFalse(new IniLoader().accept(new URI("file:/app/config.properties")));
    }

    /** The first loader in the tree to need more than one default name, which is what C5 was built for. */
    @Test
    public void itLooksForBothNamesWhenNoSourcesAreDeclared() {
        assertEquals(2, new IniLoader().defaultSpecsFor("classpath:MyConfig").length);
        assertEquals("classpath:MyConfig.ini", new IniLoader().defaultSpecsFor("classpath:MyConfig")[0]);
        assertEquals("classpath:MyConfig.cfg", new IniLoader().defaultSpecsFor("classpath:MyConfig")[1]);
    }

    // ---------------------------------------------------------------- sections become the prefix

    @Test
    public void aSectionIsThePrefixOfTheKeysBelowIt() throws IOException {
        Properties props = read("[server]", "host = localhost", "port = 8080");

        assertEquals("localhost", props.getProperty("server.host"));
        assertEquals("8080", props.getProperty("server.port"));
    }

    @Test
    public void keysBeforeAnySectionHaveNoPrefix() throws IOException {
        Properties props = read("name = owner", "[server]", "host = localhost");

        assertEquals("owner", props.getProperty("name"));
        assertEquals("localhost", props.getProperty("server.host"));
    }

    /** A dotted section needs no rule of its own: it is already the nesting separator. */
    @Test
    public void aDottedSectionIsAlreadyNested() throws IOException {
        assertEquals("localhost", read("[server.http]", "host = localhost").getProperty("server.http.host"));
    }

    @Test
    public void aSectionMetTwiceIsOneSection() throws IOException {
        Properties props = read("[server]", "host = localhost", "[other]", "a = 1", "[server]", "port = 80");

        assertEquals("localhost", props.getProperty("server.host"));
        assertEquals("80", props.getProperty("server.port"));
    }

    // ---------------------------------------------------------------- a repeated key is a list

    /** The same answer the XML loader gives to a repeated element, and for the same reason. */
    @Test
    public void aRepeatedKeyBecomesAList() throws IOException {
        Properties props = read("[servers]", "host = alpha", "host = beta", "host = gamma");

        assertEquals("alpha", props.getProperty("servers.host[0]"));
        assertEquals("beta", props.getProperty("servers.host[1]"));
        assertEquals("gamma", props.getProperty("servers.host[2]"));
        assertNull("the plain key goes when the second occurrence arrives", props.getProperty("servers.host"));
    }

    @Test
    public void aKeyThatOccursOnceKeepsItsPlainKey() throws IOException {
        Properties props = read("[servers]", "host = alpha");

        assertEquals("alpha", props.getProperty("servers.host"));
        assertNull(props.getProperty("servers.host[0]"));
    }

    /** The same name under two sections is two keys, not a repeat. */
    @Test
    public void theSameNameInTwoSectionsIsNotARepeat() throws IOException {
        Properties props = read("[a]", "host = one", "[b]", "host = two");

        assertEquals("one", props.getProperty("a.host"));
        assertEquals("two", props.getProperty("b.host"));
    }

    @Test
    public void theOtherAnswersToARepeatedKeyAreAvailable() throws IOException {
        String[] lines = {"[s]", "host = alpha", "host = beta"};

        assertEquals("alpha", read(IniDialect.INI.withDuplicates(IniDialect.Duplicates.FIRST), lines)
                .getProperty("s.host"));
        assertEquals("beta", read(IniDialect.INI.withDuplicates(IniDialect.Duplicates.LAST), lines)
                .getProperty("s.host"));
        try {
            read(IniDialect.INI.withDuplicates(IniDialect.Duplicates.ERROR), lines);
            fail("that dialect refuses a repeated key");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("s.host"));
        }
    }

    // ---------------------------------------------------------------- comments and separators

    @Test
    public void bothCommentCharactersWorkAtTheStartOfALine() throws IOException {
        Properties props = read("# a hash comment", "; a semicolon comment", "a = 1");

        assertEquals(1, props.size());
        assertEquals("1", props.getProperty("a"));
    }

    /**
     * Off by default, and this is why: a password is the value most likely to hold a hash, and losing half
     * of it silently is worse than keeping a comment somebody meant.
     */
    @Test
    public void aCommentAfterAValueIsPartOfItUnlessAsked() throws IOException {
        assertEquals("abc#123", read("password = abc#123").getProperty("password"));
        assertEquals("abc", read(IniDialect.INI.withInlineComments(true), "password = abc # 123")
                .getProperty("password"));
    }

    @Test
    public void onlyTheFirstSeparatorCounts() throws IOException {
        assertEquals("http://example.org/a=b", read("url = http://example.org/a=b").getProperty("url"));
    }

    @Test
    public void aColonSeparatesOnlyWhenAsked() throws IOException {
        assertEquals("10:30", read("time = 10:30").getProperty("time"));

        Properties python = read(IniDialect.INI.withSeparator(IniDialect.Separator.EQUALS_OR_COLON),
                "time : 10");
        assertEquals("10", python.getProperty("time"));
    }

    // ---------------------------------------------------------------- what is refused

    @Test
    public void aNameWithNoSeparatorIsRefused() throws IOException {
        try {
            read("[s]", "lonely");
            fail("a line that is neither a comment nor an assignment is malformed");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Line 2"));
            assertTrue(e.getMessage(), e.getMessage().contains("lonely"));
        }
    }

    @Test
    public void aNameWithNoSeparatorCanBeIgnoredOrReadAsTrue() throws IOException {
        assertTrue(read(IniDialect.INI.withBareKeys(IniDialect.BareKeys.IGNORE), "lonely").isEmpty());
        assertEquals("true", read(IniDialect.INI.withBareKeys(IniDialect.BareKeys.TRUE), "lonely")
                .getProperty("lonely"));
    }

    @Test
    public void anUnclosedSectionIsRefused() throws IOException {
        try {
            read("[server", "a = 1");
            fail("a section header has to close");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("never closes"));
        }
    }

    @Test
    public void aSectionWithNoNameIsRefused() throws IOException {
        try {
            read("[]", "a = 1");
            fail("a section needs a name");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no name"));
        }
    }

    @Test
    public void anAssignmentToNothingIsRefused() throws IOException {
        try {
            read("= 1");
            fail("there is no key there");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("empty name"));
        }
    }

    // ---------------------------------------------------------------- the git dialect

    @Test
    public void gitReadsASubsectionAsTheNameGitItselfPrints() throws IOException {
        Properties props = read(IniDialect.GIT, "[remote \"origin\"]", "url = git@github.com:owner.git");

        assertEquals("git@github.com:owner.git", props.getProperty("remote.origin.url"));
    }

    @Test
    public void gitDelimitsWithQuotesAndExpandsEscapesInside() throws IOException {
        Properties props = read(IniDialect.GIT, "[user]", "name = \"Matteo\"", "note = \"a\\nb\"");

        assertEquals("Matteo", props.getProperty("user.name"));
        assertEquals("a\nb", props.getProperty("user.note"));
    }

    @Test
    public void gitContinuesALineEndingInABackslash() throws IOException {
        Properties props = read(IniDialect.GIT, "[alias]", "lg = log --graph \\", "--oneline");

        assertEquals("log --graph --oneline", props.getProperty("alias.lg"));
    }

    @Test
    public void gitReadsABareKeyAsTrue() throws IOException {
        assertEquals("true", read(IniDialect.GIT, "[core]", "bare").getProperty("core.bare"));
    }

    /** Quotes are part of the value under the default dialect, as they are for docker in a .env. */
    @Test
    public void theDefaultDialectKeepsTheQuotes() throws IOException {
        assertEquals("\"Matteo\"", read("[user]", "name = \"Matteo\"").getProperty("user.name"));
    }

    // ---------------------------------------------------------------- the python dialect

    @Test
    public void pythonFoldsTheKeysButNotTheSections() throws IOException {
        Properties props = read(IniDialect.PYTHON, "[Server]", "MaxThreads = 10");

        assertEquals("10", props.getProperty("Server.maxthreads"));
        assertNull(props.getProperty("Server.MaxThreads"));
    }

    @Test
    public void pythonContinuesAValueThatIsIndentedFurther() throws IOException {
        Properties props = read(IniDialect.PYTHON, "[song]", "chorus = one", "    two", "    three");

        assertEquals("one\ntwo\nthree", props.getProperty("song.chorus"));
    }

    /** The DEFAULT section supplies what a section does not say for itself, and never overrides it. */
    @Test
    public void pythonLetsEverySectionInheritTheDefaultOne() throws IOException {
        Properties props = read(IniDialect.PYTHON,
                "[DEFAULT]", "timeout = 30", "retries = 3",
                "[a]", "timeout = 5",
                "[b]", "host = x");

        assertEquals("what the section says wins", "5", props.getProperty("a.timeout"));
        assertEquals("3", props.getProperty("a.retries"));
        assertEquals("30", props.getProperty("b.timeout"));
        assertEquals("3", props.getProperty("b.retries"));
        assertNull("DEFAULT is not a section of its own here", props.getProperty("DEFAULT.timeout"));
    }

    /**
     * The one thing that cannot be honoured. Passing it through as a literal would be the same file meaning
     * one thing to Python and another here, silently, which is what refusing exists to prevent.
     */
    @Test
    public void pythonRefusesAValueItWouldHaveInterpolated() throws IOException {
        try {
            read(IniDialect.PYTHON, "[paths]", "home = /srv", "log = %(home)s/log");
            fail("OWNER does not interpolate, and must not pretend the value is literal");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("${"));
            assertTrue(e.getMessage(), e.getMessage().contains("python"));
        }
    }

    @Test
    public void aValueWithPercentIsFineUnderTheOtherDialects() throws IOException {
        assertEquals("%(home)s/log", read("[paths]", "log = %(home)s/log").getProperty("paths.log"));
    }

    @Test
    public void pythonRefusesARepeatedKey() throws IOException {
        try {
            read(IniDialect.PYTHON, "[s]", "a = 1", "a = 2");
            fail("configparser is strict by default and so is this dialect");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("repeats the key"));
        }
    }

    // ---------------------------------------------------------------- odds and ends

    @Test
    public void aByteOrderMarkIsNotPartOfTheFirstKey() throws IOException {
        assertEquals("1", read("﻿a = 1").getProperty("a"));
    }

    @Test
    public void whitespaceAroundTheSeparatorIsNotPartOfEitherSide() throws IOException {
        Properties props = read("[s]", "   a    =    1   ");

        assertEquals("1", props.getProperty("s.a"));
    }

    @Test
    public void anEmptyValueIsAValue() throws IOException {
        assertEquals("", read("[s]", "a =").getProperty("s.a"));
    }

    @Test
    public void anUnknownDialectSaysWhichOnesExist() {
        try {
            IniDialect.named("windows");
            fail("there is no such dialect");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("'ini', 'git' and 'python'"));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Properties read(String... lines) throws IOException {
        return read(IniDialect.INI, lines);
    }

    private static Properties read(IniDialect dialect, String... lines) throws IOException {
        Properties result = new Properties();
        new IniLoader(dialect).load(result, write(lines).toURI());
        return result;
    }

    private static File write(String... lines) throws IOException {
        File file = Files.createTempFile("owner-ini", ".ini").toFile();
        file.deleteOnExit();
        StringBuilder text = new StringBuilder();
        for (String line : lines)
            text.append(line).append('\n');
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.toString().getBytes("UTF-8"));
        }
        return file;
    }
}
