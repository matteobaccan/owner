/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.java8;

import org.aeonbits.owner.Config;

/**
 * @author Luigi R. Viggiano
 */
public interface ConfigWithJava8Features extends Config {

    @DefaultValue("100")
    public int oneHundred();

    default public Integer sum(Integer a, Integer b) {
        return Integer.sum(a, b);
    }

    public static int min(int a, int b) {
        return Integer.min(a, b);
    }
}
