/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.util.LogCapture;
import org.junit.After;
import org.junit.Test;

import java.util.logging.Level;

import static org.aeonbits.owner.Config.LoadType.MERGE;
import static org.aeonbits.owner.Config.Sources.CONVENTIONAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The conventional sources - the files named after the mapping interface - and the three decisions taken
 * about them in 2.0.0.
 * <p>
 * <b>Which of them wins</b>, when a configuration has more than one: <code>.properties</code>, then
 * <code>.xml</code>, then <code>.ini</code> and <code>.cfg</code>. Pinned in
 * {@link LoadersManagerOrderingTest#theBuiltInSpecsAreOfferedMostConventionalFirst()}, and visible here
 * through a configuration that has two files on disk.
 * </p>
 * <p>
 * <b>That the ambiguity is said out loud</b>, because no ordering can do more than choose which of the two
 * silences you get: the file you did not expect being read, or the file you did expect being ignored.
 * </p>
 * <p>
 * <b>That the convention can be named</b>, with {@link Sources#CONVENTIONAL}, which is what answers
 * <a href="https://github.com/matteobaccan/owner/issues/267">#267</a>: until 2.0.0 the conventional file was
 * quietly appended to whatever a configuration declared, and that accident is exactly what somebody was
 * asking for on purpose.
 * </p>
 *
 * @author Matteo Baccan
 */
public class ConventionalSourcesTest {

    private LogCapture capture;

    @After
    public void stopListening() {
        if (capture != null) capture.close();
    }

    private String warnings() {
        return capture.messagesFrom(Level.WARNING);
    }

    // ------------------------------------------------------------ which one wins, and what is said

    interface Both extends Config {
        String value();
    }

    /**
     * Both <code>Both.properties</code> and <code>Both.xml</code> are on the classpath. The properties file
     * wins - before 2.0.0 the XML did, and before that nobody had decided - and because the two of them
     * together are nobody's intention, a warning names them, says which was read, and says how to end the
     * ambiguity.
     */
    @Test
    public void withTwoConventionalFilesThePropertiesOneWinsAndItIsSaidSoOutLoud() {
        capture = LogCapture.ofLibrary(Level.WARNING);

        assertEquals("properties", ConfigFactory.create(Both.class).value());

        String said = warnings();
        assertTrue(said, said.contains("more than one conventional source exists"));
        assertTrue(said, said.contains("Both.properties"));
        assertTrue(said, said.contains("Both.xml"));
        assertTrue(said, said.contains("LoadType.FIRST"));
        assertTrue(said, said.contains(CONVENTIONAL));
    }

    @LoadPolicy(MERGE)
    interface BothMerged extends Config {
        String value();

        String other();
    }

    /** Under MERGE both are read, so the warning says that instead - the ambiguity is the same one. */
    @Test
    public void underMergeTheWarningSaysThatAllOfThemAreRead() {
        capture = LogCapture.ofLibrary(Level.WARNING);

        BothMerged config = ConfigFactory.create(BothMerged.class);

        String said = warnings();
        assertTrue(said, said.contains("more than one conventional source exists"));
        assertTrue(said, said.contains("LoadType.MERGE"));
        assertEquals("properties", config.value());
        assertEquals("xml", config.other());
    }

    /**
     * Under {@code owner.strict} the same ambiguity is a refusal, as every warning with a caller to refuse
     * is. Named here rather than assumed: strict is the mode somebody turns on to be told at creation time.
     */
    @Test
    public void underStrictTwoConventionalFilesAreARefusal() {
        ConfigFactory.setProperty(PropertiesManager.STRICT, "true");
        try {
            ConfigFactory.create(Both.class);
            fail("strict refuses what it would otherwise warn about");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("more than one conventional"));
        } finally {
            ConfigFactory.clearProperty(PropertiesManager.STRICT);
        }
    }

    interface OnlyOne extends Config {
        String foo();
    }

    /** One conventional file is the ordinary case and says nothing: the warning is about the ambiguity. */
    @Test
    public void oneConventionalFileIsNotWorthAWord() {
        capture = LogCapture.ofLibrary(Level.WARNING);

        ConfigFactory.create(OnlyOne.class);

        assertTrue(warnings(), warnings().isEmpty());
    }

    // ------------------------------------------------------------ naming the convention

    private static final String FIRST_PROPERTIES = "classpath:org/aeonbits/owner/first.properties";

    @Sources({FIRST_PROPERTIES, CONVENTIONAL})
    @LoadPolicy(MERGE)
    interface Declaring extends Config {
        String foo();

        String value();

        String extra();
    }

    /**
     * The token stands, in place, for everything this configuration would look for with no
     * {@code @Sources} at all - so the declared file comes first and the conventional one is merged under
     * it, which is #267 in one line.
     */
    @Test
    public void theTokenStandsForTheConventionalSourcesWhereItIsWritten() {
        Declaring config = ConfigFactory.create(Declaring.class);

        assertEquals("first", config.foo());            // from the declared source
        assertEquals("conventional", config.value());   // from Declaring.properties
        assertEquals("also here", config.extra());
    }

    @Sources({FIRST_PROPERTIES, CONVENTIONAL})
    @LoadPolicy(MERGE)
    interface ChildOne extends Config {
        String value();
    }

    @Sources({FIRST_PROPERTIES, CONVENTIONAL})
    @LoadPolicy(MERGE)
    interface ChildTwo extends Config {
        String value();
    }

    /**
     * The name is that of the interface handed to the factory, so the same declaration - here repeated, in
     * practice inherited from a base interface - sends each configuration to its own file. Written the
     * other way round it would send them all to the base interface's file, which is of no use to anybody.
     */
    @Test
    public void eachConfigurationResolvesItsOwnConventionalFile() {
        assertEquals("one", ConfigFactory.create(ChildOne.class).value());
        assertEquals("two", ConfigFactory.create(ChildTwo.class).value());
    }

    @Sources(CONVENTIONAL + ".xml")
    interface OnlyXml extends Config {
        String value();
    }

    /**
     * Qualified with an extension it stands for that one conventional source, which is how a single format
     * is asked for - <code>OnlyXml.properties</code> is right there and is not read - and, written more
     * than once, how an order of your own is expressed.
     */
    @Test
    public void qualifiedWithAnExtensionItStandsForThatOneSource() {
        assertEquals("xml", ConfigFactory.create(OnlyXml.class).value());
    }

    @Sources(CONVENTIONAL + ".propertis")
    interface Misspelt extends Config {
        String value();
    }

    /**
     * And an extension no loader offers is <b>refused</b>. A source that resolves to nothing is passed over
     * by design - that is how a fallback works - so a typo here would otherwise produce a configuration
     * that reads nothing and says nothing about it.
     */
    @Test
    public void anExtensionNobodyOffersIsRefusedRatherThanPassedOver() {
        try {
            ConfigFactory.create(Misspelt.class);
            fail("a conventional source that could never exist is not a source that happens to be missing");
        } catch (UnsupportedOperationException expected) {
            String message = expected.getMessage();
            assertTrue(message, message.contains(".propertis"));
            assertTrue(message, message.contains(".properties"));
            assertTrue(message, message.contains(".xml"));
        }
    }

    @Sources({FIRST_PROPERTIES, CONVENTIONAL})
    interface WithoutAConventionalFile extends Config {
        String foo();

        String value();
    }

    /**
     * The token is not a promise that the file exists: with none of the conventional names on the
     * classpath it contributes nothing, exactly as an absent declared source does.
     */
    @Test
    public void theTokenContributesNothingWhenNoConventionalFileIsThere() {
        WithoutAConventionalFile config = ConfigFactory.create(WithoutAConventionalFile.class);

        assertEquals("first", config.foo());
        assertNull(config.value());
    }

    /** The CONFIG line shows what the token was expanded into, since that is what was looked for. */
    @Test
    public void theDiagnosticsShowTheExpansionRatherThanTheToken() {
        capture = LogCapture.ofLibrary(Level.CONFIG);

        ConfigFactory.create(Declaring.class);

        String said = capture.messagesAt(Level.CONFIG);
        assertTrue(said, said.contains(CONVENTIONAL + " stands for:"));
        assertTrue(said, said.contains("Declaring.properties"));
    }
}
