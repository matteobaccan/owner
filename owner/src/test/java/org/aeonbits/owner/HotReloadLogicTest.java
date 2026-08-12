/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.util.TimeProviderForTest;
import org.aeonbits.owner.util.UtilTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        HotReloadLogic logic = new HotReloadLogic(hotReload,
                singletonList(new URI("http://localhost/non-watchable.properties")), manager);

        // the http URI cannot be resolved to a file, hence no watchable resource is registered.
        assertTrue(watchableResourcesOf(logic).isEmpty());

        time.elapse(10, SECONDS);   // make the hot reload interval of 5 seconds expire.
        logic.checkAndReload();
        verify(manager, never()).reload();
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
        HotReloadLogic logic = new HotReloadLogic(hotReload, singletonList(watched.toURI()), manager);

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
        File watched = File.createTempFile("owner-hotreload", ".properties");
        watched.deleteOnExit();

        List<LogRecord> records = listen(Level.CONFIG);
        try {
            new HotReloadLogic(MyConfig.class.getAnnotation(HotReload.class),
                    Arrays.asList(watched.toURI(), new URI("system:properties")),
                    managerOf(MyConfig.class));
        } finally {
            stopListening();
        }

        String said = onlyLineAt(records, Level.CONFIG);
        assertTrue(said, said.contains("hot reload is on"));
        assertTrue("the file it watches is not named: " + said, said.contains(watched.getPath()));
        assertTrue("system:properties is watchable and is not named: " + said,
                said.contains("system:properties"));
        assertTrue("the interval belongs in the line: " + said, said.contains("5000"));
    }

    private Logger logger;
    private Handler collector;
    private Level previousLevel;

    private List<LogRecord> listen(Level level) {
        List<LogRecord> records = new ArrayList<>();
        logger = Logger.getLogger("org.aeonbits.owner");
        previousLevel = logger.getLevel();
        logger.setLevel(level);
        collector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };
        collector.setLevel(level);
        logger.addHandler(collector);
        return records;
    }

    private void stopListening() {
        logger.removeHandler(collector);
        logger.setLevel(previousLevel);
    }

    private static String onlyLineAt(List<LogRecord> records, Level level) {
        StringBuilder said = new StringBuilder();
        for (LogRecord record : records)
            if (record.getLevel() == level)
                said.append(record.getMessage()).append('\n');
        return said.toString();
    }
}
