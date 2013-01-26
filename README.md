OWNER
=====

OWNER, a simple API to ease Java™ property files usage.

INTRODUCTION
------------

The goal of OWNER API is to minimize the code required to handle application configuration through Java™ properties files.

The inspiring idea for this API comes from GWT i18n (see [here][gwt-i18n]).  
The problem in using GWT i18n for loading property files is that it only works in client code (JavaScript), 
not standard Java™ classes.  
Also, GWT is a big library and it is designed for different purposes, than configuration.   
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
        @DefaultValue("42")
        int maxThreads();
    }
    
Then, you can use it from inside your code:

    public class MyApp {    
        public static void main(String[] args) {
            ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
            System.out.println("Server " + cfg.hostname() + ":" + cfg.port() + " will run " + cfg.maxThreads());
        }
    }

Did you notice that there is also the `@DefaultValue("42")` annotation specified in the example? It is used in case the
`maxThread` key is missing from the properties file.  
This annotation gets automatically converted to `int`, since `maxThreads()` returns an `int`. See below to learn more
about automatic type conversion.  

With annotations, you can also customize the property keys:

    # Example of property file 'ServerConfig.properties'
    server.http.port=80
    server.host.name=foobar.com
    server.max.threads=100

    /*
     * Example of ServerConfig.java interface mapping the previous properties file
     */
    public interface ServerConfig extends Config {
        @Key("server.http.port")
        int port();

        @Key("server.host.name")
        String hostname();

        @Key("server.max.threads");
        @DefaultValue("42")
        int maxThreads();
    }

The `@DefaultValue` is very confortable to use, and the basic type conversion between the `String` value and the method
return type are done automatically.

### PROPERTIES FILES LOADING LOGIC

The mapping between the Java™ interface and the properties file is automatically resolved by OWNER API by matching the
class name and the properties file name. So if your interface is com.foo.bar.ServerConfig.java, it will try to associate
to com.foo.bar.ServerConfig.properties from the classpath.

But if you want more, this logic can be tailored to your needs using some annotations.

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

 1. first, it will try to load the properties file from user's home directory ~/.myapp.config, if this is found, this
    file alone will be used.
 2. if the previous attempt fails, then it will try to load the properties file from /etc/myapp.config, and if this is
    found, this one will be used.
 3. and, as last resort, it will try to load the properties from the classpath loading the resource identified by the
    path foo/bar/baz.properties.
 4. if none of the previous URL resources is found, then the Java interface will not be associated to any file, and only
    `@DefaultValue` will be used where specified, where not specified `null` will be returned.

So all properties will be loaded from only one file: the first that is found.  
If this property is not specified by the properties files, the `@DefaultValue` is used.  
*Only the first available properties file will be loaded, others will be ignored*.  
For instance, if the file ~/.myapp.config is found, only that one will be considered; if that file doesn't defines the
`maxThreads` property, the `@DefaultValue` will be returned; if `maxThreads` is specified inside `/etc/myapp.config` it
will not be considered, since ~/.myapp.config prevailed, because it is specified before in the `@Sources` annotation.  
This load logic, is identified as "FIRST", since only the first file found will be considered, and it is the default
logic adopted when the `@Source` annotation is specified with multiple URLs.  
You can also specify this load policy explicitly using `@LoadPolicy(LoadType.FIRST)` on the interface declaration.

But what if you want to have some *ovverriding* between properties? This is definitely possible: you can do it with
the annotation `@LoadPolicy(LoadType.MERGE)`:

    @LoadPolicy(LoadType.MERGE)
    @Sources({ "file:~/.myapp.config", "file:/etc/myapp.config", "classpath:foo/bar/baz.properties" })
    public interface ServerConfig extends Config {
        ...
    }

In this case, *every property* will be loaded from all the specified URLs, and the first will prevail.  
So, following logic will apply:

 1. first, it will try to load the given property from ~/.myapp.config;
    if the given property is found the associated value will be returned.
 2. then it will try to load the given property from /etc/myapp.config;
    if the property is found the value associated will be returned.
 3. and as last resort it will try to load the given property from the classpath from the resource identified
    by the path foo/bar/baz.properties; if the property is found, the associated value is returned.
 4. if the given property is not found of any of the above cases, it will be returned the value specified by the
    `@DefaultValue` if specified, otherwise null will be returned.

So basically we produce a merge between the properties files where the first property files overrides latter specified ones.  
So, in the previous example, when a property is not specified in `~/.myapp.confing` then it can be loaded from `/etc/mya.config`.

The `@Sources` annotation accepts system properties and/or environment variables with the syntax
`file:${user.home}/.myapp.config` (this gets resolved by 'user.home' System property) or `file:${HOME}/.myapp.config`
(this gets resolved by the$HOME environment variable). The `~` used in the previous example is another example of
variable expansion, and it is equivalent to `${user.home}`.

### IMPORTING PROPERTIES

Additionally to the loading logic, you have another mechanism to load your properties into a configuration mapping
interface. And this mechanism is to specify a [Properties][properties] object programmatically during
`ConfigFactory.create()` invocation.

Example:

        public interface ImportConfig extends Config {

            @DefaultValue("apple")
            String foo();

            @DefaultValue("pear")
            String bar();

            @DefaultValue("orange")
            String baz();

        }

        // then...

        Properties props = new Properties();
        props.setProperty("foo", "pineapple");
        props.setProperty("bar", "lime");

        ImportConfig cfg = ConfigFactory.create(ImportConfig.class, props); // props imported!

        assertEquals("pineapple", cfg.foo());
        assertEquals("lime", cfg.bar());
        assertEquals("orange", cfg.baz());

You can specify multiple properties to import, on the same line:

        ImportConfig cfg = ConfigFactory.create(ImportConfig.class, props1, props2, ...);

If there are prop1 and prop2 define the two different values for the same key, the one specified first will prevail:

        Properties p1 = new Properties();
        p1.setProperty("foo", "pineapple");
        p1.setProperty("bar", "lime");

        Properties p2 = new Properties();
        p2.setProperty("bar", "grapefruit");
        p2.setProperty("baz", "blackberry");


        ImportConfig cfg = ConfigFactory.create(ImportConfig.class, p1, p2); // props imported!

        assertEquals("pineapple", cfg.foo());
        assertEquals("lime", cfg.bar()); // p1 prevails, so this is lime and not grapefruit
        assertEquals("blackberry", cfg.baz());

This is pretty handy if you want to reference system properties or environment variables.
Example:

    interface SystemEnvProperties extends Config {
        @Key("file.separator")
        String fileSeparator();

        @Key("java.home")
        String javaHome();

        @Key("HOME")
        String home();

        @Key("USER")
        String user();

        void list(PrintStream out);
    }

    SystemEnvProperties cfg = ConfigFactory.create(SystemEnvProperties.class, System.getProperties(), System.getenv());
    assertEquals(File.separator, cfg.fileSeparator());
    assertEquals(System.getProperty("java.home"), cfg.javaHome());
    assertEquals(System.getenv().get("HOME"), cfg.home());
    assertEquals(System.getenv().get("USER"), cfg.user());

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

### TYPE CONVERSION

OWER API supports properties conversion for primitive types and enums. So when you define a properties like the
followings they will be automatically converted from `String` to the primitive types and enum.

        int maxThreads();               // conversion happens from the value specified in the properties files.

        @DefaultValue("3.1415")         // conversion happens also from @DefaultValue
        double pi();

        @DefaultValue("NANOSECONDS");   // enum values are case sensitive!
        TimeUnit timeUnit();            // java.util.concurrent.TimeUnit is an enum

Since version 1.0.2 it is possible to have configuration interfaces to declare complex return types or even custom ones.

Example:

    public interface SpecialTypes extends Config {
        @DefaultValue("foobar.txt")
        File sampleFile();

        @DefaultValue("http://owner.aeonbits.org")
        URL sampleURL();

        @DefaultValue("example")
        CustomType customType();

        @DefaultValue("Hello %s!")
        CustomType salutation(String name);
    }

You can define you own class types as in the above example `CustomType`.

OWNER API supports automatic conversion for:

 1. Primitive types: boolean, byte, short, integer, long, float, double.
 2. Enums (notice that the conversion is case sensitive, so FOO != foo or Foo).
 3. java.lang.String, of course (no conversion is needed).
 4. java.net.URL, java.net.URI, java.io.File (they fall in case #6).
 5. java.lang.Class (this can be useful, for instance, if you want to load the jdbc driver, or similar cases).
 6. Any instantiable class declaring a public constructor with a single argument of type `java.lang.String`.
 7. Any instantiable class declaring a public constructor with a single argument of type `java.lang.Object`.
 8. Any class declaring a public *static* method `valueOf(java.lang.String)` that returns an instance of itself.
 9. Any class for which you can register a [`PropertyEditor`][propedit] via
    [`PropertyEditorManager.registerEditor()`][propeditmanager].

Example:

    public class CustomType {
        private final String text;

        public CustomType(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

If any error happens during the constructor call you'll receive a `java.lang.UnsupportedOperationException` with some
meaningful description.

You can also register your custom [`PropertyEditor`][propedit] to convert text properties into your objects
using the static method [`PropertyEditorManager.registerEditor()`][propeditmanager].  
See also [`PropertyEditorSupport`][propeditsupport], it may be useful if you want to implement a `PropertyEditor`.

  [propeditmanager]: http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditorManager.html#registerEditor
  [propedit]: http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditor.html
  [propeditsupport]:http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditorSupport.html

### VARIABLES EXPANSION

Sometimes it may be useful to expand properties values from other properties.

Example (ConfigWithExpansion.properties):

    story=The ${animal} jumped over the ${target}
    animal=quick ${color} fox
    target=${target.attribute} dog
    target.attribute=lazy
    color=brown

The property `story` will expand to *The quick brown fox jumped over the lazy dog*, you can map it with:

    public interface ConfigWithExpansion  extends Config {
        String story();
    }

This also works with the annotations, but you need to specify the properties on the methods:

    public interface ConfigWithExpansion extends Config {

        @DefaultValue("The ${animal} jumped over the ${target}")
        String story();

        @DefaultValue("quick ${color} fox")
        String animal();

        @DefaultValue("${target.attribute} dog")
        String target();

        @Key("target.attribute")
        @DefaultValue("lazy")
        String targetAttribute();

        @DefaultValue("brown")
        String color();
    }

Example of usage:

    ConfigWithExpansion conf = ConfigFactory.create(ConfigWithExpansion.class);
    String story = conf.story());

Sometimes you may want to use system properties or environment variables, an idea could be to *import*
`System.getProperties()` and `System.getenv()` on the Config creation time:

    public interface SystemPropertiesExample extends Config {
        @DefaultValue("something else")
        String someOtherValue();

        @DefaultValue("Welcome: ${user.name}")
        String welcomeString();

        @DefaultValue("${TMPDIR}/tempFile.tmp")
        File tempFile();
    }

    SystemPropertiesExample conf =
            ConfigFactory.create(SystemPropertiesExample.class, System.getProperties(), System.getenv());
    String welcome = conf.welcomeString();
    File temp = conf.tempFile();


### DEBUGGING AID

In your mapping interfaces you can optionally define two methods that may be convenient for the debugging:

    void list(PrintStream out);
    void list(PrintWriter out);

Those two methods were available in Java [Properties][properties] to help the debugging process, so here we kept it.

An example of how to define those two methods:

    public interface SampleConfig extends Config {
        @Key("server.http.port")
        int httpPort();

        void list(PrintStream out);
        void list(PrintWriter out);
    }

You can use them to print the resolved properties (and eventual overrides that may occur with the `LoadType.MERGE` load
policy explained before) to the console:

    ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
    cfg.list(System.out);

Those two methods are *not* specified into `Config` interface to leave to the programmer the liberty to have them or not.  
If you want to have those methods, or just one of them, in all your properties mapping interface you can define an
adapter interface like the following:

    public interface MyConfig extends Config {
        void list(PrintStream out);
        void list(PrintWriter out);
    }

and have your interface extending from `MyConfig` instead of `Config`.

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

If you are using maven, you can add the OWNER dependency in your project:

    <dependencies>
        <dependency>
            <groupId>org.aeonbits.owner</groupId>
            <artifactId>owner</artifactId>
            <version>1.0.2</version>
        </dependency>
    </dependencies>

In future, I may try to upload the jars in Maven Central Repository, so this won't be necessary.

DEPENDENCIES
------------

OWNER 1.0 has [commons-lang][] transitive dependency, to do some variable expansions.  
OWNER 1.0.1 and subsequent versions, have no transitive dependencies.

  [commons-lang]: http://commons.apache.org/lang/

DOWNLOADS
--------

You can download pre-built binaries from [here][downloads]

  [downloads]: https://github.com/lviggiano/owner/wiki/Downloads

TESTS
-----

OWNER codebase is very compact, and it is [fully covered][] by unit tests.

To execute the tests, you need maven properly installed and configured, 
then run the following command from the distribution root:

    $ mvn test

  [fully covered]: http://lviggiano.github.com/owner/target/site/cobertura/index.html

HOW TO CONTRIBUTE
-----------------

There are several ways to contribute to OWNER API:

  1. If you have implemented some change, you can [fork the project on github][fork] then send me a pull request.
  2. If you have some idea, you can [submit it as change request][issues] on github.
  3. If you've found some defect, you can [submit the bug][issues] on github.
  4. If you want to help the development, you can pick a [bug or an enhancement][issues] then contribute
     your patches following github [collaboration process][collaborating] (see also #1).

  [fork]: https://help.github.com/articles/fork-a-repo
  [issues]: https://github.com/lviggiano/owner/issues
  [collaborating]: https://help.github.com/categories/63/articles

FAQ
---
### What does "OWNER" name mean?

Since this API is used to access *Properties* files, and we implement interfaces to deal with those, 
somehow interfaces are *owners* for the properties. So here comes the name OWNER.  
I tried to find a decent name for the project, but I didn't come out with anything better. Sorry.  

### Is OWNER a stable API?

The codebase is very compact, and the test coverage is almost 100%. So there shouldn't be many bugs to deal with.  
You have the source, you can help improving the library and fix the bugs if you find some.  

Still, OWNER is very early, and APIs may change in the future to add/change some behaviors.  
For example I would like to specify an annotation to define additional properties load policies.  
But the goal is to keep the API backward compatible.  

### What happens if some `key` is not bound to a default value, and the properties file has no value for that key?

The returned value is `null`. This is consistent with the behavior of the [Properties][properties] class.  
If you think that this should be changed, please submit a [change request][issues] explaining your idea.  
A possible solution can be inventing a new annotation like `@Mandatory` on class level and/or method level for those
methods that do not specify a `@DefaultValue`, so that if the user forgets to specify a value for the mandatory
property, a (subclass of) RuntimeException is thrown when the Config class is instantiated, to point out the
misconfiguration.  
Thoughts? Ideas? Explain it on [github issues][issues].

  [properties]: http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html

CHANGELOG
---------
### 1.0.2

* Changed package name from `owner` to `org.aeonbits.owner`.  
  This has been necessary in order to publish the artifact on Maven Central Repository.
* Custom & special return types
* Properties variables expansion

### 1.0.1
Removed [commons-lang][] transitive dependency. Minor bug fixes.

### 1.0
Initial release.

LICENSE
-------

OWNER is released under the BSD license.  
See [LICENSE][] file included for the details.  

  [LICENSE]: https://raw.github.com/lviggiano/owner/master/LICENSE

MORE INFORMATION
----------------

Refer to the documentation on the [web site][] or [github wiki][] for further details on how to use the OWNER API.

If you find some bug or have any feature request open an issue on [github issues][issues], I'll try my best to keep up
with the developments.

  [web site]: http://lviggiano.github.com/owner
  [github wiki]: https://github.com/lviggiano/owner/wiki
