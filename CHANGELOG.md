Change Log
==========

Unreleased
----------------------------

 * Build: Java 8 target, AspectJ weaving with `dev.aspectj:aspectj-maven-plugin` (works on current JDKs) and a second
   jar `dummyjdbc-<version>-with-dependencies.jar` bundling opencsv and the AspectJ runtime (relocated), ready for Boomi
 * Fixed: UPDATE without a `-- TESTCASE` comment was never captured; INSERT/UPDATE spanning several lines were not
   recognised; the `-- TESTCASE` name kept a trailing `\r` with CRLF line endings and swallowed the following lines of a
   multi-line query
 * Fixed: `DatabaseMetaData` returned `null` from many methods (NullPointerException in the Boomi Database V2 connector);
   now descriptive values and empty result sets with the standard JDBC columns
 * `getLong`, `getShort`, `getByte`, `getDouble`, `getFloat` and `getRow()` are implemented for CSV result sets
 * Driver version reported as 1.5 (was 1.0)
 * Boomi support through Dynamic Process Properties (detected at runtime via reflection, no dependency on Boomi):
   tables can be provided as `dummyjdbc_<name>` properties (name as written, then lower case) and captured
   INSERT/UPDATE parameters are exposed as `dummyjdbc_<table>_PARAMS` and `dummyjdbc_<table in lower case>_params`
   (never persisted)

Version 1.5.1
----------------------------

 * Fixed issues with using step number to identify testing data

Version 1.5.0
----------------------------

 * Rebased to com.mindmercatis in order to continue development and provide separate CirceCI integration testing
 * Added methods to simplify testing by just saying which result will be given at which step

Version 1.4.0
----------------------------

 * Support for in-memory result sets
 * Support for insert/update queries
 * Support for capturing query parameters for test purposes
 * Support for providing different resultsets depending on the query parameters 

Version 1.3.0 (2018-03-14)
----------------------------

 * Support for table metadata
 * Support for multiple databases by using different jdbc urls
 
Version 1.2 (2015-03-14)
----------------------------

 * Switching to Java 7
 * Improved logging
 * AspectJ for automated tracing

Version 1.1.3 (2013-07-04)
----------------------------

 * Stable initial version
