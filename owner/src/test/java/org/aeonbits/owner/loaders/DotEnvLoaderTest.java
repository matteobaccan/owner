/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import org.aeonbits.owner.util.LogCapture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.aeonbits.owner.util.Util.system;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link DotEnvLoader}: which sources it answers for, and how each {@link EnvDialect} reads the same
 * file differently.
 *
 * @author Matteo Baccan
 */
public class DotEnvLoaderTest {

    private static final Logger LOADER_LOG = Logger.getLogger(DotEnvLoader.class.getName());

    private Level originalLevel;
    private boolean originalUseParentHandlers;
    private LogCapture capture;

    /**
     * Several tests below read a quoted value under a dialect that keeps the quotes, which the loader is meant
     * to warn about. That is the subject of its own tests; everywhere else it is only noise on the build output.
     */
    @Before
    public void silenceTheLoaderLog() {
        originalLevel = LOADER_LOG.getLevel();
        originalUseParentHandlers = LOADER_LOG.getUseParentHandlers();
        LOADER_LOG.setLevel(Level.OFF);
        LOADER_LOG.setUseParentHandlers(false);
    }

    @After
    public void restoreTheLoaderLog() {
        if (capture != null) {
            capture.close();
            capture = null;
        }
        LOADER_LOG.setLevel(originalLevel);
        LOADER_LOG.setUseParentHandlers(originalUseParentHandlers);
    }

    /** Collects what the loader logs, without letting it reach the console. */
    private LogCapture recordLog() {
        capture = LogCapture.of(DotEnvLoader.class, Level.ALL);
        return capture;
    }

    private static File writeEnv(String... lines) throws IOException {
        return writeEnvSeparatedBy("\n", lines);
    }

    private static File writeEnvSeparatedBy(String lineSeparator, String... lines) throws IOException {
        File file = Files.createTempFile("owner-dotenv", ".env").toFile();
        file.deleteOnExit();
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.write(lineSeparator);
            }
        }
        return file;
    }

    private static Properties read(EnvDialect dialect, String... lines) throws IOException {
        Properties result = new Properties();
        new DotEnvLoader(dialect).load(result, writeEnv(lines).toURI());
        return result;
    }

    private static Properties readWithOptions(String options, String... lines) throws IOException, URISyntaxException {
        Properties result = new Properties();
        new DotEnvLoader().load(result, new URI(writeEnv(lines).toURI() + "#" + options));
        return result;
    }

    // ---------------------------------------------------------------- accept

    @Test
    public void testAcceptsAFileThatIsAllExtension() throws URISyntaxException {
        assertTrue(new DotEnvLoader().accept(new URI("file:/app/.env")));
    }

    @Test
    public void testAcceptsANamedEnvFile() throws URISyntaxException {
        assertTrue(new DotEnvLoader().accept(new URI("file:/app/local.env")));
    }

    @Test
    public void testAcceptIsCaseInsensitive() throws URISyntaxException {
        assertTrue(new DotEnvLoader().accept(new URI("file:/app/Local.ENV")));
    }

    @Test
    public void testAcceptsWhenTheDialectIsInTheFragment() throws URISyntaxException {
        assertTrue(new DotEnvLoader().accept(new URI("file:/app/.env#dialect=dotenv")));
    }

    @Test
    public void testDoesNotAcceptOtherFormats() throws URISyntaxException {
        DotEnvLoader loader = new DotEnvLoader();
        assertFalse(loader.accept(new URI("file:/app/config.properties")));
        assertFalse(loader.accept(new URI("file:/app/config.xml")));
        assertFalse(loader.accept(new URI("file:/app/environment")));
    }

    /** {@code system:env} is the {@link SystemLoader}'s, and must not be taken by the one whose suffix it nearly is. */
    @Test
    public void testDoesNotAcceptSystemEnv() throws URISyntaxException {
        assertFalse(new DotEnvLoader().accept(new URI("system:env")));
    }

    @Test
    public void testDoesNotAcceptNull() {
        assertFalse(new DotEnvLoader().accept(null));
    }

    /** It must add nothing to the sources probed when an interface carries no {@code @Sources}. */
    @Test
    public void testContributesNoDefaultSpec() {
        assertNull(new DotEnvLoader().defaultSpecFor("classpath:foo/Bar"));
    }

    // ---------------------------------------------------------------- common ground

    @Test
    public void testEveryDialectReadsAPlainAssignment() throws IOException {
        for (EnvDialect dialect : new EnvDialect[]{EnvDialect.DOCKER, EnvDialect.DOTENV, EnvDialect.COMPOSE}) {
            Properties props = read(dialect, "HOST=localhost", "PORT=8080");
            assertEquals(dialect.name(), "localhost", props.getProperty("HOST"));
            assertEquals(dialect.name(), "8080", props.getProperty("PORT"));
        }
    }

    @Test
    public void testBlankLinesAndFullLineCommentsAreSkipped() throws IOException {
        Properties props = read(EnvDialect.DOCKER, "# a comment", "", "   ", "\tHOST=localhost", "  # indented");
        assertEquals(1, props.size());
        assertEquals("localhost", props.getProperty("HOST"));
    }

    @Test
    public void testAnEmptyValueIsAnEmptyString() throws IOException {
        Properties props = read(EnvDialect.DOCKER, "EMPTY=");
        assertEquals("", props.getProperty("EMPTY"));
    }

    @Test
    public void testOnlyTheFirstEqualsSignSeparates() throws IOException {
        Properties props = read(EnvDialect.DOCKER, "QUERY=a=b=c");
        assertEquals("a=b=c", props.getProperty("QUERY"));
    }

    @Test
    public void testALaterAssignmentWins() throws IOException {
        Properties props = read(EnvDialect.DOCKER, "HOST=first", "HOST=second");
        assertEquals("second", props.getProperty("HOST"));
    }

    @Test
    public void testWhitespaceAroundTheNameIsNotPartOfIt() throws IOException {
        Properties props = read(EnvDialect.DOCKER, "  HOST  =localhost");
        assertEquals("localhost", props.getProperty("HOST"));
    }

    @Test
    public void testCarriageReturnsDoNotEndUpInValues() throws IOException {
        Properties props = new Properties();
        new DotEnvLoader().load(props, writeEnvSeparatedBy("\r\n", "HOST=localhost", "PORT=8080").toURI());
        assertEquals("localhost", props.getProperty("HOST"));
        assertEquals("8080", props.getProperty("PORT"));
    }

    @Test
    public void testAByteOrderMarkIsNotPartOfTheFirstName() throws IOException {
        Properties props = read(EnvDialect.DOCKER, "\uFEFFHOST=localhost");
        assertEquals("localhost", props.getProperty("HOST"));
    }

    @Test
    public void testTheFileIsReadAsUtf8() throws IOException {
        Properties props = read(EnvDialect.DOCKER, "GREETING=ciao per\u00f2");
        assertEquals("ciao per\u00f2", props.getProperty("GREETING"));
    }

    @Test
    public void testAnEmptyFileLoadsNothing() throws IOException {
        assertTrue(read(EnvDialect.DOCKER).isEmpty());
    }

    // ---------------------------------------------------------------- where the dialects part company

    @Test
    public void testDockerKeepsTheQuotes() throws IOException {
        assertEquals("\"Matteo\"", read(EnvDialect.DOCKER, "NAME=\"Matteo\"").getProperty("NAME"));
    }

    @Test
    public void testDotenvTreatsTheQuotesAsDelimiters() throws IOException {
        assertEquals("Matteo", read(EnvDialect.DOTENV, "NAME=\"Matteo\"").getProperty("NAME"));
    }

    @Test
    public void testComposeAlsoTreatsTheQuotesAsDelimiters() throws IOException {
        assertEquals("Matteo", read(EnvDialect.COMPOSE, "NAME=\"Matteo\"").getProperty("NAME"));
    }

    @Test
    public void testSingleQuotesDelimitToo() throws IOException {
        assertEquals("Matteo", read(EnvDialect.DOTENV, "NAME='Matteo'").getProperty("NAME"));
    }

    /**
     * Quotes that do not match do not delimit, so they stay in the value. Asserted on a dialect that does not
     * accept values spanning lines: where one does, an unclosed quote is the start of a longer value, and is
     * refused only if it never closes.
     */
    @Test
    public void testMismatchedQuotesAreJustCharacters() throws IOException {
        assertEquals("'Matteo\"", read(EnvDialect.COMPOSE, "NAME='Matteo\"").getProperty("NAME"));
    }

    @Test
    public void testAnUnclosedQuoteIsJustACharacterWithoutMultiline() throws IOException {
        Properties props = read(EnvDialect.COMPOSE, "NAME=\"Matteo", "HOST=localhost");
        assertEquals("\"Matteo", props.getProperty("NAME"));
        assertEquals("localhost", props.getProperty("HOST"));
    }

    @Test
    public void testQuotesPreserveSurroundingSpaces() throws IOException {
        assertEquals("  padded  ", read(EnvDialect.DOTENV, "NAME=\"  padded  \"").getProperty("NAME"));
    }

    @Test
    public void testAnUnquotedValueIsTrimmedWhenQuotesDelimit() throws IOException {
        assertEquals("localhost", read(EnvDialect.DOTENV, "HOST=  localhost  ").getProperty("HOST"));
    }

    @Test
    public void testDockerKeepsTheSpacesAroundAValue() throws IOException {
        assertEquals("  localhost  ", read(EnvDialect.DOCKER, "HOST=  localhost  ").getProperty("HOST"));
    }

    @Test
    public void testEscapesAreExpandedInsideDoubleQuotes() throws IOException {
        assertEquals("one\ntwo\tthree", read(EnvDialect.DOTENV, "TEXT=\"one\\ntwo\\tthree\"").getProperty("TEXT"));
    }

    @Test
    public void testEscapesAreNotExpandedInsideSingleQuotes() throws IOException {
        assertEquals("one\\ntwo", read(EnvDialect.DOTENV, "TEXT='one\\ntwo'").getProperty("TEXT"));
    }

    @Test
    public void testAnEscapedQuoteStaysInsideTheValue() throws IOException {
        assertEquals("say \"hi\"", read(EnvDialect.DOTENV, "TEXT=\"say \\\"hi\\\"\"").getProperty("TEXT"));
    }

    @Test
    public void testAnUnknownEscapeIsLeftAsWritten() throws IOException {
        assertEquals("C:\\path", read(EnvDialect.DOTENV, "DIR=\"C:\\path\"").getProperty("DIR"));
    }

    @Test
    public void testDockerLeavesEscapesAlone() throws IOException {
        assertEquals("one\\ntwo", read(EnvDialect.DOCKER, "TEXT=one\\ntwo").getProperty("TEXT"));
    }

    @Test
    public void testDotenvDropsTheExportPrefix() throws IOException {
        assertEquals("localhost", read(EnvDialect.DOTENV, "export HOST=localhost").getProperty("HOST"));
    }

    @Test
    public void testDockerDoesNotKnowTheExportPrefix() throws IOException {
        Properties props = read(EnvDialect.DOCKER, "export HOST=localhost");
        assertEquals("localhost", props.getProperty("export HOST"));
        assertNull(props.getProperty("HOST"));
    }

    @Test
    public void testATrailingCommentIsCutWhenTheDialectAllowsIt() throws IOException {
        assertEquals("localhost", read(EnvDialect.DOTENV, "HOST=localhost # the host").getProperty("HOST"));
    }

    @Test
    public void testATrailingCommentIsPartOfTheValueUnderDocker() throws IOException {
        assertEquals("localhost # the host",
                read(EnvDialect.DOCKER, "HOST=localhost # the host").getProperty("HOST"));
    }

    /** The hash needs whitespace before it to start a comment, so a value may contain one. */
    @Test
    public void testAHashWithoutWhitespaceBeforeItIsPartOfTheValue() throws IOException {
        assertEquals("abc#123", read(EnvDialect.DOTENV, "TOKEN=abc#123").getProperty("TOKEN"));
    }

    @Test
    public void testAHashInsideQuotesIsPartOfTheValue() throws IOException {
        assertEquals("a # b", read(EnvDialect.DOTENV, "TOKEN=\"a # b\"").getProperty("TOKEN"));
    }

    @Test
    public void testACommentAfterAQuotedValueIsStillCut() throws IOException {
        assertEquals("abc", read(EnvDialect.DOTENV, "TOKEN=\"abc\" # a note").getProperty("TOKEN"));
    }

    @Test
    public void testAQuotedValueMaySpanLinesUnderDotenv() throws IOException {
        Properties props = read(EnvDialect.DOTENV, "KEY=\"first", "second\"", "AFTER=yes");
        assertEquals("first\nsecond", props.getProperty("KEY"));
        assertEquals("yes", props.getProperty("AFTER"));
    }

    @Test
    public void testAQuotedValueDoesNotSpanLinesUnderCompose() throws IOException {
        Properties props = read(EnvDialect.COMPOSE, "KEY=\"first", "AFTER=yes");
        assertEquals("\"first", props.getProperty("KEY"));
        assertEquals("yes", props.getProperty("AFTER"));
    }

    @Test
    public void testAnUnclosedQuoteIsRefusedRatherThanGuessedAt() throws IOException {
        try {
            read(EnvDialect.DOTENV, "KEY=\"never closed", "MORE=lines");
            fail("an unterminated quoted value should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("never closed"));
        }
    }

    @Test
    public void testLineContinuationJoinsTheNextLine() throws IOException {
        Properties props = read(EnvDialect.DOCKER.withLineContinuation(true), "LIST=a,\\", "b,\\", "c");
        assertEquals("a,b,c", props.getProperty("LIST"));
    }

    @Test
    public void testAnEscapedBackslashDoesNotContinueTheLine() throws IOException {
        Properties props = read(EnvDialect.DOCKER.withLineContinuation(true), "DIR=C:\\\\", "HOST=localhost");
        assertEquals("C:\\\\", props.getProperty("DIR"));
        assertEquals("localhost", props.getProperty("HOST"));
    }

    @Test
    public void testWithoutContinuationABackslashIsJustACharacter() throws IOException {
        Properties props = read(EnvDialect.DOCKER, "LIST=a,\\", "HOST=localhost");
        assertEquals("a,\\", props.getProperty("LIST"));
        assertEquals("localhost", props.getProperty("HOST"));
    }

    // ---------------------------------------------------------------- a name with no value

    @Test
    public void testABareNameIsTakenFromTheEnvironmentUnderDocker() throws IOException {
        String name = anEnvironmentVariable();
        Properties props = read(EnvDialect.DOCKER, name);
        assertEquals(system().getenv().get(name), props.getProperty(name));
    }

    @Test
    public void testABareNameAbsentFromTheEnvironmentIsSkipped() throws IOException {
        Properties props = read(EnvDialect.DOCKER, "OWNER_SURELY_NOT_SET_ANYWHERE");
        assertTrue(props.isEmpty());
    }

    @Test
    public void testABareNameIsIgnoredUnderDotenv() throws IOException {
        String name = anEnvironmentVariable();
        assertTrue(read(EnvDialect.DOTENV, name).isEmpty());
    }

    @Test
    public void testABareNameCanBeMadeAnError() throws IOException {
        try {
            read(EnvDialect.DOCKER.withBareNames(EnvDialect.BareNames.ERROR), "HOME");
            fail("a bare name should be refused when the dialect says so");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("HOME"));
        }
    }

    @Test
    public void testALineThatIsNeitherCommentNorAssignmentIsRefused() throws IOException {
        try {
            read(EnvDialect.DOCKER, "this is not a property");
            fail("a line that assigns nothing should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("neither a comment nor an assignment"));
        }
    }

    @Test
    public void testAnAssignmentToAnEmptyNameIsRefused() throws IOException {
        try {
            read(EnvDialect.DOCKER, "=orphan");
            fail("an assignment with no name should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("empty name"));
        }
    }

    /** Whatever the dialect, a garbage line must never be swallowed in silence. */
    @Test
    public void testGarbageIsRefusedEvenWhenBareNamesAreErrors() throws IOException {
        try {
            read(EnvDialect.DOCKER.withBareNames(EnvDialect.BareNames.ERROR), "not a property at all");
            fail("a line that assigns nothing should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("neither a comment nor an assignment"));
        }
    }

    // ---------------------------------------------------------------- the options on the source

    @Test
    public void testTheFragmentChoosesTheDialect() throws IOException, URISyntaxException {
        assertEquals("Matteo", readWithOptions("dialect=dotenv", "NAME=\"Matteo\"").getProperty("NAME"));
    }

    @Test
    public void testWithoutOptionsTheLoaderKeepsItsOwnDialect() throws IOException {
        assertEquals("\"Matteo\"", read(EnvDialect.DOCKER, "NAME=\"Matteo\"").getProperty("NAME"));
    }

    @Test
    public void testTheFragmentCanAdjustASingleRule() throws IOException, URISyntaxException {
        assertEquals("Matteo", readWithOptions("quotes=strip", "NAME=\"Matteo\"").getProperty("NAME"));
    }

    @Test
    public void testASingleRuleIsAppliedOverTheChosenDialect() throws IOException, URISyntaxException {
        // dotenv would strip them; the explicit setting must win, whichever order the two are written in
        assertEquals("\"Matteo\"",
                readWithOptions("dialect=dotenv&quotes=literal", "NAME=\"Matteo\"").getProperty("NAME"));
        assertEquals("\"Matteo\"",
                readWithOptions("quotes=literal&dialect=dotenv", "NAME=\"Matteo\"").getProperty("NAME"));
    }

    @Test
    public void testEveryRuleCanBeSetFromTheFragment() throws IOException, URISyntaxException {
        Properties props = readWithOptions(
                "quotes=strip&escapes=expand&export=strip&comments=inline&multiline=allow"
                        + "&continuation=deny&bare=ignore",
                "export TEXT=\"one\\ntwo\" # a note", "BARE_NAME_WITH_NO_VALUE");
        assertEquals("one\ntwo", props.getProperty("TEXT"));
        assertEquals(1, props.size());
    }

    @Test
    public void testAnUnknownDialectIsRefused() throws IOException {
        try {
            readWithOptions("dialect=yaml", "HOST=localhost");
            fail("an unknown dialect should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("yaml"));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testAnUnknownOptionIsRefused() throws IOException {
        try {
            readWithOptions("flavour=spicy", "HOST=localhost");
            fail("an unknown option should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("flavour"));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testAnUnknownSettingIsRefused() throws IOException {
        try {
            readWithOptions("quotes=maybe", "HOST=localhost");
            fail("an unknown setting should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("maybe"));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testATermThatIsNotAPairIsRefused() throws IOException {
        try {
            readWithOptions("dotenv", "HOST=localhost");
            fail("a term that is not option=setting should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("option=setting"));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }

    // ---------------------------------------------------------------- corners

    @Test
    public void testAnUnclosedSingleQuoteIsNamedAsSuchWhenRefused() throws IOException {
        try {
            read(EnvDialect.DOTENV, "KEY='never closed");
            fail("an unterminated quoted value should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("single quote"));
        }
    }

    @Test
    public void testAnEmptyValueIsNotMistakenForTheStartOfALongerOne() throws IOException {
        Properties props = read(EnvDialect.DOTENV, "EMPTY=", "HOST=localhost");
        assertEquals("", props.getProperty("EMPTY"));
        assertEquals("localhost", props.getProperty("HOST"));
    }

    @Test
    public void testATrailingBackslashOnTheLastLineHasNothingToJoinTo() throws IOException {
        Properties props = read(EnvDialect.DOCKER.withLineContinuation(true), "LIST=a,\\");
        assertEquals("a,\\", props.getProperty("LIST"));
    }

    @Test
    public void testALineThatIsNothingButABackslashJoinsTheNextOne() throws IOException {
        Properties props = read(EnvDialect.DOCKER.withLineContinuation(true), "\\", "HOST=localhost");
        assertEquals("localhost", props.getProperty("HOST"));
    }

    @Test
    public void testAnEmptyFirstLineIsNotSearchedForAByteOrderMark() throws IOException {
        Properties props = read(EnvDialect.DOCKER, "", "HOST=localhost");
        assertEquals("localhost", props.getProperty("HOST"));
    }

    @Test
    public void testTheWordExportOnItsOwnIsNotAPrefix() throws IOException {
        Properties props = read(EnvDialect.DOTENV, "exported=yes");
        assertEquals("yes", props.getProperty("exported"));
    }

    /** With nothing after it the keyword is not a prefix, so the line is a name with no value like any other. */
    @Test
    public void testTheExportKeywordAloneAssignsNothing() throws IOException {
        assertTrue(read(EnvDialect.DOTENV, "export").isEmpty());
        try {
            read(EnvDialect.DOTENV.withBareNames(EnvDialect.BareNames.ERROR), "export");
            fail("a line holding only the export keyword assigns nothing");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("without assigning"));
        }
    }

    @Test
    public void testAHashRightAfterTheEqualsSignIsPartOfTheValue() throws IOException {
        assertEquals("#c0ffee", read(EnvDialect.DOTENV, "COLOUR=#c0ffee").getProperty("COLOUR"));
    }

    @Test
    public void testAHashInsideSingleQuotesIsPartOfTheValue() throws IOException {
        assertEquals("a # b", read(EnvDialect.DOTENV, "TOKEN='a # b'").getProperty("TOKEN"));
    }

    @Test
    public void testAOneCharacterValueIsNotAPairOfQuotes() throws IOException {
        assertEquals("\"", read(EnvDialect.DOCKER, "QUOTE=\"").getProperty("QUOTE"));
    }

    @Test
    public void testTheRestOfTheEscapesAreExpanded() throws IOException {
        Properties props = read(EnvDialect.DOTENV, "RETURN=\"a\\rb\"", "FEED=\"a\\fb\"", "BACK=\"a\\bb\"",
                "TICK=\"a\\'b\"", "SLASH=\"a\\\\b\"");
        assertEquals("a\rb", props.getProperty("RETURN"));
        assertEquals("a\fb", props.getProperty("FEED"));
        assertEquals("a\bb", props.getProperty("BACK"));
        assertEquals("a'b", props.getProperty("TICK"));
        assertEquals("a\\b", props.getProperty("SLASH"));
    }

    /** A closing quote that is itself escaped does not close: the value carries on to the next line. */
    @Test
    public void testAnEscapedClosingQuoteDoesNotEndTheValue() throws IOException {
        Properties props = read(EnvDialect.DOTENV, "TEXT=\"first \\\"", "still going\"");
        assertEquals("first \"\nstill going", props.getProperty("TEXT"));
    }

    @Test
    public void testAnApostropheInsideDoubleQuotesDoesNotOpenAnything() throws IOException {
        assertEquals("it's # fine", read(EnvDialect.DOTENV, "TOKEN=\"it's # fine\"").getProperty("TOKEN"));
    }

    @Test
    public void testAValueThatIsNothingButAnOpeningQuoteIsRefused() throws IOException {
        try {
            read(EnvDialect.DOTENV, "KEY=\"");
            fail("a lone opening quote should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("never closed"));
        }
    }

    @Test
    public void testAnUnknownBareNamePolicyIsRefused() throws IOException {
        try {
            readWithOptions("bare=whatever", "HOST=localhost");
            fail("an unknown bare name policy should be refused");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("whatever"));
        } catch (URISyntaxException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testTheFragmentCanSetEachBareNamePolicy() throws IOException, URISyntaxException {
        String name = anEnvironmentVariable();
        assertEquals(system().getenv().get(name), readWithOptions("bare=env", name).getProperty(name));
        assertTrue(readWithOptions("bare=ignore", name).isEmpty());
        try {
            readWithOptions("bare=error", name);
            fail("bare=error should refuse a name with no value");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(name));
        }
    }

    @Test
    public void testAnEmptyTermInTheFragmentIsSkipped() throws IOException, URISyntaxException {
        assertEquals("Matteo", readWithOptions("dialect=dotenv&&quotes=strip", "NAME=\"Matteo\"").getProperty("NAME"));
    }

    // ---------------------------------------------------------------- the word said about kept quotes

    /**
     * Keeping the quotes is the point of the dialect, but it is also the one thing about it that surprises
     * people, and the surprise is silent. One line saying so, per file, is the price of the default.
     */
    @Test
    public void testQuotedValuesKeptVerbatimAreReportedOnce() throws IOException {
        LogCapture recorded = recordLog();
        read(EnvDialect.DOCKER, "NAME=\"Matteo\"", "CITY='Cuneo'", "HOST=localhost");

        List<LogRecord> log = recorded.lines();
        assertEquals(1, log.size());
        String message = log.get(0).getMessage();
        assertEquals(Level.WARNING, log.get(0).getLevel());
        assertTrue(message, message.contains("2 value"));
        assertTrue(message, message.contains("NAME"));
        // the whole remedy, not just the option: advice this message once gave - '?dialect=dotenv' - is now
        // refused by the loader it came from, and an assertion on 'dialect=dotenv' alone let that through
        assertTrue(message, message.contains("#dialect=dotenv"));
        assertFalse(message, message.contains("?dialect=dotenv"));
    }

    @Test
    public void testNothingIsSaidWhenNoValueLooksQuoted() throws IOException {
        LogCapture recorded = recordLog();
        read(EnvDialect.DOCKER, "HOST=localhost", "PORT=8080");
        assertTrue(recorded.lines().toString(), recorded.lines().isEmpty());
    }

    @Test
    public void testNothingIsSaidWhenTheDialectStripsTheQuotesAnyway() throws IOException {
        LogCapture recorded = recordLog();
        read(EnvDialect.DOTENV, "NAME=\"Matteo\"");
        assertTrue(recorded.lines().toString(), recorded.lines().isEmpty());
    }

    @Test
    public void testAValueThatOpensWithAQuoteWithoutClosingIsNotReported() throws IOException {
        LogCapture recorded = recordLog();
        read(EnvDialect.DOCKER, "TEXT=\"unbalanced", "JSON={\"a\": 1}");
        assertTrue(recorded.lines().toString(), recorded.lines().isEmpty());
    }

    // ---------------------------------------------------------------- failures that must stay visible

    @Test
    public void testAMissingFileIsAnIOException() throws URISyntaxException {
        // LoadType catches IOException and moves on, which is right for a source that is not there; content
        // errors must not travel the same way, and the tests above check that they do not
        try {
            new DotEnvLoader().load(new Properties(), new URI("file:/surely/no/such/place/.env"));
            fail("a missing file should raise an IOException");
        } catch (IOException expected) {
            assertTrue(true);
        }
    }

    @Test
    public void testTheLoaderRejectsANullDialect() {
        try {
            new DotEnvLoader(null);
            fail("a null dialect should be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("dialect"));
        }
    }

    /**
     * The name of a variable this process really has. Taken from the map the loader itself reads, rather than
     * guessed at: the environment is spelled differently from one platform to the next.
     */
    private static String anEnvironmentVariable() {
        for (String name : system().getenv().keySet())
            if (name.matches("[A-Za-z_][A-Za-z0-9_.\\-]*") && !system().getenv().get(name).isEmpty())
                return name;
        throw new IllegalStateException("no environment variable to test with");
    }
}
