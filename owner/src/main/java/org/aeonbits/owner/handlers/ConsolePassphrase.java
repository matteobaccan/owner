/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.handlers;

import java.io.Console;

/**
 * The one thing in this library that needs a person at a keyboard: reading a passphrase off a terminal,
 * twice, without echo.
 * <p>
 * It is a class of its own so that it can be <b>excluded from the coverage measurement</b>, and the
 * boundary is drawn where it is for a reason. What lives here is only what a terminal has to do -
 * two calls to {@link Console#readPassword}. The decision those two produce is
 * {@link EncryptTool#confirmed(char[], char[])}, which stays where it can be tested, because "these two
 * typings agree and are not empty" is a rule worth pinning down and has nothing to do with terminals.
 * </p>
 * <p>
 * The alternative was a parameter on {@link EncryptTool} that exists so a test can reach a branch, and
 * that is worse than an excluded class: it puts the test's needs into the shape of the design, where the
 * next reader has to work out which of the two the parameter is for. The same separation is already used
 * in <code>owner-extras</code>, where every call that touches Curator lives in its own class.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class ConsolePassphrase {

    private ConsolePassphrase() {
    }

    /**
     * Asks twice and hands over what was typed, for {@link EncryptTool#confirmed} to judge.
     * <p>
     * Asked twice only here, and not when the passphrase comes from the environment: a variable was set on
     * purpose, while a passphrase mistyped at a prompt writes a file nobody can read and gives no sign of
     * why until somebody tries.
     * </p>
     *
     * @param console a console that is known to be a terminal - see {@link EncryptTool}, which checks.
     * @return the passphrase.
     * @throws IllegalStateException if it is empty, or the two typings differ.
     */
    static char[] ask(Console console) {
        return EncryptTool.confirmed(console.readPassword("Passphrase: "),
                console.readPassword("Again: "));
    }
}
