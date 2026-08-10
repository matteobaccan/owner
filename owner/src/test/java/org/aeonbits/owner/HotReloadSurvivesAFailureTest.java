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
import org.aeonbits.owner.util.TimeProviderForTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.aeonbits.owner.Config.HotReloadType.ASYNC;
import static org.aeonbits.owner.TestConstants.RESOURCES_DIR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A failing reload must not take the schedule down with it.
 * <p>
 * {@link ScheduledExecutorService#scheduleAtFixedRate scheduleAtFixedRate} suppresses every later run of a
 * task that throws, so without care one malformed source would stop a configuration reloading for the rest
 * of the life of the process, while it went on answering with whatever values it happened to hold and
 * nothing anywhere said so.
 * </p>
 * <p>
 * The scheduler here is a stand-in that only records the task, and the clock is the one the tests control,
 * so a check happens exactly when the test asks for one and never a moment otherwise.
 * </p>
 *
 * @author Matteo Baccan
 */
public class HotReloadSurvivesAFailureTest {

    private static final String DIR = RESOURCES_DIR + "/hotreload";
    private static final String SOURCE = DIR + "/HotReloadSurvivesAFailure.env";
    /**
     * A line that assigns nothing. Note that it is not a quoting mistake: under the dialect the loader reads
     * by default a quote is an ordinary character, so an unbalanced one is a value and not an error.
     */
    private static final String BROKEN = "this line assigns nothing";

    private static final Logger MANAGER_LOG = Logger.getLogger(PropertiesManager.class.getName());

    @Sources("file:" + SOURCE)
    @HotReload(value = 1, unit = SECONDS, type = ASYNC)
    interface WatchedConfig extends Config {
        String host();
    }

    private final List<LogRecord> log = new ArrayList<>();
    private TimeProviderForTest time;
    private Handler recorder;
    private Level originalLevel;
    private boolean originalUseParentHandlers;
    private long lastModified;

    @Before
    public void before() throws IOException {
        new File(DIR).mkdirs();
        lastModified = System.currentTimeMillis();
        write("host=first");

        time = new TimeProviderForTest();
        time.setup();

        recorder = new Handler() {
            @Override
            public void publish(LogRecord record) {
                // what these tests are about is what the library says when something went wrong, and the
                // level is set to ALL above so that nothing of that kind can be missed. Since 2.0.0 the same
                // logger also reports what was decided, at CONFIG, which is a different subject and would
                // otherwise be counted as if it were a failure
                if (record.getLevel().intValue() >= Level.WARNING.intValue())
                    log.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
                // nothing to release
            }
        };
        originalLevel = MANAGER_LOG.getLevel();
        originalUseParentHandlers = MANAGER_LOG.getUseParentHandlers();
        MANAGER_LOG.setLevel(Level.ALL);
        MANAGER_LOG.setUseParentHandlers(false);
        MANAGER_LOG.addHandler(recorder);
    }

    @After
    public void after() {
        MANAGER_LOG.removeHandler(recorder);
        MANAGER_LOG.setLevel(originalLevel);
        MANAGER_LOG.setUseParentHandlers(originalUseParentHandlers);
        time.tearDown();
        new File(SOURCE).delete();
    }

    private void write(String line) throws IOException {
        File file = new File(SOURCE);
        Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), "UTF-8");
        try {
            writer.write(line);
            writer.write("\n");
        } finally {
            writer.close();
        }
        // the watcher compares modification times, and the file system may not have the resolution to tell
        // two writes of this test apart on its own
        lastModified += 10_000;
        assertTrue(file.setLastModified(lastModified));
    }

    /** Rewrites the source and lets enough of the controlled clock pass for the next check to be due. */
    private void rewrite(String line) throws IOException {
        write(line);
        time.elapse(10, SECONDS);
    }

    /**
     * Builds the configuration against a scheduler that records the task instead of running it, and hands
     * that task back so a check can be made to happen on demand.
     */
    private Runnable scheduledCheck() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        configuration = new DefaultFactory(scheduler, new Properties()).create(WatchedConfig.class);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(task.capture(), anyLong(), anyLong(), any(TimeUnit.class));
        return task.getValue();
    }

    private WatchedConfig configuration;

    @Test
    public void testAFailedReloadDoesNotEscapeAndDoesNotStopTheSchedule() throws IOException {
        Runnable check = scheduledCheck();
        assertEquals("first", configuration.host());

        // the loader refuses the file rather than reading half of it
        rewrite(BROKEN);
        check.run();

        assertEquals("the values already held must survive a failed reload", "first", configuration.host());
        assertEquals(1, log.size());
        assertEquals(Level.WARNING, log.get(0).getLevel());
        assertTrue(log.get(0).getMessage(),
                log.get(0).getMessage().contains(WatchedConfig.class.getName()));

        // the schedule is intact, so a source that comes right is picked up
        rewrite("host=second");
        check.run();
        assertEquals("second", configuration.host());
    }

    /** A broken source is checked at the hot reload interval, and must not be logged at that rate. */
    @Test
    public void testTheSameFailureIsReportedOnce() throws IOException {
        Runnable check = scheduledCheck();

        for (int i = 0; i < 3; i++) {
            rewrite(BROKEN);
            check.run();
        }

        assertEquals("the same failure should be named once", 1, log.size());
    }

    /** Once it has cleared, the same fault returning is news again. */
    @Test
    public void testTheFailureIsReportedAgainAfterARecovery() throws IOException {
        Runnable check = scheduledCheck();

        rewrite(BROKEN);
        check.run();
        rewrite("host=recovered");
        check.run();
        rewrite(BROKEN);
        check.run();

        assertEquals("recovered", configuration.host());
        assertEquals(2, log.size());
    }

    @Test
    public void testASuccessfulReloadSaysNothing() throws IOException {
        Runnable check = scheduledCheck();

        rewrite("host=second");
        check.run();

        assertEquals("second", configuration.host());
        assertTrue(log.toString(), log.isEmpty());
    }
}
