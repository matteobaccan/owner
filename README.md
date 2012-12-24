OWNER
=====

OWNER, a simple API to ease Java(TM) property files usage.

INTRODUCTION
------------

The inspiring idea for this API comes from how GWT handles i18n and resource loading.
You can read more about [GWT ClientBundle][1]

The problem in using GWT ClientBundle for loading property files is that it only works in client code (JavaScript), 
not standard Java classes.
Also, GWT is a big library and it is designed for different purposes. 

Since I liked the approach I decided to implement this API.

[1]: https://developers.google.com/web-toolkit/doc/latest/DevGuideClientBundle

USAGE
-----

The approach used by OWNER APIs, is to define a Java interface associated to a Java properties file.

Suppose your properties file is defined as ServerConfig.properties:

    port=80
    hostname=foobar.com
    maxThreads=100
    
To access this property you need to define a convenient Java interface in ServerConfig.java:

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

The mapping between the Java interface and the properties file can be automatically resolved by Owner API.
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

#### Undefined properties 

If, in the example, ServerConfig interface cannot be mapped to any properties file, then all the methods in the interface 
will return `null`.

If, in the example, we omit `@DefaultValue` for `maxThreads()` and we forget to define the property key in the properties 
files, `null` will be used as default value.

BUILD
-----

OWNER uses maven to build. At the moment the jars are not available on any repository, so you need to use git and maven to 
create the library jar.

    $ git clone git://github.com/lviggiano/owner.git
    $ cd owner
    $ mvn install

This will install OWNER jars in your local maven repository.

Also you can pick the jar files from the target directory created by maven `mvn install' command:

 * owner-1.0-SNAPSHOT-javadoc.jar
 * owner-1.0-SNAPSHOT-sources.jar
 * owner-1.0-SNAPSHOT.jar

DEPENDENCIES
------------

OWNER depends from [commons-lang][2] to do some variable expansions.

[2]: http://commons.apache.org/lang/

TESTS
-----

OWNER codebase is very compact, and it is fully covered by unit tests.

To execute the tests, you need maven properly installed and configured, 
then run the following command from the distribution root:

$ mvn test

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

Refer to the documentation on the [web site](http://lviggiano.github.com/owner) 
or [github wiki](https://github.com/lviggiano/owner/wiki) for further details on how to use the Owner API.

If you find some bug or have any request open an issue on [github issues](https://github.com/lviggiano/owner/issues).
