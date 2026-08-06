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
import org.aeonbits.owner.util.TimeProviderForTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

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
 * The lookup walks the <b>direct</b> super-interfaces only, so an annotation on a grandparent is silently
 * ignored. That is the behaviour as it stands, and these tests exist to make any change to it deliberate and
 * visible: it differs from {@link Config.Prefix}, which counts at any depth of the hierarchy.</p>
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

    /**
     * The lookup stops at the direct super-interfaces, so the grandparent's {@code @LoadPolicy(MERGE)} does not
     * apply and the default FIRST policy is used: only first.properties is read, and {@code bar} is not there.
     */
    @Test
    public void loadPolicyOnAGrandParentIsIgnored() {
        PolicyOnTheGrandParent cfg = ConfigFactory.create(PolicyOnTheGrandParent.class);
        assertEquals("first", cfg.foo());
        assertNull(cfg.bar());
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

    /** Accumulating too stops at the direct super-interfaces: the grandparent's source is not loaded. */
    @Test
    public void sourcesOnAGrandParentAreIgnored() {
        SourcesOnTheGrandParent cfg = ConfigFactory.create(SourcesOnTheGrandParent.class);
        assertEquals("first", cfg.foo());
        assertNull(cfg.bar());
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

    /** The grandparent's {@code @HotReload} is ignored, so the change on disk is never picked up. */
    @Test
    public void hotReloadOnAGrandParentIsIgnored() throws IOException {
        writeAgedValue(10);

        HotReloadOnTheGrandParent cfg = ConfigFactory.create(HotReloadOnTheGrandParent.class);
        assertEquals(Integer.valueOf(10), cfg.someValue());

        writeValue(20);
        time.elapse(6, SECONDS);

        assertEquals(Integer.valueOf(10), cfg.someValue());
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
