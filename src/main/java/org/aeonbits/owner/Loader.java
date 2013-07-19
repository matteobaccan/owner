/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

/**
 * @author Luigi R. Viggiano
 */
abstract class Loader {
    private static InputStream getInputStream(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        if (conn == null)
            return null;
        return conn.getInputStream();
    }

    static boolean load(Properties result, URL url) throws IOException {
        InputStream stream = getInputStream(url);
        if (stream != null)
            try {
                result.load(stream);
                return true;
            } finally {
                close(stream);
            }
        return false;
    }

    static Properties load(URL url) throws IOException {
        Properties result = new Properties();
        load(result, url);
        return result;
    }

    private static void close(InputStream inputStream) throws IOException {
        if (inputStream != null)
            inputStream.close();
    }

}
