---
layout: docs
title: Reload and Hot Reload
prev_section: variables-expansion
next_section: accessible-mutable
permalink: /docs/reload/
---

Owner does support programmatic reload, as well as the automatic "hot reload" for configuration files.

There are two ways on how the automatic HotReload can be implemented
with OWNER: synchronous or asynchronous.


Programmatic reload
-------------------

You can manually ask a configuration object to reload. This is done via
the [Reloadable] interface.

Example:

```java
@Sources{...}
interface MyConfig extends Reloadable {
    String someProperties();
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);
cfg.reload();
```

The `cfg.reload()` will perform the reload of all properties in the same way as
when the object was initially created. If the configuration files have been
altered, after the reload invocation, those changes will be reflected in the
config object.

The `Reloadable` interface extends from `Config`:

![config-hierarchy]({{site.url}}/img/config-hierarchy.png)

Automatic "hot reload"
----------------------

You can instruct OWNER to automatically reload the properties files if they are
modified on the filesystem.

For instance:

```java
@HotReload
@Sources("file:foo/bar/baz.properties")
interface MyConfig extends Config {
    @DefaultValue("localhost")
    String serverName();
}
```

You see in the above example we have specified the annotation `@HotReload` on
the interface level.

The hot reload works only on filesystem URLs. This means that
you can make it work with those two types of URLs:

 - `file:path/to/your.properties` a filesystem backed URL.
 - `jar:file:path/to/some.jar!/path/to/your.properties` a jar file in your
   local filesystem that contains a properties files.
 - `classpath:path/to/your.properties` a resource loaded from the classpath,
   *if* the classpath resource is stored on filesystem (from inside a jar or
   from inside a classpath folder). If the ClassLoader is loading the resource
   from a remote url (for instance from a jar accessed via http protocol),
   then it won't work. Almost always, the application loads classes and
   resources from a filesystem backed classpath. So this should work *almost*
   always.

If you don't specify the `@Sources` annotation, then OWNER will try to load
the properties file from the classpath from a resource matching the same package and
class name of the *mapping interface*.

The hot reload annotation instructs OWNER to monitor those resources for
changes and reload them when they change.

Why only 'file:', 'jar:file' and 'classpath:' URLs?
---------------------------------------------------

Monitoring remote URLs, such as "http" or "ftp", will involve network
communication to download those files from remote servers frequently just for
checking if they are changed, and it is not convenient to implement the hot
reload doing frequent heavy operations like these.
You can still perform the reload programmatically, using the [Reloadable]
interface, for these cases.

  [Reloadable]: http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Reloadable.html

While instead, monitoring the filesystem is not a big deal, also because
filesystems implement 'last modification date' that can be checked to detect
modifications without actually check the content of the file for changes.

This is the reason why OWNER implements "hot reload" only on filesystem based
URLs.


The @HotReload annotation
-------------------------

The `@HotReload` annotation accepts 3 optional parameters.

And is defined as:

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

You can check the [latest javadocs] for further details.

  [latest javadocs]: http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Config.HotReload.html

So you can specify also the interval for the hot reload, expressed by `value`
and `unit`, and you can also specify the type of hot reload that you need.

Some examples:

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
@HotReload(value=500, unit = TimeUnit.MILLISECONDS)
@Sources("file:foo/bar/baz.properties")
interface MyConfig extends Config { ... }

// Will use ASYNC reload type: will span a
// separate thread that will check for file
// changes every 5 seconds (default)
@HotReload(type=HotReloadType.ASYNC)
@Sources("file:foo/bar/baz.properties")
interface MyConfig extends Config { ... }

// Will use ASYNC reload type and will check every 2 seconds.
@HotReload(value=2, type=HotReloadType.ASYNC)
@Sources("file:foo/bar/baz.properties")
interface MyConfig extends Config { ... }
```

The difference between SYNC and ASYNC hot reload will be explained below.

As explained before the [last modified] date of the file will be used to detect
changes on the files.

 [last modified]: http://docs.oracle.com/javase/7/docs/api/java/io/File.html#lastModified()

<div class="note warning">
  <h5>Filesystems quirks</h5>
  <p>
The date resolution vary from filesystem to filesystem. <br/>
For instance, for Ext3, ReiserFS and HSF+ the date resolution is of 1 second. <br/>
For FAT32 the date resolution for the last modified time is 2 seconds. <br/>
For Ext4 the date resolution is in nanoseconds.
  </p>
</div>

The synchronous hot reload
--------------------------

The synchronous hot reload works this way: every time you call a method on the
config object created by `ConfigFactory.create()` the configuration files will
be checked for modifications, then eventually reload the files.

This means that, if you don't use the config object for long periods there will
be no checks on the filesystem, and consequently no reload will be performed.

So, we can define this behavior a *lazy* hot reload, since it does that only
when needed, at the very last time.

This is the default for the `@HotReload` annotation, but you can also specify
this type of hot reload explicitly with `type=SYNC`:

```java
@HotReload(type=HotReloadType.SYNC)
```


The asynchronous hot reload
---------------------------

The asynchronous hot reload works this way: it schedule a periodic task to be
executed on a separate thread on the specified interval, to check the files for
modification and eventually reload them.

This means that, if you don't use the config object for long periods, the check
on the filesystem will be done in background anyway and eventually the reload
will be performed.

To enable this behavior you need to specify `type=ASYNC` to the hot reload
annotation:

```java
@HotReload(type=HotReloadType.ASYNC)
```

Intercepting reload events
--------------------------

Since reload can happen programmatically, and automatically synchronously and
asynchronously, it may be helpful for to have some notification mechanism to
intercept reload events.

For this, please look at the [Reloadable] interface, that allows the user to
attach ReloadListeners to the config object.

Hot reload example
------------------

In the project's sources it is included a working example:

```java

public class AutoReloadExample {
    private static final String spec =
      "file:target/test-resources/AutoReloadExample.properties";

    private static File target;

    @Sources(spec)
    @HotReload(1)
    interface AutoReloadConfig extends Config, Reloadable {
        @DefaultValue("5")
        Integer someValue();
    }

    static {
        try {
            target = new File(new URL(spec).getFile());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
        throws IOException, InterruptedException {

        save(target, new Properties() { {
            setProperty("someValue", "10");
        }});

        AutoReloadConfig cfg =
            ConfigFactory.create(AutoReloadConfig.class);

        cfg.addReloadListener(new ReloadListener() {
            public void reloadPerformed(ReloadEvent event) {
                System.out.print(
                    "\rReload intercepted at "
                    + new Date() + " \n");
            }
        });

        System.out.println("You can change the file "
            + target.getAbsolutePath() +
            " and see the changes reflected below");

        int someValue = 0;
        while (someValue >= 0) {
            someValue = cfg.someValue();
            System.out.print(
               "\rsomeValue is: " + someValue + "\t\t\t\t");
            Thread.sleep(500);
        }

    }
}
```

To run this example, you need to follow these steps:

```bash
# after downloading the sources in the directory 'owner'
$ cd owner
$ mvn clean compile test-compile
$ java -classpath \
       target/classes/:target/test-classes/ \
       org.aeonbits.owner.examples.AutoReloadExample
```

Then you can change the file indicated by the program to see the changes being
reflected and the reload event being intercepted.
