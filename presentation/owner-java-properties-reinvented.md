---
marp: true
theme: default
paginate: true
size: 4:3
title: 'OWNER — Java™ properties reinvented'
description: 'Get rid of the boilerplate code in properties based configuration.'
style: |
  section {
    background: radial-gradient(ellipse at 50% 15%, #5a5a5a 0%, #3a3a3a 45%, #202020 100%);
    color: #f2f2f2;
    font-family: Helvetica, Arial, sans-serif;
    font-size: 26px;
    text-shadow: 0 2px 3px rgba(0,0,0,.6);
  }
  h1 {
    color: #ffd400;
    text-align: center;
    font-size: 62px;
    margin: 0 0 .4em 0;
    text-shadow: 0 3px 4px rgba(0,0,0,.7);
  }
  h2 { color: #ffd400; font-size: 40px; }
  a { color: #f2f2f2; }
  code, pre { font-family: 'Menlo', 'Consolas', monospace; text-shadow: none; }
  pre {
    background: #2b2b2b;
    border-radius: 3px;
    font-size: 20px;
    box-shadow: 0 4px 10px rgba(0,0,0,.5);
  }
  section.lead { justify-content: center; text-align: center; }
  section.lead h1 { font-size: 72px; }
  footer, header { color: #bbb; }
  ul li { margin-bottom: .5em; }
  .note { font-size: 20px; font-style: italic; color: #ddd; }
---

<!-- _class: lead -->
<!-- _paginate: false -->

# OWNER<br/>Java™ properties reinvented.

*Get rid of the boilerplate code in properties based configuration.*

http://owner.aeonbits.org

Luigi R. Viggiano
luigi.viggiano@gmail.com

---

# About this presentation

<div style="text-align:center">

## <span style="color:#c0392b">WARNING:</span>

### Viewer discretion is advised

</div>

☢️ This presentation contains source code.
Non technical people may experience confusional states, dizziness, sleepiness and fainting.

⚠️ The author is not good in putting colors together.

---

# In a nutshell

- OWNER's goal is to minimize the code required for properties based configuration in Java applications.
- An open source (BSD License) Java library.
- Artifacts are available on __Maven Central Repository__
- Source code is managed on __GitHub__ (24 forks and 111 stars).
- Inspired on how __GWT i18n__ manages translations on client side.
- Requires Java 5 or newer.
- No dependencies on 3rd party libraries.

---

# Why?

Programmers are left to do many tasks on their own
when using `java.util.Properties`

It requires a lot of code. <u>Repetitive</u> code.

---

# What?

What are the <u>very basic things</u> needed to handle properties files?

- load the file (from the filesystem? from the classpath?)
- define a configuration object that exposes its settings via convenient business methods.
- do the conversion (boolean, int, URLs, Files, Objects…).
- define default values when not specified in the file.
- etc…

---

# How?

How OWNER do its stuff without being repetitive?

1\. Imagine you have a Properties file 'ServerConfig.properties' in a given java package:

```properties
port=80
hostname=foobar.com
maxThreads=100
```

2\. Define a class named the same way 'ServerConfig.java':

```java
import org.aeonbits.owner.Config;

public interface ServerConfig extends Config {
    int port();
    String hostname();
    @DefaultValue("42")
    int maxThreads();
}
```

---

# How?

How OWNER do its stuff without being repetitive?

3\. Use it!

```java
ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
System.out.println("Server " + cfg.hostname() + ":" + cfg.port()
                 + " will run " + cfg.maxThreads());
```

Since the properties file does have the same name as the Java class, and they are located in the same package, OWNER will be able to associate them.

The properties names defined in the properties file will be associated to the methods in the Java class having the same name.

---

# Principles

<div style="text-align:center">

*"Simple things should be simple,
complex things should be possible."*
-- Alan Kay (Computer Scientist)

</div>

- Things should be as much simple as they can be.
  New/advanced features should not complicate the basic usages.
- Convention over configuration.
  Require as less code as possible.
- Use of annotations to customize the default behavior.

---

# Goals

- Be fully documented.
  See: http://owner.aeonbits.org/

- Be stable. OWNER is fully tested, development follows TDD approach. Test coverage is currently 97%
  See: https://coveralls.io/r/lviggiano/owner

- Be feature rich.
  See: http://owner.aeonbits.org/docs/features/

- Don't narrow the possibilities to the end user. The user should be able to do with OWNER everything that he is already doing with `java.util.Properties`. And more, of course.

---

# Features Overview

- <u>Powerful</u> Type Conversion; supports collections, primitives, common Java objects and user defined objects.

- Loading properties from multiple resources.

- Two different "Loading Strategies": load the FIRST resource, MERGE all the available resources.

- Importing (wrap) existing properties objects, as well as system properties and environment variables.

- Variables Expansion via the `${variable}` notation.

---

# Features Overview

- Hot Reload and programmable reload.

- Event notification. On reload, and on property change. With some basic validation mechanism.

- "Accessible" and "Mutable" interface to add capabilities to access or change a Config object.

- Debugging facilities.

- Multiple formats: XML and Properties files support; user can implement and configure custom loader for more file formats.

---

# @DefaultValue and @Key

`@Key` and `@DefaultValue` example

```properties
# Example of property file 'ServerConfig.properties'
server.http.port=80
server.host.name=foobar.com
server.max.threads=100
```

```java
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

---

# Type Conversion

The return type on the Configuration interface determines how to convert the property value (String) to user's specified type. OWNER support primitive types, enumerations …

```java
// conversion happens from the value specified in the
// properties files (if available).
int maxThreads();

// conversion happens also from @DefaultValue
@DefaultValue("3.1415")
double pi();

// enum values are case sensitive!
// java.util.concurrent.TimeUnit is an enum
@DefaultValue("NANOSECONDS");
TimeUnit timeUnit();
```

---

# Type Conversion

As well as common Java types or User's specified business objects…

```java
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

---

# Type Conversion

Collections and arrays (of all the supported types)…

```java
@DefaultValue("apple, pear, orange")
public String[] fruit();

@Separator(";")
@DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
public int[] fibonacci();

@Separator(File.pathSeparator);
File[] path();

@DefaultValue("1, 2, 3, 4")
List<Integer> ints();
```

---

# Type Conversion

User defined collections and concrete collections from the JRE…

```java
@DefaultValue(
  "http://aeonbits.org, http://github.com, http://google.com")
MyOwnCollection<URL> myBookmarks();

// Concrete class are allowed (in this case java.util.Stack)
// when type is not specified <String> is assumed as default
@DefaultValue(
  "The Lord of the Rings,The Little Prince,The Da Vinci Code")
Stack books();
```

---

# Type Conversion

You can define a `@TokenizerClass` when `@Separator` is not enough

```java
public class MyConfig extends Config {

    @Separator(";")
    @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
    public int[] fibonacci();

    @TokenizerClass(CustomDashTokenizer.class)
    @DefaultValue("foo-bar-baz")
    public String[] withSeparatorClass();

}
```

```java
public class CustomDashTokenizer implements Tokenizer {
    // this logic can be as much complex as you need
    @Override
    public String[] tokens(String values) {
        return values.split("-", -1);
    }
}
```

---

# Type Conversion

And as last resort, you can define a `@ConverterClass`

```java
interface MyConfig extends Config {
    @DefaultValue("foobar.com:8080")
    @ConverterClass(ServerConverter.class)
    Server server();

    @DefaultValue(
      "google.com, yahoo.com:8080, owner.aeonbits.org:4000")
    @ConverterClass(ServerConverter.class)
    Server[] servers();
}

class Server {
    private final String name;
    private final Integer port;

    public Server(String name, Integer port) {
        this.name = name;
        this.port = port;
    }
}
```

```java
public class ServerConverter implements Converter<Server> {
    public Server convert(Method targetMethod, String text) {
        String[] split = text.split(":", -1);
        String name = split[0];
        Integer port = 80;
        if (split.length >= 2)
            port = Integer.valueOf(split[1]);
        return new Server(name, port);
    }
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);
Server s = cfg.server();      // will return a single server
Server[] ss = cfg.servers();  // it works also with collections
```

---

# Type Conversion

To recap, all the types supported by OWNER for conversion:

1. Primitive types: `boolean`, `byte`, `short`, `integer`, `long`, `float`, `double`.
2. Enums (notice that the conversion is case sensitive, so FOO != foo or Foo).
3. `java.lang.String`, of course (no conversion is needed).
4. `java.net.URL`, `java.net.URI`.
5. `java.io.File` (the character `~` will be expanded to `user.home` System Property).
6. `java.lang.Class` (this can be useful, for instance, if you want to load the jdbc driver, or similar cases).

---

# Type Conversion

7. Any instantiable class declaring a public constructor with a single argument of type `java.lang.String`.
8. Any instantiable class declaring a public constructor with a single argument of type `java.lang.Object`.
9. Any class declaring a public static method `valueOf(java.lang.String)` that returns an instance of itself.
10. Any class for which you can register a `PropertyEditor` via `PropertyEditorManager.registerEditor()`. (See PropertyEditorTest as an example).
11. Any array having above types as elements.
12. Any object that can be instantiated via `@ConverterClass` annotation explained before.
13. Any Java Collections of all above types: `Set`, `List`, `SortedSet` or concrete implementations like `LinkedHashSet` or user defined collections having a default no-arg constructor. `Map` and sub-interfaces are not supported (yet).

---

# Loading Strategies

Common utilities (especially in unix) allow multiple configuration files.

Tipically a System Level configuration located in

`/etc/myapp.conf`

and a User Level configuration located in

`~/.myapp.conf`

an example is the git scm command line tool.

---

# Loading Strategies

A User Level configuration may totally override the configuration at System Level, or may just redefine some options. Example:

```console
$ cat .gitconfig                 $ cat .git/config
[user]                           [core]
    name = Luigi R. Viggiano         repositoryformatversion = 0
    email = luigi.viggiano@…         filemode = true
[color]                              bare = false
    ui = true                        logallrefupdates = true
[merge]                          [remote "origin"]
    tool = p4merge                   url = git@github.com:lviggiano/owner.git
[diff]                           [branch "master"]
    tool = p4merge                   remote = origin
[push]                               merge = refs/heads/master
    default = upstream
```

Repository Level Configuration overrides user level configuration

---

# Loading Strategies

OWNER allows configuration overriding in 2 different way.
The "Load FIRST" approach (`LoadType.FIRST`):

```java
@Sources({ "file:~/.myapp.config",
           "file:/etc/myapp.config",
           "classpath:foo/bar/baz.properties" })
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

Only the first available resource is loaded. Others are ignored.

---

# Loading Strategies

OWNER allows configuration overriding in 2 different way.
The "Load MERGE" approach (`LoadType.MERGE`):

```java
@LoadPolicy(LoadType.MERGE)
@Sources({ "file:~/.myapp.config",
           "file:/etc/myapp.config",
           "classpath:foo/bar/baz.properties" })
public interface ServerConfig extends Config {
    ...
}
```

The actual configuration is the result of the merge between all the specified resources. Topmost configuration resources redefine properties in lowest resources.

---

# Loading Strategies

`@Sources` annotation supports variable expansion:

- `file:${user.home}/.myapp.config` (system property)

- `file:${HOME}/.myapp.config` (environment variable)

- `file:~/.myapp.config` (the '~' literal accepted by bash)

Above examples are equivalent.

---

# Loading Strategies

`@Sources` annotation can also expand variables specified programmatically:

```java
// notice ${mypath} here
@Sources("file:${mypath}/myconfig.properties");
interface MyConfig extends Config { ... }

// notice ${mypath} here
ConfigFactory.setProperty("mypath", "/foo/bar/baz");
MyConfig cfg = ConfigFactory.create(MyConfig.class);
```

---

# Loading Strategies

`@Sources` annotation can also expand variables specified programmatically:

```java
// notice ${myurl} here
@Sources("${myurl}");
interface MyConfig extends Config { ... }

// notice ${myurl} here
ConfigFactory.setProperty(
  "myurl", "http://somewhere.com/conf.properties");
MyConfig cfg = ConfigFactory.create(MyConfig.class);
```

The ConfigFactory can accept configuration properties itself. I call this "Metaconfiguring"

---

# Importing (or "wrapping") existing Properties objects

Existing Properties objects can be "wrapped" or imported.

```java
Properties props = new Properties();
props.setProperty("foo", "pineapple");
props.setProperty("bar", "lime");

ImportConfig cfg = ConfigFactory
    .create(ImportConfig.class, props); // props imported!

assertEquals("pineapple", cfg.foo());
assertEquals("lime", cfg.bar());
assertEquals("orange", cfg.baz());
```

---

# Importing Properties

You can specify multiple properties to import on the same line:

```java
ImportConfig cfg = ConfigFactory
    .create(ImportConfig.class, props1, props2, ...);
```

If there are prop1 and prop2 defining two different values for the same property key, the one specified first will prevail.

---

# Importing Properties

This comes handy even for System properties or Environment Variables:

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

SystemEnvProperties cfg = ConfigFactory
    .create(SystemEnvProperties.class,
            System.getProperties(),
            System.getenv());
```

---

# Variable Expansion

Sometimes it may be useful to expand properties values from other properties.

```properties
story=The ${animal} jumped over the ${target}
animal=quick ${color} fox
target=${target.attribute} dog
target.attribute=lazy
color=brown
```

```java
public interface ConfigWithExpansion extends Config {
    String story();
}
```

The property `story()` will expand to:
*"The quick brown fox jumped over the lazy dog"*

---

# Variable Expansion

This will also work with just the annotations:

```java
@DefaultValue(
    "The ${animal} jumped over the ${target}")
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
```

---

# Reload and Hot Reload

Reloading configuration resources programmatically:

```java
@Sources{...}
interface MyConfig extends Reloadable {
    String someProperties();
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);
cfg.reload();
```

---

# Reload and Hot Reload

Automatic "Hot Reload"

```java
@HotReload
@Sources("file:foo/bar/baz.properties")
interface MyConfig extends Config {
    @DefaultValue("localhost")
    String serverName();
}
```

Hot Reload works fine with following URLs:

- `file:path/to/your.properties` filesystem backed URL
- `jar:file:path/to/some.jar!/path/to/your.properties` a jar file in your local filesystem that contains a properties files.
- `classpath:path/to/your.properties` a resource loaded from the classpath, if the classpath resource is stored on filesystem (from inside a jar or from inside a classpath folder).

---

# Reload and Hot Reload

The "Hot Reload" annotation definition:

```java
@interface HotReload {
    long value() default 5;
    TimeUnit unit() default SECONDS;
    HotReloadType type() default SYNC;
}

enum HotReloadType {
    SYNC, ASYNC
}
```

---

# Reload and Hot Reload

"Hot Reload" can be synchronous (`HotReloadType.SYNC`)…

```java
// Using the default values:
// will check for MyConfig.properties file changes in classpath
// with interval of 5 seconds.
// It will use SYNC hot reload.
@HotReload
interface MyConfig extends Config { ... }

// Will check for file changes every 2 seconds.
// It will use SYNC hot reload.
@HotReload(2)
@Sources("file:foo/bar/baz.properties")
interface MyConfig extends Config { ... }

// Will check for file changes every 500 millis.
// It will use SYNC hot reload.
@HotReload(500, unit = TimeUnit.MILLISECONDS);
@Sources("file:foo/bar/baz.properties")
interface MyConfig extends Config { ... }
```

---

# Reload and Hot Reload

…or asynchronous (`HotReloadType.ASYNC`)…

```java
// Will use ASYNC reload type: will span a
// separate thread that will check for file
// changes every 5 seconds (default)
@HotReload(type=HotReloadType.ASYNC);
@Sources("file:foo/bar/baz.properties")
interface MyConfig extends Config { ... }

// Will use ASYNC reload type and will check every 2 seconds.
@HotReload(2, type=HotReloadType.ASYNC);
@Sources("file:foo/bar/baz.properties")
interface MyConfig extends Config { ... }
```

---

# Reload and Hot Reload

Intercepting reload events:

```java
@HotReload(1)
interface AutoReloadConfig extends Config, Reloadable {
    @DefaultValue("5")
    Integer someValue();
}
```

```java
AutoReloadConfig cfg =
    ConfigFactory.create(AutoReloadConfig.class);

cfg.addReloadListener(new ReloadListener() {
    public void reloadPerformed(ReloadEvent event) {
        System.out.print(
            "\rReload intercepted at "
            + new Date() + " \n");
    }
});
```

---

# Interfaces Tree

```
       Serializable
            ▲
            │
          Config
            ▲
   ┌────────┼────────┐
Reloadable Mutable Accessible
```

- `Config` (is a marker interface).
- `Reloadable` defines methods to programmatically realod the configuration and to attach `ReloadListener`s
- `Accessible` define methods to access internal properties values, and to save/dump to `OutputStream` or `Writer`
- `Mutable` defines methods to change properties values programmatically and attach `PropertyChangeListener`s

---

# Listening for reloads

```java
interface Reloadable {
    void addReloadListener(ReloadListener);
    void removeReloadListener(ReloadListener);
    void reload();
}

interface ReloadListener {
    void reloadPerformed(ReloadEvent);
}

interface TransactionalReloadListener extends ReloadListener {
    void beforeReload(ReloadEvent);   // throws RollbackBatchException
}

class ReloadEvent {
    ReloadEvent(Object, List<PropertyChangeEvent>,
                Properties, Properties);
    List<PropertyChangeEvent> events;
    Properties oldProperties;
    Properties newProperties;
}
```

---

# Listening for property changes

```java
interface Mutable {
    String setProperty(String, String);
    String removeProperty(String);
    void   clear();
    void   load(InputStream);
    void   load(Reader);
    void   addPropertyChangeListener(PropertyChangeListener);
    void   removePropertyChangeListener(PropertyChangeListener);
    void   addPropertyChangeListener(String, PropertyChangeListener);
}

interface PropertyChangeListener {
    void propertyChange(PropertyChangeEvent);
}

interface TransactionalPropertyChangeListener
        extends PropertyChangeListener {
    void beforePropertyChange(PropertyChangeEvent);
    // RollbackOperationException / RollbackBatchException
}

class PropertyChangeEvent {
    String getPropertyName();
    Object getNewValue();
    Object getOldValue();
    void   setPropagationId(Object);
    Object getPropagationId();
}
```

---

# Debugging Facilities

Config objects define a convenient `toString()` method:

```java
interface MyConfig extends Config {
    @Key("max.threads")
    @DefaultValue("25")
    int maxThreads();

    @Key("max.folders")
    @DefaultValue("99")
    int maxFolders();

    @Key("default.name")
    @DefaultValue("untitled")
    String defaultName();
}

public static void main(String[] args) {
    MyConfig cfg = ConfigFactory.create(MyConfig.class);
    System.out.println("cfg = " + cfg);
    // output will be:
    // "cfg = {default.name=untitled, max.folders=99, max.threads=25}"
}
```

---

# Debugging Facilities

In your mapping interfaces you can optionally define one of the following methods that may be convenient for debugging:

```java
void list(PrintStream out);
void list(PrintWriter out);
```

```java
public interface SampleConfig extends Config {
    @Key("server.http.port")
    @DefaultValue("80")
    int httpPort();

    void list(PrintStream out); // manually defined
}

ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
cfg.list(System.out);
```

```java
public interface SampleConfig extends Config, Accessible {
    @Key("server.http.port")
    @DefaultValue("80")
    int httpPort();
}

ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
cfg.list(System.out); // list() is defined in Accessible interface
```

---

# XML Support

The `java.util.Properties` class supports an XML format that looks like this:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
    <comment>this is an example</comment>
    <entry key="server.ssh.alive.interval">60</entry>
    <entry key="server.ssh.address">127.0.0.1</entry>
    <entry key="server.http.port">80</entry>
    <entry key="server.http.hostname">localhost</entry>
    <entry key="server.ssh.user">admin</entry>
    <entry key="server.ssh.port">22</entry>
</properties>
```

⇒

```properties
server.http.port=80
server.http.hostname=localhost
server.ssh.port=22
server.ssh.address=127.0.0.1
server.ssh.alive.interval=60
server.ssh.user=admin
```

…a *fantastic\** facility compared to the plain textual format of properties files, uh?

<span class="note">The * means "I'm ironic"</span>

---

# XML Support

OWNER supports the previous XML format for properties as well as any XML that can be possible mapped to a properties list. Example:

```xml
<server>
    <http port="80">
        <hostname>localhost</hostname>
    </http>
    <ssh port="22">
        <address>127.0.0.1</address>
        <alive interval="60"/>
        <user>admin</user>
    </ssh>
</server>
```

⇒

```properties
server.http.port=80
server.http.hostname=localhost
server.ssh.port=22
server.ssh.address=127.0.0.1
server.ssh.alive.interval=60
server.ssh.user=admin
```

The developer is free to use tags and attributes to define properties names. Better now?

---

# XML Support

<div style="text-align:center">

## Q: How to load the XML into the Config object?

</div>

A: Simple: in the same way as you already do for properties file.

1. Suppose you have a mapping interface class in a source file called foo.bar.ServerConfig.java, just place a resource in your classpath in foo.bar.ServerConfig.xml.

2. Or you can specify the `@Sources` annotation with an URL pointing to an file.xml. OWNER will notice the .xml extension and will load the configuration source as XML.

---

# User defined file formats

If you want to support your own file format, you can register your loader in the ConfigFactory. Example:

```java
ConfigFactory.registerLoader(new YamlLoader());
```

XML and Properties file format are in fact internally implemented in this way. More file formats support are planned for future releases.

---

# Parameter Formatting

This comes directly from the GWT i18n:

```java
public interface Sample extends Config {
    @DefaultValue("Hello Mr. %s!")
    String helloMr(String name);
}

Sample cfg = ConfigFactory.create(Sample.class);
print(cfg.helloMr("Luigi")); // will println 'Hello Mr. Luigi!'
```

Not really related to configuration. Maybe I should drop this feature in favor of something better? The parameter could have been used for different purpose (i.e. a type safe setter method)

---

# Disabling unwanted features

Variables expansion and parameters formatting can be inconvenient if you have your own mechanisms. If for any reason you want to disable it, on class level or method level, you can use `@DisableFeature` annotation.

```java
// on class level...
@DisableFeature({VARIABLE_EXPANSION, PARAMETER_FORMATTING})
public interface SampleConfig extends Config {
    @DefaultValue("Earth")
    public String planet();

    // on method level...
    @DisableFeature({VARIABLE_EXPANSION, PARAMETER_FORMATTING})
    @DefaultValue("Hello %s, welcome on ${planet}!")
    public String hello(String name);
}
```

---

# Conclusion

- Lots of features.

- Little to code.

- If you are using properties files, you may give it a try.

---

# Future

- Validation (JSR 349? Bean Validation?)
- Encryptable Properties (i.e. for keeping passwords)
- Variable Expansion in `@Key` annotation. Example: `@Key("servers.${env}.name")`
- More file formats: INI, JSON, YAML, HOCON, plist, applet params, servlet params, jndi, jdbc…
- JMX bean for Config objects.
- Singleton mechanism.
- Any other idea? Join and help!

---

<!-- _class: lead -->
<!-- _paginate: false -->

# Thank you!
