/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Set;

/**
 * <p>Allows a <tt>Config</tt> object to access the contents of the properties, providing utility methods to perform
 * consequent operations.</p>
 *
 * <p>Example:</p>
 * <pre>
 *     public interface MyConfig extends Config, Accessible {
 *         int someProperty();
 *     }
 *
 *     public void doSomething() {
 *         MyConfig cfg = ConfigFactory.create(MyConfig.class);
 *         cfg.list(System.out);
 *     }
 * </pre>
 * <p>These methods will print the list of properties, see {@link java.util.Properties#list(java.io.PrintStream)} and
 * {@link java.util.Properties#list(java.io.PrintWriter)}.</p>
 *
 * @author Luigi R. Viggiano
 * @since 1.0.4
 */
public interface Accessible extends Config {

    /**
     * Prints this property list out to the specified output stream. This method is useful for debugging.
     *
     * @param out an output stream.
     * @throws ClassCastException if any key in this property list is not a string.
     * @see java.util.Properties#list(java.io.PrintStream)
     * @since 1.0.4
     */
    void list(PrintStream out);

    /**
     * Prints this property list out to the specified output stream. This method is useful for debugging.
     *
     * @param out an output stream.
     * @throws ClassCastException if any key in this property list is not a string.
     * @see java.util.Properties#list(java.io.PrintWriter)
     * @since 1.0.4
     */
    void list(PrintWriter out);

    /**
     * Stores the underlying properties into an {@link java.io.OutputStream}.
     * <p>
     * Notice that method {@link java.util.Properties#store(java.io.Writer, String)} is not implemented since it's not
     * available in JDK 1.5 (while the target of this library is Java 1.5+).
     *
     * @param out      an output stream.
     * @param comments a description of the property list.
     * @throws IOException if writing this property list to the specified output stream throws an <tt>IOException</tt>.
     * @see java.util.Properties#store(java.io.OutputStream, String)
     * @since 1.0.4
     */
    void store(OutputStream out, String comments) throws IOException;

    /**
     * Searches for the property with the specified key in this property list.
     * If the key is not found in this property list, the default property list,
     * and its defaults, recursively, are then checked. The method returns
     * <code>null</code> if the property is not found.
     *
     * @param   key   the property key.
     * @return  the value in this property list with the specified key value.
     * @see     java.util.Properties#getProperty(String)
     * @since 1.0.4
     */
    String getProperty(String key);

    /**
     * Searches for the property with the specified key in this property list.
     * If the key is not found in this property list, the default property list,
     * and its defaults, recursively, are then checked. The method returns the
     * default value argument if the property is not found.
     *
     * @param   key            the property key.
     * @param   defaultValue   a default value.
     * @return  the value in this property list with the specified key value.
     * @see java.util.Properties#getProperty(String, String)
     *
     * @since 1.0.4
     */
    String getProperty(String key, String defaultValue);

    /**
     * Emits an XML document representing all of the properties contained
     * in this table.
     *
     * <p> An invocation of this method of the form <tt>props.storeToXML(os,
     * comment)</tt> behaves in exactly the same way as the invocation
     * <tt>props.storeToXML(os, comment, "UTF-8");</tt>.
     *
     * @param os the output stream on which to emit the XML document.
     * @param comment a description of the property list, or <code>null</code>
     *        if no comment is desired.
     * @throws IOException if writing to the specified output stream
     *         results in an <tt>IOException</tt>.
     * @throws NullPointerException if <code>os</code> is null.
     * @throws ClassCastException  if this <code>Properties</code> object
     *         contains any keys or values that are not
     *         <code>Strings</code>.
     * @since 1.0.5
     */
    void storeToXML(OutputStream os, String comment) throws IOException;

    /**
     * Returns a set of keys in this property list
     * including distinct keys in the default property list if a key
     * of the same name has not already been found from the main
     * properties list.
     * <p>
     * The returned set is not backed by the <tt>Properties</tt> object.
     * Changes to this <tt>Properties</tt> are not reflected in the set,
     * or vice versa.
     *
     * @return  a set of keys in this property list, including the keys in the
     *          default property list.
     * @throws  ClassCastException if any key in this property list
     *          is not a string.
     * @see     java.util.Properties#defaults
     * @see     java.util.Properties#stringPropertyNames()
     * @see     java.util.Properties#propertyNames()
     * @since   1.0.5
     */
    Set<String> propertyNames();

}
