/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

/**
 * @author Luigi R. Viggiano
 */
public interface SampleConfig extends Config {
    String testKey();
    String hello(String param);

    @DefaultValue("Bohemian Rapsody - Queen")
    String favoriteSong();

    String unspecifiedProperty();

    @Key("server.http.port")
    int httpPort();

    @Key("salutation.text")
    @DefaultValue("Good Morning")
    String salutation();
}
