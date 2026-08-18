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
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.aeonbits.owner.Config.LoadType.MERGE;
import static org.aeonbits.owner.Config.Sources.CONVENTIONAL;
import static org.junit.Assert.assertEquals;

/**
 * See: https://github.com/matteobaccan/owner/issues/267
 * <p>
 * ledebroux asked two things in 2020. <b>Can I declare my own sources and still fall back on my
 * conventional file, without writing out its path?</b> — and <b>can one property be read under two names</b>,
 * since an environment variable is <code>CORE_THREAD_NUMBER</code> where a properties file says
 * <code>core.thread_number</code>. jzonthemtn asked the second one again in the same thread, having patched
 * <code>SystemLoader</code> to lower-case the environment himself.
 * </p>
 * <p>
 * Both are answered in 2.0.0, and the tests are here rather than only in
 * {@code ConventionalSourcesTest} and {@code RelaxedBindingTest} because what those two pin down is the
 * <i>rule</i>: this class pins down that the two rules together answer the question as it was asked, in the
 * shape it was asked in.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue267Test {

    private static final String THEIR_OWN_FILE = "classpath:org/aeonbits/owner/issues/Issue267Test.properties";

    @After
    public void forgetTheLocation() {
        ConfigFactory.clearProperty("owner.config.file");
    }

    // ------------------------------------------------------------------ "without giving the whole path"

    @Sources({THEIR_OWN_FILE, "system:env", CONVENTIONAL})
    @LoadPolicy(MERGE)
    interface TheirConfig extends Config {

        String declared();

        String conventional();
    }

    /**
     * Their own example, with the last source written as the token instead of
     * <code>classpath:path/to/package/MyConfig.properties</code>.
     */
    @Test
    public void theConventionalFileIsReachedWithoutSpellingOutItsPath() {
        TheirConfig config = ConfigFactory.create(TheirConfig.class);

        assertEquals("from the declared source", config.declared());
        assertEquals("from Issue267Test$TheirConfig.properties", config.conventional());
    }

    // ------------------------------------------------------------------ "several keys for one property"

    interface Threads extends Config {

        @Key("core.thread_number")
        int coreThreadNumber();
    }

    /**
     * The key as the file spells it, and the name the same setting has in a shell: one property, one
     * method, nothing written on it. Before the environment form was fixed this key was looked for as
     * <code>CORE.THREAD_NUMBER</code>, which is not a name any shell can export - so the half of this
     * issue that mattered most was answered with a spelling nobody could use.
     */
    @Test
    public void oneMethodReadsBothTheFileNameAndTheEnvironmentName() {
        assertEquals(8, ConfigFactory.newInstance()
                .create(Threads.class, one("core.thread_number", "8")).coreThreadNumber());
        assertEquals(16, ConfigFactory.newInstance()
                .create(Threads.class, one("CORE_THREAD_NUMBER", "16")).coreThreadNumber());
    }

    interface Property extends Config {

        @Key("property.name")
        String propertyName();
    }

    /** jzonthemtn's case, which is the same one: no patched {@code SystemLoader} is needed for it. */
    @Test
    public void theSameHoldsForTheCaseReportedInTheThread() {
        assertEquals("v", ConfigFactory.newInstance()
                .create(Property.class, one("PROPERTY_NAME", "v")).propertyName());
    }

    // ------------------------------------------------------------------ the source chosen at run time

    @Sources("classpath:${owner.config.file:org/aeonbits/owner/issues/Issue267Test.properties}")
    interface ChosenAtRunTime extends Config {

        String declared();
    }

    /**
     * The third comment in the thread loaded a file by hand when a system property was set, and passed the
     * result to the factory as an import - which is what put it out of reach of {@code @HotReload}. A source
     * spec is expanded before it is read and takes a default, so the same thing is one line of annotation
     * and stays a <b>source</b>.
     */
    @Test
    public void theSourceItselfMayBeChosenByASystemProperty() {
        assertEquals("from the declared source", ConfigFactory.create(ChosenAtRunTime.class).declared());

        ConfigFactory.setProperty("owner.config.file", "org/aeonbits/owner/issues/Issue267Test$Elsewhere.properties");
        assertEquals("from somewhere else", ConfigFactory.create(ChosenAtRunTime.class).declared());
    }

    private static Map<String, String> one(String key, String value) {
        Map<String, String> properties = new HashMap<>();
        properties.put(key, value);
        return properties;
    }
}
