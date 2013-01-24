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

### CUSTOM & SPECIAL RETURN TYPES

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

The user can define his own class types as `CustomType` in the previous example. The return type needs to be a public
class declaring a public constructor with a single argument of type `java.lang.String'.

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

If any error happens during the constructor call you'll receive a `java.lang.UnsupportedOperationException`.

Example:

    java.lang.UnsupportedOperationException: Cannot convert 'example' to org.aeonbits.owner.CustomType
        at org.aeonbits.owner.PropertiesInvocationHandler.convert(PropertiesInvocationHandler.java:108)
        at org.aeonbits.owner.PropertiesInvocationHandler.resolveProperty(PropertiesInvocationHandler.java:72)
        at org.aeonbits.owner.PropertiesInvocationHandler.invoke(PropertiesInvocationHandler.java:63)
        at $Proxy4.customType(Unknown Source)
        ... 31 more
    Caused by: java.lang.NoSuchMethodException: org.aeonbits.owner.CustomType.<init>(java.lang.String)
        at java.lang.Class.getConstructor0(Class.java:2721)
        at java.lang.Class.getConstructor(Class.java:1674)
        at org.aeonbits.owner.PropertiesInvocationHandler.convert(PropertiesInvocationHandler.java:105)
        ... 38 more

The exception description states that OWNER failed to convert the text 'example' to `org.aeonbits.owner.CustomType` and
the cause, in this case is because OWNER was unable to find the public constructor with a single `String` parameter.

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

        @DefaultValue("${user.home}")
        String sysPropertyHome();

        @DefaultValue("${HOME}")
        String envHome();
    }

    SystemPropertiesExample conf =
            ConfigFactory.create(SystemPropertiesExample.class, System.getProperties(), System.getenv());
    String homeFromSystemProperty = conf.sysPropertyHome();
    String homeFromSystemEnviroment = conf.envHome();

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
