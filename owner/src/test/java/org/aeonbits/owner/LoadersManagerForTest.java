/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.loaders.Loader;

import java.net.URL;

/**
 * @author Luigi R. Viggiano
 */
public class LoadersManagerForTest extends LoadersManager {
    @Override
    public Loader findLoader(URL url) {
        return super.findLoader(url);
    }
}
