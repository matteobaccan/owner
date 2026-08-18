RUNNING THE OLD TEST SUITE AGAINST THE NEW LIBRARY
==================================================

**Internal working document.** How the backward compatibility of 2.0.0 was measured on 2026-08-18, what it
found, and the recipe — because the result is only worth what the method behind it is, and because both
halves of that method had to be discovered twice before they were written down.

Why this test and not the other ones
------------------------------------

The project's own suite says the library does what *this* version promises. It cannot say what an upgrade
costs, because it was written alongside the code it tests. **The suite released with 1.0.12 can**: every one
of its assertions is a promise the old version made to somebody, so anything it refuses is a promise this
version breaks — intentionally or otherwise, and the difference between those two is the whole point.

The result on 2026-08-18
------------------------

**216 tests, 212 green.**

The four are two things:

- **two are a feature of 2.0.0 doing its job.** `InvalidAnnotationTest.testNonInstantiableTokenizer` and
  `ConverterClassTest.testConverterCantBeAccessed` asserted that a **private** class named in an annotation
  cannot be instantiated — `UnsupportedOperationException` caused by `IllegalAccessException`. Since 2.0.0 a
  class named in an annotation no longer has to be public, so those two fail by proving the change;
- **two are the harness.** `PropertiesInvocationHandlerTest` puts a Mockito `@Spy` on
  `java.util.Properties`; a spy of that class does not carry its internals on a current JDK, and the NPE
  comes out of `Properties.putAll` rather than out of anything here. Mockito 2.23.4, which 1.0.12 declared,
  on JDK 25.

**Seventeen of the ninety-one test files do not compile**, all for the same reason — they use an internal
this release removed:

| What they reach for | Where it went |
|---|---|
| `org.aeonbits.owner.util.Base64` | `java.util.Base64`, in the JDK since 8 |
| `Util.save`, `Util.saveJar`, `Util.delete` | `Properties.store`, `JarOutputStream`, `Files` |
| `Util.eq`, `Util.debug`, `Util.newArray` | `Objects.equals` and the callers' own business |
| `new PropertiesManager(...)`, `new PropertiesInvocationHandler(...)` | package-private, never API; the signatures changed |

**Conclusion: no unintentional change of behaviour was found in the public API.** Everything that moved is
either in the *Removals* of the release notes or is an addition that makes something work where it used to
be refused.

The recipe, for the next release
--------------------------------

1. `mvn -DskipTests -pl owner install` — the current jar into the local repository.
2. `git archive owner-1.0.12 owner/src/test | tar -x -C <scratch>` — the old suite, tests and resources.
3. A pom in `<scratch>` with **no main sources**: a dependency on `org.aeonbits.owner:owner:2.0.0-SNAPSHOT`
   and the four test dependencies 1.0.12 declared — hamcrest-all 1.3, junit 4.12, mockito-core 2.23.4,
   commons-codec 1.11. Set `testFailureIgnore` so that one broken class does not hide the rest.
4. Compile. **Separate the two kinds of failure before reading anything into them**: a test that does not
   compile because it uses an internal is a different fact from a test that runs and disagrees. Only the
   second is a compatibility break.
5. For the first kind, adapt the *helpers* rather than the tests — `UtilTest` in 1.0.12 is a bag of test
   utilities that delegate to `Util`, so reimplementing four of them on the JDK methods the removals point
   at brings back the seventy-odd behavioural tests that merely used them to write a file.

**Do not commit the harness.** It is a copy of another release's tests; what belongs in the repository is
this document and the paragraph in the release notes.

While here: SonarCloud needs no token for a public project
----------------------------------------------------------

Written down because it was worked out twice, and because the local greps that came first got nearly every
rule wrong:

```
https://sonarcloud.io/api/issues/search?componentKeys=matteobaccan_owner&rules=java:S1128&issueStatuses=OPEN
```

The response carries the file, the line and the message for each finding. **Ask it instead of reasoning
about what a rule probably flags**: `java:S1128` turned out to be seventeen imports of `Config.Key` and its
neighbours in interfaces that *extend* `Config`, where the nested type is already in scope — a case in
which the name does appear in the file, so every "is this name used" heuristic answers yes and the import is
redundant all the same.
