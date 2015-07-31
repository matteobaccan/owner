package org.aeonbits.owner.issues.issue134;

import java.net.URI;
import java.net.URISyntaxException;

public class ParseURI {

    public static void main(String[] args) throws URISyntaxException {
        String spec = "file:${my.properties}";

        URI url = new URI(spec);

        System.out.println("The parsed URI is: " + url);
    }
}
