/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package sun.net.www.protocol.zookeper;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @author Koray Sariteke
 * @author Luigi R. Viggiano
 */
public class Handler extends URLStreamHandler {
    public static final String PROTOCOL = "zookeper";

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return new ZookeeperConnection(url);
    }
}
