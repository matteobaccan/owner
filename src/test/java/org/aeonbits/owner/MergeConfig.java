/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;

import java.io.PrintStream;
import java.io.PrintWriter;

import static org.aeonbits.owner.LoadType.MERGE;

/**
 * @author luigi
 */
@Sources({"classpath:org/aeonbits/owner/first.properties",
          "classpath:foo/bar/thisDoesntExists.properties",
          "classpath:org/aeonbits/owner/second.properties",
          "file:${user.dir}/src/test/resources/foo/bar/thisDoesntExists.properties",
          "file:${user.dir}/src/test/resources/org/aeonbits/owner/third.properties"})
@LoadPolicy(MERGE)
public interface MergeConfig extends Config {
    @DefaultValue("this should be ignored")
    String foo();
    @DefaultValue("this should be ignored")
    String bar();
    @DefaultValue("this should be ignored")
    String baz();
    @DefaultValue("this should be ignored")
    String qux();

    String quux(); // this should return null;
    @DefaultValue("theDefaultValue")
    String fubar();

    void list(PrintStream out);
    void list(PrintWriter out);
}
