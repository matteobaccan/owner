/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.util.LogCapture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.text.MessageFormat;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/matteobaccan/owner/issues/118
 * <p>
 * kassim asked in 2015 for {@link MessageFormat} patterns - <code>{0}</code>, <code>{1}</code> - so that a
 * message file could be shared with a GWT project. This library formats with {@link java.util.Formatter},
 * and it still does: i18n is not what a configuration binder is for, which is also where Spring and
 * MicroProfile stand - the first has a <code>MessageSource</code> of its own, the second has no
 * parametrized properties at all.
 * </p>
 * <p>
 * <b>What was worth changing is the silence.</b> A <code>{0}</code> pattern is not a broken format string,
 * it is a correct one in another dialect: <code>String.format</code> raises nothing, hands the text back
 * unchanged, and the arguments are dropped without a word - which is precisely what makes somebody open an
 * issue rather than fix a typo. It says so now, once per key, and the two-line way of doing what was asked
 * is in the last test.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue118Test {

    public interface Messages extends Config {

        @DefaultValue("The disk \"{1}\" contains {0} file(s).")
        String diskMessage(int files, String disk);

        @DefaultValue("The disk \"{1}\" contains {0} file(s).")
        String diskPattern();

        /** What #118 asked for, in the code that wanted it: two lines and no annotation. */
        default String disk(int files, String disk) {
            return MessageFormat.format(diskPattern(), files, disk);
        }

        @DefaultValue("Hello %s, welcome on %s!")
        String greeting(String name, String planet);

        @DefaultValue("a constant, arguments or no arguments")
        String constant(String ignored);
    }

    private LogCapture capture;

    @Before
    public void listen() {
        capture = LogCapture.ofLibrary(Level.WARNING);
    }

    @After
    public void stopListening() {
        capture.close();
    }

    private Messages config() {
        return ConfigFactory.create(Messages.class);
    }

    /** The behaviour itself is unchanged: the value comes back as it was written. */
    @Test
    public void aMessageFormatPatternIsNotFormatted() {
        assertEquals("The disk \"{1}\" contains {0} file(s).", config().diskMessage(1, "/dev/sda"));
    }

    /** And the arguments vanishing is now said out loud, with what to do about it. */
    @Test
    public void andTheLibrarySaysThatTheArgumentsWereDropped() {
        config().diskMessage(1, "/dev/sda");

        String said = capture.messagesFrom(Level.WARNING);
        assertTrue(said, said.contains("java.text.MessageFormat"));
        assertTrue(said, said.contains("diskMessage"));
        assertTrue("the key, so that it can be found in the file", said.contains("diskMessage"));
        assertTrue("and what to do instead", said.contains("default method"));
    }

    /** Once per key: a message formatted in a loop is one line in the log, not a million. */
    @Test
    public void itIsSaidOnce() {
        Messages config = config();
        for (int i = 0; i < 5; i++)
            config.diskMessage(i, "/dev/sda");

        assertEquals(capture.messagesFrom(Level.WARNING), 1,
                capture.linesFrom(Level.WARNING).size());
    }

    /**
     * A value that is a template in <b>this</b> library's dialect says nothing, and neither does one that
     * simply ignores its arguments: a value has no obligation to use them, and only the other dialect is
     * unambiguous enough to report.
     */
    @Test
    public void nothingIsSaidWhenThereIsNothingToSay() {
        Messages config = config();

        assertEquals("Hello Luigi, welcome on Earth!", config.greeting("Luigi", "Earth"));
        assertEquals("a constant, arguments or no arguments", config.constant("whatever"));

        assertTrue(capture.messagesFrom(Level.WARNING), capture.messagesFrom(Level.WARNING).isEmpty());
    }

    /** And the answer to the question as it was asked, which needs nothing from the library. */
    @Test
    public void aDefaultMethodFormatsItTheOtherWay() {
        assertEquals("The disk \"/dev/sda\" contains 1 file(s).", config().disk(1, "/dev/sda"));
    }
}
