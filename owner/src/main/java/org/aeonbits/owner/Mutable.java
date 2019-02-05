/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * <p>Allows a <code>Config</code> object to change its property values at runtime.</p>
 * <p>Example:</p>
 * <pre>
 *     public interface MyConfig extends Config, Mutable {
 *         &#64;DefaultValue("18")
 *         int minAge();
 *     }
 *
 *     public void example() {
 *         MyConfig cfg = ConfigFactory.create(MyConfig.class);
 *         int before = cfg.minAge();                 // before = 18
 *         int old = cfg.setProperty("minAge", "21"); // old = 18
 *         int after = cfg.minAge();                  // after = 21
 *         int old2 = cfg.removeProperty("minAge");   // old2 = 21
 *         int end = cfg.minAge();                    // end = null
 *     }
 * </pre>
 *
 * @author Luigi R. Viggiano
 * @since 1.0.4
 */
public interface Mutable extends Config {

    /**
     * <p>Sets a given property to the specified value.</p>
     * <p>Differently than {@link
     * java.util.Properties#setProperty(String, String)}, if <code>key</code> is set to <code>null</code> then this call is
     * equivalent to {@link #removeProperty(String)}.</p>
     *
     * @param key   the key to be placed into the property list.
     * @param value the value corresponding to <code>key</code>, or <code>null</code> if the property must be removed.
     * @return the previous value of the specified key, or <code>null</code> if it did not have one.
     * @since 1.0.4
     */
    String setProperty(String key, String value);

    /**
     * Removes a given property.
     *
     * @param key the key of the property to remove.
     * @return the previous value of the specified key, or <code>null</code> if it did not have one.
     * @see java.util.Hashtable#remove(Object)
     * @since 1.0.4
     */
    String removeProperty(String key);

    /**
     * Clears all properties.
     *
     * @since 1.0.4
     */
    void clear();

    /**
     * Reads a property list (key and element pairs) from the input byte stream.
     *
     * @param inStream the input stream.
     * @throws java.io.IOException      if an error occurred when reading from the input stream.
     * @throws IllegalArgumentException if the input stream contains a malformed Unicode escape sequence.
     * @see java.util.Properties#load(java.io.InputStream)
     * @since 1.0.4
     */
    void load(InputStream inStream) throws IOException;

    /**
     * Reads a property list (key and element pairs) from the input character stream in a simple line-oriented format.
     *
     * @param reader the input character stream.
     * @throws IOException              if an error occurred when reading from the input stream.
     * @throws IllegalArgumentException if a malformed Unicode escape appears in the input.
     * @see java.util.Properties#load(java.io.Reader)
     * @since 1.0.4
     */
    void load(Reader reader) throws IOException;

    /**
     * Adds a {@link PropertyChangeListener} to the Mutable interface.
     *
     * @param listener the listener to be added.
     * @since 1.0.5
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Removes a {@link PropertyChangeListener} from the Mutable interface.
     *
     * @param listener the property change listener to be removed
     */
    void removePropertyChangeListener(PropertyChangeListener listener);

    /**
     * Adds a PropertyChangeListener to the listener list for a specific
     * property.
     * If <code>propertyName</code> or <code>listener</code> is <code>null</code>,
     * no exception is thrown and no action is taken.
     *
     * @param propertyName one of the property names listed above
     * @param listener the property change listener to be added
     */
    void addPropertyChangeListener(String propertyName, PropertyChangeListener listener);

}
