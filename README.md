Owner
=====

Owner, a simple API to ease Java(TM) property files usage.

INTRODUCTION
------------

The inspiring idea for this API comes from how GWT handles i18n and resource loading.
You can read more about [GWT ClientBundle][1]

The problem in using GWT ClientBundle for loading property files is that it only works in client code, 
that later gets translated in JavaScript.
GWT is a big library and it is designed for different purposes. But since I liked the approach I decided 
to implement this API.

[1]: https://developers.google.com/web-toolkit/doc/latest/DevGuideClientBundle

USAGE
-----

The approach used by Owner APIs, is to define a Java interface associated to a Property file.

Suppose your property file is ServerConfig.properties:

    port=80
    hostname=foobar.com
    maxThreads=100
    
To access this property you need to define a convenient Java interface as ServerConfig.java:

    public interface ServerConfig extends Config {
        int port();
        String hostname();
        int maxThreads();
    }
    
Then, you can use from inside your code:

    public class MyApp {    
        public static void main(String[] args) {
            ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
            System.out.println("Server " + cfg.hostname() + ":" + cfg.port() + " will run " + cfg.maxThreads());
        }
    }

The mapping between the Java interface and the Properties file can be automatically resolved by Owner API.
By default Owne API tries to load the properties for the interface com.foo.bar.ServerConfig from the classpath as
com.foo.bar.ServerConfig.properties; then it tries to assoaciate every method to the property keys contained in the file.

But this default mapping can be tailored to your needs annotating the interface. 

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

In the above example, Owner will try to load the properties for several `@Sources`:

 1. first, it will try to load from user's home directory ~/.myapp.config
 2. if the previous attempt fails, then it will try to load the properties from /etc/myapp.config
 3. and, as last resort, it will try to load the properties from the classpath loding the resource identified by the path foo/bar/baz.properties

In the `@Sources` annotation you can also specify system properties and/or environment variables with the syntax 
`file:${user.home}/.myapp.config` (this gets resolved by 'user.home' System property) or `file:${HOME}/.myapp.config` 
(this gets resolved by the$HOME environment variable). The `~` used in the previous example is another example of 
expansion, and it is equivalent to `${user.home}`

Did you notice that there is also the `@DefaultValue("42")` annotation specified in the example?
This annotation gets automatically converted to `int`, since `maxThreads()` returns an `int`, and the value specified is 
used as default, if `server.max.threads` key is not specified in the property file.

The `@DefaultValue` is so confortable to use, and the basic type conversion between the `String` value and the method return type are done automatically.

### What does "Owner" name mean?

Since this API is used to access *Properties* files, and we implement interfaces to deal with those, 
somehow interfaces are owners for the properties.

BUILD
-----

Owner uses maven. At the moment the Owner jar is not available on any repository, so you need to use git and maven to 
create the library jar.

    $ git clone git://github.com/lviggiano/owner.git
    $ cd owner
    $ mvn install

This will install owner jars in your local maven repository.

Also you can pick the jar files from the target directory created by maven `mvn install' command:

 * owner-1.0-SNAPSHOT-javadoc.jar
 * owner-1.0-SNAPSHOT-sources.jar
 * owner-1.0-SNAPSHOT.jar

-----

Owner depends from [commons-lang 2.6][2] to do some variable expansions.


TESTS
-----

Owner is fully covered by unit tests.

To execute the tests, you need maven properly installed and configured, 
then run the following command from the distribution root:

$ mvn test


MORE INFORMATION
----------------

Refer to the documentation on the web site (http://lviggiano.github.com/owner)
for further details on how to use the Owner API.
