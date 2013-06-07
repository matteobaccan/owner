/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

/**
 * <p>Allows a <tt>Config</tt> object to change its property values at runtime.</p>
 * <p>Example:</p>
 * <pre>
 *     public interface MyConfig extends Config, Modifiable {
 *         @DefaultValue("10")
 *         int someProperty();
 *     }
 *
 *     public void example() {
 *         MyConfig cfg = ConfigFactory.create(MyConfig.class);
 *         int before = cfg.someProperty();                 // before is 10
 *         int old = cfg.setProperty("someProperty", "20"); // old is 10
 *         int after = cfg.someProperty();                  // after is 20
 *         int old2 = cfg.removeProperty("someProperty");   // old2 is 20
 *         int end = cfg.someProperty();                    // end is null
 *     }
 * </pre>
 *
 * @author  Luigi R. Viggiano
 * @since   1.0.4
 */
public interface Modifiable {

    /**
     * <p>Sets a given property to the specified value.</p>
     *
     * <p>Differently than {@link java.util.Properties#setProperty(String, String)},
     * if <tt>key</tt> is set to <tt>null</tt> then this call is equivalent to {@link #removeProperty(String)}.</p>
     *
     * @param key   the key to be placed into the property list.
     * @param value the value corresponding to <tt>key</tt>, or <tt>null</tt> if the property must be removed.
     * @return      the previous value of the specified key,
     *              or <code>null</code> if it did not have one.
     * @since 1.0.4
     */
    String setProperty(String key, String value);

    /**
     * Removes a given property.
     *
     * @param key   the key of the property to remove.
     * @return      the previous value of the specified key,
     *              or <code>null</code> if it did not have one.
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
}
