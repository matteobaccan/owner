/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.util.LogCapture;
import org.aeonbits.owner.util.TimeProviderForTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.logging.Level;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.aeonbits.owner.Config.LoadType.MERGE;
import static org.aeonbits.owner.TestConstants.RESOURCES_DIR;
import static org.aeonbits.owner.util.UtilTest.fileFromURI;
import static org.aeonbits.owner.util.UtilTest.save;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins down how the interface-level annotations read by {@link PropertiesManager} — {@link Sources},
 * {@link LoadPolicy} and {@link HotReload} — behave when they sit on a super-interface rather than on the
 * interface handed to the factory.
 * <p>
 * The lookup walks the <b>whole</b> hierarchy since 2.0.0, breadth first: the interface itself, then the
 * ones it extends in the order of the {@code extends} clause, then theirs. Until then it stopped at the
 * direct super-interfaces and an annotation on a grandparent was silently ignored, which contradicted
 * {@link Config.Prefix} — that one always counted at any depth. Each of these three had its own copy of
 * the same loop; they share one now — {@link Annotations} — which is what made the depth a single decision
 * rather than three.</p>
 * <p>
 * These three are the ones {@link PropertiesManager} reads when the configuration is created. The other
 * class-level annotations, and the rule that decides which of them walks the hierarchy at all, are in
 * {@link ClassLevelAnnotationsTest}.</p>
 *
 * @author Matteo Baccan
 */
public class AnnotationInheritanceTest {

    // first.properties  -> foo=first,  baz=first
    // second.properties -> foo=second, bar=second
    private static final String FIRST_PROPERTIES = "classpath:org/aeonbits/owner/first.properties";
    private static final String SECOND_PROPERTIES = "classpath:org/aeonbits/owner/second.properties";

    interface TwoSources extends Config {
        String foo();

        /** Defined in second.properties only: it can be read only when both sources are merged. */
        String bar();
    }

    // -- @LoadPolicy ---------------------------------------------------------------------------------

    @Sources({FIRST_PROPERTIES, SECOND_PROPERTIES})
    @LoadPolicy(MERGE)
    interface PolicyOnTheInterface extends TwoSources {
    }

    @Test
    public void loadPolicyOnTheInterfaceItselfIsUsed() {
        PolicyOnTheInterface cfg = ConfigFactory.create(PolicyOnTheInterface.class);
        assertEquals("first", cfg.foo());
        assertEquals("second", cfg.bar());
    }

    @LoadPolicy(MERGE)
    interface MergingParent extends TwoSources {
    }

    @Sources({FIRST_PROPERTIES, SECOND_PROPERTIES})
    interface PolicyOnTheParent extends MergingParent {
    }

    @Test
    public void loadPolicyOnADirectSuperInterfaceIsUsed() {
        PolicyOnTheParent cfg = ConfigFactory.create(PolicyOnTheParent.class);
        assertEquals("second", cfg.bar());
    }

    interface MiddleWithoutPolicy extends MergingParent {
    }

    @Sources({FIRST_PROPERTIES, SECOND_PROPERTIES})
    interface PolicyOnTheGrandParent extends MiddleWithoutPolicy {
    }

    /** The lookup walks the whole hierarchy, so the grandparent's {@code @LoadPolicy(MERGE)} applies. */
    @Test
    public void loadPolicyOnAGrandParentIsUsed() {
        PolicyOnTheGrandParent cfg = ConfigFactory.create(PolicyOnTheGrandParent.class);
        assertEquals("first", cfg.foo());
        assertEquals("second", cfg.bar());
    }

    @Sources({FIRST_PROPERTIES, SECOND_PROPERTIES})
    interface TwoBranches extends MiddleWithoutPolicy, FirstParent {
    }

    @LoadPolicy(FIRST)
    interface FirstParent extends TwoSources {
    }

    @Sources({FIRST_PROPERTIES, SECOND_PROPERTIES})
    interface MergeDeclaredFirst extends MergingParent, FirstParent {
    }

    @Sources({FIRST_PROPERTIES, SECOND_PROPERTIES})
    interface FirstDeclaredFirst extends FirstParent, MergingParent {
    }

    /** With two annotated super-interfaces, the one declared first in the {@code extends} clause wins. */
    @Test
    public void theFirstDeclaredSuperInterfaceWins() {
        assertEquals("second", ConfigFactory.create(MergeDeclaredFirst.class).bar());
        assertNull(ConfigFactory.create(FirstDeclaredFirst.class).bar());
    }

    /**
     * The walk is breadth first, and this is the case that says so: {@code TwoBranches} extends an
     * unannotated interface whose own parent declares {@code MERGE}, and a second one that declares
     * {@code FIRST} itself. Every direct parent is asked before any grandparent, so {@code FIRST} answers.
     * Depth first would have gone up the first branch to the end and merged instead — which is the same
     * question the {@code extends} clause already answers between two siblings, asked one level up.
     */
    @Test
    public void everyDirectParentIsAskedBeforeAnyGrandParent() {
        assertNull(ConfigFactory.create(TwoBranches.class).bar());
    }

    // -- @Sources ------------------------------------------------------------------------------------

    @Sources(SECOND_PROPERTIES)
    interface SecondSourceParent extends TwoSources {
    }

    @Sources(FIRST_PROPERTIES)
    @LoadPolicy(MERGE)
    interface SourcesOnBoth extends SecondSourceParent {
    }

    /**
     * {@code @Sources} is the odd one out, and by design: it is not a "first annotation found wins" lookup, it
     * <b>accumulates</b>. The URIs of the interface come first, followed by those of every direct
     * super-interface, and the resulting list is what the load policy is applied to.
     */
    @Test
    public void sourcesOfTheInterfaceAndOfItsDirectParentsAreAccumulated() {
        SourcesOnBoth cfg = ConfigFactory.create(SourcesOnBoth.class);
        assertEquals("first", cfg.foo());       // the interface's own source comes first
        assertEquals("second", cfg.bar());      // ...and the parent's one is merged after it
    }

    @Sources(SECOND_PROPERTIES)
    interface SecondSourceGrandParent extends TwoSources {
    }

    interface MiddleWithoutSources extends SecondSourceGrandParent {
    }

    @Sources(FIRST_PROPERTIES)
    @LoadPolicy(MERGE)
    interface SourcesOnTheGrandParent extends MiddleWithoutSources {
    }

    /** Accumulating reaches the whole hierarchy too: the grandparent's source is loaded after the rest. */
    @Test
    public void sourcesOnAGrandParentAreAccumulatedToo() {
        SourcesOnTheGrandParent cfg = ConfigFactory.create(SourcesOnTheGrandParent.class);
        assertEquals("first", cfg.foo());
        assertEquals("second", cfg.bar());
    }

    interface OneBranch extends SecondSourceGrandParent {
    }

    interface AnotherBranch extends SecondSourceGrandParent {
    }

    @Sources(FIRST_PROPERTIES)
    @LoadPolicy(MERGE)
    interface Diamond extends OneBranch, AnotherBranch {
    }

    /**
     * An interface reached by two paths is read once, so its sources are declared once. Nothing observable
     * would break if they were listed twice — the same file merged with itself says the same thing — but the
     * diagnostics would say it twice, and the file would be opened twice for nothing.
     */
    @Test
    public void anInterfaceReachedTwiceContributesItsSourcesOnce() {
        try (LogCapture capture = LogCapture.ofLibrary(Level.CONFIG)) {
            ConfigFactory.create(Diamond.class);

            String said = capture.messagesAt(Level.CONFIG);
            assertEquals(said, 1, countOf("SecondSourceGrandParent", said));
        }
    }

    /**
     * The convention — {@code MyConfig.properties} and its siblings — is what a configuration that declares
     * no source at all falls back on, and until 2.0.0 it was quietly appended to the sources of one that
     * <b>had</b> declared them: {@link Sources} was read one interface at a time, and every interface without
     * the annotation contributed the default list. {@code Config} itself has none, so it happened to
     * everybody. Here first.properties is declared and merged, and the convention file next to this test
     * holds a {@code bar} that must not be read.
     */
    @Test
    public void theConventionIsNotAppendedToTheSourcesThatWereDeclared() {
        DeclaringAndConventional cfg = ConfigFactory.create(DeclaringAndConventional.class);

        assertEquals("first", cfg.foo());
        assertNull(cfg.bar());
    }

    @Sources(FIRST_PROPERTIES)
    @LoadPolicy(MERGE)
    interface DeclaringAndConventional extends TwoSources {
    }

    /** And the fallback itself is looked for once, rather than once per interface that does not declare it. */
    @Test
    public void theConventionIsLookedForOnceWhenNobodyDeclaresASource() {
        try (LogCapture capture = LogCapture.ofLibrary(Level.CONFIG)) {
            ConfigFactory.create(DeclaringNothing.class);

            String said = capture.messagesAt(Level.CONFIG);
            assertEquals(said, 1, countOf("no @Sources, looking for:", said));
        }
    }

    interface DeclaringNothing extends TwoSources {
    }

    private static int countOf(String needle, String text) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length()))
            count++;
        return count;
    }

    // -- @HotReload ----------------------------------------------------------------------------------

    private static final String RELOADED_FILE = RESOURCES_DIR + "/AnnotationInheritanceTest.properties";
    private static final String RELOADED_SPEC = "file:" + RELOADED_FILE;

    private TimeProviderForTest time;
    private File target;

    @Before
    public void before() throws URISyntaxException {
        target = fileFromURI(RELOADED_SPEC);
        time = new TimeProviderForTest();
        time.setup();
    }

    @After
    public void after() {
        time.tearDown();
    }

    interface ReloadableValue extends Config {
        Integer someValue();
    }

    @HotReload(5)
    interface HotReloadingParent extends ReloadableValue {
    }

    @Sources(RELOADED_SPEC)
    interface HotReloadOnTheParent extends HotReloadingParent {
    }

    @Test
    public void hotReloadOnADirectSuperInterfaceIsUsed() throws IOException {
        writeAgedValue(10);

        HotReloadOnTheParent cfg = ConfigFactory.create(HotReloadOnTheParent.class);
        assertEquals(Integer.valueOf(10), cfg.someValue());

        writeValue(20);
        time.elapse(6, SECONDS);

        assertEquals(Integer.valueOf(20), cfg.someValue());
    }

    interface MiddleWithoutHotReload extends HotReloadingParent {
    }

    @Sources(RELOADED_SPEC)
    interface HotReloadOnTheGrandParent extends MiddleWithoutHotReload {
    }

    /** The grandparent's {@code @HotReload} applies, so the change on disk is picked up. */
    @Test
    public void hotReloadOnAGrandParentIsUsed() throws IOException {
        writeAgedValue(10);

        HotReloadOnTheGrandParent cfg = ConfigFactory.create(HotReloadOnTheGrandParent.class);
        assertEquals(Integer.valueOf(10), cfg.someValue());

        writeValue(20);
        time.elapse(6, SECONDS);

        assertEquals(Integer.valueOf(20), cfg.someValue());
    }

    /** Writes the watched file and backdates it, so that the test starts with the reload interval already past. */
    private void writeAgedValue(int value) throws IOException {
        writeValue(value);
        boolean aged = target.setLastModified(target.lastModified() - 15000);
        assertTrue(aged);
        time.setTime(target.lastModified());
    }

    private void writeValue(final int value) throws IOException {
        save(target, new Properties() {{
            setProperty("someValue", String.valueOf(value));
        }});
    }
}
