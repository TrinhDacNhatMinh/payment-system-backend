# Java formatting

Spotless is the shared formatting entry point for Maven, local development, and CI.
It formats `src/main/java/**/*.java` and `src/test/java/**/*.java` using pinned
versions of Spotless and Eclipse JDT. Maven must run on JDK 17 or newer.

The team profile is stored in
[`config/spotless/eclipse-java-formatter.xml`](../config/spotless/eclipse-java-formatter.xml).
It uses four spaces and K&R braces, with no team-imposed line-length limit. Spotless also
uses UTF-8 and LF line endings, removes unused imports and trailing whitespace,
and adds a final newline. Import groups are static imports, `java`/`javax`/`jakarta`,
third-party imports, then `com.minh.paymentsystem` imports.

## Commands

Run from the backend project directory. On Windows PowerShell:

```powershell
.\mvnw.cmd spotless:check
.\mvnw.cmd spotless:apply
```

On Linux or macOS:

```shell
./mvnw spotless:check
./mvnw spotless:apply
```

`check` reports violations without changing source files. `apply` changes source
files; review `git diff` and stage the intended changes before committing.
`spotless:check` also runs during Maven's `verify` phase. No Git hook is installed.

## Initial rollout and limits

This setup does not reformat existing Java files. Until the separate formatting
cleanup is applied, `spotless:check` and the formatting step in `verify` are
expected to fail on existing violations.

Code wrapping uses the maximum integer value as a technical ceiling, preventing
fallback to Eclipse's default code width. Comment reformatting is disabled for
line comments, block comments, and Javadoc; no comment line-length limit is imposed.
Existing manual code line breaks are preserved where supported; explicit annotation
and array wrapping rules still apply. The formatter does not add
missing control-flow braces, expand wildcard imports, reorder members, or enforce
naming and architectural rules. Review those separately; static wildcard imports
remain allowed in tests by the coding conventions.

The Java LF policy is also recorded in `.gitattributes` so Git checkouts remain
consistent on Windows. Maven Wrapper scripts retain their existing line endings.

If an IDE formatter is used, align it with the shared profile and run Spotless
before committing. IDE default formatting alone is not the shared standard.

Annotation arrays such as `@ApiResponses` put each entry on its own line. Class,
field, and method annotations each occupy their own line above the declaration.
Method parameter annotations may share a line with each other and the parameter,
such as `@Valid @RequestBody RegisterRequest request`. Record components remain
one per line, with each annotation on a separate line above the component type.
Eclipse JDT shares annotation settings between these two contexts, so the
`record-annotations.groovy` Spotless step runs after Eclipse and uses the JDK Java
syntax tree to adjust whitespace only in record component headers. A full JDK 17+
is required. The array wrapping rule also applies to ordinary Java array
initializers. Incremental checking is disabled so edits to the formatter profile
are applied even when source-file timestamps have not changed.

Reference: [Spotless Maven documentation](https://github.com/diffplug/spotless/tree/main/plugin-maven).

The record-specific step has regression checks in
`config/spotless/verify-record-annotations.groovy`. With Groovy 3 available, run
`groovy config/spotless/verify-record-annotations.groovy` from the backend directory.
These checks cover nested records, generic and varargs components, annotation
arguments, comments, unchanged method parameters, and repeated formatting.
