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
public interface ConfigWithSubstitutionFile  extends Config {
    String story();
    String animal();
    String target();
    @Key("target.attribute")
    String targetAttribute();
}
