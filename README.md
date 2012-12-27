OWNER
=====

OWNER, a simple API to ease Java™ property files usage.

INTRODUCTION
------------

The inspiring idea for this API comes from GWT i18n (see [here][gwt-i18n]).

The problem in using GWT i18n for loading property files is that it only works in client code (JavaScript), 
not standard Java™ classes.
Also, GWT is a big library and it is designed for different purposes. 

Since I liked the approach I decided to implement something similar, and here we are.

  [gwt-i18n]: https://developers.google.com/web-toolkit/doc/latest/DevGuideI18nConstants

USAGE
-----

The approach used by OWNER APIs, is to define a Java™ interface associated to a Java™ properties file.

Suppose your properties file is defined as ServerConfig.properties:

    port=80
    hostname=foobar.com
    maxThreads=100
    
To access this property you need to define a convenient Java™ interface in ServerConfig.java:

    public interface ServerConfig extends Config {
        int port();
        String hostname();
        int maxThreads();
    }
    
Then, you can use it from inside your code:

    public class MyApp {    
        public static void main(String[] args) {
            ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
            System.out.println("Server " + cfg.hostname() + ":" + cfg.port() + " will run " + cfg.maxThreads());
        }
    }

The mapping between the Java™ interface and the properties file can be automatically resolved by OWNER API.
By default OWNER API tries to load the properties for the interface com.foo.bar.ServerConfig from the classpath as
com.foo.bar.ServerConfig.properties; then it tries to assoaciate every method of the interface to the property keys 
contained in the properties file.

This default mapping can be tailored to your needs using some annotations on the interface. 

Example:

    @Sources({ "file:~/.myapp.config", "file:/etc/myapp.config", "classpath:foo/bar/baz.properties" })
    public interface ServerConfig extends Config {
        
        @Key("server.http.port")
        int port();
        
        @Key("server.host.name")
        String hostname();
        
        @Key("server.max.threads");
        @DefaultValue("42")
        int maxThreads();
    }

In the above example, OWNER will try to load the properties from several `@Sources`:

 1. first, it will try to load from user's home directory ~/.myapp.config
 2. if the previous attempt fails, then it will try to load the properties from /etc/myapp.config
 3. and, as last resort, it will try to load the properties from the classpath loding the resource identified by the path foo/bar/baz.properties

In the `@Sources` annotation you can also specify system properties and/or environment variables with the syntax 
`file:${user.home}/.myapp.config` (this gets resolved by 'user.home' System property) or `file:${HOME}/.myapp.config`  
(this gets resolved by the$HOME environment variable). The `~` used in the previous example is another example of 
variable expansion, and it is equivalent to `${user.home}`

Did you notice that there is also the `@DefaultValue("42")` annotation specified in the example?
This annotation gets automatically converted to `int`, since `maxThreads()` returns an `int`, and the value specified is 
used as default, if `server.max.threads` key is not specified in the property file.

The `@DefaultValue` is very confortable to use, and the basic type conversion between the `String` value and the method 
return type are done automatically.

### UNDEFINED PROPERTIES

If, in the example, ServerConfig interface cannot be mapped to any properties file, then all the methods in the interface 
will return `null`, unless on the methods it is defined a `@DefaultValue` annotation, of course.

If, in the example, we omit `@DefaultValue` for `maxThreads()` and we forget to define the property key in the properties 
files, `null` will be used as default value.

### PARAMETRIZED PROPERTIES

Another neat feature, is the possibility to provide parameters on method interfaces, then the property value shall respect 
the positional notation specified by the [`java.util.Formatter`][fmt] class.

  [fmt]: http://docs.oracle.com/javase/7/docs/api/java/util/Formatter.html#syntax

Example: 

    public interface SampleParamConfig extends Config {
        @DefaultValue("Hello Mr. %s!")
        String helloMr(String name);
    }
    
    SampleParamConfig cfg = ConfigFactory.create(SampleParamConfig.class);
    System.out.println(cfg.helloMr("Luigi")); // will println 'Hello Mr. Luigi!'

JAVADOCS
--------

API javadocs can be found [here][javadocs].

  [javadocs]: http://lviggiano.github.com/owner/target/site/apidocs/index.html

BUILD
-----

OWNER uses maven to build.

    $ git clone git://github.com/lviggiano/owner.git
    $ cd owner
    $ mvn install

This will install OWNER jars in your local maven repository. Or, you can pick the jar files from the target directory.

MAVEN
-----

If you are using maven, you can add the OWNER dependency in your project.

First you need to add the OWNER repository:

    <repositories>
        <repository>
            <id>owner-repo</id>
            <url>http://lviggiano.github.com/owner/target/repo/release</url>
        </repository>
    </repositories>

Then you can get the dependency:

    <dependencies>
        <dependency>
            <groupId>owner</groupId>
            <artifactId>owner</artifactId>
            <version>1.0</version>
        </dependency>
    </dependencies>

In future, I may try to upload the jars in Maven Central Repository, so this won't be necessary.


DOWNLOADS
--------

You can download pre-built binaries from following links:

 * http://lviggiano.github.com/owner/target/owner-1.0-bin.tar.bz2
 * http://lviggiano.github.com/owner/target/owner-1.0-bin.tar.gz
 * http://lviggiano.github.com/owner/target/owner-1.0-bin.zip

DEPENDENCIES
------------

OWNER 1.0 depends from [commons-lang][] to do some variable expansions.

  [commons-lang]: http://commons.apache.org/lang/

From version 1.0.1 this dependency will be removed.

TESTS
-----

OWNER codebase is very compact, and it is [fully covered][] by unit tests.

To execute the tests, you need maven properly installed and configured, 
then run the following command from the distribution root:

    $ mvn test

  [fully covered]: http://lviggiano.github.com/owner/target/site/cobertura/index.html

FAQ
---
### What does "OWNER" name mean?

Since this API is used to access *Properties* files, and we implement interfaces to deal with those, 
somehow interfaces are *owners* for the properties. So here comes the name OWNER.

### Is OWNER a stable API?

The codebase is very compact, and the test coverage is almost 100%. So there shouldn't be many bugs to deal with.
You have the source, you can help improving the library and fix the bugs if you find some.

Still, OWNER is very early, and APIs may change in the
future to add/change some behaviors. 
For example I would like to specify an annotation to define additional properties load policies. 
But the goal is to keep the API backward compatible.

LICENSE
-------

OWNER is released under the BSD license. 

See LICENSE file included for the details.

MORE INFORMATION
----------------

Refer to the documentation on the [web site][]
or [github wiki][] for further details on how to use the OWNER API.

If you find some bug or have any request open an issue on [github issues][].

  [web site]: http://lviggiano.github.com/owner
  [github wiki]: https://github.com/lviggiano/owner/wiki
  [github issues]: https://github.com/lviggiano/owner/issues
