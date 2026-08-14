/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.util.LogCapture;
import org.aeonbits.owner.util.SystemProviderForTest;
import org.aeonbits.owner.util.TimeProviderForTest;
import org.aeonbits.owner.util.UtilTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link HotReloadLogic}, covering the case of a URI that cannot be resolved to a
 * watchable file (e.g. a <code>http:</code> URI): such URIs must simply be ignored when the watchable
 * resources are set up, so no reload is ever triggered for them. It also covers the guard that
 * prevents a reload from being triggered while the manager is already loading.
 *
 * @author Matteo Baccan
 */
public class HotReloadLogicTest {

    private TimeProviderForTest time;

    @Before
    public void before() {
        time = new TimeProviderForTest();
        time.setup();               // become owner of time (now I can control the elapse of time in this test)
    }

    @After
    public void after() {
        time.tearDown();
    }

    @HotReload(5)
    interface MyConfig extends Config {
    }

    /**
     * The manager here is a double, and the interface it was created from is part of what a double has to
     * answer: the hot reload names it when it reports a source it cannot watch, and a mock returning null
     * there would fail in the diagnostic rather than in what the test is about.
     */
    private static PropertiesManager managerOf(Class<? extends Config> clazz) {
        PropertiesManager manager = mock(PropertiesManager.class);
        when(manager.configuredClass()).thenAnswer(invocation -> clazz);
        return manager;
    }

    @Test
    public void shouldIgnoreURIsThatCannotBeResolvedToFiles() throws Exception {
        PropertiesManager manager = managerOf(MyConfig.class);
        HotReload hotReload = MyConfig.class.getAnnotation(HotReload.class);

        HotReloadLogic logic = new HotReloadLogic(hotReload, SECONDS.toMillis(5),
                singletonList(new URI("http://localhost/non-watchable.properties")), manager);

        // the http URI cannot be resolved to a file, hence no watchable resource is registered.
        assertTrue(watchableResourcesOf(logic).isEmpty());

        time.elapse(10, SECONDS);   // make the hot reload interval of 5 seconds expire.
        logic.checkAndReload();
        verify(manager, never()).reload();
    }

    @Sources("system:properties")
    @HotReload(5)
    interface WatchingSystemProperties extends Config {
    }

    /**
     * One change of the system properties is one reload, and a check that follows with nothing changed is
     * not another — which is what <a href="https://github.com/matteobaccan/owner/issues/278">#278</a>
     * reported and what the surrounding tests could not see.
     * <p>
     * The report was precise: the hash of the system properties was computed on every check and never
     * stored, so after the first change every later check compared against the hash taken when the
     * configuration was created and answered "changed" forever. Nothing was <em>missed</em> by that — the
     * values did arrive — so a test asserting that a new value is picked up passes either way, which is
     * why {@code SystemPropertiesReloadTest} did not catch it. What was wrong was the cost: a reload at
     * every interval, for the life of the process, after any single change.
     * </p>
     * <p>
     * So this asserts the count and not the value, and it asserts it with time under the test's control
     * rather than with a wait.
     * </p>
     */
    @Test
    public void oneChangeOfTheSystemPropertiesIsOneReload() throws Exception {
        SystemProviderForTest systemForTest =
                new SystemProviderForTest(new Properties(), new java.util.HashMap<>());
        Object save = UtilTest.setSystem(systemForTest);
        try {
            PropertiesManager manager = managerOf(WatchingSystemProperties.class);
            HotReload hotReload = WatchingSystemProperties.class.getAnnotation(HotReload.class);
            HotReloadLogic logic = new HotReloadLogic(hotReload, SECONDS.toMillis(5),
                    singletonList(new URI("system:properties")), manager);

            // nothing has changed yet, so an expired interval is still not a reload
            time.elapse(10, SECONDS);
            logic.checkAndReload();
            verify(manager, never()).reload();

            systemForTest.setProperty("someValue", "20");
            time.elapse(10, SECONDS);
            logic.checkAndReload();
            verify(manager, times(1)).reload();

            // and now the part that was broken: nothing changed since, so nothing reloads
            time.elapse(10, SECONDS);
            logic.checkAndReload();
            time.elapse(10, SECONDS);
            logic.checkAndReload();
            verify(manager, times(1)).reload();

            // a second change is a second reload, so the watch is still watching
            systemForTest.setProperty("someValue", "30");
            time.elapse(10, SECONDS);
            logic.checkAndReload();
            verify(manager, times(2)).reload();
        } finally {
            UtilTest.setSystem(save);
        }
    }

    private List<?> watchableResourcesOf(HotReloadLogic logic) throws Exception {
        Field field = HotReloadLogic.class.getDeclaredField("watchableResources");
        field.setAccessible(true);
        return (List<?>) field.get(logic);
    }

    @Test
    public void shouldNotReloadWhileManagerIsStillLoading() throws Exception {
        File watched = new File("target/hotreloadlogictest/watched.properties");
        watched.getParentFile().mkdirs();
        UtilTest.save(watched, new Properties() {{
            setProperty("someValue", "1");
        }});

        PropertiesManager manager = managerOf(MyConfig.class);
        // the manager is busy loading on the first check, done on the second one
        when(manager.isLoading()).thenReturn(true, false);
        HotReload hotReload = MyConfig.class.getAnnotation(HotReload.class);
        HotReloadLogic logic =
                new HotReloadLogic(hotReload, SECONDS.toMillis(5), singletonList(watched.toURI()), manager);

        // the file changes and the hot reload interval of 5 seconds expires...
        assertTrue(watched.setLastModified(watched.lastModified() + 10000));
        time.elapse(10, SECONDS);

        // ...but no reload happens while a load is already in progress
        logic.checkAndReload();
        verify(manager, never()).reload();

        // once loading is over, the next check performs the reload
        time.elapse(10, SECONDS);
        logic.checkAndReload();
        verify(manager).reload();
    }

    // --------------------------------------------------- what it says it is watching

    /**
     * The CONFIG line naming what is watched is built inside a lazy supplier, so none of it runs unless
     * something is listening at that level - which nothing was, and the list it builds had never been
     * executed. A diagnostic nobody has read is a diagnostic nobody can rely on.
     */
    @Test
    public void itSaysWhichFilesItIsWatchingAndThatSystemPropertiesAreAmongThem() throws Exception {
        // Files.createTempFile rather than File.createTempFile: it creates the file with owner-only
        // permissions, which is the same reason #325 changed the ones in the library itself
        File watched = Files.createTempFile("owner-hotreload", ".properties").toFile();
        watched.deleteOnExit();

        String said;
        try (LogCapture capture = LogCapture.ofLibrary(Level.CONFIG)) {
            new HotReloadLogic(MyConfig.class.getAnnotation(HotReload.class), SECONDS.toMillis(5),
                    Arrays.asList(watched.toURI(), new URI("system:properties")),
                    managerOf(MyConfig.class));
            said = capture.messagesAt(Level.CONFIG);
        }

        assertTrue(said, said.contains("hot reload is on"));
        assertTrue("the file it watches is not named: " + said, said.contains(watched.getPath()));
        assertTrue("system:properties is watchable and is not named: " + said,
                said.contains("system:properties"));
        assertTrue("the interval belongs in the line: " + said, said.contains("5000"));
    }

}
