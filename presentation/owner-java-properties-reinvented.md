---
marp: true
theme: default
paginate: true
size: 16:9
title: 'OWNER — Java™ properties reinvented'
description: 'Get rid of the boilerplate code in properties based configuration.'
style: |
  :root {
    --bg: #14161b;
    --bg-soft: #1b1e25;
    --fg: #e7e9ee;
    --muted: #9aa3b2;
    --accent: #ffc93c;
    --rule: #2b3040;
  }
  section {
    background: var(--bg);
    background-image: radial-gradient(ellipse 80% 55% at 50% -10%, #232838 0%, transparent 70%);
    color: var(--fg);
    font-family: -apple-system, 'Segoe UI', Inter, Helvetica, Arial, sans-serif;
    font-size: 25px;
    line-height: 1.5;
    letter-spacing: .1px;
    padding: 56px 68px;
    justify-content: flex-start;
  }
  h1 {
    color: var(--accent);
    font-size: 46px;
    font-weight: 700;
    letter-spacing: -.4px;
    margin: 0 0 .55em 0;
    padding-bottom: .28em;
    border-bottom: 2px solid var(--rule);
  }
  h2 { color: var(--accent); font-size: 32px; font-weight: 650; margin: .2em 0 .4em; }
  strong { color: #fff; }
  a { color: var(--accent); text-decoration: none; border-bottom: 1px solid var(--rule); }
  code, pre, kbd { font-family: 'JetBrains Mono', 'Cascadia Code', Menlo, Consolas, monospace; }
  /* inline code */
  p code, li code, td code, h2 code {
    background: #232833; color: #ffd970;
    padding: .06em .34em; border-radius: 4px; font-size: .88em;
  }
  pre {
    background: var(--bg-soft);
    border: 1px solid var(--rule);
    border-left: 3px solid var(--accent);
    border-radius: 8px;
    padding: .75em 1em;
    font-size: 19px;
    line-height: 1.45;
    overflow: visible;
  }
  pre code { font-size: inherit; color: #dfe4ec; text-shadow: none; }

  /* ---- syntax colours, tuned for a dark background ----
     Prism (.token.*) is what Marp ships; the .hljs-* twins keep this
     readable if the deck is ever rendered by a highlight.js pipeline. */
  .token.comment, .token.prolog, .token.doctype, .token.cdata,
  .hljs-comment, .hljs-quote {
    color: #7d8798; font-style: italic;
  }
  .token.punctuation, .token.operator, .token.entity,
  .hljs-punctuation {
    color: #aab4c4;
  }
  .token.keyword, .token.atrule, .token.important, .token.selector,
  .hljs-keyword, .hljs-literal, .hljs-selector-tag {
    color: #c792ea; font-weight: 600;
  }
  .token.string, .token.char, .token.attr-value, .token.inserted,
  .token.value, .token.url,
  .hljs-string, .hljs-attr, .hljs-addition {
    color: #a5e075;
  }
  .token.number, .token.boolean, .token.constant, .token.symbol,
  .hljs-number, .hljs-literal {
    color: #ff9d5c;
  }
  .token.class-name, .token.builtin, .token.tag,
  .hljs-title, .hljs-type, .hljs-class, .hljs-name, .hljs-section {
    color: #6fd3ff;
  }
  .token.function, .hljs-function, .hljs-title.function_ {
    color: #82aaff;
  }
  .token.annotation, .token.meta, .token.decorator,
  .hljs-meta, .hljs-doctag {
    color: #ffc93c;
  }
  /* .properties and .env sources: key on the left, value on the right */
  .token.key, .token.attr-name, .token.property,
  .hljs-attribute, .hljs-variable {
    color: #6fd3ff;
  }
  .token.variable { color: #ffc93c; }
  .token.deleted, .hljs-deletion { color: #ff7b72; }
  ul, ol { margin: 0; padding-left: 1.15em; }
  li { margin-bottom: .42em; }
  li::marker { color: var(--accent); }
  /* the bundled theme paints tables for a light background: undo that */
  table, thead, tbody, tfoot, tr, th, td {
    background: transparent !important;
    background-color: transparent !important;
    color: inherit;
    border-color: var(--rule) !important;
  }
  table { border-collapse: collapse; font-size: .84em; width: 100%; margin: .2em 0; }
  th, td { border: 0; border-bottom: 1px solid var(--rule); padding: .38em .6em; text-align: left; }
  th { color: var(--accent); font-weight: 600; }
  tbody tr:nth-child(odd) td { background: rgba(255,255,255,.028) !important; }
  blockquote { border-left: 3px solid var(--accent); margin: 0; padding-left: .9em; color: var(--muted); }

  /* ---- layout helpers ---- */
  .columns { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; align-items: start; }
  .columns.wide-left  { grid-template-columns: 1.25fr 1fr; }
  .columns.wide-right { grid-template-columns: 1fr 1.25fr; }
  .columns pre { margin: 0 0 .5em 0; }
  .step { color: var(--muted); font-size: .82em; text-transform: uppercase;
          letter-spacing: 1.2px; margin: 0 0 .3em 0; }
  .note { font-size: .78em; font-style: italic; color: var(--muted); }
  /* denser code for the heaviest slides */
  section.tight { font-size: 22px; }
  section.tight pre { font-size: 16px; line-height: 1.38; }
  section.tighter pre { font-size: 14px; line-height: 1.34; }

  /* ---- title / closing ---- */
  section.lead {
    justify-content: center; text-align: center;
    background-image: radial-gradient(ellipse 70% 60% at 50% 40%, #262c3d 0%, transparent 70%);
  }
  section.lead h1 { font-size: 68px; border: 0; margin-bottom: .1em; padding: 0; }
  section.lead h2 { color: var(--fg); font-weight: 400; font-size: 26px; }

  header, footer { color: var(--muted); font-size: 15px; }
  section::after { color: var(--muted); font-size: 15px; }
---

<!-- _class: lead -->
<!-- _paginate: false -->

# OWNER<br/>Java™ properties reinvented.

## Get rid of the boilerplate code in properties based configuration.

https://matteobaccan.github.io/owner

**Luigi R. Viggiano** — original author
**Matteo Baccan** — maintainer

---

# About this presentation

<div style="text-align:center">

## <span style="color:#c0392b">WARNING:</span>

### Viewer discretion is advised

</div>

This presentation contains source code.
Non technical people may experience confusional states, dizziness, sleepiness and fainting.

The author is not good in putting colors together.

---

# In a nutshell

- OWNER's goal is to minimize the code required for properties based configuration in Java applications.
- An open source (BSD License) Java library.
- Artifacts are available on __Maven Central Repository__ (latest release 1.0.12; 2.0.0 in preparation).
- Source code is managed on __GitHub__, at `matteobaccan/owner`.
- Inspired on how __GWT i18n__ manages translations on client side.
- Requires Java 8 or newer.
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

<div class="columns">
<div>

<p class="step">1 — the properties file</p>

`ServerConfig.properties`, in a given java package:

```properties
port=80
hostname=foobar.com
maxThreads=100
```

</div>
<div>

<p class="step">2 — the mapping interface</p>

A class named the same way, `ServerConfig.java`:

```java
import org.aeonbits.owner.Config;

public interface ServerConfig
        extends Config {
    int port();
    String hostname();
    @DefaultValue("42")
    int maxThreads();
}
```

</div>
</div>

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
  See: https://matteobaccan.github.io/owner/

- Be stable. OWNER is fully tested, development follows TDD approach.
  See: https://sonarcloud.io/summary/new_code?id=matteobaccan_owner

- Be feature rich.
  See: https://matteobaccan.github.io/owner/docs/features/

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

# Features Overview — added since this talk

| feature | since |
|---|---|
| Preprocessors — `@PreprocessorClasses` | 1.0.9 |
| Crypto — `@EncryptedValue`, `@DecryptorClass` | 1.0.10 |
| Crypto — `${$aes-gcm::…}`, `${$rsa-oaep::…}`, and a cipher shipped | 2.0.0 |
| JNDI as a source — `jndi:comp/env/…` in `owner-extras` | 2.0.0 |
| JMX — every `Config` is a `DynamicMBean` | 1.0.10 |
| Singleton — `ConfigCache` | 1.0.10 |
| Key prefix — `@Prefix` | 2.0.0 |
| Mandatory properties — `@Mandatory` | 2.0.0 |
| Sensitive values — `@Sensitive` | 2.0.0 |
| `Optional` and `Map` return types | 2.0.0 |
| `.env` files, and options on a source | 2.0.0 |
| Nested configuration interfaces | 2.0.0 |
| INI, JSON, YAML, TOML — `owner-formats` | 2.0.0 |
| Where a value came from — `Traceable` | 2.0.0 |
| Diagnostics, and `owner.strict` | 2.0.0 |

The rest of this deck is the 2014 talk; the last section covers 2.0.0.

---

# @DefaultValue and @Key

<div class="columns">
<div>

<p class="step">ServerConfig.properties</p>

```properties
server.http.port=80
server.host.name=foobar.com
server.max.threads=100
```

The key in the file no longer has to match the method name.

</div>
<div>

<p class="step">ServerConfig.java</p>

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

</div>
</div>

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

    @DefaultValue("https://matteobaccan.github.io/owner")
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
  "https://matteobaccan.github.io, http://github.com, http://google.com")
MyOwnCollection<URL> myBookmarks();

// Concrete class are allowed (in this case java.util.Stack)
// when type is not specified <String> is assumed as default
@DefaultValue(
  "The Lord of the Rings,The Little Prince,The Da Vinci Code")
Stack books();
```

---

<!-- _class: tight -->

# Type Conversion

You can define a `@TokenizerClass` when `@Separator` is not enough

<div class="columns">
<div>

```java
public class MyConfig extends Config {

    @Separator(";")
    @DefaultValue(
      "0; 1; 1; 2; 3; 5; 8; 13; 21")
    public int[] fibonacci();

    @TokenizerClass(
      CustomDashTokenizer.class)
    @DefaultValue("foo-bar-baz")
    public String[] withSeparatorClass();

}
```

</div>
<div>

```java
public class CustomDashTokenizer
        implements Tokenizer {
    // this logic can be as much
    // complex as you need
    @Override
    public String[] tokens(String values) {
        return values.split("-", -1);
    }
}
```

</div>
</div>

---

<!-- _class: tighter -->

# Type Conversion

And as last resort, you can define a `@ConverterClass`

<div class="columns">
<div>

```java
interface MyConfig extends Config {
    @DefaultValue("foobar.com:8080")
    @ConverterClass(ServerConverter.class)
    Server server();

    @DefaultValue(
      "google.com, yahoo.com:8080")
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

</div>
<div>

```java
public class ServerConverter
        implements Converter<Server> {
    public Server convert(Method targetMethod,
                          String text) {
        String[] split = text.split(":", -1);
        String name = split[0];
        Integer port = 80;
        if (split.length >= 2)
            port = Integer.valueOf(split[1]);
        return new Server(name, port);
    }
}
```

```java
MyConfig cfg =
    ConfigFactory.create(MyConfig.class);

// a single server
Server s = cfg.server();
// works also with collections
Server[] ss = cfg.servers();
```

</div>
</div>

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

<!-- _class: tight -->

# Type Conversion

7. Any instantiable class declaring a public constructor with a single argument of type `java.lang.String`.
8. Any instantiable class declaring a public constructor with a single argument of type `java.lang.Object`.
9. Any class declaring a public static method `valueOf(java.lang.String)` that returns an instance of itself.
10. Any class for which you can register a `PropertyEditor` via `PropertyEditorManager.registerEditor()`.
11. Any array having above types as elements.
12. Any object that can be instantiated via `@ConverterClass` annotation explained before.
13. Any Java Collections of all above types: `Set`, `List`, `SortedSet` or concrete implementations like `LinkedHashSet`, or user defined collections having a default no-arg constructor.
14. `Map` and sub-interfaces, reading a **group of properties** under a common prefix. *(2.0.0)*

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
    tool = p4merge                   url = git@github.com:matteobaccan/owner.git
[diff]                           [branch "master"]
    tool = p4merge                   remote = origin
[push]                               merge = refs/heads/master
    default = upstream
```

Repository Level Configuration overrides user level configuration

---

<!-- _class: tight -->

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

<div class="columns">
<div>

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
```

</div>
<div>

```java
SystemEnvProperties cfg = ConfigFactory
    .create(SystemEnvProperties.class,
            System.getProperties(),
            System.getenv());
```

System properties and environment variables are just two more `Map`s to import: no special case, no new API.

</div>
</div>

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

<!-- _class: tight -->

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

<!-- _class: tight -->

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

<!-- _class: tight -->

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

<div class="columns">
<div>

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
```

</div>
<div>

```java
public static void main(String[] args) {
    MyConfig cfg =
        ConfigFactory.create(MyConfig.class);
    System.out.println("cfg = " + cfg);
}
```

```text
cfg = {default.name=untitled,
       max.folders=99,
       max.threads=25}
```

</div>
</div>

---

# Debugging Facilities

In your mapping interfaces you can optionally define one of the following methods:

```java
void list(PrintStream out);          void list(PrintWriter out);
```

<div class="columns">
<div>

<p class="step">declared by hand</p>

```java
public interface SampleConfig
        extends Config {
    @Key("server.http.port")
    @DefaultValue("80")
    int httpPort();

    // manually defined
    void list(PrintStream out);
}

cfg.list(System.out);
```

</div>
<div>

<p class="step">inherited from Accessible</p>

```java
public interface SampleConfig
        extends Config, Accessible {
    @Key("server.http.port")
    @DefaultValue("80")
    int httpPort();
}

// list() comes from Accessible
cfg.list(System.out);
```

</div>
</div>

---

# XML Support

The `java.util.Properties` class supports an XML format that looks like this:

<div class="columns">
<div>

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM
  "http://java.sun.com/dtd/properties.dtd">
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

</div>
<div>

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

</div>
</div>

---

# XML Support

OWNER supports the previous XML format for properties as well as any XML that can be possible mapped to a properties list. Example:

<div class="columns">
<div>

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

</div>
<div>

```properties
server.http.port=80
server.http.hostname=localhost
server.ssh.port=22
server.ssh.address=127.0.0.1
server.ssh.alive.interval=60
server.ssh.user=admin
```

The developer is free to use tags and attributes to define properties names. Better now?

</div>
</div>

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

Variables expansion, parameters formatting and the key prefix can be inconvenient if you have your own mechanisms. If for any reason you want to disable them, on class level or method level, you can use `@DisableFeature` annotation.

The disableable features are `VARIABLE_EXPANSION`, `PARAMETER_FORMATTING` and `PREFIX`.

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

<!-- _class: lead -->

# What's new in 2.0.0

## The first release since maintenance moved from Luigi Viggiano to Matteo Baccan.

---

# 2.0.0 in one slide

- **Java 8 is the minimum runtime.** `owner-java8` and `owner-java8-extras` are gone: `default` methods, `DurationConverter`, `ByteSize` are all in the core `owner` artifact now.
- **New annotations:** `@Prefix`, `@Mandatory`, `@Sensitive`, `@CollectionConverterClass`, `@DefaultValue(useOnEmpty = true)`.
- **New return types:** `Optional<T>`, `Map`— which used to throw on every access — and **a nested interface**, for a configuration that is not flat.
- **New formats:** `.env` in the core, and INI, JSON, YAML, TOML in `owner-formats`. Every parser written by hand, so the core still has **no dependencies**.
- **New interface:** `Traceable`, which says where a value came from.
- **It stopped failing in silence:** warnings where there were none, and `owner.strict` to refuse instead.
- **A handful of behaviour changes** that earned the major number.

---

<!-- _class: tight -->

# @Prefix

Keys are usually grouped under a common prefix. Say it once, on the interface, instead of on every method.

<div class="columns">
<div>

<p class="step">before — 1.0.12</p>

```java
public interface ServerConfig
        extends Config {
    @Key("server.hostname")
    String hostname();

    @Key("server.port")
    int port();

    @Key("server.max.threads")
    @DefaultValue("42")
    int maxThreads();
}
```

</div>
<div>

<p class="step">now — 2.0.0</p>

```java
@Prefix("server.")
public interface ServerConfig
        extends Config {
    String hostname();

    int port();

    @Key("max.threads")
    @DefaultValue("42")
    int maxThreads();
}
```

</div>
</div>

Every method takes the prefix of the interface **that declares it**: a prefix never leaks onto what a sub-interface inherits.

---

<!-- _class: tight -->

# @Prefix — the rest of it

<div class="columns">
<div>

The prefix is part of the key, so it expands variables like the rest of it:

```java
@Prefix("servers.${env:dev}.")
public interface ServerConfig extends Config {
    String name();
    String hostname();
    Integer port();
}
```

`PREFIX` is a disableable feature, for the method that has to opt out:

```java
@DisableFeature(PREFIX)
@DefaultValue("UTF-8")
String encoding();          // encoding, not server.encoding
```

</div>
<div>

A prefix can also be configured **on the factory**, for the interfaces that declare none:

```java
Factory factory = ConfigFactory.newInstance();
factory.setProperty(
    "owner.key.prefix.from.package", "true");
```

| property | effect |
|---|---|
| `owner.key.prefix` | a literal, prepended to every key |
| `owner.key.prefix.from.package` | the package of the declaring interface, plus a dot |

OWNER already derived the *file* name from the package; it was only the **keys** that were left out of that convention.

</div>
</div>

---

# Optional and @Mandatory

<div class="columns">
<div>

<p class="step">absence, declared in the signature</p>

```java
Optional<Integer> port();
```

Comes back empty when the property is defined nowhere and has no default, instead of `null`.

Everything else applies unchanged: `@Key`, `@Prefix`, preprocessors, expansion, decryption. A value that is **wrong** rather than missing keeps failing — a typo does not silently become an empty `Optional`.

</div>
<div>

<p class="step">absence, refused outright</p>

```java
@Mandatory
String url();
```

A `MissingMandatoryPropertyException` lists **all** the unresolvable keys when the Config is created — and again on access, if a mandatory property disappears after a hot reload.

</div>
</div>

`@Mandatory` and `Optional` on the same method contradict each other, and are reported when the object is created.

---

<!-- _class: tight -->

# @Sensitive

A `cfg.list(System.out)` added while debugging and then forgotten is how a password ends up in a log file.

<div class="columns">
<div>

```java
public interface DbConfig extends Accessible {
    String url();

    String username();

    @Sensitive
    String password();
}
```

</div>
<div>

```java
cfg.list(System.out);
// -- listing properties --
// url=jdbc:postgresql://localhost/app
// username=app
// password=********
```

</div>
</div>

Only the output **meant to be read by a human** is masked — `list()` and `toString()`. The method itself, `getProperty()`, `fill()`, `store()`, `storeToXML()` and the JMX attributes keep returning the real value: those are how a configuration is written back, and masking them would save `********` into the file.

> Masking is not encryption. For that there is a marker — `db.password=${$aes-gcm::…}` — whose values are printed as the marker and unreadable already.

---

# Map, and lists one element per key

<div class="columns">
<div>

<p class="step">a group of properties</p>

```properties
credentials.user=admin
credentials.password=secret
```

```java
Map<String, String> credentials();
```

Reads everything below the key of the method. A `SortedMap` comes back as a `TreeMap`, a plain `Map` as a `LinkedHashMap`; `EnumMap` is refused, and so is a `@DefaultValue` on a map. No match gives an empty map, never `null`.

</div>
<div>

<p class="step">one element per key</p>

```properties
servers[0]=alpha
servers[1]=beta
```

```java
List<String> servers();   // [alpha, beta]
```

Open as a request since 2013.

Marking a `Map` `@Sensitive` masks **everything** under the prefix — picking out the entries that look like a secret would be guesswork.

</div>
</div>

---

<!-- _class: tight -->

# .env files

How container tooling carries configuration into a process: `docker run --env-file`, `env_file` in Compose, `envFrom` in Kubernetes, the secrets of a CI pipeline.

<div class="columns">
<div>

```properties
# .env
DB_HOST=localhost
DB_PORT=5432
NAME="Matteo"
```

```java
@Sources("file:.env")
public interface AppConfig extends Config {
    @Key("DB_HOST")
    String dbHost();
}
```

A `.env` is **never looked for on its own**: it is not named after the interface, so it is always named explicitly.

</div>
<div>

**There is no `.env` standard.** Given `NAME="Matteo"`, `docker run --env-file` gives you the quotes and the `dotenv` family does not — and Compose agrees with neither.

So OWNER implements a **dialect**, not "the format": three presets — `docker`, `dotenv`, `compose` — plus seven rules settable on their own.

```java
@Sources("file:.env#dialect=dotenv")
```

`docker` is the default, because it does nothing to a value: quotes that arrive attached are noticed at once, quotes silently removed are not.

</div>
</div>

---

# Options on a source, loaders on the classpath

<div class="columns">
<div>

<p class="step">the fragment belongs to OWNER</p>

```java
@Sources("https://cfg/app.env?token=abc#dialect=dotenv")
```

The **query belongs to the protocol** and is never touched — the token reaches the server. The fragment is also the only place options can be written for a resource inside a jar, whose URI has no query.

An option a loader does not recognise is **refused, not ignored**, and the message names the option, the source, and what would have been accepted.

</div>
<div>

<p class="step">a jar that ships a format</p>

```
META-INF/services/
  org.aeonbits.owner.loaders.Loader
```

Declared there, a loader is picked up when a factory is created — no `registerLoader()` call. Being found is what enables it: it answers for its formats at once, and its default file names join the ones looked for when an interface declares no `@Sources`.

</div>
</div>

---

# Nested configuration

A configuration is rarely flat, and since 2.0.0 an interface can have the shape the file has.

<div class="columns">
<div>

```properties
server.host=localhost
server.port=8080
```

```java
public interface AppConfig extends Config {
    ServerConfig server();
}

public interface ServerConfig extends Config {
    String host();
    int port();
}
```

The section shares its parent's sources, its reload and its listeners — it is a **view**, not a copy.

</div>
<div>

<p class="step">four shapes, one convention</p>

```properties
servers[0].host=alpha     # a list
servers.beta.host=beta    # a map
```

```java
List<ServerConfig> servers();
Map<String, ServerConfig> servers();

@Key("servers.%s")
ServerConfig server(String name);
```

Which closes requests open since 2013 and 2015 — and reads an XML tree end to end, because that is the shape every tree-structured format flattens to.

</div>
</div>

---

<!-- _class: tight -->

# Four more formats, still no dependencies

`.env` in the core; **INI, JSON, YAML and TOML** in a new `owner-formats` artifact — every parser written by hand.

<div class="columns">
<div>

```java
@Sources("classpath:app.yaml")
public interface AppConfig extends Config {
    ServerConfig server();
}
```

```yaml
server:
  host: localhost
  port: 8080
```

They flatten to the same keys nesting reads, so a format is a way of writing the file and nothing more.

</div>
<div>

<p class="step">a documented subset, refused by name</p>

The YAML parser is ~450 lines rather than 5000: anchors, aliases, merge keys, tags and a second document are **refused, naming the line**, instead of half-supported.

The Norway problem never arises — the literal scalar is kept and the **interface** declares the type.

A repeated key is a **list**, because that is already what a repeated XML element is here.

</div>
</div>

---

# Where did this value come from?

<div class="columns">
<div>

```java
interface AppConfig extends Config, Traceable {
    int port();
}
```

```java
cfg.originOf("port").kind();     // SOURCE
cfg.originOf("port").source();   // file:app.properties
```

A merge is exactly what makes a value indistinguishable from the one it overwrote and from a default. `Traceable` keeps the distinction that the merged properties can no longer make.

</div>
<div>

<p class="step">what it was asked for</p>

```java
Properties mine = new Properties();
for (Map.Entry<String, Origin> e : cfg.origins().entrySet())
    if ("file:app.properties".equals(e.getValue().source()))
        mine.setProperty(e.getKey(),
                cfg.getRawProperty(e.getKey()));
```

Saving back **only what you own**, instead of writing the whole environment into your configuration file.

A source never carries its credentials into an origin: `https://***@config/app.properties`.

</div>
</div>

---

<!-- _class: tight -->

# It works and it lies

OWNER's way of failing is to keep working: a source that cannot be read is passed over, the object is built out of defaults, and the caller gets something that answers every question. That is a fallback doing its job — and it is also what a misspelt path looks like.

<div class="columns">
<div>

<p class="step">2.0.0 says what happened</p>

- a source **named** and unreadable
- **not one** declared source readable
- `@HotReload` over what cannot be watched
- a value built out of an **`@EncryptedValue`** one (a marker has no such problem)
- and, at `CONFIG`, what was *decided*: which sources, which loader, which key each method reads

Never a value: that is what `@Sensitive` is for. And said **once** — a hot reload runs the load again at its interval.

</div>
<div>

<p class="step">or refuses outright</p>

```java
Factory f = ConfigFactory.newInstance();
f.setProperty("owner.strict", "true");
```

Every warning becomes a refusal when the object is created.

**What counts as a failure is not a list of its own — it is the warnings**, which already leave the legitimate cases alone. A source that is merely absent stays silent under strict too: `LoadType.FIRST` expects misses by design.

</div>
</div>

---

# A password in the file, and a cipher to put it there

<div class="columns">
<div>

```properties
db.password = ${$aes-gcm::AAM0UBtPtHU9kZcgvqX673gZ...}
jdbc.url    = jdbc:h2:mem:test?password=${db.password}
```

```java
ConfigFactory.registerValueHandler(
        new AesGcmHandler(passphrase));
```

Nothing on the interface. `password()` is an ordinary `String` method, and `jdbcUrl()` gets **the secret**, because a marker *is* expansion.

Until 2.0.0 this library shipped **no cipher at all** — and the documentation published an AES/ECB example for the reader to copy, under which two equal passwords have equal ciphertext.

</div>
<div>

<p class="step">and the one that changes who can do what</p>

```properties
api.token = ${$rsa-oaep::7VcoaAGAX+3tbyARpqJRCyZ4...}
```

With a key pair, whoever *writes* a secret holds only the public key: a CI job can add one **without being able to read the others**.

- AES-256/GCM, PBKDF2 at 210,000 — measured, not guessed
- `store()` writes the marker back; `fill()` gets the secret
- `EncryptTool` in the same jar; the passphrase is never an argument
- no discovery: a cipher on the classpath is not a decision anybody took

`${$vault::…}`, `${$file::…}` — the envelope knows nothing about cryptography.

</div>
</div>

---

# The smaller things that add up

- **`java.time.Duration` is converted out of the box**, like a `File` or a `URL`.
- **`ByteSize`** implements `Comparable` and `Serializable`, and gains `in(ByteSizeStandard)`.
- **The single-method SPIs are `@FunctionalInterface`**: `Converter`, `Preprocessor` and `ReloadListener` can officially be lambdas.
- **`@DefaultValue(useOnEmpty = true)`** — for the value left empty by a template, as in `port=${PORT}` with `PORT` unset. Opt-in and per method: an empty value falls back on the default, while a value that is *wrong* keeps failing.
- **`@CollectionConverterClass`** hands the raw value to a single converter instead of tokenizing first.
- **`Accessible.store(Writer, String)`**, mirroring `Properties.store(Writer, String)`.
- **`Accessible.getRawProperty(key)`** — the value as it was written, for whatever is on its way back to a file.
- The jars declare an `Automatic-Module-Name`.

---

<!-- _class: tight -->

# Why the major number

Everything else is additive. Four changes alter the result of a configuration that used to work, and a patch number would have been a quiet place to put them. Two are about variables:

- **Braces are matched, not counted from the left.** Up to 1.0.12 a `${` was closed by the first `}` that followed it; now by the one that matches it, which is what makes **nested variables** possible.
  `-Downer.nested.variable.expansion=false` restores the old behaviour for the whole JVM.

- **A circular variable reference is an error.** It used to exhaust the stack, or — for `a=${a:default}` — to produce an empty string. Now it throws, naming the chain. No cycle ever produced a useful value, but a configuration that quietly resolved to `""` will now fail loudly, which is the point.

---

<!-- _class: tight -->

# Why the major number — the other two

- **`getProperty` and `fill` expand the variables now.** They returned the text as written, so the same property answered two ways depending on whether it was read through its method or by name — which somebody reported in 2022, and worked around by reaching our private substitutor field with reflection.
  The line is drawn where the value is **going**: what writes the properties out — `list`, `store`, `storeToXML` — still leaves them exactly as written, because what goes out has to be able to come back. `getRawProperty` is the old behaviour under a name that says what it does.

- **Repeated sibling elements in an XML source are numbered.** Two elements of the same name under the same parent used to write the same key, so every value but the last was lost without a word. They now become `parent.tag[0]` and `parent.tag[1]` — which is what a list is read from. An element occurring once keeps its plain key.

`Accessible` also gains two abstract methods, so an implementation of it written by hand needs them — the interface is meant for the proxy, but it is a public type and that is a break like any other.

---

# Conclusion

- Lots of features.

- Little to code.

- If you are using properties files, you may give it a try.

---

# Future — as of 2014

- Validation (JSR 349? Bean Validation?)
- Encryptable Properties (i.e. for keeping passwords)
- Variable Expansion in `@Key` annotation. Example: `@Key("servers.${env}.name")`
- More file formats: INI, JSON, YAML, HOCON, plist, applet params, servlet params, jndi, jdbc…
- JMX bean for Config objects.
- Singleton mechanism.
- Any other idea? Join and help!

---

# …and what became of it

| 2014 | today |
|---|---|
| Encryptable Properties | ✔ `@EncryptedValue` since 1.0.10 — and since 2.0.0 a cipher we actually ship, named in the value |
| Variable Expansion in `@Key` | ✔ since 1.0.6 |
| JMX bean | ✔ every `Config` is a `DynamicMBean` |
| Singleton mechanism | ✔ `ConfigCache` |
| More file formats | ✔ `.env` in the core; INI, JSON, YAML, TOML in `owner-formats`; HOCON and JNDI in `owner-extras` |
| Validation | - still open |

Arrived without being asked for: `@Prefix`, Preprocessors, `@Sensitive` masking, transactional event listeners.

---

<!-- _class: lead -->
<!-- _paginate: false -->

# Thank you!
