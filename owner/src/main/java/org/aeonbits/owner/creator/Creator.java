/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.creator;

/**
 * Interface to convert Config class into something else.
 * 
 * @author Luca Taddeo
 */
public interface Creator {
    boolean parse(Class clazz, String output, String headerName, String projectName) throws Exception;
}
