/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config.Sources;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.aeonbits.owner.TestConstants.RESOURCES_DIR;
import static org.aeonbits.owner.util.UtilTest.fileFromURI;
import static org.aeonbits.owner.util.UtilTest.save;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A source that was named and did not arrive.
 * <p>
 * The rule has three parts and they only make sense together: an absent source says nothing, because a
 * fallback chain is built out of absences; one that is there and cannot be read is a warning, because
 * nobody designs a fallback on that; and a set of declared sources of which <b>none</b> could be read is a
 * warning of its own, because that is what a mistyped path looks like and the first rule is what hides it.
 * </p>
 *
 * @author Matteo Baccan
 */
public class SourceThatDidNotArriveTest {

    private static final String PRESENT = "file:" + RESOURCES_DIR + "/SourceThatArrives.properties";
    private static final String ABSENT = "file:" + RESOURCES_DIR + "/no-such-file.properties";
    /**
     * A source that is named, is reachable in principle and refuses: nothing listens on port 1, so the
     * connection is refused at once and deterministically. A directory would have been the obvious fixture
     * and is not one — the <code>file:</code> URL handler answers a directory with its listing rather than
     * with an error, which is a silent failure of a different kind and not this one.
     */
    private static final String UNREADABLE = "http://localhost:1/app.properties";

    private final List<LogRecord> warnings = new ArrayList<>();
    private final Logger logger = Logger.getLogger("org.aeonbits.owner");
    private Handler collector;

    @Before
    public void collectTheWarnings() {
        collector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue())
                    warnings.add(record);
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };
        logger.addHandler(collector);
    }

    @After
    public void stopCollecting() {
        logger.removeHandler(collector);
    }

    private String warningsAsText() {
        StringBuilder text = new StringBuilder();
        for (LogRecord record : warnings)
            text.append(record.getMessage()).append('\n');
        return text.toString();
    }

    private static void writeThePresentOne() throws Exception {
        save(fileFromURI(PRESENT), new Properties() {{
            setProperty("host", "alpha");
        }});
    }


    // -------------------------------------------------------------------------------------------------
    // the absence that is how the feature works
    // -------------------------------------------------------------------------------------------------

    @Sources({ABSENT, PRESENT})
    public interface FallbackConfig extends Config {
        String host();
    }

    @Test
    public void aSourceThatIsSimplyAbsentSaysNothing() throws Exception {
        writeThePresentOne();

        assertEquals("alpha", ConfigFactory.create(FallbackConfig.class).host());
        assertTrue("the fallback worked and should have been quiet: " + warningsAsText(),
                warnings.isEmpty());
    }

    public interface NoSourcesConfig extends Config {
        @DefaultValue("8080")
        int port();
    }

    /** Four names are probed for every interface that declares none; finding no file is not news. */
    @Test
    public void aConfigurationThatDeclaresNoSourceSaysNothingEither() {
        assertEquals(8080, ConfigFactory.create(NoSourcesConfig.class).port());
        assertTrue(warningsAsText(), warnings.isEmpty());
    }

    // -------------------------------------------------------------------------------------------------
    // the source that is there and will not be read
    // -------------------------------------------------------------------------------------------------

    @Sources({UNREADABLE, PRESENT})
    @LoadPolicy(LoadType.MERGE)
    public interface UnreadableConfig extends Config {
        String host();
    }

    @Test
    public void aSourceThatCannotBeReadIsAWarning() throws Exception {
        writeThePresentOne();

        assertEquals("the others still answer", "alpha",
                ConfigFactory.create(UnreadableConfig.class).host());

        String said = warningsAsText();
        assertFalse("nothing was said about a source that could not be read", warnings.isEmpty());
        assertTrue(said, said.contains("localhost:1"));
        assertTrue(said, said.contains("could not be read"));
    }

    // -------------------------------------------------------------------------------------------------
    // none of them arrived, which is what a typo looks like
    // -------------------------------------------------------------------------------------------------

    @Sources({ABSENT, "file:" + RESOURCES_DIR + "/nor-this-one.properties"})
    public interface AllAbsentConfig extends Config {
        @DefaultValue("8080")
        int port();
    }

    @Test
    public void sourcesOfWhichNoneCouldBeReadAreAWarning() {
        assertEquals("it holds its defaults, which is the point", 8080,
                ConfigFactory.create(AllAbsentConfig.class).port());

        String said = warningsAsText();
        assertFalse("a configuration that read nothing at all said nothing at all", warnings.isEmpty());
        assertTrue(said, said.contains("not one of the sources"));
        assertTrue(said, said.contains("default values"));
    }

    @Test
    public void andItIsSaidOnceRatherThanAtEveryReload() {
        ReloadableAllAbsentConfig cfg = ConfigFactory.create(ReloadableAllAbsentConfig.class);
        cfg.reload();
        cfg.reload();

        assertEquals("said once, not three times: " + warningsAsText(), 1, warnings.size());
    }

    @Sources({ABSENT, "file:" + RESOURCES_DIR + "/nor-this-one.properties"})
    public interface ReloadableAllAbsentConfig extends Config, Reloadable {
        @DefaultValue("8080")
        int port();
    }

    // -------------------------------------------------------------------------------------------------
    // hot reload asked for on a source that cannot be watched
    // -------------------------------------------------------------------------------------------------

    @Sources({PRESENT, "classpath:org/aeonbits/owner/underneath.properties", UNREADABLE})
    @Config.HotReload(1)
    public interface WatchingTheUnwatchableConfig extends Config {
        String host();
    }

    /**
     * Watching means asking something whether it has changed, and only a file and the system properties can
     * answer that. A source that cannot be watched is dropped — there is nothing else to do with it — but
     * dropping it in silence is what produced "I changed the file and nothing happened". Unlike an absent
     * source, this one was asked for: somebody wrote {@code @HotReload}.
     */
    @Test
    public void aSourceThatCannotBeWatchedIsSaidOnce() throws Exception {
        writeThePresentOne();

        ConfigFactory.create(WatchingTheUnwatchableConfig.class);

        String said = warningsAsText();
        assertTrue(said, said.contains("hot reload"));
        assertTrue(said, said.contains("cannot be watched"));
        assertTrue("the remote source is one of them: " + said, said.contains("localhost:1"));
    }

    @Sources(PRESENT)
    @Config.HotReload(1)
    public interface WatchingAFileConfig extends Config {
        String host();
    }

    @Test
    public void aFileIsWatchedAndNothingIsSaid() throws Exception {
        writeThePresentOne();

        ConfigFactory.create(WatchingAFileConfig.class);

        assertTrue("a file can be watched, so there is nothing to report: " + warningsAsText(),
                warnings.isEmpty());
    }

    // -------------------------------------------------------------------------------------------------
    // the source that says it has to be there
    // -------------------------------------------------------------------------------------------------

    @Sources(ABSENT + "#required=true")
    public interface RequiredFileConfig extends Config {
        String host();
    }

    @Test
    public void aRequiredSourceThatIsNotThereIsRefused() {
        try {
            ConfigFactory.create(RequiredFileConfig.class);
            fail("a source declared required was allowed to be missing");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("required"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("no-such-file"));
        }
    }

    @Sources("classpath:no/such/resource.properties#required=true")
    public interface RequiredResourceConfig extends Config {
        String host();
    }

    /**
     * A classpath source that resolves to nothing never reaches a loader — it is dropped while the list of
     * sources is being built — so the promise has to be answered for there as well, or it would be the one
     * place where <code>required</code> is quietly ignored.
     */
    @Test
    public void aRequiredResourceThatIsNotOnTheClasspathIsRefused() {
        try {
            ConfigFactory.create(RequiredResourceConfig.class);
            fail("a classpath source declared required was allowed to be missing");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("required"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("no/such/resource.properties"));
        }
    }

    @Sources({PRESENT + "#required=true", ABSENT})
    public interface RequiredAndPresentConfig extends Config {
        String host();
    }

    @Test
    public void aRequiredSourceThatIsThereChangesNothing() throws Exception {
        writeThePresentOne();

        assertEquals("alpha", ConfigFactory.create(RequiredAndPresentConfig.class).host());
        assertTrue(warningsAsText(), warnings.isEmpty());
    }

    @Sources(ABSENT + "#required=maybe")
    public interface BadlyRequiredConfig extends Config {
        String host();
    }

    @Test
    public void anythingElseThanTheTwoWordsIsRefused() {
        try {
            ConfigFactory.create(BadlyRequiredConfig.class);
            fail("a setting that is neither true nor false was accepted");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("maybe"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("required"));
        }
    }

    /** Every loader accepts it without declaring it, which is what makes it an option of the library. */
    @Sources(PRESENT + "#required=true")
    public interface RequiredIsNotALoaderOptionConfig extends Config {
        String host();
    }

    @Test
    public void noLoaderRefusesItAsAnOptionItDoesNotKnow() throws Exception {
        writeThePresentOne();

        assertEquals("alpha", ConfigFactory.create(RequiredIsNotALoaderOptionConfig.class).host());
    }
}
