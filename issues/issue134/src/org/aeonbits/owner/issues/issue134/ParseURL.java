package org.aeonbits.owner.issues.issue134;

import java.net.MalformedURLException;
import java.net.URL;

public class ParseURL {

    public static void main(String[] args) throws MalformedURLException {
        String spec = "file:${my.properties}";

        URL url = new URL(spec);

        System.out.println("The parsed URL is: " + url);
    }
}
