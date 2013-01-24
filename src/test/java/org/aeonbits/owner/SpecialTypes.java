/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.File;
import java.net.URL;

/**
 * @author luigi
 */
public interface SpecialTypes extends Config {
    @DefaultValue("foobar.txt")
    File sampleFile();

    @DefaultValue("http://owner.aeonbits.org")
    URL sampleURL();

    @DefaultValue("test")
    CustomType customType();

    @DefaultValue("Hello %s!")
    CustomType salutation(String name);

    @DefaultValue("this should raise an exception")
    InvalidCustomType invalid();
}
