/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Sources;

/**
 * @author Luigi R. Viggiano
 */
@Sources({"file:${user.dir}/src/test/resources/test.properties"})
public interface SampleConfigWithExpansion extends Config {
    public String favoriteColor();
}
