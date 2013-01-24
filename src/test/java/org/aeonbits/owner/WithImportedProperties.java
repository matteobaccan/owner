/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

/**
 * @author luigi
 */
public interface WithImportedProperties extends Config {
    String someValue();

    @DefaultValue("${user.home}")
    String userHome();

    @DefaultValue("${HOME}")
    String envHome();
}
