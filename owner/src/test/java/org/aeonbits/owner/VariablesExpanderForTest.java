/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.util.Properties;

/**
 * @author Luigi R. Viggiano
 */
public class VariablesExpanderForTest extends VariablesExpander {
    public VariablesExpanderForTest(Properties properties) {
        super(properties);
    }

    @Override
    public String expand(String path) {
        return super.expand(path);
    }
}
