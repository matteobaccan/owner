/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Interfaces extending from this will inherit list() methods to aid debugging.
 *
 * These methods will print the list of properties, see {@link java.util.Properties#list(java.io.PrintStream)} and
 * {@link java.util.Properties#list(java.io.PrintWriter)}.
 *
 * @author Luigi R. Viggiano
 * @since 1.0.4
 */
public interface Listable {

    /**
     * Prints this property list out to the specified output stream.
     * This method is useful for debugging.
     *
     * @param   out   an output stream.
     * @throws  ClassCastException if any key in this property list
     *          is not a string.
     * @see     {@link java.util.Properties#list(java.io.PrintStream)}.
     * @since 1.0.4
     */
    public void list(PrintStream out);

    /**
     * Prints this property list out to the specified output stream.
     * This method is useful for debugging.
     *
     * @param   out   an output stream.
     * @throws  ClassCastException if any key in this property list
     *          is not a string.
     * @see {@link java.util.Properties#list(java.io.PrintWriter)}.
     * @since 1.0.4
     */
    public void list(PrintWriter out);
}
