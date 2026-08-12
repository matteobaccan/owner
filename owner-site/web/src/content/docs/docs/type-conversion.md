---
title: "Type conversion"
---


OWER API supports properties conversion for primitive types and enums.
When you define the *mapping interface* you can use a wide set of return types,
and they will be automatically converted from `String` to the primitive types
and enums:

```java
// conversion happens from the value specified in the
// properties files (if available).
int maxThreads();

// conversion happens also from @DefaultValue
@DefaultValue("3.1415")
double pi();

// enum values are case sensitive!
// java.util.concurrent.TimeUnit is an enum
@DefaultValue("NANOSECONDS")
TimeUnit timeUnit();
```

It is possible to have configuration interfaces to declare business objects as return types, many are compatible and
you can also define your own objects:

The easiest way is to define your business object with a public constructor
taking a single parameter of type `java.lang.String`:

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

    @DefaultValue("https://matteobaccan.github.io/owner")
    URL sampleURL();

    @DefaultValue("example")
    CustomType customType();

    @DefaultValue("Hello %s!")
    CustomType salutation(String name);
}
```

OWNER API will take the value "example" and pass it to the CustomType
constructor then return it.

### Types built by a static factory

*Since 2.0.0.* A `String` constructor is not the only way in. A type is also read
when it has a public static factory taking the text, which is how most types
written since Java 8 are built:

| The type has | Example |
|---|---|
| `public static T valueOf(String)` | every `enum`, `Integer`, `Boolean` |
| `public static T of(String)` | `ZoneId`, `Year` |
| `public static T parse(CharSequence)` | `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime`, `Instant` |

So the `java.time` types read out of the box, with nothing to register:

```java
public interface ScheduleConfig extends Config {
    @DefaultValue("2026-08-12")
    LocalDate releaseDate();

    @DefaultValue("1979-05-27T07:32:00Z")
    OffsetDateTime createdAt();

    @DefaultValue("Europe/Rome")
    ZoneId zone();

    @DefaultValue("2026-01-01, 2026-06-15")
    List<LocalDate> milestones();
}
```

None of these worked before 2.0.0: they have no `String` constructor and no
`valueOf`, so the chain ran out and refused them. The names come from
[MicroProfile Config][mpconfig], which settled the question for the ecosystem —
its implicit converters are `of`, `valueOf`, `parse` and the `String`
constructor, and those are exactly the four now understood here.

We differ from it in one respect, deliberately: MicroProfile tries the `String`
constructor **last**, and we try it **first**, as this library always has.
Changing that would silently move a type that has both a constructor and a
factory from one to the other, which is not worth doing to a configuration that
already works.

When the factory exists and rejects the text, what it said is kept:
`@DefaultValue("the thirty-first of never")` on a `LocalDate` refuses with the
`DateTimeParseException` underneath, naming the character it did not expect,
rather than a bare *cannot convert*.

  [mpconfig]: https://download.eclipse.org/microprofile/microprofile-config-3.0.1/apidocs/org/eclipse/microprofile/config/spi/Converter.html

Arrays and Collections
----------------------

OWNER have first class support for Java Arrays and Collections.

So now you can define properties like:

```java
public class MyConfig extends Config {

  @DefaultValue("apple, pear, orange")
  public String[] fruit();

  @Separator(";")
  @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
  public int[] fibonacci();

  @DefaultValue("1, 2, 3, 4")
  List<Integer> ints();

  @DefaultValue(
    "http://aeonbits.org, http://github.com, http://google.com")
  MyOwnCollection<URL> myBookmarks();

  // Concrete class are allowed (in this case java.util.Stack)
  // when type is not specified <String> is assumed as default
  @DefaultValue(
    "The Lord of the Rings,The Little Prince,The Da Vinci Code")
  Stack books();

}
```

You can use array of objects or primitive Java types, as well as Java
collections, as specified by interfaces [`Collection`][Collection],
[`List`][List], [`Set`][Set], [`SortedSet`][SortedSet] or concrete
implementations like [`Vector`][Vector], [`Stack`][Stack],
[`LinkedList`][LinkedList] etc. or your own concrete implementation of the Java
Collections Framework interfaces, as long as your implementation class defines
a default no-arg constructor.

  [Collection]: http://docs.oracle.com/javase/7/docs/api/java/util/Collection.html
  [List]: http://docs.oracle.com/javase/7/docs/api/java/util/List.html
  [Set]: http://docs.oracle.com/javase/7/docs/api/java/util/Set.html
  [SortedSet]: http://docs.oracle.com/javase/7/docs/api/java/util/SortedSet.html
  [Vector]: http://docs.oracle.com/javase/7/docs/api/java/util/Vector.html
  [Stack]: http://docs.oracle.com/javase/7/docs/api/java/util/Stack.html
  [LinkedList]: http://docs.oracle.com/javase/7/docs/api/java/util/LinkedList.html

Since version 2.0.0, [`EnumSet`][EnumSet] is also supported for enum types:

```java
public interface MyConfig extends Config {

    enum Fruit {
        APPLE, PEAR, ORANGE
    }

    // returns EnumSet.of(Fruit.APPLE, Fruit.ORANGE);
    // duplicate values are discarded, as you would expect from a Set
    @DefaultValue("APPLE, ORANGE")
    EnumSet<Fruit> favoriteFruit();
}
```

  [EnumSet]: https://docs.oracle.com/javase/8/docs/api/java/util/EnumSet.html

### One element per key

*Since 2.0.0.*

A list can also be written with an index, one element to a property:

```properties
servers[0]=alpha
servers[1]=beta
```

```java
public interface MyConfig extends Config {
    List<String> servers();               // [alpha, beta]
}
```

which works for every array and collection type above. The order is the one the indices give, whatever
order the file happens to be written in.

The reason to prefer it is that **an element written this way is one element whatever it contains**. A list
held in a single value has to be split on something, and a value containing that something then cannot be
written at all — `a,b` as a single element is simply not expressible. With an index it is:

```properties
servers[0]=a,b
servers[1]=c
```

gives two elements, `a,b` and `c`. The separator — the default comma, or one declared with `@Separator`
or `@TokenizerClass` — does not apply here: there is nothing to split.

**If there is an indexed key, that is the list**, and a single value under the plain key is not read — nor
is a `@DefaultValue`, which applies only when no indexed key is there at all. Nothing that worked before
changes: a `servers=alpha,beta` with no indexed keys anywhere is split exactly as it always was.

<div class="note warning">
  <h5>Number them from zero, without gaps</h5>
  <p>
    <code>servers[0]</code> and <code>servers[2]</code> with no <code>servers[1]</code> is refused, and so
    is a list that starts at <code>servers[1]</code>. Reading across a gap would silently give a list
    shorter than the file describes, with every element after the gap at a different position from the one
    it was written at — and nothing in the result would show it. Other libraries close the gap up or fill
    it with a null; OWNER would rather tell you.
  </p>
</div>

A key *below* an element, such as `servers[0].port`, is not an element and is left alone: that describes
something inside an entry rather than an entry, and reading it will come with nested configuration
interfaces.

A [`Map`][Map] return type reads a **group of properties**: the ones whose
name starts with the key of the method, followed by a dot. The rest of the
name becomes the entry key.

```properties
something.foo=1
something.bar=2
something.baz=3
```

```java
public interface MyConfig extends Config {
    Map<String, Integer> something();     // {foo=1, bar=2, baz=3}
}
```

Both sides of the entry go through the regular type conversion, so neither is
limited to strings: `Map<Integer, String>` and `Map<Colour, String>` work as
you would expect, and so does anything else OWNER can convert. The group is
named like any other key, which means [`@Key`](/owner/docs/usage/) and
[`@Prefix`](/owner/docs/key-prefix/) select it, and a variable can
pick it at runtime:

```java
@Key("servers.${env}")
Map<String, String> servers();      // servers.dev.* when env is dev
```

A name with further dots in it keeps them: `something.a.b=2` becomes the entry
`a.b`, so nothing is dropped and nothing has to be escaped. When no property
matches, the result is an empty map, never `null`. `@DefaultValue` is refused
on such a method: a default belongs to the individual properties, not to the
group.

Each value is read exactly as it would be if the property were mapped to a
method of its own — [preprocessors](/owner/docs/preprocessors/) run on
it, `${...}` variables are expanded, and an `@EncryptedValue` group is
decrypted entry by entry:

```properties
group.url=http://${host}/api
```

### Which map you get

The map that comes back is the one you declared, and **nothing is ever
substituted**. A class is instantiated as itself, provided it has a
no-argument constructor: declare a `HashMap` and you get a `HashMap`. An
interface has no constructor to call, so an implementation is chosen for
it — and the one chosen always satisfies the interface, which is what the
table below is really saying.

| You declare | You get | Ordering |
|---|---|---|
| `Map` | `LinkedHashMap` | the order the properties were read in |
| `SortedMap` | `TreeMap` | sorted by key |
| `NavigableMap` | `TreeMap` | sorted by key |
| `ConcurrentMap` | `ConcurrentHashMap` | none |
| `ConcurrentNavigableMap` | `ConcurrentSkipListMap` | sorted by key |
| `EnumMap<E, …>` | `EnumMap` over `E` | the order the constants are declared |
| `HashMap`, `TreeMap`, `LinkedHashMap`, `Hashtable`, `Properties`, `ConcurrentHashMap`, `WeakHashMap`, … | that exact class | whatever the class does |
| any other class with a no-argument constructor, yours included | that exact class | whatever the class does |

Two are refused rather than guessed at, each naming itself in the message: a
**map interface OWNER does not know**, since nothing it can build would
satisfy it, and a **class whose only constructor takes arguments**, since
there is nothing to call.

`EnumMap` is the one worth a note. It is the only map in the JDK with no
no-argument constructor, having to be told the class of its keys — which
OWNER already knows, having read it off the return type in order to convert
them. A raw `EnumMap`, with no key type to read, is the one case that cannot
work, and the message says so.

### How each side is converted

| Part | Comes from | Converted to |
|---|---|---|
| entry key | the part of the property name after `theKey.` | the first type argument, `K` |
| entry value | the property value | the second type argument, `V` |

Both go through the ordinary conversion, so neither side is limited to
strings and both accept [anything OWNER can
convert](#all-the-types-supported-by-owner) — including a `@ConverterClass`
registered for the type. A raw
`Map`, and any type argument that is not a plain class such as a wildcard or
a type variable, is read as `String`.

Whichever side fails, the message names **the property**, not the group:
`Cannot convert 'alpha' to Colour for property 'group.alpha'`. With fifty
properties under one prefix, that is the difference between a fix and a
search.

An enum key is worth knowing about, being the common case where the two do
not line up by themselves: conversion is `Enum.valueOf` and folds nothing, so
the properties have to name the constants exactly — `group.GREEN`, not
`group.green`.

The other shape: one property holding the pairs
-----------------------------------------------

Sometimes the pairs do not live in properties of their own: they are written
inside a single property value. That is the same configuration said
differently, and OWNER reads it into the same map — but you have to say *how*
that value is written, because there is no canonical syntax for it: comma or
space, equals or colon.

Here is one map, `{host=localhost, port=8080}`, obtained both ways.

**As properties of their own**, which needs nothing declared:

```properties
server.host=localhost
server.port=8080
```

```java
public interface MyConfig extends Config {
    Map<String, String> server();
}
```

**As a single value**, which needs a [`@ConverterClass`](#the-converterclass-annotation) saying how to
split it:

```properties
server=host=localhost, port=8080
```

```java
public interface MyConfig extends Config {
    @ConverterClass(PairsConverter.class)
    Map<String, String> server();
}

public class PairsConverter implements Converter<Map<String, String>> {
    public Map<String, String> convert(Method method, String input) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (String pair : input.split(",", -1)) {
            String[] entry = pair.split("=", 2);
            result.put(entry[0].trim(), entry[1].trim());
        }
        return result;
    }
}
```

The two interfaces differ by one annotation, and that annotation is what tells
the shapes apart: declaring how to parse a value says that there is a value to
parse, so the converter takes precedence and the properties below `server.`
are left alone. The equality of the two results is checked by a test.

The converter decides both sides of the entry, so the values are not limited
to strings — a `Map<String, Integer>` is a matter of calling `Integer.valueOf`
in the loop above — and the map it returns is the one you get back, so the
implementation and its iteration order are yours to choose.

An array of maps works as well: the value is split by the separator first and
the converter is handed one chunk at a time.

```java
@Separator(";")
@ConverterClass(PairsConverter.class)
@DefaultValue("name=Dante Alighieri, book=Divine Comedy;" +
              "name=Alessandro Manzoni, book=The Betrothed")
Map<String, String>[] authors();
```

  [Map]: http://docs.oracle.com/javase/7/docs/api/java/util/Map.html
  [Optional]: https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html

By default OWNER uses the comma `","` character to tokenize values for the
arrays and collections, but you can specify different characters (and regexp)
with the [`@Separator`][separator] annotation or, if your property format has a
more complex split logic, you can define your own tokenizer class via the
[`@TokenizerClass`][tokenizerclass] annotation plus [`Tokenizer`][tokenizer]
interface.

  [separator]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Config.Separator.html
  [tokenizerclass]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Config.TokenizerClass.html
  [tokenizer]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Config.Tokenizer.html

Example:

```java
public class MyConfig extends Config {

    @Separator(";")
    @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
    public int[] fibonacci();

    @TokenizerClass(CustomDashTokenizer.class)
    @DefaultValue("foo-bar-baz")
    public String[] withSeparatorClass();

}

public class CustomDashTokenizer implements Tokenizer {

    // this logic can be as much complex as you need
    @Override
    public String[] tokens(String values) {
        return values.split("-", -1);
    }
}
```

The [`@Separator`][separator] and [`@TokenizerClass`][tokenizerclass]
annotations can be specified on method level and on class level. When specified
on method level, the annotation will affect only that method. When specified on
class level, the annotation will affect the complete class.

Annotations specified on method level override the setting specified on the
class level:

```java
@Separator(";")
public interface ArrayExample extends Config {

    // takes the class level @Separator
    @DefaultValue("1; 2; 3; 4")
    public int[] semicolonSeparated();

    // overrides the class-level @Separator(";")
    @Separator(",")
    @DefaultValue("1, 2, 3, 4")
    public int[] commaSeparated();

    // overrides the class level @Separator(";")
    @TokenizerClass(CustomDashTokenizer.class)
    @DefaultValue("1-2-3-4")
    public int[] dashSeparated();
}
```

<div class="note warning">
  <h5>@Separator and @TokenizerClass don't go together!</h5>
    Notice that it is invalid to specify together on the same level both
    <code>@Separator</code> and <code>@TokenizerClass</code> annotations:
    you cannot specify two different ways to do the same thing!
</div>

So in following cases you'll get a [`UnsupportedOperationException`][unsupported-ex]:

```java

// @Separator and @TokenizerClass cannot be used together
// on class level.
@TokenizerClass(CustomCommaTokenizer.class)
@Separator(",")
public interface Wrong extends Config {

    // will throw UnsupportedOperationException!
    @DefaultValue("1, 2, 3, 4")
    public int[] commaSeparated();

}

public interface AlsoWrong extends Config {

    // will throw UnsupportedOperationException!
    // @Separator and @TokenizerClass cannot be
    // used together on method level.
    @Separator(";")
    @TokenizerClass(CustomDashTokenizer.class)
    @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
    public int[] conflictingAnnotationsOnMethodLevel();

}
```

  [unsupported-ex]: http://docs.oracle.com/javase/7/docs/api/java/lang/UnsupportedOperationException.html

But even though the following example contains a conflict on class level
(and should be considered a bug in the example), OWNER is able to resolve things
correctly on method level:

```java

// @Separator and @TokenizerClass cannot be used together
// on class level.
@Separator(";")
@TokenizerClass(CustomDashTokenizer.class)
public interface WrongButItWorks extends Config {

    // but this overrides the class level annotations
    // hence it will work!
    @Separator(";")
    @DefaultValue("1, 2, 3, 4")
    public int[] commaSeparated();

}
```

It is not recommended to have above wrong annotations setup: it is considered a bug in the code, and even if this setup
works at the moment, we may change this behavior in future.


The @ConverterClass annotation
------------------------------

OWNER provides the
[`@ConverterClass`](https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Config.ConverterClass.html)
annotation that allows the user to specify a customized conversion logic implementing the
[`Converter`](https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Converter.html) interface.

```java
interface MyConfig extends Config {
    @DefaultValue("foobar.com:8080")
    @ConverterClass(ServerConverter.class)
    Server server();

    @DefaultValue(
      "google.com, yahoo.com:8080, matteobaccan.github.io/owner:4000")
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
Server s = cfg.server(); // will return a single server
Server[] ss = cfg.servers(); // it works also with collections
```

In the above example, when calling the method `servers()` that returns an array of Server objects, the ServerConverter
will be used several times to convert every single element. In any case the ServerConverter in the above example always
works with a single element.

Since 2.0.0 the converter class does not have to be public, and neither does its constructor: it may be
package-private beside the interface that names it, or a `private static` class nested inside it. A converter is
an implementation detail of the configuration that uses it, and requiring it to be public meant widening your
own API to satisfy this library's — see [#186](https://github.com/matteobaccan/owner/issues/186), which applies
equally to [preprocessors](/owner/docs/preprocessors/), tokenizers and [decryptors](/owner/docs/crypto/). What
is still required is a constructor taking no arguments.

To see the complete test cases supported by owner see [ConverterClassTest] on GitHub.

  [ConverterClassTest]: https://github.com/matteobaccan/owner/blob/master/owner/src/test/java/org/aeonbits/owner/typeconversion/ConverterClassTest.java

The @CollectionConverterClass annotation
----------------------------------------

`@ConverterClass` converts *one element at a time*: OWNER splits the property value first, then calls the
converter on each piece. When you need to take over the whole value instead, use
[`@CollectionConverterClass`](https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Config.CollectionConverterClass.html),
available since version 2.0.0. The raw value is handed to the converter untouched, and the collection it
returns is the one the method gives back.

This is what you want when:

 * the property holds a single indivisible document — a JSON array, for instance — that the built-in
   tokenization would tear apart before the converter ever saw it;
 * the collection has to be of a type OWNER cannot instantiate on its own, such as an implementation without a
   no-argument constructor, or an immutable one;
 * you want to keep the concrete collection type out of the interface signature.

```java
interface MyConfig extends Config {
    @DefaultValue("google.com, yahoo.com:8080, owner.aeonbits.org:4000")
    @CollectionConverterClass(CollectionServerConverter.class)
    List<Server> servers();
}

public class CollectionServerConverter implements Converter<List<Server>> {
    public List<Server> convert(Method targetMethod, String text) {
        String[] split = text.split(",", -1);
        ServerConverter converter = new ServerConverter();
        List<Server> list = new ArrayList<Server>(split.length);
        for (String server : split) {
            list.add(converter.convert(targetMethod, server.trim()));
        }
        return Collections.unmodifiableList(list);
    }
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);
List<Server> ss = cfg.servers(); // the immutable list built by the converter
```

The converter is fully responsible for the whole process. In particular, `@Separator`, `@TokenizerClass` and
`@ConverterClass` are *not* applied for you: if you want to honour them, read them off the `Method` you receive,
as [CollectionConverterClassTest] does.

<div class="note warning">
  <h5>Only on methods returning a Collection.</h5>
  <p>
  The annotated method must return a <code>java.util.Collection</code>. Arrays are not collections, so
  <code>@CollectionConverterClass</code> on an array — or on any other type — raises an
  <code>UnsupportedOperationException</code> naming the method, instead of failing later with an obscure
  <code>ClassCastException</code>. Use <code>@ConverterClass</code> for those cases.
  </p>
</div>

  [CollectionConverterClassTest]: https://github.com/matteobaccan/owner/blob/master/owner/src/test/java/org/aeonbits/owner/typeconversion/collections/CollectionConverterClassTest.java

Optional values
---------------

*Since 2.0.0.*

A property that is not defined anywhere, and has no `@DefaultValue` to fall back on, is read as `null`. When
the caller has something to do about it, saying so in the signature is clearer than leaving a `null` to be
remembered:

```java
public interface ServerConfig extends Config {
    Optional<Integer> port();
}
```

```java
int port = cfg.port().orElse(8080);
cfg.port().ifPresent(this::bind);
```

The wrapper says what happens when the property is **absent**; it changes nothing about how the value is
read. `Optional<Integer>` converts its value to an `Integer` exactly like `Integer` does, so `@Key`,
`@Prefix`, the preprocessors, the variable expansion and the decryption all apply as usual, and so does
everything in this chapter: `Optional<List<String>>` is tokenized and converted element by element, and a
`@ConverterClass` is used if there is one. A `@ConverterClass` that returns `null` yields an empty
`Optional`, since that is what "this value could not be turned into anything" means.

Two cases are deliberately *not* absences:

  * **A value that is there but wrong.** `port=8O80`, written with the letter O, keeps failing with an
    `UnsupportedOperationException` instead of coming back empty. Turning it into an empty `Optional` would
    make a typo indistinguishable from a property nobody ever set, which is the opposite of the point.
  * **A value that is there but empty.** `port=` is a value like any other here as everywhere else, so
    `Optional<String>` holds the empty string rather than being absent. Use
    [`@DefaultValue(useOnEmpty = true)`](/owner/docs/usage/#a-property-that-is-set-but-empty) when an
    empty value should be treated as a missing one.

A `@DefaultValue` combines with an `Optional`, but the result is never empty, since the default always
resolves. And a raw `Optional`, or one holding a wildcard, carries no type to convert to and is read as
`String`, the same default a raw collection takes.

<div class="note info">
  <h5>Optional and @Mandatory</h5>
  <p>
  The two say the opposite of each other, and writing both <b>on the same method</b> is reported when the
  Config object is created. A <code>@Mandatory</code> written <b>on the interface</b> is a different matter:
  it is the way of saying "these are all required", and a method returning an <code>Optional</code> is the
  exception being made, so it is left alone rather than rejected.
  </p>
</div>

There is no `Optional<Map<...>>`: a `Map` return type reads a *group* of properties and already comes back
empty when nothing matches it, so there is no absence for an `Optional` to describe.

All the types supported by OWNER
--------------------------------

But there is more. OWNER API supports automatic conversion for:

  1. Primitive types: boolean, byte, short, integer, long, float, double.
  2. Enums (notice that the conversion is case sensitive, so FOO != foo or Foo).
  3. java.lang.String, of course (no conversion is needed).
  4. java.net.URL, java.net.URI.
  5. java.io.File and java.nio.file.Path (since 2.0.0), both expanding a leading `~` to the `user.home`
     System Property.
  6. java.lang.Class (this can be useful, for instance, if you want to load the jdbc driver, or similar cases).
  7. Any instantiable class declaring a public constructor with a single argument of type `java.lang.String`.
  8. Any instantiable class declaring a public constructor with a single argument of type `java.lang.Object`.
  9. Any class declaring a public *static* method `valueOf(java.lang.String)` that returns an instance of itself.
  10. Any class for which you can register a [`PropertyEditor`][propedit] via
      [`PropertyEditorManager.registerEditor()`][propeditmanager].
      (See [PropertyEditorTest] as an example).
  11. Any array having above types as elements.
  12. Any object that can be instantiated via `@ConverterClass` annotation explained before.
  13. Any Java Collections of all above types: Set, List, SortedSet, EnumSet (since 2.0.0) or concrete
      implementations like LinkedHashSet or user defined collections having a default no-arg constructor.
  14. [`Map`][Map] and sub-interfaces (since 2.0.0), reading the group of properties below the key of the
      method, with both the keys and the values converted to the declared types.
  15. [`Optional`][Optional] (since 2.0.0) of any of the above, empty when the property is not defined
      anywhere.
  16. [`java.time.Duration`][duration] (since 2.0.0), written with an explicit time unit — `10 s`,
      `500 ms` — or as an ISO-8601 duration. See [below](#duration).

If OWNER API cannot find any way to map your business object, you'll receive a [`UnsupportedOperationException`][unsupported-ex]
with some meaningful description to identify the problem as quickly as possible. The message names the value, the
type it could not be converted to, and the key of the property it came from, for instance
`Cannot convert 'abc' to int for property 'server.port'`. The key is the one the property is read with, so it
accounts for `@Key` and `@Prefix`.

The same applies to the single elements of an array or a collection: the conversion strategy is determined once
from the first element, then applied to all of them, and a single element that cannot be converted fails the whole
property with an [`UnsupportedOperationException`][unsupported-ex] naming the offending value. For instance
`@DefaultValue("1, 2, foo, 4")` on a `MyType[]` reports `Cannot convert 'foo' to MyType for property 'myTypes'`.
A `@ConverterClass` is free to return `null` for an element, which produces a `null` in the resulting array or
collection.

An empty value
--------------

A property that is present but empty, `server.port=`, is a value like any other: the `@DefaultValue` is not
used in its place, and whether the conversion succeeds depends on whether the declared type can represent an
empty text. What follows is the whole picture, with `useOnEmpty` being the opt-in described in
[Using @DefaultValue](/owner/docs/usage/) that makes the empty value fall back on the default.

| Declared type | `prop=` (empty) | `prop=abc` (not convertible) | `prop=` with `useOnEmpty = true` |
|---|---|---|---|
| `int`, `long`, `double`, `Integer`, ... | `UnsupportedOperationException` | `UnsupportedOperationException` | the default value |
| `boolean`, `Boolean` | `UnsupportedOperationException` | `UnsupportedOperationException` | the default value |
| `char` | `UnsupportedOperationException` | `UnsupportedOperationException` | the default value |
| `enum` | `UnsupportedOperationException` | `UnsupportedOperationException` | the default value |
| `BigDecimal`, and any class built from a `String` constructor that rejects it | `UnsupportedOperationException` | `UnsupportedOperationException` | the default value |
| `Class` | `UnsupportedOperationException` | `UnsupportedOperationException` | the default value |
| `URL` | `UnsupportedOperationException` | `UnsupportedOperationException` | the default value |
| `String` | `""` | `"abc"` | the default value |
| `File`, `Path`, `URI` | an empty path | accepted | the default value |
| `Duration` | `UnsupportedOperationException` | `UnsupportedOperationException` | the default value |
| arrays and collections | an empty array or collection | `UnsupportedOperationException` on the element | the default value |

Two rows deserve a word. `File`, `Path` and `URI` accept an empty value because any text is a valid path or
URI, so nothing is there to fail. Arrays and collections read an empty value as an empty collection, which is
the same choice the MicroProfile Config specification makes, and it is why an empty value fails on a number
but not on a list of numbers.

Notice that on the last three rows `useOnEmpty` replaces a result that works today, which is one of the
reasons why it is opt-in: without it, nothing of what is described above changes.

You can also register your custom [`PropertyEditor`][propedit] to convert text properties into your business objects
using the static method [`PropertyEditorManager.registerEditor()`][propeditmanager].
See also [`PropertyEditorSupport`][propeditsupport], it may be useful if you want to implement a `PropertyEditor`.

  [propeditmanager]: http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditorManager.html#registerEditor
  [propedit]: http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditor.html
  [propeditsupport]:http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditorSupport.html
  [PropertyEditorTest]: https://github.com/matteobaccan/owner/blob/master/owner/src/test/java/org/aeonbits/owner/typeconversion/editor/PropertyEditorTest.java

Converter classes shipped with OWNER
------------------------------------

Since specifying duration and byte size values in configuration files is very common,
OWNER ships with converter classes for these as well as some classes for the types themselves. A
duration is converted automatically since 2.0.0; a byte size still asks for its converter to be named,
since `ByteSize` is a type of ours and not one of the JDK's.

Since 2.0.0 they are part of the core `owner` artifact, and no extra dependency is needed: they used
to be shipped separately only because the core had to run on Java 6 and could not name
`java.time.Duration`, which stopped being true when Java 8 became the minimum. Their package names
are unchanged, so an existing `import` keeps working — if you depended on `owner-java8-extras` for
them, replace that dependency with `owner`.

### Duration

*Automatic since 2.0.0.*

A [`java.time.Duration`][duration] is converted out of the box, like a `File` or a `URL`: a timeout is
the commonest typed setting there is after a number and a string, so no annotation is needed.

  [duration]: https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html

```java
public interface ServerConfig extends Config {

  @DefaultValue("10 s")
  Duration connectTimeout();

  @DefaultValue("500 ms")
  Duration readTimeout();

  // the ISO-8601 form that java.time.Duration.parse reads is accepted as well
  @DefaultValue("PT15M")
  Duration sessionExpiry();
}
```

The suffix may be written attached or separated — `10s` and `10 s` are the same — and collections,
arrays and `Optional` work as they do for any other type: `List<Duration> retries()` reading
`10 s, 30 s, 1 m` gives three durations.

<div class="note warning">
  <h5>The time unit is required</h5>
  <p>
  <code>timeout=30</code> is <b>refused</b>, with a message saying what to write instead. A bare number
  would be read as milliseconds, and whoever writes 30 means seconds far more often than milliseconds —
  a service that gives up thirty milliseconds in, with nothing said, is not a failure worth being
  clever about. Write the unit, or an ISO-8601 duration.
  </p>
</div>

The suffix is **case sensitive**: `10 s` is ten seconds and `10 S` is an error. Note that this differs
from the byte size units below, which are case insensitive.

#### Asking for the converter explicitly

`DurationConverter` is still there and can still be named on a method, which is what you want when the
value is not the automatic form:

```java
  @ConverterClass(DurationConverter.class)
  @DefaultValue("30")
  Duration legacyMillis();   // 30 milliseconds
```

**Named explicitly, the converter accepts a bare number and reads it as milliseconds.** That is the
behaviour it has always had, and it is left alone: a configuration written before 2.0.0 keeps working
exactly as it did. The rule is simply which of the two you get:

| | A bare number, `30` | `30 s`, `PT30S` |
|---|---|---|
| automatic, no annotation | refused, with a message | 30 seconds |
| `@ConverterClass(DurationConverter.class)` | 30 milliseconds | 30 seconds |

A `@ConverterClass` naming any other converter, or one
[registered](/owner/docs/configuring/) for `Duration`, also takes precedence over the automatic
conversion: the default never gets in the way of an explicit choice.

The suffixes supported by DurationConverter are:

- `ns`, `nano`, `nanos`, `nanosecond`, `nanoseconds`
- `us`, `µs`, `micro`, `micros`, `microsecond`, `microseconds`
- `ms`, `milli`, `millis`, `millisecond`, `milliseconds`
- `s`, `second`, `seconds`
- `m`, `minute`, `minutes`
- `h`, `hour`, `hours`
- `d`, `day`, `days`

<div class="note warning">
  <h5>Two characters that look the same</h5>
  <p>
    The <code>µ</code> above is <strong>MICRO SIGN</strong>, U+00B5. There is a second character,
    <strong>GREEK SMALL LETTER MU</strong> at U+03BC, that most fonts draw identically, and some keyboard
    layouts and word processors produce it instead. It is not accepted — but rather than refusing it with the
    ordinary "could not parse" message, which would leave you staring at a unit that looks exactly right,
    OWNER names the difference and tells you which one you wrote. Writing <code>us</code> avoids the question
    entirely, and is what we would suggest for a file that gets edited by more than one person.
  </p>
</div>

<div class="note warning">
  <h5>And a space that is not a space</h5>
  <p>
    The separator in <code>30 s</code> is also worth a word. A value pasted out of a word processor, a web
    page or a chat window often carries a <strong>no-break space</strong> (U+00A0), a narrow one (U+202F) or
    a zero-width one (U+200B) where an ordinary space belongs. None of them is removed by trimming, none of
    them is visible anywhere, and the number then fails to parse over a character nobody can see. OWNER names
    the character and its position instead of quoting a value that looks entirely correct. The same applies
    to a <a href="#byte-size">byte size</a>.
  </p>
</div>

### Byte Size

The Java API does not provide any classes to represent data sizes. Therefore,
OWNER provides this functionality with a set of classes in the
`org.aeonbits.owner.util.bytesize` package: `ByteSize` and `ByteSizeUnit`.

The usage of these classes is best explained with an example:

```java
import org.aeonbits.owner.util.bytesize.*;

[...]

ByteSize oneByte = new ByteSize(1, ByteSizeUnit.BYTES);
ByteSize oneMegaByte = new ByteSize(1, ByteSizeUnit.MEGABYTES);

// Units can be converted
ByteSize mbAsGb = oneMegaByte.convertTo(ByteSizeUnit.GIGABYTES);

// Both IEC and SI units are supported
ByteSize mbAsGiB = oneMegaByte.convertTo(ByteSizeUnit.GIBIBYTES);

// Get the number of bytes a ByteSize represents as a long
long oneMegaByteAsLong = oneMegaByte.getBytesAsLong();

// Sizes are compared by the amount of data, whatever unit they are written in
boolean mebibyteIsLarger = oneMegaByte.compareTo(new ByteSize(1, ByteSizeUnit.MEBIBYTES)) < 0; // true

// When the unit that suits a size is not known in advance, ask for the family instead:
// in() picks the largest unit of that standard in which the value does not fall below one
ByteSize sum = new ByteSize(2048576, ByteSizeUnit.BYTES);
sum.in(ByteSizeStandard.SI);   // 2.048576 MB
sum.in(ByteSizeStandard.IEC);  // 1.95367431640625 MiB
```

`convertTo` needs to be told the unit; `in` needs only the family to pick from, which is usually
what one has when a size read from a configuration file has to be logged or shown. Its answer is
canonical — it depends on the size and never on the unit it happened to be written in, so `1 MB` and
`1000000 B` both read as `1 MB` in SI — and it is exact, since every factor is a power of 1000 or of
1024 and no division by one of those can fail to terminate. Zero, and anything below one byte, reads
in bytes; a negative size keeps its sign and takes the unit its magnitude asks for.

`ByteSize` is immutable and `final`. Two instances are equal when they represent the same number of
bytes, so `1 MB` equals `1000000 B`, and since 2.0.0 it implements
[`Comparable`][comparable] with an ordering consistent with that equality: a `TreeSet` of byte sizes
agrees with a `HashSet` on which of them are duplicates. It is also `Serializable`, and the unit
survives the round trip along with the value: a size written as `1 MB` comes back reading as `1 MB`.

  [comparable]: https://docs.oracle.com/javase/8/docs/api/java/lang/Comparable.html

For converting configuration strings into the `ByteSize` type, the
`ByteSizeConverter` class is provided.

Example:

```java
public interface ByteSizeConfig extends Config {
  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10 byte")
  ByteSize singular10byteWithSpace();

  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10byte")
  ByteSize singular10byteWithoutSpace();

  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10 bytes")
  ByteSize plural10byte();

  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10m")
  ByteSize short10mebibytes();

  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10mi")
  ByteSize medium10mebibytes();

  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10mib")
  ByteSize long10mebibytes();

  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10 megabytes")
  ByteSize full10megabytes();
}
```

The suffixes supported by ByteSizeConverter are:

- `byte`, `bytes`, `b`
- `kibibyte`, `kibibytes`, `k`, `ki`, `kib`
- `kilobyte`, `kilobytes`, `kb`
- `mebibyte`, `mebibytes`, `m`, `mi`, `mib`
- `megabyte`, `megabytes`, `mb`
- `gibibyte`, `gibibytes`, `g`, `gi`, `gib`
- `gigabyte`, `gigabytes`, `gb`
- `tebibyte`, `tebibytes`, `t`, `ti`, `tib`
- `terabyte`, `terabytes`, `tb`
- `pebibyte`, `pebibytes`, `p`, `pi`, `pib`
- `petabyte`, `petabytes`, `pb`
- `exbibyte`, `exbibytes`, `e`, `ei`, `eib`
- `exabyte`, `exabytes`, `eb`
- `zebibyte`, `zebibytes`, `z`, `zi`, `zib`
- `zettabyte`, `zettabytes`, `zb`
- `yobibyte`, `yobibytes`, `y`, `yi`, `yib`
- `yottabyte`, `yottabytes`, `yb`
