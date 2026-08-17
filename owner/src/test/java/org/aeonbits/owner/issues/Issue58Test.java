/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Mutable;
import org.aeonbits.owner.event.RollbackBatchException;
import org.aeonbits.owner.event.RollbackOperationException;
import org.aeonbits.owner.event.TransactionalPropertyChangeListener;
import org.aeonbits.owner.util.LogCapture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.beans.PropertyChangeEvent;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/matteobaccan/owner/issues/58
 * <p>
 * lviggiano asked in 2013 for a <code>RollbackListener</code>, so that <b>somebody could find out</b> when
 * a change had been rolled back. Twelve years later the listener does not exist, and the reason to look at
 * the issue again is what was underneath it: a rollback reached nobody at all. The exception was caught and
 * handed to <code>Util.ignore()</code>, a method whose body is empty - so <code>setProperty</code> returned
 * normally, the property did not change, and there was no line anywhere saying why.
 * </p>
 * <p>
 * <b>It says so now</b>, at <code>CONFIG</code>: what was refused, and the listener's own message if it gave
 * one. Not a warning, and that is deliberate - a transactional listener is <i>meant</i> to be able to
 * refuse, and an application that uses a veto as a rule would drown in warnings for working as designed.
 * <code>CONFIG</code> is the switch somebody turns on to ask the library what it decided, which is exactly
 * the question here.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue58Test {

    public interface Editable extends Mutable {

        @Config.DefaultValue("18")
        int minAge();

        @Config.DefaultValue("localhost")
        String host();
    }

    private LogCapture capture;

    @Before
    public void listen() {
        capture = LogCapture.ofLibrary(Level.CONFIG);
    }

    @After
    public void stopListening() {
        capture.close();
    }

    /** Refuses one change and lets the rest through: {@link RollbackOperationException}. */
    private static class RefuseTheAge implements TransactionalPropertyChangeListener {

        @Override
        public void beforePropertyChange(PropertyChangeEvent event) throws RollbackOperationException {
            if ("minAge".equals(event.getPropertyName()))
                throw new RollbackOperationException("the age is set by the licence, not by the file");
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
        }
    }

    /** Refuses the whole operation: {@link RollbackBatchException}. */
    private static class RefuseEverything implements TransactionalPropertyChangeListener {

        @Override
        public void beforePropertyChange(PropertyChangeEvent event) throws RollbackBatchException {
            throw new RollbackBatchException("the configuration is read-only while the job runs");
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
        }
    }

    @Test
    public void aRefusedChangeNamesTheKeyAndTheReasonTheListenerGave() {
        Editable config = ConfigFactory.create(Editable.class);
        config.addPropertyChangeListener(new RefuseTheAge());

        config.setProperty("minAge", "21");

        assertEquals("the change was refused, which is the listener's right", 18, config.minAge());
        String said = capture.messagesAt(Level.CONFIG);
        assertTrue(said, said.contains("the change to 'minAge' was rolled back by a listener"));
        assertTrue("and the listener's own explanation is carried",
                said.contains("the age is set by the licence"));
    }

    @Test
    public void aRefusedBatchSaysWhichOperationItWas() {
        Editable config = ConfigFactory.create(Editable.class);
        config.addPropertyChangeListener(new RefuseEverything());

        config.clear();

        assertEquals("nothing was cleared", "localhost", config.host());
        String said = capture.messagesAt(Level.CONFIG);
        assertTrue(said, said.contains("the clear was rolled back by a listener"));
        assertTrue(said, said.contains("read-only while the job runs"));
    }

    /** And a change nobody refuses says nothing: this line exists for the case that surprises somebody. */
    @Test
    public void aChangeThatGoesThroughIsNotWorthALine() {
        Editable config = ConfigFactory.create(Editable.class);

        config.setProperty("minAge", "21");

        assertEquals(21, config.minAge());
        assertTrue(capture.messagesAt(Level.CONFIG),
                !capture.messagesAt(Level.CONFIG).contains("rolled back"));
    }
}
