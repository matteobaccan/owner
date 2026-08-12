/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.util.TimeProviderForTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.aeonbits.owner.TestConstants.RESOURCES_DIR;
import static org.aeonbits.owner.util.UtilTest.fileFromURI;
import static org.aeonbits.owner.util.UtilTest.save;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * See: https://github.com/lviggiano/owner/issues/179
 * <p>
 * The interval between two hot reload checks, taken from outside the source file: five seconds in
 * development and five minutes in production, without two interfaces. An annotation takes constants, which
 * is where this stopped in 2016 — <code>@HotReload(value = HOT_RELOAD_TIME)</code> does not compile unless
 * the field is a compile-time constant, and then it is fixed at build time anyway.
 * <p>
 * <code>@HotReload(interval = "${ttl}")</code> is the answer, and it is the second of the two shapes Luigi
 * named in the thread: a new attribute rather than a change to the type of <code>value</code>, so no
 * existing configuration is touched. The variable is expanded from the properties of the factory, the
 * system properties and the environment — the same three, in the same order, that expand a
 * <code>@Sources</code> spec, which is exactly what the reporter had reached for by hand.
 * <p>
 * Time is controlled by {@link TimeProviderForTest} here, so the test proves the interval that is in force
 * rather than waiting for it.
 */
public class Issue179Test {

    private static final String SPEC = "file:" + RESOURCES_DIR + "/Issue179Config.properties";

    private TimeProviderForTest time;
    private File target;

    @Sources(SPEC)
    @HotReload(interval = "${ttl}")
    interface ConfiguredIntervalConfig extends Config {
        @DefaultValue("5")
        Integer someValue();
    }

    @Sources(SPEC)
    @HotReload(interval = "soon")
    interface NotADurationConfig extends Config {
        @DefaultValue("5")
        Integer someValue();
    }

    @Sources(SPEC)
    @HotReload(interval = "${ttl}")
    interface BareNumberConfig extends Config {
        @DefaultValue("5")
        Integer someValue();
    }

    @Sources(SPEC)
    @HotReload(interval = "${ttl}")
    interface NotPositiveConfig extends Config {
        @DefaultValue("5")
        Integer someValue();
    }

    @Sources(SPEC)
    @HotReload(interval = "${no.such.ttl}")
    interface UnsetVariableConfig extends Config {
        @DefaultValue("5")
        Integer someValue();
    }

    @Before
    public void before() throws URISyntaxException, IOException {
        target = fileFromURI(SPEC);
        save(target, new Properties() {{
            setProperty("someValue", "10");
        }});
        assertTrue(target.setLastModified(target.lastModified() - 60000));

        time = new TimeProviderForTest();
        time.setup();
        time.setTime(target.lastModified());
    }

    @After
    public void after() {
        time.tearDown();
        ConfigFactory.clearProperty("ttl");
        target.delete();
    }

    /**
     * Twenty seconds, which is neither the five of the default nor anything written in the interface: the
     * change is invisible at fifteen seconds and read at twenty-five.
     */
    @Test
    public void theIntervalIsTakenFromAPropertyOfTheFactory() throws IOException {
        ConfigFactory.setProperty("ttl", "20s");

        ConfiguredIntervalConfig cfg = ConfigFactory.create(ConfiguredIntervalConfig.class);
        assertEquals(Integer.valueOf(10), cfg.someValue());

        save(target, new Properties() {{
            setProperty("someValue", "20");
        }});

        time.elapse(15, SECONDS);
        assertEquals("the interval is 20 seconds, so 15 are not enough",
                Integer.valueOf(10), cfg.someValue());

        time.elapse(10, SECONDS);
        assertEquals("25 seconds in, the file is read again",
                Integer.valueOf(20), cfg.someValue());
    }

    /**
     * The same interface with a different value of the same property, which is the point of the issue:
     * one build, two deployments, two intervals.
     */
    @Test
    public void theSameInterfaceTakesTheIntervalItIsGiven() throws IOException {
        ConfigFactory.setProperty("ttl", "2s");

        ConfiguredIntervalConfig cfg = ConfigFactory.create(ConfiguredIntervalConfig.class);
        assertEquals(Integer.valueOf(10), cfg.someValue());

        save(target, new Properties() {{
            setProperty("someValue", "20");
        }});

        time.elapse(3, SECONDS);
        assertEquals("two seconds is the whole interval now, and three have gone by",
                Integer.valueOf(20), cfg.someValue());
    }

    @Test
    public void aValueThatIsNotADurationIsRefusedWhenTheObjectIsCreated() {
        String refusal = refusalOf(NotADurationConfig.class);
        assertTrue(refusal, refusal.contains("does not read as a duration"));
        assertTrue("the refusal has to name the interface it is on", refusal.contains("NotADurationConfig"));
    }

    /**
     * The unit is not optional, and this is the reason: the parser reads a bare number as milliseconds,
     * while <code>@HotReload(5)</code> — the attribute next door — means five seconds. Whoever moved the
     * one to the other would get a check every five milliseconds, so the digits alone are refused.
     */
    @Test
    public void aBareNumberIsRefusedRatherThanReadAsMilliseconds() {
        ConfigFactory.setProperty("ttl", "5");

        String refusal = refusalOf(BareNumberConfig.class);
        assertTrue(refusal, refusal.contains("carries no unit"));
        assertTrue(refusal, refusal.contains("'5s'"));
    }

    @Test
    public void anIntervalThatIsNotPositiveIsRefused() {
        ConfigFactory.setProperty("ttl", "0s");

        String refusal = refusalOf(NotPositiveConfig.class);
        assertTrue(refusal, refusal.contains("positive"));
    }

    /**
     * A variable nobody set stays as it is written, and what reaches the parser is the text
     * <code>${no.such.ttl}</code>. The refusal says both what was written and what it expanded to, since
     * the two differ and only one of them is in the source file.
     */
    @Test
    public void aVariableThatNobodySetIsRefusedAndBothFormsAreNamed() {
        String refusal = refusalOf(UnsetVariableConfig.class);
        assertTrue(refusal, refusal.contains("${no.such.ttl}"));
        assertTrue(refusal, refusal.contains("does not read as a duration"));
    }

    private String refusalOf(Class<? extends Config> type) {
        try {
            ConfigFactory.create(type);
            fail("an interval that cannot be read was expected to be refused");
            return null;
        } catch (UnsupportedOperationException refused) {
            return refused.getMessage();
        }
    }
}
