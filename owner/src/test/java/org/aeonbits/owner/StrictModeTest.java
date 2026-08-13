/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * <code>owner.strict</code>, the {@link Factory} property that turns this library's warnings into refusals.
 * <p>
 * Each test states both halves: what the default does, which is what OWNER has always done, and what the
 * same configuration does with the property on. The pairs are the point — a test that only asserted the
 * refusal would not show that nothing changed for anybody who does not ask for it.
 * </p>
 */
public class StrictModeTest {

    private static Factory strictFactory() {
        Factory factory = ConfigFactory.newInstance();
        factory.setProperty("owner.strict", "true");
        return factory;
    }

    // ------------------------------------------- a source that is named and cannot be read

    /**
     * Named, reachable in principle, and refusing: nothing listens on port 1, so the connection fails at
     * once and deterministically. A directory is <b>not</b> the fixture for this, as
     * {@code SourceThatDidNotArriveTest} explains — the <code>file:</code> handler answers a directory with
     * its listing rather than with an error.
     */
    private static final String UNREADABLE = "http://localhost:1/app.properties";

    private static final String PRESENT = "classpath:org/aeonbits/owner/StrictModeTest.properties";

    @Config.Sources({UNREADABLE, PRESENT})
    @Config.LoadPolicy(Config.LoadType.MERGE)
    public interface ReadsAnUnreachableSource extends Config {
        String value();
    }

    /**
     * The source is there and something else is wrong with it. This is the case the warning was written
     * for — a path spelt wrong looks exactly like it — and the one strict refuses.
     */
    @Test
    public void aSourceThatCannotBeReadIsAWarningByDefaultAndARefusalUnderStrict() {
        assertEquals("by default the other sources still answer", "second",
                ConfigFactory.newInstance().create(ReadsAnUnreachableSource.class).value());

        try {
            strictFactory().create(ReadsAnUnreachableSource.class);
            fail("expected the unreadable source to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("could not be read"));
            assertTrue("it names the property that made it a refusal",
                    refused.getMessage().contains("owner.strict"));
        }
    }

    // --------------------------------------------------- nothing at all could be read

    @Config.Sources("file:/nowhere/owner-strict-does-not-exist.properties")
    public interface NothingAnswers extends Config {
        @DefaultValue("fallback")
        String value();
    }

    /**
     * Every declared source missing. Note that this is <b>not</b> the "merely absent" case being refused:
     * a single absent source stays silent even here, and what strict refuses is their sum, which is the
     * shape a wrong path takes.
     */
    @Test
    public void aConfigurationThatReadNothingIsAWarningByDefaultAndARefusalUnderStrict() {
        assertEquals("fallback", ConfigFactory.newInstance().create(NothingAnswers.class).value());

        try {
            strictFactory().create(NothingAnswers.class);
            fail("expected a configuration that read nothing to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("not one of the sources"));
            assertTrue(refused.getMessage().contains("owner.strict"));
        }
    }

    // ----------------------------------------------- hot reload on a source nobody can watch

    @Config.Sources({PRESENT, "system:env"})
    @Config.LoadPolicy(Config.LoadType.MERGE)
    @Config.HotReload(1)
    public interface WatchesTheEnvironment extends Config {
        String value();
    }

    /**
     * Watching means asking something whether it has changed, and the environment cannot answer. So
     * <code>@HotReload</code> over it is a request that will never be honoured for that source — which is
     * why it was made a warning rather than a CONFIG line, and why strict refuses it. The environment is
     * read without trouble, so nothing else in this configuration is amiss.
     */
    @Test
    public void anUnwatchableSourceIsAWarningByDefaultAndARefusalUnderStrict() {
        assertNotNull("by default it is created and that source simply never triggers a reload",
                ConfigFactory.newInstance().create(WatchesTheEnvironment.class));

        try {
            strictFactory().create(WatchesTheEnvironment.class);
            fail("expected the unwatchable source to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("cannot be watched"));
            assertTrue(refused.getMessage().contains("owner.strict"));
        }
    }

    // ------------------------------------------- what strict deliberately leaves alone

    @Config.Sources({"file:/nowhere/owner-strict-first.properties", PRESENT})
    public interface FallsBackToTheSecond extends Config {
        String value();
    }

    /**
     * The reason strict is defined by the warnings rather than by a list of its own. Under
     * {@link Config.LoadType#FIRST} the sources before the one that answers are expected to miss — that is
     * what a fallback is — so a source which is merely absent stays silent, strict or not. Refusing it
     * would make the property unusable with the commonest shape a configuration has.
     */
    @Test
    public void anAbsentSourceIsNotARefusalEvenUnderStrict() {
        assertEquals("read from the classpath", "second",
                strictFactory().create(FallsBackToTheSecond.class).value());
    }

    // ------------------------------------------- a variable that resolves to nothing

    public interface ReadsAnUnsetVariable extends Config {
        @DefaultValue("jdbc:h2:mem:${db.name}")
        String url();
    }

    /**
     * The oldest silence in the library, and the one strict was worth having for. An expression nothing
     * resolves is replaced by the empty string, which is also exactly what a misspelt name gives — so
     * <code>${db.nmae}</code> has always produced a value that looks almost right.
     */
    @Test
    public void aVariableThatResolvesToNothingIsEmptyByDefaultAndARefusalUnderStrict() {
        assertEquals("by default it is the empty string", "jdbc:h2:mem:",
                ConfigFactory.newInstance().create(ReadsAnUnsetVariable.class).url());

        try {
            strictFactory().create(ReadsAnUnsetVariable.class).url();
            fail("expected the unresolvable variable to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("db.name"));
            assertTrue(refused.getMessage(), refused.getMessage().contains("owner.strict"));
        }
    }

    public interface SaysItMeansEmpty extends Config {
        @DefaultValue("jdbc:h2:mem:${db.name:}")
        String url();
    }

    /**
     * The way to mean it: a default value that is itself empty. Strict refuses the variable nobody wrote a
     * default for, not the one whose default happens to be nothing.
     */
    @Test
    public void anExplicitlyEmptyDefaultIsNotARefusal() {
        assertEquals("jdbc:h2:mem:", strictFactory().create(SaysItMeansEmpty.class).url());
    }

    @Config.Sources("file:${owner.strict.test.home}/app.properties")
    public interface ExpandsItsSourceSpec extends Config {
        @DefaultValue("fallback")
        String value();
    }

    /**
     * The same rule where it bites hardest: a <code>@Sources</code> spec is expanded before there is a
     * Config object at all, so an unset variable there turns <code>file:${app.home}/app.properties</code>
     * into <code>file:/app.properties</code> and the configuration then holds nothing but its defaults.
     */
    @Test
    public void anUnsetVariableInASourceSpecIsARefusalUnderStrict() {
        assertEquals("by default the spec expands to nonsense and the defaults answer",
                "fallback", ConfigFactory.newInstance().create(ExpandsItsSourceSpec.class).value());

        try {
            strictFactory().create(ExpandsItsSourceSpec.class);
            fail("expected the unresolvable variable in the spec to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("owner.strict.test.home"));
        }
    }

    public interface NoSourcesAtAll extends Config {
        @DefaultValue("all defaults")
        String value();
    }

    /**
     * A configuration made entirely of defaults declares no sources, so there is nothing that failed to be
     * read and nothing to refuse. The default probe looking for four names and finding none is how such a
     * configuration is written, not a failure.
     */
    @Test
    public void aConfigurationOfDefaultsIsNotARefusalEvenUnderStrict() {
        assertEquals("all defaults", strictFactory().create(NoSourcesAtAll.class).value());
    }

    @Test
    public void theDefaultIsOffAndTheOldBehaviourIsUntouched() {
        assertNull("nothing sets it", ConfigFactory.newInstance().getProperty("owner.strict"));
        assertEquals("fallback", ConfigFactory.newInstance().create(NothingAnswers.class).value());
    }

    /**
     * The property belongs to the factory, not to the JVM: an application turning it on must not make a
     * library that happens to use OWNER strict as a side effect. This is why it is not a system property,
     * the way the nested-variable switch is.
     */
    @Test
    public void strictBelongsToTheFactoryThatWasToldAboutIt() {
        Factory strict = strictFactory();
        Factory lenient = ConfigFactory.newInstance();

        assertEquals("fallback", lenient.create(NothingAnswers.class).value());
        try {
            strict.create(NothingAnswers.class);
            fail("expected the strict factory to refuse");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected.getMessage());
        }
        assertEquals("and the lenient one is still lenient afterwards",
                "fallback", lenient.create(NothingAnswers.class).value());
    }
}
