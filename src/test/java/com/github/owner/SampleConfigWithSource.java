/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package com.github.owner;


import com.github.owner.Config.Sources;

/**
 * @author Luigi R. Viggiano
 */
@Sources({"classpath:foo/bar/baz.properties",
          "file:~/.testfoobar.blahblah",
          "file:/etc/testfoobar.blahblah",
          "classpath:com/github/owner/FooBar.properties",
          "file:~/blahblah.properties"})
public interface SampleConfigWithSource extends Config {
    //  @Key("hello.world");
    //  @DefaultValue("Hello World");
    String helloWorld();

    @DefaultValue("Hello Mr. %s!")
    String helloMr(String name);

    @DefaultValue("42")
    int answerToLifeUniverseAndEverything();

    @DefaultValue("3.141592653589793")
    double pi();

    @DefaultValue("0.5")
    float half();

    @DefaultValue("false")
    boolean worldIsFlat();

    @DefaultValue("7")
    Integer daysInWeek();
}
