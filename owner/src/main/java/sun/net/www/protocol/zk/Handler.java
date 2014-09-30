package sun.net.www.protocol.zk;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * Author: Koray Sariteke
 */
public class Handler extends URLStreamHandler {
    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return new ZookeeperConnection(url);
    }
}
