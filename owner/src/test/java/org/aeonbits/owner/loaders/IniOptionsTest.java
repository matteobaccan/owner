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
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Every option an INI source can carry, and every setting each one takes.
 * <p>
 * {@code IniLoaderTest} shows what the rules mean; this one shows that each of them can be reached from a
 * source and that a setting nobody recognises is refused with the alternatives named. The two are separate
 * because a rule that works only when set in Java is a rule half the users cannot reach.
 * </p>
 *
 * @author Matteo Baccan
 */
public class IniOptionsTest {

    // ---------------------------------------------------------------- every option, set from the source

    @Test
    public void separator() throws IOException {
        assertEquals("10", read("#separator=colon", "[s]", "a : 10").getProperty("s.a"));
        assertEquals("a : 10", read("#separator=equals", "[s]", "b = a : 10").getProperty("s.b"));
    }

    @Test
    public void duplicates() throws IOException {
        String[] file = {"[s]", "a = one", "a = two"};

        assertEquals("one", read("#duplicates=first", file).getProperty("s.a"));
        assertEquals("two", read("#duplicates=last", file).getProperty("s.a"));
        assertEquals("one", read("#duplicates=list", file).getProperty("s.a[0]"));
        refuses("#duplicates=error", "repeats the key", file);
    }

    @Test
    public void keys() throws IOException {
        assertEquals("1", read("#keys=lower", "[S]", "AbC = 1").getProperty("S.abc"));
        assertEquals("1", read("#keys=literal", "[S]", "AbC = 1").getProperty("S.AbC"));
    }

    @Test
    public void bare() throws IOException {
        assertEquals("true", read("#bare=true", "lonely").getProperty("lonely"));
        assertTrue(read("#bare=ignore", "lonely").isEmpty());
        refuses("#bare=error", "neither a comment nor an assignment", "lonely");
    }

    @Test
    public void comments() throws IOException {
        assertEquals("a", read("#comments=inline", "[s]", "k = a # b").getProperty("s.k"));
        assertEquals("a # b", read("#comments=none", "[s]", "k = a # b").getProperty("s.k"));
    }

    @Test
    public void quotes() throws IOException {
        assertEquals("x", read("#quotes=strip", "[s]", "k = \"x\"").getProperty("s.k"));
        assertEquals("\"x\"", read("#quotes=literal", "[s]", "k = \"x\"").getProperty("s.k"));
    }

    @Test
    public void continuation() throws IOException {
        assertEquals("ab", read("#continuation=backslash", "[s]", "k = a\\", "b").getProperty("s.k"));
        assertEquals("a\nb", read("#continuation=indent", "[s]", "k = a", "   b").getProperty("s.k"));
        refuses("#continuation=none", "neither a comment nor an assignment", "[s]", "k = a", "   b");
    }

    @Test
    public void subsections() throws IOException {
        assertEquals("1", read("#subsections=allow", "[a \"b\"]", "k = 1").getProperty("a.b.k"));
        assertEquals("1", read("#subsections=deny", "[a \"b\"]", "k = 1").getProperty("a \"b\".k"));
    }

    @Test
    public void defaultSection() throws IOException {
        String[] file = {"[DEFAULT]", "t = 30", "[s]", "k = 1"};

        assertEquals("30", read("#default=inherit", file).getProperty("s.t"));
        assertNull(read("#default=inherit", file).getProperty("DEFAULT.t"));
        assertEquals("30", read("#default=section", file).getProperty("DEFAULT.t"));
        assertNull(read("#default=section", file).getProperty("s.t"));
    }

    @Test
    public void interpolation() throws IOException {
        assertEquals("%(a)s", read("#interpolation=literal", "[s]", "k = %(a)s").getProperty("s.k"));
        refuses("#interpolation=refuse", "${", "[s]", "k = %(a)s");
    }

    @Test
    public void theDialectSetsTheStartingPointWhereverItAppears() throws IOException {
        // quotes=literal is applied over git, which strips them, whichever order the two are written in
        assertEquals("\"x\"", read("#dialect=git&quotes=literal", "[s]", "k = \"x\"").getProperty("s.k"));
        assertEquals("\"x\"", read("#quotes=literal&dialect=git", "[s]", "k = \"x\"").getProperty("s.k"));
    }

    // ---------------------------------------------------------------- and every way of getting one wrong

    @Test
    public void aSettingNobodyRecognisesIsRefusedWithTheAlternativesNamed() throws IOException {
        refuses("#separator=pipe", "use 'colon' or 'equals'", "a = 1");
        refuses("#duplicates=merge", "'list', 'error', 'first' or 'last'", "a = 1");
        refuses("#keys=upper", "use 'lower' or 'literal'", "a = 1");
        refuses("#bare=maybe", "'error', 'ignore' or 'true'", "a = 1");
        refuses("#comments=sometimes", "use 'inline' or 'none'", "a = 1");
        refuses("#quotes=maybe", "use 'strip' or 'literal'", "a = 1");
        refuses("#continuation=tab", "'none', 'backslash' or 'indent'", "a = 1");
        refuses("#subsections=maybe", "use 'allow' or 'deny'", "a = 1");
        refuses("#default=maybe", "use 'inherit' or 'section'", "a = 1");
        refuses("#interpolation=maybe", "use 'refuse' or 'literal'", "a = 1");
    }

    @Test
    public void anOptionNobodyRecognisesIsRefusedToo() throws IOException {
        refuses("#flavour=spicy", "is not an option", "a = 1");
    }

    @Test
    public void anUnknownDialectNamesTheOnesThatExist() throws IOException {
        refuses("#dialect=windows", "'ini', 'git' and 'python'", "a = 1");
    }

    // ---------------------------------------------------------------- the corners of the parser

    /** A backslash on the last line has nothing to join, and must not lose the line it is on. */
    @Test
    public void aTrailingBackslashOnTheLastLineKeepsIt() throws IOException {
        assertEquals("a", read("#continuation=backslash", "[s]", "k = a\\").getProperty("s.k"));
    }

    @Test
    public void quotesThatDoNotWrapTheWholeValueAreLeftAlone() throws IOException {
        assertEquals("a\"b\"c", read("#quotes=strip", "[s]", "k = a\"b\"c").getProperty("s.k"));
        assertEquals("\"", read("#quotes=strip", "[s]", "k = \"").getProperty("s.k"));
    }

    /** An escape nobody defined is likelier a backslash in a path than a sequence somebody meant. */
    @Test
    public void anUnknownEscapeIsLeftAsWritten() throws IOException {
        assertEquals("C:\\dir", read("#quotes=strip", "[s]", "k = \"C:\\dir\"").getProperty("s.k"));
        assertEquals("a\tb", read("#quotes=strip", "[s]", "k = \"a\\tb\"").getProperty("s.k"));
    }

    /** A comment character inside quotes is part of the value even when inline comments are on. */
    @Test
    public void aCommentCharacterInsideQuotesIsNotAComment() throws IOException {
        assertEquals("\"a # b\"", read("#comments=inline", "[s]", "k = \"a # b\"").getProperty("s.k"));
        assertEquals("a", read("#comments=inline", "[s]", "k = a ; b").getProperty("s.k"));
    }

    @Test
    public void anIndentedLineWithNoKeyBeforeItIsStillMalformed() throws IOException {
        refuses("#continuation=indent", "neither a comment nor an assignment", "   orphan");
    }

    @Test
    public void anIndentedCommentIsAComment() throws IOException {
        Properties props = read("#continuation=indent", "[s]", "k = a", "   # not part of it");

        assertEquals("a", props.getProperty("s.k"));
    }

    @Test
    public void aSubsectionWhoseQuoteNeverClosesIsRefused() throws IOException {
        refuses("#subsections=allow", "never closes the quote", "[a \"b]", "k = 1");
    }

    @Test
    public void aSectionHeaderResetsTheValueBeingContinued() throws IOException {
        Properties props = read("#continuation=indent", "[a]", "k = one", "[b]", "k = two");

        assertEquals("one", props.getProperty("a.k"));
        assertEquals("two", props.getProperty("b.k"));
    }

    // ---------------------------------------------------------------- the dialects as objects

    @Test
    public void aDialectSaysWhichOneItIs() {
        assertEquals("ini", IniDialect.INI.toString());
        assertEquals("git", IniDialect.GIT.name());
        assertEquals("python", IniDialect.PYTHON.name());
        assertEquals(IniDialect.GIT, IniDialect.named("GIT "));
        assertEquals(IniDialect.INI, IniDialect.named("ini"));
        assertEquals(IniDialect.PYTHON, IniDialect.named("Python"));
    }

    /** Nothing is not a dialect either, and it has to say so rather than fall over on the name. */
    @Test
    public void thereIsNoNamelessDialect() {
        try {
            IniDialect.named(null);
            fail("null names no dialect");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("'ini', 'git' and 'python'"));
        }
    }

    @Test
    public void everyRuleIsReadableFromTheDialect() {
        assertEquals(IniDialect.Separator.EQUALS, IniDialect.INI.separator());
        assertEquals(IniDialect.Duplicates.LIST, IniDialect.INI.duplicates());
        assertEquals(IniDialect.KeyCase.LITERAL, IniDialect.INI.keyCase());
        assertEquals(IniDialect.BareKeys.ERROR, IniDialect.INI.bareKeys());
        assertEquals(IniDialect.Quotes.LITERAL, IniDialect.INI.quotes());
        assertEquals(IniDialect.Continuation.NONE, IniDialect.INI.continuation());
        assertEquals(false, IniDialect.INI.isInlineComments());
        assertEquals(false, IniDialect.INI.isSubsections());
        assertEquals(false, IniDialect.INI.isDefaultSectionInherited());
        assertEquals(false, IniDialect.INI.isInterpolationRefused());

        assertEquals(IniDialect.Continuation.INDENT, IniDialect.PYTHON.continuation());
        assertEquals(true, IniDialect.PYTHON.isDefaultSectionInherited());
        assertEquals(true, IniDialect.GIT.isSubsections());
        assertEquals(true, IniDialect.GIT.isInlineComments());
    }

    @Test
    public void aDialectRefusesToBeNothing() {
        try {
            new IniLoader(null);
            fail("a loader has to read by some rules");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("dialect"));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Properties read(String options, String... lines) throws IOException {
        Properties result = new Properties();
        new IniLoader().load(result, URI.create(write(lines).toURI() + options));
        return result;
    }

    private static void refuses(String options, String expected, String... lines) throws IOException {
        try {
            read(options, lines);
            fail("expected a refusal mentioning: " + expected);
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expected));
        }
    }

    private static File write(String... lines) throws IOException {
        File file = Files.createTempFile("owner-ini-options", ".ini").toFile();
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
