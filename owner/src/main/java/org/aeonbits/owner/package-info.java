/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

/**
 *  <p>
 *  The goal of OWNER API is to minimize the code required to handle application configuration through Java properties
 *  files.
 *  </p>
 *
 *  <p>
 *  The approach used by OWNER APIs, is to define a Java interface associated to a properties file.
 *  </p>
 *
 *  Suppose your properties file is defined as ServerConfig.properties:
 *
 *  <pre>
 *  port=80
 *  hostname=foobar.com
 *  maxThreads=100
 *  </pre>
 *
 *  To access this property you need to define a convenient Java interface in ServerConfig.java:
 *
 *  <pre>
 *  public interface ServerConfig extends Config {
 *      int port();
 *      String hostname();
 *
 *      &#64;DefaultValue("42");
 *      int maxThreads();
 *  }
 *  </pre>
 *
 * <p>
 * We'll call this interface the Properties Mapping Interface or just Mapping Interface since its goal is to map
 * Properties into a an easy to use piece of code.
 * </p>
 *
 * <p>
 * Owner has a lot of features and its behavior is fully customizable to your needs.
 * </p>
 * <p>
 * Have a look at the full documentation from the <a href="http://owner.aeonbits.org/" target="_top">OWNER website</a>.
 * </p>
 */
package org.aeonbits.owner;
