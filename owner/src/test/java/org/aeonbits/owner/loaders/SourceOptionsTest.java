/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The rule under test is one sentence: the query belongs to the protocol and the fragment belongs to OWNER.
 *
 * @author Matteo Baccan
 */
public class SourceOptionsTest {

    // ---------------------------------------------------------------- what to match an extension against

    @Test
    public void testThePathDropsTheFragment() throws URISyntaxException {
        assertEquals("file:/app/.env", SourceOptions.path(new URI("file:/app/.env#dialect=dotenv")));
    }

    @Test
    public void testThePathDropsTheQuery() throws URISyntaxException {
        assertEquals("http://host/app.xml", SourceOptions.path(new URI("http://host/app.xml?v=2")));
    }

    /**
     * The fragment is cut before the query because a fragment may legally contain a '?': cutting the query
     * first would truncate the source itself.
     */
    @Test
    public void testThePathDropsBothInTheRightOrder() throws URISyntaxException {
        assertEquals("http://host/app.env",
                SourceOptions.path(new URI("http://host/app.env?token=abc#dialect=dotenv&note=a?b")));
    }

    /**
     * <code>file:.env</code> is the form the documentation uses and it is an <b>opaque</b> URI, whose
     * {@link URI#getPath()} is null. So is every resource resolved inside a jar. This is why the path is
     * read from the text.
     */
    @Test
    public void testThePathWorksOnAnOpaqueURI() throws URISyntaxException {
        assertEquals("file:.env", SourceOptions.path(new URI("file:.env#dialect=dotenv")));
        assertEquals("jar:file:/a.jar!/conf/app.env",
                SourceOptions.path(new URI("jar:file:/a.jar!/conf/app.env#dialect=dotenv")));
    }

    @Test
    public void testThePathOfNothingIsEmptyRatherThanAFailure() {
        assertEquals("", SourceOptions.path(null));
    }

    // ---------------------------------------------------------------- reading the options

    @Test
    public void testASourceWithoutAFragmentHasNoOptions() throws URISyntaxException {
        assertTrue(SourceOptions.of(new URI("file:/app/.env")).isEmpty());
        assertTrue(SourceOptions.of(new URI("file:/app/.env#")).isEmpty());
        assertTrue(SourceOptions.of(null).isEmpty());
    }

    @Test
    public void testSeveralOptionsAreReadInOrderAndSeparatedByAmpersand() throws URISyntaxException {
        List<SourceOptions.Option> all =
                SourceOptions.of(new URI("file:/app/.env#dialect=dotenv&quotes=strip&bare=error")).all();
        assertEquals(3, all.size());
        assertEquals("dialect=dotenv", all.get(0).toString());
        assertEquals("quotes=strip", all.get(1).toString());
        assertEquals("bare=error", all.get(2).toString());
    }

    /** A loader may let a later option refine an earlier one, so a repetition is kept rather than merged. */
    @Test
    public void testARepeatedOptionIsKeptTwice() throws URISyntaxException {
        List<SourceOptions.Option> all =
                SourceOptions.of(new URI("file:/app/.env#quotes=strip&quotes=literal")).all();
        assertEquals(2, all.size());
        assertEquals("strip", all.get(0).setting());
        assertEquals("literal", all.get(1).setting());
    }

    /**
     * A name is folded to lower case because it is a keyword; a setting is not, because only the loader
     * knows whether its settings are keywords too. A literal space cannot be tested here at all - it is not
     * a legal character in a fragment and {@link URI} refuses the whole source.
     */
    @Test
    public void testAnOptionNameIsAKeywordAndASettingIsNot() throws URISyntaxException {
        SourceOptions.Option option = SourceOptions.of(new URI("file:/app/.env#DiaLect=DotEnv")).all().get(0);
        assertEquals("dialect", option.name());
        assertEquals("DotEnv", option.setting());
    }

    /**
     * The setting is not percent decoded, and deliberately: decoding would have to happen before the pairs
     * are split, so a %26 inside a setting would become a separator and split one option into two.
     */
    @Test
    public void testASettingIsTakenLiterally() throws URISyntaxException {
        assertEquals("a%26b", SourceOptions.of(new URI("file:/app/.env#note=a%26b")).all().get(0).setting());
    }

    @Test
    public void testAnEmptyTermIsSkipped() throws URISyntaxException {
        assertEquals(2, SourceOptions.of(new URI("file:/app/.env#a=1&&b=2")).all().size());
    }

    @Test
    public void testATermThatIsNotAPairIsRefused() throws URISyntaxException {
        try {
            SourceOptions.of(new URI("file:/app/.env#dotenv"));
            fail("a term that is not option=setting should be refused");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("is not an option=setting pair"));
        }
    }

    @Test
    public void testATermWithNoNameIsRefused() throws URISyntaxException {
        try {
            SourceOptions.of(new URI("file:/app/.env#=dotenv"));
            fail("a term with no name should be refused");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("is not an option=setting pair"));
        }
    }

    // ---------------------------------------------------------------- refusing what is not understood

    @Test
    public void testAKnownOptionPasses() throws URISyntaxException {
        SourceOptions.of(new URI("file:/app/.env#dialect=dotenv")).refuseUnknown("dialect", "quotes");
    }

    /** The message names the offender, the source, and what would have been accepted. */
    @Test
    public void testAnUnknownOptionIsRefusedAndTheMessageSaysWhatIsAccepted() throws URISyntaxException {
        try {
            SourceOptions.of(new URI("file:/app/.env#flavour=spicy")).refuseUnknown("dialect", "quotes", "bare");
            fail("an unknown option should be refused");
        } catch (UnsupportedOperationException e) {
            String message = e.getMessage();
            assertTrue(message, message.contains("flavour"));
            assertTrue(message, message.contains("file:/app/.env"));
            assertTrue(message, message.contains("dialect, quotes and bare"));
        }
    }

    @Test
    public void testALoaderThatTakesNoOptionsSaysSo() throws URISyntaxException {
        try {
            SourceOptions.of(new URI("file:/app/app.properties#dialect=dotenv")).refuseUnknown();
            fail("a loader with no options should refuse one");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("this source takes none"));
        }
    }

    // ---------------------------------------------------------------- whose query is it

    /**
     * On a scheme that speaks to a server the query is the server's, and OWNER does not touch it. Reading
     * this one used to be impossible: {@link URI#getQuery()} returns null on the opaque URIs a jar produces,
     * which is half the reason the options live in the fragment.
     */
    @Test
    public void testAQueryIsLeftAloneOnASchemeThatCanUseOne() throws URISyntaxException {
        URI uri = new URI("http://host/app.env?token=abc#dialect=dotenv");
        assertEquals("token=abc", uri.getQuery());
        assertEquals(1, SourceOptions.of(uri).all().size());
        assertEquals("dialect=dotenv", SourceOptions.of(uri).all().get(0).toString());
    }

    /**
     * On file: and jar: a query cannot mean anything - URL.getFile() includes it, so the handler looks for a
     * file whose name ends in '?dialect=dotenv' and the source disappears without a word. Refusing it is how
     * that silence ends.
     */
    @Test
    public void testAQueryIsRefusedOnASchemeThatCannotUseOne() throws URISyntaxException {
        for (String spec : new String[]{"file:/app/.env?dialect=dotenv", "file:.env?dialect=dotenv",
                "jar:file:/a.jar!/app.env?dialect=dotenv"}) {
            try {
                SourceOptions.of(new URI(spec));
                fail("a query on " + spec + " should be refused");
            } catch (UnsupportedOperationException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("has no meaning"));
                assertTrue(e.getMessage(), e.getMessage().contains("go in the fragment"));
            }
        }
    }

    @Test
    public void testAFileSourceWithoutAQueryIsFine() throws URISyntaxException {
        assertFalse(SourceOptions.of(new URI("file:/app/.env#dialect=dotenv")).isEmpty());
    }
}
