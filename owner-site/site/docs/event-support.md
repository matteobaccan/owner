---
layout: docs
title: Event support
prev_section: xml-support
next_section: singleton
permalink: /docs/event-support/
---

The HotReload is a cool feature, however it would be nice to know which properties has changed so that you can
re-configure only the affected services.

This is why OWNER implements a feature rich event system that allows to know when a reload event occurs, when a
property is changed, and lets you to check if the new value is compliant to your application requirements and,
if not, you can rollback the property change or the entire change set.

Listening for Reloads
---------------------

As explained in [Reload and Hot Reload]({{site.url}}/docs/reload/) section, OWNER supports programmatic reload and
automatic reload (when a configuration file is changed). In both cases a reload event is generated.

To get notified when a reload event occurs, your interface must extend `Reloadable` that exposes some methods to
register a `ReloadListener`.

There is also the possibility to implement a `TransactionalReloadListener` which does notify you before the reload
becomes effective and allows you to check what is changed and eventually to rollback the reload operation if the
changes you see are not compliant to your application's requirements.

![reload-event]({{site.url}}/img/reload-event.png)

The `Reloadable` interfaces allows to attach and remove `ReloadListener` objects.

The `ReloadListener` interface defines a method called `reloadPerformed()` that receives a `ReloadEvent` which contains
the `oldProperties` (before the reload) and the `newProperties` (after the reload) plus a list of `PropertyChangeEvent`
for every property which is changed.

The `PropertyChangeEvent` contains the `propertyName`, the `oldValue` and the `newValue`.

When calling the method `Reloadable.addReloadListener()` it is possible to pass an instance of
`TransactionalReloadListener` which extends the `ReloadListener` adding the method `beforeReload()`.
This listener receives the notification just after the reload is triggered and before the changes are effective. In this
way it is possible for this listener to check what is changed and, if the changes are not welcome, it can trigger the
exception `RollbackBatchException` which means that all the changes in the reload will be discarded, and the current
values will be kept.
If the `RollbackBatchException` is thrown, the `ReloadListener.reloadPerformed()` will not be invoked, since the reload
has been aborted by the listener.

Example:

```java

@Sources("Example.properties")
interface MyConfig extends Reloadable {
    @DefaultValue("5")
    Integer someInteger();

    @DefaultValue("foobar")
    String someString();

    @DefaultValue("3.14")
    Double someDouble();

    String nullsByDefault();
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);

final boolean[] reloadPerformed = new boolean[] {false};

cfg.addReloadListener(new TransactionalReloadListener() {

    public void beforeReload(ReloadEvent event)
        throws RollbackBatchException {

        String notAllowedValue = "42";

        String newSomeInteger = event.getNewProperties()
            .getProperty("someInteger");

        // 42 makes the reload to rollback completely!
        if (notAllowedValue.equals(newSomeInteger))
            throw new RollbackBatchException(
              "42 is not allowed for property 'someInteger'");

    }

    public void reloadPerformed(ReloadEvent event) {
        reloadPerformed[0] = true;
    }

});

// we update the properties file in the filesystem
File target = new File("Example.properties");
save(target, new Properties() { {
    setProperty("someInteger", "41");
    setProperty("someString", "bazbar");
    setProperty("someDouble", "2.718");
    setProperty("nullsByDefault", "NotNullNow");
}});

cfg.reload();

// reload happened
assertTrue(reloadPerformed[0]);
assertEquals(new Integer(41), cfg.someInteger());
assertEquals("bazbar", cfg.someString());
assertEquals(new Double("2.718"), cfg.someDouble());
assertNotNull(cfg.nullsByDefault());

reloadPerformed[0] = false; // reset the flag

save(target, new Properties() { {
    setProperty("someInteger", "42"); // not allowed!
    setProperty("someString", "blahblah");
    setProperty("someDouble", "1.234");
}});

cfg.reload();

// reload was rolled back since 42 is not allowed
assertFalse(reloadPerformed[0]);
assertEquals(new Integer(41), cfg.someInteger());
assertEquals("bazbar", cfg.someString());
assertEquals(new Double("2.718"), cfg.someDouble());
assertNotNull(cfg.nullsByDefault());

```

Listening for PropertyChanges
-----------------------------

A `PropertyChangeEvent` happens every time the user changes a property using a method declared in the `Mutable`
interface, or when a [reload]({{site.url}}/docs/reload/) happens.

To get notified when a property change event occurs, your interface must extend `Mutable` that exposes some methods to
register a `PropertyChangeListener`.

There is also the possibility to implement a `TransactionalPropertyChangeListener` which does notify you before the
property change becomes effective and allows you to check what is changed and eventually to rollback the single
property change operation or the whole set of changes triggered by the source event.

![propertychange-event]({{site.url}}/img/propertychange-event.png)


The `Mutable` interfaces allows to attach and remove `PropertyChangeListener` objects. It is possible to attach
a listener to a specific property name, so that the listener will be associated to a single property name:

```java
public interface Mutable extends Config {
  void addPropertyChangeListener(String propertyName, PropertyChangeListener listener);
  void addPropertyChangeListener(PropertyChangeListener listener);
  void removePropertyChangeListener(PropertyChangeListener listener);
  // ...the rest of the interface is cut...
}
```

The `Mutable` interface defines methods to change the internal values of the properties as well, all of them will
trigger a `PropertyChangeEvent` and notify the registered listeners:

```java
public interface Mutable extends Config {
  String setProperty(String key, String value);
  String removeProperty(String key);
  void clear();
  void load(InputStream inStream) throws IOException;
  void load(Reader reader) throws IOException;
  // ...the rest of the interface is cut...
}
```


The `PropertyChangeListener` interface comes from the `java.beans` package and defines a method called
`propertyChange()` that receives a `PropertyChangeEvent` which contains the `propertyName`, the `oldValue` and
the `newValue`.

When calling the method `Mutable.addPropertyChangeListener()` it is possible to pass an instance of
`TransactionalPropertyChangeListener` which extends the `PropertyChangeListener` adding the method
`beforePropertyChange()`.
This listener receives the notification just after the change is triggered but before the changes become effective.
In this way it is possible for this listener to check what is changed and, if the changes are not welcome, the listener
can trigger the exception `RollbackOperationException` or `RollbackBatchException`.

Throwing `RollbackOperationException` means that the listener wants to rollback the single property change, even though
the property change may have been triggered due to an event who involves many property changes, and only the single
property change needs to be rolled back.
Instead the `RollbackBatchException` means that all the changes triggered by the source event must be rolled back, and
the previous values must be kept.

If the any of the two `RollbackException` explained above is thrown, the `PropertyChangeListener.propertyChange()`
will not be invoked, since the change has been aborted by the listener.

Obviously also the `reload()` operation happenning during a hot reload, or when invoked via the `Reloadable` interface
triggers the PropertyChangeEvent.

Example:

```java

@Sources("Example.properties")
interface MyConfig extends Reloadable {
    @DefaultValue("5")
    Integer someInteger();

    @DefaultValue("foobar")
    String someString();

    @DefaultValue("3.14")
    Double someDouble();

    String nullsByDefault();
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);

final boolean[] reloadPerformed = new boolean[] {false};

cfg.addPropertyChangeListener("someInteger",
        new TransactionalPropertyChangeListener() {
    public void beforePropertyChange(PropertyChangeEvent event)
            throws RollbackOperationException, RollbackBatchException {
        String notAllowedValue = "88";
        String makesEverythingToRollback = "42";

        String newSomeInteger = (String)event.getNewValue();
        if (notAllowedValue.equals(newSomeInteger))
            throw new RollbackOperationException(
                "88 is not allowed for property 'someInteger', " +
                "the single property someInteger is rolled back");

        if (makesEverythingToRollback.equals(newSomeInteger))
            throw new RollbackBatchException(
                "42 is not allowed for property 'someInteger', " +
                "the whole event is rolled back");

    }

    public void propertyChange(PropertyChangeEvent evt) {
        reloadPerformed[0] = true;
    }
});

save(target, new Properties() { {
    setProperty("someInteger", "41");
    setProperty("someString", "bazbar");
    setProperty("someDouble", "2.718");
    setProperty("nullsByDefault", "NotNullNow");
}});

cfg.reload();

assertTrue(reloadPerformed[0]);
assertEquals(new Integer(41), cfg.someInteger());
assertEquals("bazbar", cfg.someString());
assertEquals(new Double("2.718"), cfg.someDouble());
assertNotNull(cfg.nullsByDefault());

reloadPerformed[0] = false;

cfg.setProperty("someInteger", "55");
assertTrue(reloadPerformed[0]);
assertEquals(new Integer(55), cfg.someInteger());

reloadPerformed[0] = false;


cfg.setProperty("someInteger", "88");
// 88 is rolled back.
assertFalse(reloadPerformed[0]);
assertEquals(new Integer(55), cfg.someInteger());

reloadPerformed[0] = false;

save(target, new Properties() { {
    setProperty("someInteger", "42");
    setProperty("someString", "blahblah");
    setProperty("someDouble", "1.234");
}});

cfg.reload();

assertFalse(reloadPerformed[0]);
assertEquals(new Integer(55), cfg.someInteger());
assertEquals("bazbar", cfg.someString());
assertEquals(new Double("2.718"), cfg.someDouble());
assertNotNull(cfg.nullsByDefault());


reloadPerformed[0] = false;

save(target, new Properties() { {
    setProperty("someInteger", "88");
    setProperty("someString", "this is not rolled back");
    setProperty("someDouble", "1.2345");
}});

cfg.reload();

assertFalse(reloadPerformed[0]);
// only someInteger=88 is rolled back
assertEquals(new Integer(55), cfg.someInteger());
assertEquals("this is not rolled back", cfg.someString());
assertEquals(new Double("1.2345"), cfg.someDouble());
assertNull(cfg.nullsByDefault());

```

Conclusions
-----------

With `ReloadListener` and `PropertyChangeListener` it is possible to get notified when your configuration changes.
This allows your application to take actions subsequently.

With `TransactionalReloadListener` and `TransactionalPropertyChangeListener` you can also check if the changes being
applied are coherent to your requirements and eventually rollback the single property change or the
complete set of changes, keeping things as they where before a reload.

This is a mechanism which allows for some basic validation.
A more simple and powerful validation mechanism is planned for the future releases, and the event mechanism will be
probably the backbone of this.
