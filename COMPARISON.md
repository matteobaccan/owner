COMPARISON WITH OTHER CONFIGURATION LIBRARIES
=============================================

**Internal working document — not published on the project site.**

This file is a snapshot taken on **2026-08-07**, for our own use in deciding what to build next.
It is deliberately not part of the documentation: a comparison of somebody else's API dates fast,
and this project is the proof — every page on the web comparing OWNER to something else still names
cfg4j as *the* alternative, and cfg4j has not seen a release since 2017. Statements about OWNER
belong on the [why page](owner-site/web/src/content/docs/docs/why.md) and stay true; statements about other people's
libraries belong here and need re-checking before they are relied on.

Everything below was verified against the projects themselves — the GitHub API for release dates,
the actual source files for the APIs — rather than taken from articles.

**Amended 2026-08-09**, everywhere marked with that date. Four things were checked against the sources
that day, each because we were deciding the same question and wanted to know what the field had settled
on before choosing: how SmallRye reads a `.env`, how the three libraries with indexed keys treat a gap in
the sequence, how each of them decides that a value is a secret, and — for the backlog line on formats —
what `FORMATS.md` has since superseded.

Two of those four found **no agreement at all** among the others, which is itself the useful result: it
meant the question had to be decided on merit rather than by alignment, and both decisions are written
down with the reasoning rather than with a citation.

**Amended again 2026-08-10**, before deciding C6 rather than after: how the field discovers loaders and
whether discovery means enablement, whether anyone has per-loader settings or per-source options, what
happens to a `null` in a tree-shaped source, and whether refusing an unrecognised option has a precedent.
The first of those found the field **unanimous against what we had proposed**, and the reason it is still
right to follow them is a difference in precedence models rather than in taste — see below. The one on
`null` found the two largest in open disagreement.

Everything not marked with one of those dates still dates from the snapshot above and still needs
re-checking before it is relied on.


The landscape
-------------

| Library | ★ | Last release | State |
|---|---|---|---|
| Typesafe / Lightbend Config | 6310 | 1.4.9 — Jun 2026 | alive, the de facto standard |
| Netflix Archaius | 2496 | 2.8.8 — Jun 2026 | alive |
| **OWNER** | **939** | **1.0.12 — Jun 2020** | alive, 2.0.0 in preparation |
| cfg4j | 555 | 4.4.1 — **Jul 2017** | **dead** (last push 2022) |
| Configurate (Sponge) | 466 | 4.2.0 — Feb 2025 | alive |
| NightConfig | 284 | 3.9.0 — Jun 2026 | alive |
| Apache Commons Configuration | 215 | — | alive (pushed Aug 2026) |
| SmallRye Config | 206 | 3.18.1 — Jul 2026 | alive, very |
| avaje-config | 108 | 5.2 — Jun 2026 | alive |
| Gestalt | 102 | 0.38.0 — Jun 2026 | alive, growing |
| Coat | 20 | — | alive (pushed Aug 2026) |
| Apache Tamaya | — | — | **retired to the Apache Attic** |

Star counts understate the Apache mirrors. The figure that matters for us is the third row: we have
the third largest installed base in the field and the oldest release of anyone in it.


The four that do what we do
---------------------------

"Interface + proxy = typed configuration" is no longer ours alone.

- **SmallRye Config `@ConfigMapping`** — the real competitor, because it implements MicroProfile
  Config and is therefore the default in Quarkus and Open Liberty: those users already have it.
  Nested groups, configurable naming strategy (kebab-case by default), `Map` with dynamic keys,
  indexed collections `prop[0]`, `Optional`, a `Secret<T>` type excluded from listings, Bean
  Validation, interface hierarchies with overrides, `@WithConverter`.
- **Netflix Archaius `ConfigProxyFactory`** — nearly our design: `@Configuration(prefix)`,
  `@PropertyName`, `@DefaultValue`, `default` methods, parametrized methods. The philosophical
  difference is that *their* default is dynamic — every call re-reads the current value,
  `immutable()` freezes — where ours is static with `@HotReload` as opt-in.
- **Coat** — generates the code at compile time with an annotation processor instead of proxying.
  No runtime dependency, no reflection, natively GraalVM-friendly. It attacks our most exposed
  flank.
- **Gestalt** — the most ambitious of the lot and the one to watch. Binds to interfaces, records,
  beans and Kotlin data classes; sources from files, env, K8s secrets, Git, S3, Azure Blob, GCS,
  Vault; 40+ decoders; load-time `${}` and run-time `#{}` substitution; `$include` for config trees;
  reload on file watch and on a timer; temporary secrets that expire after N reads; Micrometer
  metrics; zero-dependency core and JPMS support.

Other models, briefly: **Typesafe Config** (HOCON) leads on adoption but is a node tree, not a typed
binding. **Commons Configuration 2** covers every format and has reload and events, but pulls in
commons-lang3/text/beanutils and its API is `getString(key)`. **Configurate** and **NightConfig** are
strong on formats (TOML, YAML, HOCON, JSON) but neither starts from an interface. **avaje-config** is
modern and light but is a static `Config.getInt(...)` API. **Spring Boot** and **Micronaut** are not
competitors: they are frameworks that bring their own configuration.


What is ours
------------

Checked against all of the above:

- **Transactional reload with rollback** (`TransactionalReloadListener`, `RollbackBatchException`):
  a listener can refuse a reload and restore the previous state. **Found in no other library.**
- **Zero runtime dependencies in the core** — verified in the pom: test scope only.
- **Java 8 baseline.** Coat needs 11, Gestalt 11, SmallRye 17. We are the only modern option for
  anyone still on 8.
- **Encryption of values with a cipher actually shipped** (added 2026-08-14): `${$aes-gcm::…}`,
  AES-256/GCM over PBKDF2 at 210,000 iterations, in the core, with no dependency and no framework, on the
  Java 8 baseline — and the tool that encrypts a value in the same jar. The *marker* is not ours, SmallRye
  has the same shape; the construction, the absence of a dependency and the fact that the key does not
  come from a configuration property are.
- **And a key pair, `${$rsa-oaep::…}`**, so that whoever writes a secret into a configuration cannot read
  the ones already there. **No other Java configuration library has this in the library**: Spring Cloud
  Config has it behind a config server, and the rest are symmetric only. See "Encrypting a value against
  the equivalents" below. The older `@EncryptedValue` / `@DecryptorClass` remain, for whoever brought
  their own decryptor.
- **JMX**, **preprocessors**, **`Mutable`/`Accessible`** (we write, the others mostly only read),
  **parametrized properties**, **`@DisableFeature`** granularity, **prefix derived from the package**.
- BSD licence, no framework lock-in.
- **A `.env` whose dialect you choose** (added 2026-08-09; see `FORMATS.md`). Reading a `.env` is not
  ours — SmallRye does it, and so does a handful of standalone libraries. Being able to say *which*
  rules to read it by appears to be: SmallRye hands the file to `java.util.Properties.load` and takes
  what comes, and the dotenv ports each implement one dialect and only that one. Since `docker run
  --env-file`, Compose and the dotenv family genuinely disagree, and the same file is often read by
  more than one of them, having the reader say so is worth something. Small, but real, and it cost
  no dependency.


ByteSize against the equivalents
--------------------------------

State after the hardening of 2026-08-07.

| | OWNER `ByteSize` | Spring `DataSize` | Typesafe `ConfigMemorySize` | Quarkus `MemorySize` |
|---|---|---|---|---|
| Public types | 3 (+`ByteSizeUnit` 17 const., `ByteSizeStandard`) | 2 (+`DataUnit` 5 const.) | 1 | 1 |
| Internal representation | `BigDecimal` + unit | `long bytes` | `BigInteger` | `BigInteger` |
| **Keeps the written form** | **yes** | no | no | no |
| SI vs IEC | **distinguished** (KB=1000, KiB=1024) | no: "KB" is 1024 | in the parser | none |
| Fractions (`0.5 GB`) | **yes** | no | no | no |
| Range | unbounded | `long`, ~8 EB | unbounded | unbounded |
| `final` + immutable | yes | yes | yes | yes |
| `equals`/`hashCode` consistent | yes | yes | yes | yes |
| `Comparable` | yes | yes | no | yes |
| `Serializable` | yes | yes | no | no |
| Stream validation | **yes** (`readObject`) | n/a | — | — |
| Rejects null arguments | yes | n/a | n/a | n/a |
| Locale-independent parsing | yes | yes | yes | yes |
| Convert to a named unit | `convertTo(unit)` | `toKilobytes()`, … | no | no |
| **Pick the unit for me** | **`in(standard)`** | no | no | no |
| Arithmetic | no (deliberate) | no | no | yes |
| Negatives | accepted | accepted + `isNegative()` | — | accepted + `MINUS_1` |

Notes worth keeping:

- Spring also exposes a unit enum, so a unit model is not eccentric. But `DataSize` holds a single
  `long bytes` field: the unit is a construction and accessor flavour, not part of the value.
  Keeping the written form is ours alone.
- Spring labels 1024 bytes `KB`. We are the only one of the four that names the two families
  correctly.
- Arithmetic was considered and dropped. Three of the four do not have it; the one that does uses it
  internally for the framework, and pairs it with a `MINUS_1` "unlimited" sentinel that its own
  arithmetic cannot handle (`-1 + 1 MB` = 999999 bytes).
- One known defect survives that decision: `equals` compares the **rounded** byte count, so
  `0.4 B` equals `0.6 B` and `compareTo` agrees. It only bites on sub-byte fractions. The fix, if it
  is ever wanted, is to require a whole number of bytes at construction, which would also make an
  arithmetic correct by construction.


Indexed keys against the equivalents
------------------------------------

Checked 2026-08-09, while deciding our own. Three libraries have this, and **on the one case that has to
be decided they do three incompatible things**, which is worth knowing: there was no convention to follow,
so it was decided on merit rather than by alignment.

| | `[0]` and `[2]`, with no `[1]` | An indexed key beside a single value |
|---|---|---|
| **OWNER** | refused, naming the gap | the indexed key wins |
| **Spring Boot** | refused: *"omitting indices will lead to an `UnboundConfigurationPropertiesException`"* | not documented; a collection is never merged across sources, it comes whole from the highest one |
| **SmallRye** | closed up: the values are collected and sorted, with no empty elements | *"the indexed property format is prioritized"* |
| **Gestalt** | a `null` is inserted, with `setTreatMissingArrayIndexAsError` to refuse instead | not documented |

Two things came out of this that are ours rather than borrowed. **The notation is `[0]` because the dot
was already taken** by the `Map` grouping we added in 2.0.0 — with `list.0` a map whose keys are numbers
would be indistinguishable from a list — which is a better argument than "the others write it that way".
And **the gap is refused** because SmallRye's compaction moves every element after the gap to a different
position with nothing said, and Gestalt thought a switch to strictness worth adding, which suggests the
lenient default bit somebody.

Spring can afford to refuse a gap for a reason we cannot copy: it never merges a collection across
sources, so a gap is always one file's mistake. Ours merge by key, which makes splitting a list across
files broken already, in a quieter way — see `FORMATS.md`.


Hiding a value against the equivalents
--------------------------------------

Checked 2026-08-09, while fixing our own. The interesting part is that **nobody else decides from the
method**, and the four who do it decide from the key in four different ways.

| | What marks a value as secret | Reaches a group? |
|---|---|---|
| **OWNER** | `@Sensitive` on the method or the interface | yes, since 2026-08-09: an annotated method reading a group masks everything under its prefix |
| **Spring Boot 3** | nothing — every value in `/env` and `/configprops` is hidden unless `show-values` is turned up, optionally per role | yes, by hiding everything |
| **Spring Boot 2** | patterns on the property name: `password`, `secret`, `key`, `token`, `*credentials.*`, `vcap_services` | yes, the pattern matches the whole name |
| **SmallRye** | a registered set of property names, enforced at lookup: a read throws `SecurityException` unless inside `SecretKeys.doUnlocked` | not addressed; the documentation covers single named properties only |
| **Gestalt** | keywords searched in the path of the node, leaf replaced by a configurable mask. Also temporary values, released after N reads | yes, path-based |

Deciding from the method is the more precise of the two designs and it is worth keeping: a password
called `pwd` or `dsn` is masked because somebody said it was one, not because its name happened to match
a regular expression. Spring 2 and Gestalt are both guessing, and a guess that misses is silent.

It costs one thing, which is worth writing down because it is structural and no fix removes it: **a
property no method reads cannot be masked**, and `list()` prints every property held, not only those with
an accessor. That is exactly the ground the pattern-based designs cover and we do not. If it ever matters,
the shape would be additive — a list of patterns that adds to the annotation rather than replacing it —
and it should stay optional, because turning it on by default would start guessing on everyone's behalf.

Spring Boot 3's answer is the strongest of the four, and it is available to them because `/env` is an
endpoint with roles and authorization behind it. Our `list()` is a debugging convenience: hiding
everything by default would break every existing caller and leave the feature useless.


Encrypting a value against the equivalents
------------------------------------------

Checked 2026-08-14, while building ours. The finding that matters is the first one, and it is not
flattering: **the marker is not our idea, and SmallRye got there first with almost our exact syntax.**

| | How a value says it is encrypted | The cipher | Where the key comes from | Mixes with ordinary expansion |
|---|---|---|---|---|
| **OWNER 2.0.0** | `${$aes-gcm::…}` or `${$rsa-oaep::…}` in the value | AES-256/GCM, PBKDF2-HMAC-SHA256 at 210,000, random IV per value; **and** RSA-OAEP over AES-256/GCM. Both in the core | an instance the caller registers, holding the passphrase or the key | **yes** |
| **SmallRye Config** | `${aes-gcm-nopadding::…}` in the value | AES/GCM, key of **128 bits**, base64, **no key derivation** | the configuration property `smallrye.config.secret-handler.aes-gcm-nopadding.encryption-key` | **no** — "it is not possible to mix Secret Keys Expressions with Property Expressions" |
| **Jasypt** (`jasypt-spring-boot`) | `ENC(…)` prefix on the value | PBEWITHHMACSHA512ANDAES_256 since 3.0.0: PBKDF2-HMAC-SHA512 at **1,000** iterations, AES-256-**CBC** | `jasypt.encryptor.password`, a property or an environment variable | a prefix is not an expression at all |
| **Spring Cloud Config** | `{cipher}` prefix, decrypted **server side** | symmetric `encrypt.key`, or a keystore for the asymmetric case | `encrypt.key` / `ENCRYPT_KEY`, or a keystore | server side, before the client sees anything |
| **Gestalt** | — | encrypts secrets **in memory after reading**, per-secret cipher and IV; also a temporary secret released after N reads | — | a different feature: nothing in the file is encrypted |

Four things follow, and only the last two are ours.

**The shape is settled and we are late to it.** `${handler::value}` is what SmallRye ships, which is
reassurance rather than a problem: the design was reached twice independently, and a Quarkus user meets
our syntax already knowing what it means. Our one deliberate difference is the leading `$`, and it is not
decoration. SmallRye's expressions are a separate resolution pass, which is exactly why theirs *cannot*
be mixed with property expansion; ours share the substitutor with ordinary keys, so a sigil is what tells
`${$aes-gcm::…}` from a key called `aes-gcm::…`. **The sigil is what buys the mixing**, and the mixing is
the whole reason the marker beats the annotation: `jdbc.url=…?password=${db.password}` gets the secret.

**Their key comes from the configuration.** `smallrye.config.secret-handler.aes-gcm-nopadding.encryption-key`
is a property, read from the same sources as everything else — the secret that protects the file, alongside
the file. Jasypt has the same shape with `jasypt.encryptor.password`, and both are usually pointed at an
environment variable to get out of it. We refused the circularity at the design stage instead: the handler
is constructed by the caller and registered, so the passphrase arrives from wherever the application
already keeps it and there is no property to forget.

**On the construction we are ahead, and by more than politeness allows leaving unsaid.** SmallRye's
handler takes a raw 128-bit key with no derivation, so the passphrase *is* the key and has to be
generated rather than chosen. Jasypt derives properly but at **1,000 iterations**, which is the 2000s
default and 210 times below current OWASP guidance, and encrypts with **CBC**, which has no integrity: an
edited value decrypts to something else instead of failing. Ours is AES-256/GCM at 210,000 iterations
with the whole header authenticated.

**Only Spring Cloud Config has the asymmetric case, and it needs a server for it.** That is the second
place we are alone: `${$rsa-oaep::…}` gives a developer or a CI job the **public** key, so they can add a
secret to a configuration without being able to read the ones already there. SmallRye has one symmetric
handler and no key pair; Jasypt is symmetric by construction, since PBE *is* a passphrase; Spring Cloud
Config does offer a keystore, but through a config server with `/encrypt` and `/decrypt` endpoints rather
than in the library reading the file. `sops` and `age`, which are built around exactly this, are not Java
configuration libraries at all. Ours is a second handler name and no new mechanism, which is the dividend
of having dispatched on names in the first place.

**And it costs nothing to reach.** SmallRye's needs `smallrye-config-crypto` and Java 17; Jasypt needs
Spring; Spring Cloud Config needs a config server. Ours is in the core jar, on the Java 8 baseline, with
no dependency — because AES-GCM and PBKDF2 are in the JDK and there was never anything to pull in. The
tool that encrypts a value is in the same jar, which is one thing none of them can say: Jasypt ships a CLI
in its own artifact and Spring Cloud Config makes you POST to `/encrypt`.

Where we deliberately did **not** follow them: SmallRye discovers handlers through `ServiceLoader` on
`META-INF/services/io.smallrye.config.SecretKeysHandler`. We do not, and the reasoning is in
`CRYPTO.md` — a file format found on the classpath reads files that are already yours, while a handler
found on the classpath answers for the values inside them.


Loader discovery against the equivalents
----------------------------------------

Checked 2026-08-10, while deciding C6. **All three do the opposite of what `FORMATS.md` proposed**, which
was "discovered and registered, but probed only when asked".

| | discovery | does it imply probing? |
|---|---|---|
| **Spring Boot** | `PropertySourceLoader` listed in `META-INF/spring.factories` | **yes.** `StandardConfigDataLocationResolver` collects the extensions of every registered loader and looks for `application.<ext>` for each |
| **MicroProfile / SmallRye** | `ServiceLoader` on `ConfigSource` / `ConfigSourceProvider` | **yes.** A discovered source is active by the fact of being discovered, ordered by `config_ordinal`, default 100 |
| **Gestalt** | `ServiceLoader.load(ConfigLoader.class)` in `addDefaultConfigLoaders()` | **yes**, with a twist: register one loader of your own and the defaults are *not* added, so you have to ask for them back |

The reason to follow them is not that they agree. It is that **the disagreement is about precedence, not
about discovery**: they all merge every source with a defined ordinal, so a discovered loader *adds* a
source. We resolve `LoadType.FIRST` to exactly one source, and since `registerLoader` inserts at the head
and `defaultSpecs` walks the list in order, a discovered loader would put its spec **first** — and adding
a jar to the classpath would silently make a forgotten `MyConfig.yaml` beat a working `MyConfig.properties`.

So the rule that lets us follow the field without importing that risk is to **separate the two orderings**:
a discovered loader goes to the **head** of the `accept()` list, or `PropertiesLoader` — which accepts
everything it can resolve — takes its files away from it; and to the **tail** of the default-spec list,
where it cannot displace the file an application already loads. Those two are the same list today.

Per-loader options: **nobody has a settings namespace for them.** Gestalt configures a loader with a typed
`ModuleConfig` object registered on the builder, Spring has nothing at all — you register a different
loader — and SmallRye's settings are per *source* (`smallrye.config.source.file.locations`), not per parser.
Which answers question 8 in `FORMATS.md`: `owner.loaders.<name>.*` earns nothing that
`registerLoader(new DotEnvLoader(EnvDialect.DOTENV))` does not already do, so `Loader` needs no name.

**More than one extension per format (our C5) has an exact precedent**: Spring's `PropertySourceLoader`
declares `String[] getFileExtensions()` — *"the file extensions that the loader supports (excluding the
'.')"* — an array from the first version. Our `defaultSpecFor` returns a single `String`.

And **per-source options in the URI query are ours alone.** Searched for and not found in any of them:
Spring uses prefixes on the location (`optional:`, `configtree:`), Gestalt uses objects in code, SmallRye
uses global settings. That is not an argument against what shipped with `.env` — it distinguishes one file
from another, which none of those do — but it is the third decision in two days with no field to align
with, and it is written with reasoning rather than a citation for that reason.


Refusing what is not understood
-------------------------------

Checked 2026-08-10. The question was whether an unrecognised option should be an error, and Spring has
lived the whole story in [#17241](https://github.com/spring-projects/spring-boot/issues/17241).

They used to ignore in silence a location whose extension no loader could read. A user opened the issue
because a `.conf` was not being read and nothing said so; Wilkinsona's answer was *"Perhaps we should fail
or log a warning if [...] none of the known loaders handles the location's file extension"*, and 2.2.0
made it an error.

The part worth importing is what happened next. The new error **broke somebody's application at startup** —
a directory written without a trailing slash — and the reporter's own words were *"The new exception did
point at a problem with my config"*. It had been broken all along, quietly. They did not revert; they put
the remedy inside the message, which today reads: `File extension is not known to any PropertySourceLoader.
If the location is meant to reference a directory, it must end in '/' or File.separator`.

So: refusing is right, **and the message has to carry the likely cause**, because the day strictness is
turned on the people it hits first are the ones whose configuration was already wrong. Same direction at
SmallRye, where `smallrye.config.mapping.validate-unknown` defaults to `true` and an unmapped property
under the prefix fails the mapping.


Null against the equivalents
----------------------------

Checked 2026-08-10, for question 9 in `FORMATS.md`. **The two largest disagree**, which is the third time
in two days.

- **SmallRye drops the key**: `if (value != null) target.put(key, value.toString())` in `YamlConfigSource`.
  A `host: null` produces no key at all, and neither does an empty list.
- **Spring writes the empty string**: `result.put(key, (value != null ? value : ""))` in
  `YamlProcessor#buildFlattenedMap`. It has been a running sore for a decade — SPR-15425, spring-boot
  #40176, #24133 — because it lands differently per type: a `String` becomes `""` and a `Long` becomes
  `null`, from the same file.

Two more things from reading that source, both about the flattening convention we chose independently.
SmallRye indexes as `servers[0].host`, with no dot before the bracket, which is exactly ours. And it
**quotes a segment containing a dot** — `if (key.contains(".")) key = "\"" + key + "\"";` — which is the
escaping we deliberately did not build, with the reasoning in `FORMATS.md`.


What the gaps line up with
--------------------------

Our open issues are, almost one for one, the features the others have shipped. That is not a
coincidence; it is evidence the demand is real.

| Gap | Who has it | Our issue |
|---|---|---|
| Nested config interfaces | SmallRye, Gestalt, Coat | — (**closed 2026-08-11**) |
| TOML | Gestalt, Micronaut, avaje, Spring via Jackson | — (YAML #14 #65 **closed 2026-08-11**; HOCON and #240 **closed 2026-08-12**) |
| `.env` files | SmallRye, Spring via env, the dotenv ports | — (**closed 2026-08-09**) |
| Bean Validation (JSR-380) | SmallRye, Gestalt, Spring | #201 |
| Relaxed binding / kebab-case | SmallRye, Gestalt, Spring | — (**closed 2026-08-14**: four spellings, on by default, `@DisableFeature(RELAXED_BINDING)` to switch off. Where they differ from us is the direction — SmallRye and Boot 2 both canonicalise the *source* keys into an index, we derive the spellings from the key a method resolved to, which is what keeps `store()` and the origins showing the file's own names) |
| Indexed keys `list[0]` | SmallRye, Gestalt, Spring | — (**closed 2026-08-09**) |
| "Which source provided this?" | Spring (origin tracking), Gestalt | — (**closed 2026-08-11**) |
| Cloud sources (S3, Vault, Consul) | Gestalt, cfg4j | #130 — **partly answered 2026-08-14**: a `ValueHandler` makes `${$vault::secret/data/app}` a per-value reference anybody can write, with no module and no dependency from us. What is still missing is a *source* — a whole tree read from S3 or Consul — which is a `Loader`, not a handler |
| JNDI as a source | Spring (`JndiPropertySource`), Commons Configuration (`JNDIConfiguration`) | — (**closed 2026-08-14**: `jndi:comp/env/myconfig` in `@Sources`, plus `${$jndi::…}` per value. Ours refuses a non-`java:` name outright, which neither of theirs does) |
| An encrypted value in the file | SmallRye, Jasypt, Spring Cloud Config | — (**closed 2026-08-14**, and we ship the cipher, which SmallRye half does and Jasypt does at 1,000 iterations) |
| An encrypted value only the deployment can read | Spring Cloud Config, and only through a server | — (**closed 2026-08-14**: `${$rsa-oaep::…}`. The one row here where no library is level with us) |
| DI integration | every framework | #222, #147 |
| GraalVM native image | Coat, by construction | — |


How the others nest
-------------------

Surveyed on 2026-08-11, before writing our own. Two questions had no obvious answer and the field turned
out to disagree on both, so what each one does is recorded here rather than in a commit message.

| | Segment of the key | Prefix declared by the nested type | Lists | Maps |
|---|---|---|---|---|
| **SmallRye / Quarkus** | the **method** name, kebab-cased | **ignored** | `server.environments[0].name` | `server."my-server".host` |
| **Archaius** | the property name of the getter | **ignored** — `derivePrefix` reads `@Configuration` only when the prefix passed in is null | — | via parametrized getters |
| **Coat** | written on the accessor: `@Coat.Embedded(key="mqtt")` | does not arise: an embedded type has no prefix of its own (it has a `keySeparator` instead) | — | — |
| **Gestalt** | the field name | **composes**: base path + annotation prefix + field | `db.hosts[0].user` | yes |
| **OWNER** | the key the accessor resolves to, `@Key` and all | **composes** | `servers[0].host` | `servers.alpha.host` |

**The segment is the method, in all four.** Quarkus says it outright — *"The method name of a mapping
group acts as sub-namespace to the configuration properties"* — and the alternative, taking the name of
the type, makes a second accessor of the same type impossible to write. We do the same, except that ours
is the *key* the accessor resolves to, so `@Key` renames a section and an empty `@Key("")` inlines it,
which is what SmallRye spells `@WithParentName`.

**On composing we are with Gestalt, against SmallRye and Archaius**, and it is a deliberate minority
position: neither declaration is a default the other overrides, so ignoring one of them silently is the
one outcome we refuse. The cost is that an interface which already carries a prefix keeps carrying it when
nested, which is visible in the keys the first time it is tried.

**On an absent section the field has moved twice, in opposite directions.** SmallRye [#945](https://github.com/smallrye/smallrye-config/issues/945)
is the same bug report we would have received: a `@WithDefault` inside an `Optional` group. Its answer, in
version 3, is that the default makes the group present — *"An `Optional` and a `default` are opposing
concepts"* — and the behaviour it dropped to get there, presence decided only by what another property
wrote, is precisely the one we would otherwise have built. Spring Boot went the other way in 3.2, building
every absent nested object where 3.1 left it null, and
[broke the validation](https://github.com/spring-projects/spring-boot/issues/21281) of everyone who
relied on the absence. We took SmallRye's rule, and took Spring's accident as the reason to refuse
`@Mandatory` on the accessor of a section outright rather than ship a check that cannot fail.


Field evidence
--------------

- **Nobody uses `ZooKeeperLoader`.** Every apparent hit in a code search is our own build logs,
  mirrored inside research datasets. It was undocumented until 2026-08-07.
- **The `Loader` SPI *is* used**, contrary to what we assumed. Two external implementations found:
  `krevelen/coala-binder` → `io.coala.config.YamlLoader`, and `ansonliao/Selenium-Extensions` →
  `com.github.ansonliao.selenium.json.JsonLoader`. A YAML loader and a JSON loader, hand-written by
  two projects that do not know each other — exactly what we would ship. They live in their own
  packages and import only `org.aeonbits.owner.loaders.Loader`, so that interface must not move.
- **Someone squats in our root package.** `TechnologyBrewery/krausening` declares
  `package org.aeonbits.owner;` in `KrauseningConfigFactory` so it can subclass `DefaultFactory`,
  whose constructor is package-private. A real `module-info` in 3.0 breaks them twice over — split
  package, and no access. It is also a signal of missing public API: "build a Factory with my own
  scheduler". Decide deliberately before modularising.


Backlog, highest value first
----------------------------

1. ~~**Nested config interfaces**~~ — **done 2026-08-11**, in the four shapes the others have between
   them: a section, a list of sections from `servers[0].host`, a map of sections from
   `servers.alpha.host`, and one asked for by name with a parametrized key. The survey that settled the
   two contested decisions is below, under *How the others nest*.
2. **Further formats** — a loader is a three-method class, the SPI has existed since 1.0.5, and people
   are already writing these by hand (see above). Removes the "properties only" objection, which is
   the top reason people pick Typesafe Config. **`FORMATS.md` supersedes this line**: it was written
   here as "YAML/JSON/TOML as optional loaders in owner-extras", and the analysis that followed
   changed both halves of that. No external dependency means every parser is ours to write, which
   makes HOCON the most expensive item rather than the cheapest; and `.env`, not YAML, turned out to
   be the place to start — it is the most widespread format in container work and the only one
   needing none of the data-model work. It shipped on 2026-08-09, in the core.
   **INI followed on 2026-08-10 and JSON on 2026-08-11**, the second in a new artifact,
   `owner-formats`, which settles the other half of the question this line got wrong: the parsers we
   write ourselves do not go in `owner-extras` — that artifact is for sources needing somebody else's
   library — but in one of their own, because a hand-written parser is untrusted-input code and a defect
   in it should not be a security release for people who never used the format. **YAML followed the same
   day**, a documented subset whose refusals are named one by one — and the Norway problem, which is what
   makes YAML expensive for everybody else, never arose here: we keep the literal scalar and the mapping
   interface declares the type. **HOCON followed on 2026-08-12**, and it was indeed a decision rather than
   a piece of work — but not the decision expected here. It is the one format we delegate, to
   `com.typesafe:config`, because HOCON's specification *is* an implementation. **Only TOML is left**, and
   that one will be written.

   Checking the field first changed the answer, as it keeps doing. What stood here was that HOCON is a gap
   against *everyone*, and that is false: Spring Boot has no native HOCON — only `.properties` and `.yaml`
   loaders, the rest being third-party starters — and avaje-config has none at all. **Gestalt and
   Micronaut have it, and both delegate**: `gestalt-hocon` declares `com.typesafe:config`, and Micronaut
   reaches it through Config4k, which is a Kotlin wrapper over the same library. **Nobody hand-writes a
   HOCON parser**, and the unanimity is the argument.

   The wider finding is worth keeping, because it is unflattering: Gestalt writes no parser for *any*
   format — `gestalt-json` declares Jackson, `gestalt-yaml` and `gestalt-toml` the Jackson dataformats. So
   its artifact-per-format split, which this file cites as precedent for ours, splits *dependencies* where
   ours splits *code*. On hand-written parsing we are not a minority; we are alone.
3. ~~**Origin tracking** (#277)~~ — **done 2026-08-11**, `Traceable` and `Origin`. Spring and Typesafe
   both attach the origin to the *value*, which we cannot: ours are strings in a `Properties`, so it is a
   lookup by key instead. Both of them also carry a file and a line number, which we do not — that needs
   every loader to report positions, and `Origin` is a type rather than a `String` precisely so it can
   grow one later without a second API.
4. ~~**Configurable naming strategy** (#116)~~ — **done 2026-08-14**, and not as a strategy to configure:
   a closed set of four spellings, all of them tried, which is what the issue actually asked for and what
   removes the setting instead of adding one. SmallRye's `NamingStrategy` picks *one* convention per
   mapping, so a file in the other one is simply not read; ours has no wrong answer to pick. The cost of
   accepting several is that two of them may be written at once, and that is reported rather than left to
   be discovered — which is the half neither SmallRye nor Boot does.
5. **Bean Validation** (#201) as an optional module, never in the core.
6. **GraalVM reachability metadata** plus a documentation chapter — defensive: today people hit the
   proxy wall and leave for Coat.


Two strategic risks
-------------------

**The runtime proxy** is our ergonomics and our ceiling: native image and raw speed structurally
favour Coat and Micronaut. Do not change the model; do ship the metadata.

**Java 8 is both the moat and the swamp.** It is the only segment where we have no competition, and
it costs us records, sealed types and modern switch. Keep it for 2.x, plan a 3.0 on 17 once 2.x has
settled — not before.
