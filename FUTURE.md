# Future ideas

Improvements that were identified while documenting the library (see [FEATURES.md](FEATURES.md)) but are **not
implemented yet**. Each item says what is missing today and what a change would involve.

## Result set getters

### 1. `getObject` and `wasNull`

* **Today:** `getObject(...)` and `getBytes(...)` return `null`; `wasNull()` is always `false`.
* **Idea:** make `getObject` return a Java object based on the declared column type (`name|integer` → `Integer`,
  `date` → `java.sql.Date`, ...; `String` when no type is declared), and make `wasNull()` reflect the last value read.
* **Depends on:** the NULL convention below.

### 2. A representation for SQL `NULL`

* **Today:** the text `NULL` is the string `"NULL"` and an empty cell is an empty string, so a database `NULL` cannot be
  mocked. `getInt` on an empty cell throws `NumberFormatException`.
* **Idea:** pick a convention (e.g. an empty unquoted cell and/or the literal `NULL` means SQL NULL), return `null` /
  `0` from the getters accordingly and set `wasNull()`.
* **Compatibility:** changes the result for existing CSVs that rely on empty strings, so it may need to be opt-in.

## Date and time handling

### 3. ISO-style default formats

* **Today:** the defaults are `dd-MMM-yy`, `HH:mm`, `yyyyMMdd HHmmss.SSS`. The date format depends on the JVM
  locale, the time default drops seconds, and the timestamp default requires milliseconds. Users have to call
  `setDateFormat` / `setTimeFormat` / `setTimestampFormat` in every thread.
* **Idea:** default to ISO patterns (`yyyy-MM-dd`, `HH:mm:ss`, `yyyy-MM-dd HH:mm:ss[.SSS]`), possibly accepting several
  patterns per getter, and use a fixed locale for month names.
* **Compatibility:** breaking for existing CSVs and `?params` keys that use the current formats; consider a major version
  or a switch to select the old behaviour.

### 4. Format settings shared across threads and reset by `reset()`

* **Today:** the formats are `ThreadLocal`, so they apply only to the calling thread, and `reset()` does not restore
  the defaults.
* **Idea:** a process-wide setting (with thread-local override if needed) and restore the defaults in `reset()`.

## Encoding

### 5. Explicit charset for `InputStream` resources

* **Today:** `addInMemoryTableResource(String, InputStream)` and `addInMemoryTableResource(int, InputStream)` read the stream
  with the default charset of the VM, like CSV files.
* **Idea:** add overloads taking a `Charset` for streams whose encoding differs from the VM default.

## Resources and connections

### 6. Database directories loaded lazily

* **Today:** for `jdbc::mock::<dir>` URLs the CSV files are read when `connect()` is called. After `reset()`, a connection
  that is already open no longer sees those tables (new connections reload them).
* **Idea:** look up the directory on demand (or re-register it after `reset()`) so open connections keep working.

### 7. Table-name detection for INSERT/UPDATE

* **Today:** the table of an `INSERT INTO <table> (` / `UPDATE <table>` is matched with letters only (`[a-zA-Z]`), so names
  containing digits or underscores (`user_roles`, `table2`) are not captured under `<table>_PARAMS`; a `-- TESTCASE:` comment
  is needed.
* **Idea:** accept `[A-Za-z0-9_$.]` (and schema-qualified names), with tests for each form.

### 8. Precedence between files and in-memory resources

* **Today:** if both a registered file and an in-memory resource exist for the same name, the **file wins**
  (step resources always win over both).
* **Idea:** decide whether in-memory should override files, since it is registered closer to the test; document the
  decision either way.

## Build

### 9. Modern Java and Maven build

* **Today:** `pom.xml` targets Java 7 (source/target 1.7), which current JDKs refuse to compile, and the build needs
  `aspectj-maven-plugin`, which is not available in offline environments.
* **Idea:** raise the source/target level (Java 8+), update the plugins, and check whether the AspectJ logging aspect
  (`AspectLogger.aj`) is still worth keeping.

## Documentation

### 10. Slimmer README

* **Today:** the README links to `FEATURES.md`, but its older sections ("New Methods in 1.4.0", "1.5.0", sample usage)
  partly duplicate it, and it still refers to the old Maven coordinates and the upstream wiki.
* **Idea:** shrink the README to an overview plus links, keep the detailed behaviour in `FEATURES.md`, and update the
  dependency snippet to the `me.avogadro` coordinates once a release is published.
