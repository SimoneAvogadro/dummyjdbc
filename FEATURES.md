# dummyjdbc – Features

dummyjdbc is a JDBC driver that never talks to a real database. Your code opens a normal
`java.sql.Connection`, runs normal SQL, and gets back the result sets *you* have prepared –
either **in memory** (a CSV string inside your test) or **on file** (CSV files on disk / classpath).

This guide starts from the simplest case and builds up.

**Contents**

1. [Setup](#1-setup)
2. [Quick start: in-memory dataset](#2-quick-start-in-memory-dataset)
3. [How a query finds its dataset](#3-how-a-query-finds-its-dataset)
4. [Different results for different parameters](#4-different-results-for-different-parameters)
5. [Step-based results (a scripted sequence)](#5-step-based-results-a-scripted-sequence)
6. [Capturing INSERT / UPDATE parameters](#6-capturing-insert--update-parameters)
7. [Datasets on file](#7-datasets-on-file)
8. [Several "databases" with JDBC URLs](#8-several-databases-with-jdbc-urls)
9. [CSV format reference](#9-csv-format-reference)
10. [Resolution order and good-to-know](#10-resolution-order-and-good-to-know)
11. [API summary](#11-api-summary)

---

## 1. Setup

Add the dependency (see the README for the current coordinates) and make sure the driver class
is loaded; it registers itself with `DriverManager` on load:

```java
Class.forName(DummyJdbcDriver.class.getCanonicalName());
Connection connection = DriverManager.getConnection("any");
```

The driver accepts the URL `any` (handy in tests) and URLs starting with `jdbc::mock::`
(see [section 8](#8-several-databases-with-jdbc-urls)).

---

## 2. Quick start: in-memory dataset

Register a CSV string under a name, then run a query that resolves to that name.
The first line is the header, the following lines are rows.

```java
Class.forName(DummyJdbcDriver.class.getCanonicalName());
DummyJdbcDriver.reset();                       // start from a clean state

DummyJdbcDriver.addInMemoryTableResource("users",
        "name, age\n" +
        "John, 20\n" +
        "Mary, 31");

Connection connection = DriverManager.getConnection("any");
PreparedStatement statement = connection.prepareStatement("SELECT * FROM users");
ResultSet rs = statement.executeQuery();

while (rs.next()) {
    String name = rs.getString("name");        // by label (case-insensitive)
    int age     = rs.getInt(2);                // or by 1-based index
}
```

Your application code does not know the difference: it is plain JDBC.
The name (`users`) is the **table name found in the SQL** (`FROM users`), matched case-insensitively.

Instead of a `String` you can also pass an `InputStream`, e.g. a file from `src/test/resources`:

```java
DummyJdbcDriver.addInMemoryTableResource("users",
        getClass().getResourceAsStream("/users.csv"));
```

---

## 3. How a query finds its dataset

For every query the driver builds a *resource name* and looks for a dataset registered under it.
Three ways to select it:

### a) Table name deduction (default)

The name is taken from the word after `FROM`:

```sql
SELECT name FROM mytable WHERE surname = 'Happy'   -- resource: mytable
```

### b) Explicit `-- TESTCASE:` comment

When the same table is queried in different places and you want different results, name the
dataset explicitly with a comment on the first line:

```sql
-- TESTCASE: Hello1
SELECT * FROM TableUsedEverywhere                  -- resource: hello1
```

```java
DummyJdbcDriver.addInMemoryTableResource("Hello1", "id, label\n1, first");
```

The comment wins over the table name.

### c) Stored procedures

`EXEC my_proc ...` / `EXECUTE my_proc ...` resolves to the resource `my_proc`, so stored-procedure
results can be mocked the same way as tables.

### Fallbacks

* `SELECT 1`-style queries without a `FROM` return a single row with the value `1`.
* If nothing is found, the driver returns an empty "dummy" result set instead of failing.

Names are case-insensitive for in-memory resources.

---

## 4. Different results for different parameters

With a `PreparedStatement` the driver also tries a more specific name that includes the bound parameters:
`<resource>?<param1>,<param2>,...`. If it exists it is used, otherwise it falls back to `<resource>`.

```java
DummyJdbcDriver.addInMemoryTableResource("mytable?Smith,34", "name\nJohn Smith");
DummyJdbcDriver.addInMemoryTableResource("mytable",          "name\n(anybody else)");

PreparedStatement ps = connection.prepareStatement(
        "SELECT name FROM mytable WHERE surname = ? AND age = ?");
ps.setString(1, "Smith");
ps.setInt(2, 34);
ResultSet rs = ps.executeQuery();              // -> "John Smith"
```

Lookup order for the query above (always case-insensitive):

1. `mytable?Smith,34`
2. `mytable`

Parameters are rendered with `toString()`. `Date`, `Time` and `Timestamp` use the formats described in
[section 9](#dates-and-times).

---

## 5. Step-based results (a scripted sequence)

Sometimes you don't care what the SQL says – you only know *"the first query returns A, the second returns B"*.
Register results by the **execution step** instead of by name:

```java
DummyJdbcDriver.reset();                       // resets the step counter to the beginning
DummyJdbcDriver.addInMemoryTableResource(0, "name, age\nJohn, 20");
DummyJdbcDriver.addInMemoryTableResource(1, "id, country\n1, Italy\n2, USA");
DummyJdbcDriver.addInMemoryTableResource(2, "id, make, model\n1, Mazda, CX-5");

Connection c = DriverManager.getConnection("any");

ResultSet first  = c.prepareStatement("whatever").executeQuery();   // step 0 -> John
ResultSet second = c.prepareStatement("whatever").executeQuery();   // step 1 -> countries
ResultSet third  = c.createStatement().executeQuery("anything");    // step 2 -> cars
```

* The step counter increases **each time a statement is created** (`createStatement` / `prepareStatement`),
  starting from 0 after `reset()`.
* A step resource has the **highest priority**: table name and `-- TESTCASE:` comments are ignored for that statement.
* Call `DummyJdbcDriver.reset()` at the start of every test, otherwise the counter carries over.
* For `executeUpdate`, a step resource whose content is a plain number is returned as the *affected rows count*
  (e.g. `addInMemoryTableResource(3, "5")`).

---

## 6. Capturing INSERT / UPDATE parameters

To verify that your application wrote the right data, the driver records the parameters of
every `INSERT INTO ...` / `UPDATE ...` as a comma-separated string, under the name
`<table>_PARAMS`:

```java
PreparedStatement ps = connection.prepareStatement(
        "INSERT INTO users (name, age) VALUES (?, ?)");
ps.setString(1, "hello");
ps.setInt(2, 30);
ps.execute();

String params = DummyJdbcDriver.getInMemoryTableResource("users_PARAMS");
Assert.assertEquals("hello,30", params);
```

* `executeUpdate` reports **1 affected row** when the table is recognised, `0` otherwise.
* With an explicit `-- TESTCASE: name` comment the key is `name_PARAMS`.
* The table name in `INSERT INTO <table> (` / `UPDATE <table>` is matched with letters only (`[a-zA-Z]`),
  so names with digits or underscores are not captured by name – use a `-- TESTCASE:` comment for those.
* Only the latest call is kept per table; read it right after the statement runs.

---

## 7. Datasets on file

For larger or shared data, keep the CSV in a file. There are three ways to wire files in.

### a) Register a file for a table

```java
DummyJdbcDriver.addTableResource("test_table",
        new File(getClass().getResource("/test_table.csv").toURI()));

Connection c = DriverManager.getConnection("any");
ResultSet rs = c.prepareStatement("SELECT * FROM test_table").executeQuery();
```

`src/test/resources/test_table.csv`:

```csv
ID, country_name, country_iso
1, Germany, DE
2, Spain, ES
3, France, FR
```

Use a **lower-case** table name when registering (the driver looks tables up in lower case).
`addTableResource` is used for the default `any` database and replaces the previously registered
file, so register the table right before you need it.

### b) The `/tables/` classpath folder (zero setup)

If nothing else matches, the driver looks for `/tables/<tablename>.csv` on the classpath
(e.g. `src/test/resources/tables/users.csv` for `SELECT * FROM users`). No Java code needed.

### c) A directory per "database"

See the next section.

If both a file and an in-memory resource exist for the same name, the **file wins**.
Step resources ([section 5](#5-step-based-results-a-scripted-sequence)) always win over both.

---

## 8. Several "databases" with JDBC URLs

Use a URL of the form `jdbc::mock::<dir>[::<subdir>...]` to point to a **classpath directory**.
Every `*.csv` file in it becomes a table (file name without extension = table name):

```
src/test/resources/
└── database/
    └── test_table.csv
```

```java
Connection c = DriverManager.getConnection("jdbc::mock::database");
ResultSet rs = c.prepareStatement("SELECT * FROM test_table").executeQuery();
```

* `jdbc::mock::a::b` maps to the directory `a/b`.
* Different URLs give you different, independent sets of tables (e.g. one per simulated database).
* A missing directory raises a `RuntimeException`.

---

## 9. CSV format reference

```csv
string_column, int_column|integer, boolean_column, bigdecimal_column, date_column
string_value,  17,                 true,           123456789123456789, 17-MAY-12
```

* **Header first**, then one line per row. Values are trimmed. Standard CSV quoting applies.
* Every row must have as many values as the header, otherwise an `IllegalArgumentException` is thrown.
* Column names are case-insensitive; duplicate columns are rejected.
* **Optional column types** in the header with `name|type`. Supported: `varchar` (default), `integer`,
  `double`, `date`, `time`, `timestamp`. They are exposed through `ResultSetMetaData`
  (`getColumnType`, `getColumnClassName`, ...).
* Supported getters: `getString`, `getInt`, `getBoolean`, `getBigDecimal`, `getDate`, `getTime`, `getTimestamp`
  – by column label or by index.
* Empty text is `0` for `getBigDecimal`.

### Dates and times

Text in the CSV is parsed with these default formats:

| Type        | Default pattern       | Example             |
|-------------|-----------------------|---------------------|
| `Date`      | `dd-MMM-yy`           | `17-MAY-12`         |
| `Time`      | `HH:mm`               | `14:30`             |
| `Timestamp` | `yyyyMMdd HHmmss.SSS` | `20120517 143000.000` |

Change them (per thread) with `DummyJdbcDriver.setDateFormat(...)`, `setTimeFormat(...)`, `setTimestampFormat(...)`
using `SimpleDateFormat` patterns. The same formats are used to render date parameters in the
`?params` / `_PARAMS` strings.

---

## 10. Resolution order and good-to-know

For each executed query the driver tries, in this order:

1. **Step resource** for the current step ([section 5](#5-step-based-results-a-scripted-sequence)).
2. **`-- TESTCASE: name`** comment.
3. **Table name** after `FROM`.
4. **Stored procedure** name after `EXEC`/`EXECUTE`.
5. **Pure `SELECT`** without a table: one row with value `1`.
6. Otherwise an empty dummy result set.

For a given name, the data source is chosen as: `name?params` (in memory) → `name` (in memory) → registered file →
`/tables/name.csv`.

Tips:

* Call `DummyJdbcDriver.reset()` in your `@Before` – the driver keeps **static** state (resources and step counter).
* `DummyJdbcDriver.clearInMemoryTableResources()` clears only the in-memory data.
* Not every JDBC method is implemented; unsupported ones throw `UnsupportedOperationException`.
* In-memory CSV text is read as ISO-8859-1.

---

## 11. API summary

| Method | Purpose |
|--------|---------|
| `addInMemoryTableResource(String name, String csv)` | Register an in-memory dataset by name |
| `addInMemoryTableResource(String name, InputStream csv)` | Same, reading the CSV from a stream |
| `addInMemoryTableResource(int step, String csv)` | Register the result of the *n*-th statement |
| `addInMemoryTableResource(int step, InputStream csv)` | Same, from a stream |
| `getInMemoryTableResource(String name)` | Read a resource, e.g. `<table>_PARAMS` after an INSERT/UPDATE |
| `getInMemoryTableResourceForCurrentStep()` | Read the resource of the current step |
| `addTableResource(String table, File csv)` | Register a CSV file for a table |
| `clearInMemoryTableResources()` | Remove all in-memory datasets |
| `reset()` | Clear all resources and restart step counting |
| `setDateFormat / setTimeFormat / setTimestampFormat(String)` | Formats used to parse CSV dates and render parameters |
