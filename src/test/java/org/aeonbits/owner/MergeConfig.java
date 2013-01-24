/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;

import static org.aeonbits.owner.LoadType.MERGE;

/**
 * @author luigi
 */
@Sources({"classpath:org/aeonbits/owner/first.properties", "classpath:org/aeonbits/owner/second.properties"})
@LoadPolicy(MERGE)
public interface MergeConfig extends Config {
    String foo();
    String bar();
    String baz();
}
