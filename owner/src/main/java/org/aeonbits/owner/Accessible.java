/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

/**
 * <p>Allows a <code>Config</code> object to access the contents of the properties, providing utility methods to perform
 * consequent operations.</p>
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
 * <h2>Which of these methods expand the variables</h2>
 * <p>
 * The methods that answer with a <b>value</b> expand it, exactly as the mapping methods do:
 * {@link #getProperty(String)}, {@link #getProperty(String, String)} and {@link #fill(Map)}. The methods
 * that write the properties <b>out</b> do not: {@link #list(PrintStream)}, {@link #list(PrintWriter)},
 * {@link #store(OutputStream, String)}, {@link #store(Writer, String)} and
 * {@link #storeToXML(OutputStream, String)} reproduce the properties as they were written, because that
 * is what makes them round-trip &mdash; a <code>${...}</code> expanded on the way out is a
 * <code>${...}</code> lost from the file on the next save. {@link #getRawProperty(String)} is the way to
 * read a single value the same way.
 * </p>
 * <p>
 * Only the expansion crosses over. A {@link Config.EncryptedValue}, a {@link Config.ConverterClass} or a
 * preprocessor is declared <i>on a method</i>, and a property asked for by name has no method to read the
 * declaration from; the expansion lives in the value itself, and so applies wherever the value is read.
 * </p>
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
     *
     * @param out      an output stream.
     * @param comments a description of the property list.
     * @throws IOException if writing this property list to the specified output stream throws an <code>IOException</code>.
     * @see java.util.Properties#store(java.io.OutputStream, String)
     * @since 1.0.4
     */
    void store(OutputStream out, String comments) throws IOException;

    /**
     * Stores the underlying properties into a {@link java.io.Writer}.
     *
     * @param out      an output character stream.
     * @param comments a description of the property list.
     * @throws IOException if writing this property list to the specified writer throws an <code>IOException</code>.
     * @see java.util.Properties#store(java.io.Writer, String)
     * @since 2.0.0
     */
    void store(Writer out, String comments) throws IOException;

    /**
     * Fills the given {@link java.util.Map} with the properties contained by this object. <br>
     * This is useful to extract the content of the config object into a {@link java.util.Map}.
     * <p>
     * Notice that you can specify a properties object as parameter instead of a map,
     * since {@link java.util.Properties} implements the {@link java.util.Map} interface.
     * </p>
     * <p>
     * <b>The values are expanded</b>, as they are for {@link #getProperty(String)} and for the mapping
     * methods: this hands out the configuration to be used, not to be written back. A map filled here and
     * then stored to a file would save the expanded values and lose the <code>${...}</code> that produced
     * them &mdash; use {@link #store(java.io.OutputStream, String)} to write the properties back, or
     * {@link #getRawProperty(String)} over {@link #propertyNames()} to choose key by key.
     * </p>
     *
     * @param map the {@link java.util.Map} to fill.
     * @since 1.0.9
     */
    void fill(Map map);

    /**
     * Searches for the property with the specified key in this property list.
     * If the key is not found in this property list, the default property list,
     * and its defaults, recursively, are then checked. The method returns
     * <code>null</code> if the property is not found.
     * <p>
     * <b>Since 2.0.0 the value is expanded</b>: a <code>${...}</code> in it is resolved exactly as it is
     * for the method that maps the same key, so that a property read by name and the same property read
     * through its method no longer answer differently. Up to 1.0.12 the text was returned as written.
     * {@link #getRawProperty(String)} returns it as written.
     * </p>
     * <p>
     * What is <i>declared</i> on a mapping method is not applied here, and cannot be: a
     * {@link Config.EncryptedValue}, a {@link Config.ConverterClass} or a preprocessor belongs to a
     * method, and a key asked for by name has none. Variable expansion belongs to the value, which is why
     * it is the one thing that applies to both.
     * </p>
     *
     * @param   key   the property key.
     * @return  the value in this property list with the specified key value, expanded.
     * @throws  IllegalArgumentException if the value holds a circular variable reference.
     * @see     java.util.Properties#getProperty(String)
     * @see     #getRawProperty(String)
     * @since 1.0.4
     */
    String getProperty(String key);

    /**
     * Searches for the property with the specified key in this property list.
     * If the key is not found in this property list, the default property list,
     * and its defaults, recursively, are then checked. The method returns the
     * default value argument if the property is not found.
     * <p>
     * The value is expanded, as it is for {@link #getProperty(String)}, and <b>so is the given default</b>:
     * it is text supplied by the caller at the call, so expanding it can surprise nobody, and a default
     * that could not carry a <code>${...}</code> would be the odd one out.
     * </p>
     *
     * @param   key            the property key.
     * @param   defaultValue   a default value.
     * @return  the value in this property list with the specified key value, expanded; the given default,
     *          expanded, when there is no such property.
     * @throws  IllegalArgumentException if the value holds a circular variable reference.
     * @see java.util.Properties#getProperty(String, String)
     * @see #getRawProperty(String, String)
     *
     * @since 1.0.4
     */
    String getProperty(String key, String defaultValue);

    /**
     * Searches for the property with the specified key and returns its value <b>as it was written</b>,
     * with the variables left unexpanded.
     * <p>
     * This is what {@link #getProperty(String)} returned up to 1.0.12, and it is what a caller wants when
     * the value is on its way back to a file rather than on its way to being used: expanding a
     * <code>${...}</code> and saving the result destroys the reference that produced it.
     * </p>
     *
     * @param   key   the property key.
     * @return  the value in this property list with the specified key value, as it was written;
     *          <code>null</code> if the property is not found.
     * @see     #getProperty(String)
     * @since 2.0.0
     */
    String getRawProperty(String key);

    /**
     * Searches for the property with the specified key and returns its value <b>as it was written</b>,
     * with the variables left unexpanded, or the given default when there is no such property.
     *
     * @param   key            the property key.
     * @param   defaultValue   a default value, returned as it is given.
     * @return  the value in this property list with the specified key value, as it was written; the given
     *          default when there is no such property.
     * @see     #getProperty(String, String)
     * @since 2.0.0
     */
    String getRawProperty(String key, String defaultValue);

    /**
     * Writes this configuration to a properties file, <b>keeping the file that is already there</b>.
     * <p>
     * Unlike {@link #store(OutputStream, String)}, which serialises a map and so loses the comments, the
     * blank lines and the order, this rewrites the file in place:
     * </p>
     * <ul>
     * <li>every key the file already has stays <b>where it is</b>, with its value brought up to date;</li>
     * <li>keys the file does not have are <b>appended</b>, in alphabetical order among themselves;</li>
     * <li>keys the file has and this interface has never heard of are <b>left alone</b> — an
     *     <code>application.properties</code> is usually read by more than one thing;</li>
     * <li>the comment above a key with a {@link Config.Description} is <b>rewritten from the code</b>;
     *     every other comment is left as it was.</li>
     * </ul>
     * <p>
     * If the file does not exist it is created, keys in alphabetical order — which is how a template is
     * generated from an interface that has never been configured.
     * </p>
     * <p>
     * <b>Only what belongs in the file is written.</b> A configuration merging system properties, the
     * environment and a file holds all of them, and <code>store()</code> writes all of them — so saving
     * it puts your environment in your file. This writes what came from a file, what was set through
     * {@link Mutable#setProperty}, and the defaults, and leaves the environment where it is.
     * </p>
     * <p>
     * A {@link Config.Sensitive} value is written <b>in the clear</b>, exactly as
     * {@link #store(OutputStream, String)} does, because masking it here would replace the password in
     * your file with asterisks. Masking applies to {@link #list} and <code>toString</code>, not to
     * writing.
     * </p>
     *
     * <p>
     * <b>Properties files only.</b> OWNER reads INI, <code>.env</code> and XML too, and this does not
     * write them: each is a family of dialects chosen by options on the source, and the dialect that
     * read a file is not known here — an INI written in the wrong one, or an XML rebuilt from keys its
     * elements were flattened into, would be a file we read back differently from how we wrote it. That
     * is worse than not writing it.
     * </p>
     *
     * @param file the file to write; created if it does not exist
     * @throws IOException if the file cannot be read or written
     * @see Config.Description
     * @since 2.0.0
     */
    void save(File file) throws IOException;

    /**
     * Emits an XML document representing all of the properties contained
     * in this table.
     *
     * <p> An invocation of this method of the form <code>props.storeToXML(os,
     * comment)</code> behaves in exactly the same way as the invocation
     * <code>props.storeToXML(os, comment, "UTF-8");</code>.
     *
     * @param os the output stream on which to emit the XML document.
     * @param comment a description of the property list, or <code>null</code>
     *        if no comment is desired.
     * @throws IOException if writing to the specified output stream
     *         results in an <code>IOException</code>.
     * @throws NullPointerException if <code>os</code> is null.
     * @throws ClassCastException  if this <code>Properties</code> object
     *         contains any keys or values that are not
     *         <code>Strings</code>.
     * @since 1.0.5
     * @see java.util.Properties#storeToXML(java.io.OutputStream, String)
     */
    void storeToXML(OutputStream os, String comment) throws IOException;

    /**
     * Returns a set of keys in this property list
     * including distinct keys in the default property list if a key
     * of the same name has not already been found from the main
     * properties list.
     * <p>
     * The returned set is not backed by the <code>Properties</code> object.
     * Changes to this <code>Properties</code> are not reflected in the set,
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
