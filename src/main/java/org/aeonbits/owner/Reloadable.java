/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

/**
 * Allows the a Config object to implement the reload of the properties.
 *
 * @author Luigi
 * @since 1.0.4
 */
public interface Reloadable {
    public void reload();
}
