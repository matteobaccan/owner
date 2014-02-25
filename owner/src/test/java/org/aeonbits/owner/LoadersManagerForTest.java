/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.net.URI;

import org.aeonbits.owner.loaders.Loader;

/**
 * @author Luigi R. Viggiano
 */
public class LoadersManagerForTest extends LoadersManager {
    @Override
    public Loader findLoader(URI uri) {
        return super.findLoader(uri);
    }
}
