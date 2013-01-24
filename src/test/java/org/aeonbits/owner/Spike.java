/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.IOException;
import java.util.Properties;

/**
 * @author luigi
 */
public class Spike {
    public static void main(String[] args) throws IOException {
        Properties props = new Properties();
        props.load(Spike.class.getResourceAsStream("first.properties"));
        props.load(Spike.class.getResourceAsStream("second.properties"));
        props.list(System.out);
    }
}
