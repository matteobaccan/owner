/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.aeonbits.owner.Config.LoadType.MERGE;
import static org.aeonbits.owner.Config.Sources.CONVENTIONAL;
import static org.junit.Assert.assertEquals;

/**
 * See: https://github.com/matteobaccan/owner/issues/173
 * <p>
 * rrialq asked for an <code>@AutoSources</code>: a ladder of sources - a shared file, an override per host,
 * an override per server, and the file named after the configuration itself - applied to every mapping
 * interface <b>without writing it on each of them</b>.
 * </p>
 * <p>
 * There is no such annotation and there does not need to be. The ladder is written once, on an interface
 * the others extend, and the rung that changes per configuration is {@link Sources#CONVENTIONAL}. Both
 * halves of that are true only since 2026-08-17: {@code @Sources} used to be read off the direct
 * super-interfaces alone, so a base two levels up did nothing, and there was no way to say "the file named
 * after <i>this</i> interface" from a base interface that does not know which one it is.
 * </p>
 * <p>
 * The order is the one rrialq's own example shows, and it is worth reading twice: under
 * {@link Config.LoadType#MERGE} the <b>earlier</b> source prevails, so the ladder is written from the most
 * specific override down to the shared default - which is his list upside down, and is what his equivalent
 * {@code @Sources} already said.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue173Test {

    private static final String LADDER = "classpath:org/aeonbits/owner/issues/issue173/";

    /** Written once. Every configuration below extends it and declares no sources of its own. */
    @LoadPolicy(MERGE)
    @Sources({LADDER + "${hostName}.host.properties", LADDER + "global.properties", CONVENTIONAL})
    public interface OurConfig extends Config {
    }

    public interface Demo extends OurConfig {

        String name();

        int timeout();

        int retries();
    }

    public interface Other extends OurConfig {

        String name();

        int timeout();
    }

    @Before
    public void runningOnAlpha() {
        ConfigFactory.setProperty("hostName", "alpha");
    }

    @After
    public void runningNowhere() {
        ConfigFactory.clearProperty("hostName");
    }

    /**
     * One interface, three rungs answering: the host file wins where it says something, the shared file
     * answers for the rest, and the name of the application comes from the file named after the interface -
     * which no line of the annotation mentions.
     */
    @Test
    public void theLadderIsWrittenOnceAndEachRungAnswersForWhatItHas() {
        Demo config = ConfigFactory.create(Demo.class);

        assertEquals("the host override prevails, being written first", 5, config.timeout());
        assertEquals("the shared file answers for what the host does not override", 2, config.retries());
        assertEquals("and this comes from Issue173Test$Demo.properties", "demo", config.name());
    }

    /**
     * The point of the whole arrangement: a second configuration extends the same base, writes nothing, and
     * <b>its own</b> conventional file is the one that answers - not the base interface's.
     */
    @Test
    public void asecondConfigurationGetsTheSameLadderAndItsOwnFile() {
        assertEquals("other", ConfigFactory.create(Other.class).name());
        assertEquals(5, ConfigFactory.create(Other.class).timeout());
    }

    /**
     * And the host rung is a path decided outside the code: on a machine whose name matches no file the
     * spec resolves to nothing and is passed over, which is how a ladder of optional rungs is meant to work.
     */
    @Test
    public void aRungThatIsNotThereIsPassedOver() {
        ConfigFactory.setProperty("hostName", "a-host-with-no-file-of-its-own");

        assertEquals("the shared value, the override being absent", 30, ConfigFactory.create(Demo.class).timeout());
    }
}
