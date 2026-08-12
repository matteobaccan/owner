/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.util;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * What a logger said while a test ran.
 *
 * <p>
 * Since 2.0.0 the library reports what went wrong and what it decided, and a diagnostic is only worth
 * having if something reads it — so eight test classes had each written the same anonymous
 * {@link Handler}, collecting records into a list of their own and putting the logger back afterwards.
 * This is that handler, written once. What differed between the copies was the logger, the level and the
 * filtering, and those are arguments here.
 * </p>
 * <p>
 * Installing it also silences the parent handlers, so the records a test is inspecting do not also print
 * themselves on the console; both that and the level are restored by {@link #close()}, which is why this
 * is a {@link Closeable} and belongs in a try-with-resources or an {@code @After}.
 * </p>
 *
 * @author Matteo Baccan
 */
public final class LogCapture implements Closeable {

    /** The logger the whole library reports under. */
    public static final String LIBRARY = "org.aeonbits.owner";

    private final Logger logger;
    private final Handler collector;
    private final Level previousLevel;
    private final boolean previousUseParentHandlers;

    // a hot reload of the ASYNC kind reports from its own thread, so what collects those reports cannot
    // be a plain ArrayList: the test reading it is on another one
    private final List<LogRecord> lines = Collections.synchronizedList(new ArrayList<LogRecord>());

    private LogCapture(Logger logger, Level level) {
        this.logger = logger;
        this.previousLevel = logger.getLevel();
        this.previousUseParentHandlers = logger.getUseParentHandlers();

        this.collector = new Handler() {
            @Override
            public void publish(LogRecord line) {
                lines.add(line);
            }

            @Override
            public void flush() {
                // nothing is buffered: a record is kept the moment it arrives
            }

            @Override
            public void close() {
                // nothing was opened to release; LogCapture.close() is what puts the logger back
            }
        };
        this.collector.setLevel(level);

        logger.setLevel(level);
        logger.setUseParentHandlers(false);
        logger.addHandler(collector);
    }

    /**
     * Listens to the logger the whole library reports under.
     *
     * @param level the level to listen from.
     * @return the running capture, to be closed when the test is done with it.
     */
    public static LogCapture ofLibrary(Level level) {
        return new LogCapture(Logger.getLogger(LIBRARY), level);
    }

    /**
     * Listens to the logger of one class, which is what a test about a single loader wants.
     *
     * @param loggedClass the class whose logger to listen to.
     * @param level       the level to listen from.
     * @return the running capture, to be closed when the test is done with it.
     */
    public static LogCapture of(Class<?> loggedClass, Level level) {
        return new LogCapture(Logger.getLogger(loggedClass.getName()), level);
    }

    /**
     * @return every record collected so far, in the order they were reported.
     */
    public List<LogRecord> lines() {
        return new ArrayList<>(lines);
    }

    /**
     * @param level the level to keep.
     * @return the records reported at exactly that level.
     */
    public List<LogRecord> linesAt(Level level) {
        List<LogRecord> kept = new ArrayList<>();
        for (LogRecord line : lines())
            if (line.getLevel().equals(level))
                kept.add(line);
        return kept;
    }

    /**
     * @param level the level to keep from.
     * @return the records reported at that level or above it.
     */
    public List<LogRecord> linesFrom(Level level) {
        List<LogRecord> kept = new ArrayList<>();
        for (LogRecord line : lines())
            if (line.getLevel().intValue() >= level.intValue())
                kept.add(line);
        return kept;
    }

    /**
     * @param level the level to keep.
     * @return the messages reported at exactly that level, one per line.
     */
    public String messagesAt(Level level) {
        return joined(linesAt(level), false);
    }

    /**
     * @param level the level to keep from.
     * @return the messages reported at that level or above it, one per line.
     */
    public String messagesFrom(Level level) {
        return joined(linesFrom(level), false);
    }

    /**
     * @return every message collected, one per line, each preceded by its level — for a test that is
     *         about which level something was said at as much as about what was said.
     */
    public String messagesWithTheirLevel() {
        return joined(lines(), true);
    }

    private static String joined(List<LogRecord> kept, boolean withLevel) {
        StringBuilder text = new StringBuilder();
        for (LogRecord line : kept) {
            if (withLevel)
                text.append(line.getLevel()).append(' ');
            text.append(line.getMessage()).append('\n');
        }
        return text.toString();
    }

    /**
     * Puts the logger back exactly as it was found: the handler removed, the level and the parent
     * handlers restored.
     */
    @Override
    public void close() {
        logger.removeHandler(collector);
        logger.setLevel(previousLevel);
        logger.setUseParentHandlers(previousUseParentHandlers);
    }
}
