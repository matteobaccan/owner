/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Properties;

/**
 * @author Luigi R. Viggiano
 */
public class ExamplesBase {
    public static void save(File target, Properties p) throws IOException {
        Util.save(target, p);
    }

    public static File fileFromURL(String spec) throws MalformedURLException {
        return Util.fileFromURL(spec);
    }
}
