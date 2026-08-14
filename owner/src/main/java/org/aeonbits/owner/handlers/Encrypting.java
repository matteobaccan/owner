/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.handlers;

/**
 * The half of a handler that <b>writes</b> a marker, which is what {@link EncryptTool} drives.
 * <p>
 * Deliberately not part of {@link ValueHandler}. A handler exists to resolve a value, and most of the ones
 * worth writing - a vault client, a file reader - have nothing to encrypt: requiring them to answer here
 * would be requiring them to throw. The two handlers this library ships implement both because they are
 * ciphers, and the tool talks to this interface so that it holds one copy of reading standard input,
 * refusing secrets on the command line and writing markers out.
 * </p>
 * <p>
 * Package-private on purpose. Nothing discovers handlers, so a third party gains nothing by implementing
 * this that it does not gain from a <code>main</code> of its own.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
interface Encrypting {

    /**
     * Encrypts several values, each with its own IV, sharing whatever a construction can legitimately
     * share between the values of one run - a salt for the symmetric one, nothing for the asymmetric.
     *
     * @param plainTexts the values to encrypt.
     * @return the tokens, in the same order.
     */
    String[] encryptAll(String... plainTexts);

    /** The marker wrapping an already encrypted token, which is what gets pasted into a file. */
    String marker(String token);
}
