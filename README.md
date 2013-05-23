OWNER
=====

OWNER, a simple API to ease Java property files usage.

Jenkins: [![Build Status](https://aeonbits.ci.cloudbees.com/job/owner-api/badge/icon)](https://aeonbits.ci.cloudbees.com/job/owner-api/)  
Travis:  [![Build Status](https://travis-ci.org/lviggiano/owner.png?branch=master)](https://travis-ci.org/lviggiano/owner)

INTRODUCTION
------------

The goal of OWNER API is to minimize the code required to handle application configuration through Java properties files.

The inspiring idea for this API comes from GWT i18n (see [here][gwt-i18n]).  
The problem in using GWT i18n for loading property files is that it only works in client code (JavaScript), 
not standard Java classes.  
Also, GWT is a big library and it is designed for different purposes, than configuration.   
Since I liked the approach I decided to implement something similar, and here we are.  

  [gwt-i18n]: https://developers.google.com/web-toolkit/doc/latest/DevGuideI18nConstants

USAGE
-----

The approach used by OWNER APIs, is to define a Java interface associated to a properties file.

Suppose your properties file is defined as `ServerConfig.properties`:

```properties
port=80
hostname=foobar.com
maxThreads=100
```
    
To access this property you need to define a convenient Java interface in `ServerConfig.java`:

```java
public interface ServerConfig extends Config {
    int port();
    String hostname();
    @DefaultValue("42")
    int maxThreads();
}
```

We'll call this interface the *Properties Mapping Interface* or just *Mapping Interface* since its goal is to map
Properties into a an easy to use piece of code.
    
Then, you can use it from inside your code:

```java
public class MyApp {    
    public static void main(String[] args) {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
        System.out.println("Server " + cfg.hostname() + ":" + cfg.port() + " will run " + cfg.maxThreads());
    }
}
```

Did you notice that there is also the `@DefaultValue("42")` annotation specified in the example? It is used in case the
`maxThread` key is missing from the properties file.  
This annotation gets automatically converted to `int`, since `maxThreads()` returns an `int`. See below to learn more
about automatic type conversion.  

With annotations, you can also customize the property keys:

```properties
# Example of property file 'ServerConfig.properties'
server.http.port=80
server.host.name=foobar.com
server.max.threads=100
```

```java
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
```

The `@DefaultValue` and `@Key` annotations are the basics to start using this API.

Eventually, you may also code your project using the `@DefaultValue` without creating the associated properties file,
since you can do that any time later or leave this task to the end user.

### PROPERTIES FILES LOADING LOGIC

The properties file for a *mapping interface* is automatically resolved by OWNER API by matching the class name and the
file name ending with `.properties extennsion`.
For instance, if your *mapping interface* is com.foo.bar.ServerConfig, OWNER API will try to associate it to
`com.foo.bar.ServerConfig.properties`, loading it from the classpath.

But this logic can be tailored to your needs using some additional annotations:

```java
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
```

In the above example, OWNER will try to load the properties from several `@Sources`:

 1. First, it will try to load the properties file from user's home directory ~/.myapp.config, if this is found, this
    file alone will be used.
 2. If the previous attempt fails, then it will try to load the properties file from /etc/myapp.config, and if this is
    found, this one will be used.
 3. As last resort, it will try to load the properties from the classpath loading the resource identified by the
    path foo/bar/baz.properties.
 4. If none of the previous URL resources is found, then the Java interface will not be associated to any file, and only
    `@DefaultValue` will be used where specified. Where properties don't have a default value, `null` will be returned
    (as it happens for [`java.util.Properties`][properties]).

In the above case, the properties values will be loaded from only one file: the first that is found.
*Only the first available properties file will be loaded, others will be ignored*.

This load logic, is identified as *"FIRST"*, since only the first file found will be considered, and it is the default
logic adopted when the `@Source` annotation is specified with multiple URLs.  
You can also specify this load policy explicitly using `@LoadPolicy(LoadType.FIRST)` on the interface declaration.

But what if you want to have some *overriding* between properties? This is definitely possible: you can do it with
the annotation `@LoadPolicy(LoadType.MERGE)`:

```java
@LoadPolicy(LoadType.MERGE)
@Sources({ "file:~/.myapp.config", "file:/etc/myapp.config", "classpath:foo/bar/baz.properties" })
public interface ServerConfig extends Config {
    ...
}
```

In this case, for *every property* all the specified URLs will be queries, and the first resource defining the property
will prevail.
More in detail, this is what will happen:

 1. First, it will try to load the given property from ~/.myapp.config;
    if the given property is found the associated value will be returned.
 2. Then it will try to load the given property from /etc/myapp.config;
    if the property is found the value associated will be returned.
 3. As last resort it will try to load the given property from the classpath from the resource identified
    by the path foo/bar/baz.properties; if the property is found, the associated value is returned.
 4. If the given property is not found of any of the above cases, it will be returned the value specified by the
    `@DefaultValue` if specified, otherwise null will be returned.

So basically we produce a merge between the properties files where the first property files overrides latter ones.

The `@Sources` annotation considers system properties and/or environment variables with the syntax
`file:${user.home}/.myapp.config` (this gets resolved by 'user.home' system property) or `file:${HOME}/.myapp.config`
(this gets resolved by the$HOME environment variable). The `~` used in the previous example is another example of
variable expansion, and it is equivalent to `${user.home}`.

### IMPORTING PROPERTIES

You can use another mechanism to load your properties into a *mapping interface*.
And this mechanism is to specify a [Properties][properties] object programmatically when calling
`ConfigFactory.create()`:

```java
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
```

You can specify multiple properties to import on the same line:

```java
ImportConfig cfg = ConfigFactory.create(ImportConfig.class, props1, props2, ...);
```

If there are prop1 and prop2 defining two different values for the same property key, the one specified first will
prevail:

```java
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
```

This is pretty handy if you want to reference system properties or environment variables:

```java
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
```

### UNDEFINED PROPERTIES

If, in the example, ServerConfig interface cannot be mapped to any properties file, then all the methods in the
interface  will return `null`, unless on the methods it is defined a `@DefaultValue` annotation, of course.

If, in the example, we omit `@DefaultValue` for `maxThreads()` and we forget to define the property key in the
properties  files, `null` will be used as default value.

This is very convenient since you may just write your application defining the *properties mapping interface* and the
default values, then leave to the user to specify a configuration file in a convenient location to override the
defaults.

### PARAMETRIZED PROPERTIES

Another neat feature, is the possibility to provide parameters on method interfaces. The property values shall respect
the positional notation specified by the [`java.util.Formatter`][fmt] class:

  [fmt]: http://docs.oracle.com/javase/7/docs/api/java/util/Formatter.html#syntax

```java
public interface SampleParamConfig extends Config {
    @DefaultValue("Hello Mr. %s!")
    String helloMr(String name);
}

SampleParamConfig cfg = ConfigFactory.create(SampleParamConfig.class);
System.out.println(cfg.helloMr("Luigi")); // will println 'Hello Mr. Luigi!'
```

#### DISABLING PARAMETRIZED PROPERTIES FORMATTING

The parametrized properties feature can be disabled if the user doesn't find it convenient for some reason.
This can be done using the `@DisableFeature` annotation:

```java
public interface Sample extends Config {
    @DisableFeature(PARAMETER_FORMATTING)
    @DefaultValue("Hello %s.")
    public String hello(String name); // will return the string "Hello %s." ignoring the parameter.
}
```

The `@DisabledFeature` annotation can be applied on method level and/or on class level.

### TYPE CONVERSION

OWER API supports properties conversion for primitive types and enums.
When you define the *mapping interface* you can use a wide set of return types, and they will be automatically
converted from `String` to the primitive types and enums:

```java
int maxThreads();               // conversion happens from the value specified in the properties files.

@DefaultValue("3.1415")         // conversion happens also from @DefaultValue
double pi();

@DefaultValue("NANOSECONDS");   // enum values are case sensitive!
TimeUnit timeUnit();            // java.util.concurrent.TimeUnit is an enum
```

Since version 1.0.2 it is possible to have configuration interfaces to declare business objects as return types, many are
compatible and you can also define your own objects:

The easiest way is to define your business object with a public constructor taking a single parameter of type
`java.lang.String`:

```java
public class CustomType {
    private final String text;

    public CustomType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}

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
```

OWNER API will take the value "example" and pass it to the CustomType constructor then return it.

But there is more. OWNER API supports automatic conversion for:

  1. Primitive types: boolean, byte, short, integer, long, float, double.
  2. Enums (notice that the conversion is case sensitive, so FOO != foo or Foo).
  3. java.lang.String, of course (no conversion is needed).
  4. java.net.URL, java.net.URI.
  5. java.io.File (the character `~` will be expanded to `user.home` System Property).
  6. java.lang.Class (this can be useful, for instance, if you want to load the jdbc driver, or similar cases).
  7. Any instantiable class declaring a public constructor with a single argument of type `java.lang.String`.
  8. Any instantiable class declaring a public constructor with a single argument of type `java.lang.Object`.
  9. Any class declaring a public *static* method `valueOf(java.lang.String)` that returns an instance of itself.
  10. Any class for which you can register a [`PropertyEditor`][propedit] via
     [`PropertyEditorManager.registerEditor()`][propeditmanager].
  11. Any array having above types as elements.

If OWNER API cannot find any way to map your business object, you'll receive a `java.lang.UnsupportedOperationException`
with some meaningful description to identify the problem as quickly as possible.

You can also register your custom [`PropertyEditor`][propedit] to convert text properties into your business objects
using the static method [`PropertyEditorManager.registerEditor()`][propeditmanager].  
See also [`PropertyEditorSupport`][propeditsupport], it may be useful if you want to implement a `PropertyEditor`.

  [propeditmanager]: http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditorManager.html#registerEditor
  [propedit]: http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditor.html
  [propeditsupport]:http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditorSupport.html

### VARIABLES EXPANSION

Sometimes it may be useful to expand properties values from other properties:

```properties
story=The ${animal} jumped over the ${target}
animal=quick ${color} fox
target=${target.attribute} dog
target.attribute=lazy
color=brown
```

```java
public interface ConfigWithExpansion  extends Config {
    String story();
}
```

The property `story` will expand to *The quick brown fox jumped over the lazy dog*.

This also works with the annotations, but you need to specify every properties on the methods:

```java
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

ConfigWithExpansion conf = ConfigFactory.create(ConfigWithExpansion.class);
String story = conf.story();
```

Sometimes you may want expand System Properties or Environment Variables.
This can be done using *imports* (see dedicated paragraph to learn more):

```java
public interface SystemPropertiesExample extends Config {
    @DefaultValue("Welcome: ${user.name}")
    String welcomeString();

    @DefaultValue("${TMPDIR}/tempFile.tmp")
    File tempFile();
}

SystemPropertiesExample conf =
        ConfigFactory.create(SystemPropertiesExample.class, System.getProperties(), System.getenv());
String welcome = conf.welcomeString();
File temp = conf.tempFile();
```

#### DISABLING VARIABLES EXPANSION

The variables expansion feature can be disabled if the user doesn't find it convenient for some reason.
This can be done using the `@DisableFeature` annotation:

```java
public interface Sample extends Config {
    @DefaultValue("Earth")
    String world();

    @DisableFeature(VARIABLE_EXPANSION)
    @DefaultValue("Hello ${world}.")
    String sayHello(); // will return the string "Hello ${world}."
}

```

The `@DisabledFeature` annotation can be applied on method level and/or on class level.

### DISABLING UNWANTED FEATURES
It is possible to disable some of the features implemented in the API, if for some reasons they are inconvenient for the
programmer. This can be done using the [`@DisabledFeature`][df] annotation, as explained in other paragraphs in this
tutorial.

The `@DisabledFeature` can also be combined with multiple [`DisableableFeature`][dfe] and it can be used on method level
or on class level:

```java
@DisableFeature({VARIABLE_EXPANSION, PARAMETER_FORMATTING}) // on class level...
public static interface SampleConfig extends Config {
    @DefaultValue("Earth")
    public String planet();

    @DisableFeature({VARIABLE_EXPANSION, PARAMETER_FORMATTING}) // on method level...
    @DefaultValue("Hello %s, welcome on ${planet}!")
    public String hello(String name); // will return the string "Hello %s, welcome on ${planet}!" ignoring the parameter.
}

```

  [dfe]: http://owner.newinstance.it/maven-site/apidocs/org/aeonbits/owner/Config.DisableableFeature.html
  [df]: http://owner.newinstance.it/maven-site/apidocs/org/aeonbits/owner/Config.DisableFeature.html


### DEBUGGING AID

In your *mapping interfaces* you can optionally define one of the following methods that may be convenient for
debugging:

```java
void list(PrintStream out);
void list(PrintWriter out);
```

Those two methods were available in Java [Properties][properties] to help the debugging process, so here we kept it.

You can use them to print the resolved properties (and eventual overrides that may occur when using the
`LoadType.MERGE`):

```java
public interface SampleConfig extends Config {
    @Key("server.http.port")
    int httpPort();

    void list(PrintStream out);
}

ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
cfg.list(System.out);
```

These two methods are *not* specified into `Config` interface, so you don't have them available in your *mapping
interfaces* by default. This is done by purpose, to leave to the programmer the liberty to have this feature or not,
keeping your mapping interface hiding its internal details.

If you want to have those methods - or just one of them - in all of your *mapping interfaces* you can define an
intermediate adapter interface like the following:

```java
public interface MyConfig extends Config {
    void list(PrintStream out);
    void list(PrintWriter out);
}
```

then, you'll extend your mapping interfaces from `MyConfig` instead than using `Config` directly.

JAVADOCS
--------

API javadocs can be found [here][javadocs].

  [javadocs]: http://owner.newinstance.it/maven-site/apidocs/index.html

BUILD
-----

OWNER uses maven to build.

```
$ git clone git://github.com/lviggiano/owner.git
$ cd owner
$ mvn install
```

This will install OWNER jars in your local maven repository. Or, you can pick the jar files from the target directory.

### Continuous Integration

You can access latest builds reports from [Jenkins on CloudBees](https://aeonbits.ci.cloudbees.com/job/owner-api/)
and on [Travis](https://travis-ci.org/lviggiano/owner).

MAVEN
-----

If you are using maven, you can add the OWNER dependency in your project:

```xml
<dependencies>
    <dependency>
        <groupId>org.aeonbits.owner</groupId>
        <artifactId>owner</artifactId>
        <version>1.0.3</version>
    </dependency>
</dependencies>
```

The maven generated site, with all code reports and information can be found [here][maven-site].

 [maven-site]: http://owner.newinstance.it/maven-site/

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

OWNER codebase is very compact. It has been developed using test driven development approach, and it is
[fully covered][] by unit tests.

To execute the tests, you need maven properly installed and configured in your system, then run the following command
from the distribution root:

```
$ mvn test
```

  [fully covered]: http://newinstance.it/owner/maven-site/cobertura/index.html

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

Since this API is used to access *Properties* files, and we implement mapping interfaces to deal with those,
somehow interfaces are *owners* for the properties. So here comes the name OWNER, all uppercase because when you speak
about it you must shout it out :-)

The true story, is that I tried to find a decent name for the project, but I didn't come out with anything better.
Sorry.

### Is OWNER a stable API?

The codebase is very compact, and I try to keep the test coverage to 100%, developing many tests for each new feature.
You have the source, you can help improving the library and fix the bugs if you find some.

Still, OWNER API is a very early project, and APIs may change in the future to add/change some behaviors. But the
philosophy is to keep always backward compatibility (unless not possible).

### What happens if some `key` is not bound to a default value, and the properties file has no value for that key?

The returned value is `null`. This is consistent with the behavior of the [Properties][properties] class.  
If you think that this should be changed, please submit a [change request][issues] explaining your idea.  
A possible solution can be inventing a new annotation like `@Mandatory` on class level and/or method level for those
methods that do not specify a `@DefaultValue`, so that if the user forgets to specify a value for the mandatory
property, a (subclass of) RuntimeException is thrown when the Config class is instantiated, to point out the
misconfiguration.

### Why OWNER API doesn't implement this ${pretty.neat.feature} ?

Explain it on [github issues][issues]. If I like the idea I will implement it.
Or, you can implement by yourself and send me a push request on github.
The idea is to keep things minimal and code clean and easy. And for every new feature, having a complete test suite to
verify all cases.

  [properties]: http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html

CHANGELOG
---------
### 1.0.4 (under development)

 * Added basic array support. Thanks [ffbit][].
 * Implemented `@DisableFeature` annotation to provide the possibility to disable variable expansion and parametrized
   formatting. See Issue [#20][issue-20].
 * Website code snippets now have syntax highlighting. Thanks [ming13][].
 * Fixed bug [#17][issue-17] Substitution and format not working as expected when used together
 
  [issue-17]: https://github.com/lviggiano/owner/issues/17
  [issue-20]: https://github.com/lviggiano/owner/issues/20
  [ffbit]: https://github.com/ffbit
  [ming13]: https://github.com/ming13

### 1.0.3

 * Fixed incompatibility with JRE 6 (project was compiled using JDK 7 and in some places I was catching
   ReflectiveOperationException that has been introduced in JDK 7).
 * Minor code cleanup/optimization.

### 1.0.2

 * Changed package name from `owner` to `org.aeonbits.owner`.
   Sorry to break backward compatibility, but this has been necessary in order to publish the artifact on Maven Central
   Repository.
 * Custom & special return types.
 * Properties variables expansion.
 * Added possibility to specify [Properties][properties] to import with the method `ConfigFactory.create()`.
 * Added list() methods to aide debugging. User can specify these methods in his properties mapping interfaces.
 * Improved the documentation (this big file that you are reading), and Javadocs.

### 1.0.1

 * Removed [commons-lang][] transitive dependency. Minor bug fixes.

### 1.0

 * Initial release.

LICENSE
-------

OWNER is released under the BSD license.  
See [LICENSE][] file included for the details.  

  [LICENSE]: https://raw.github.com/lviggiano/owner/master/LICENSE

MORE INFORMATION
----------------

Refer to the documentation on the [web site][] or [github wiki][] for further details on how to use the OWNER API.
You may also discuss and ask help on the [mailing-list][].

If you find some bug or have any feature request open an issue on [github issues][issues], I'll try my best to keep up
with the developments.

  [web site]: http://lviggiano.github.com/owner
  [github wiki]: https://github.com/lviggiano/owner/wiki
  [mailing-list]: http://groups.google.com/group/owner-api
