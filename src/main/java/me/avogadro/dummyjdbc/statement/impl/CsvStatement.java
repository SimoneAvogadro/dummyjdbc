package me.avogadro.dummyjdbc.statement.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.avogadro.dummyjdbc.DummyJdbcDriver;
import me.avogadro.dummyjdbc.resultset.DummyResultSet;
import me.avogadro.dummyjdbc.resultset.DummyResultSetMetaData;
import me.avogadro.dummyjdbc.resultset.impl.CSVResultSet;
import me.avogadro.dummyjdbc.statement.StatementAdapter;

import au.com.bytecode.opencsv.CSVReader;

/**
 * This class does the actual work of the Generic... classes. It tries to open a CSV file for the table name in the
 * query and parses the contained data.
 *
 * @author Kai Winter
 */
public final class CsvStatement extends StatementAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(CsvStatement.class);

	/**
	 * Pattern used to recognize explicitly declared table names inside an heading comment.
	 * The name stops at the end of the first line (\n or \r\n), whatever follows on the next lines.
	 */
	private static final Pattern COMMENT_HEADLINE_PATTERN = Pattern.compile("\\s*--\\s*TESTCASE:[ \\t]*([^\\r\\n]*)\\r?\\n.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	
	/** Pattern to get table name from an SQL statement. */
	private static final Pattern TABLENAME_PATTERN = Pattern.compile(".*from\\s*(\\S*)\\s?.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL );

	/** Pattern to get the name of a stored procedure from an SQL statement. */
	private static final Pattern STORED_PROCEDURE_PATTERN = Pattern.compile(".*(EXEC|EXECUTE) (\\S*)\\s?.*",	Pattern.CASE_INSENSITIVE);

	/** Stored procedure call: EXEC/EXECUTE/CALL name ... or the JDBC escape {[? =] call name(...)}, after optional comment lines */
	private static final Pattern STORED_PROCEDURE_CALL_PATTERN = Pattern.compile("(?:\\s*--[^\\n]*\\n)*\\s*\\{?\\s*(?:\\?\\s*=\\s*)?(?:exec|execute|call)\\s+([^\\s(){};,]+).*", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);

	private static final Pattern PURE_SELECT_PATTERN = Pattern.compile("select .*", Pattern.CASE_INSENSITIVE);

    private final Map<String, File> tableResources;

    /**
     * used to describe the "current" params.
     * Always null when invoked from {@link CsvStatement} and may hold values when used from {@link CsvPreparedStatement}
     */
    String paramsString = null;

    /** see {@link #setMaxRows(int)}: 0 = no limit */
    private int maxRows = 0;

    /** SQL added with {@link #addBatch(String)} */
    private final List<String> batch = new ArrayList<String>();
    
    
    /**
     * Initializer
     * 
     */
    {
    	DummyJdbcDriver.nextStep();
    }
    
	/**
	 * Constructs a new {@link CsvStatement}.
	 *
	 * @param tableResources
	 *            {@link Map} of table name to CSV file.
	 */
	public CsvStatement(Map<String, File> tableResources) {
		this.tableResources = tableResources;
	}
	
	@Override
	public ResultSet executeQuery(String sql) throws SQLException {
		ResultSet resultSet = query(sql);
		if (maxRows > 0 && resultSet instanceof CSVResultSet) {
			((CSVResultSet) resultSet).setMaxRows(maxRows);
		}
		return resultSet;
	}

	@Override
	public void setMaxRows(int max) throws SQLException {
		if (max < 0) {
			throw new SQLException("maxRows must be >= 0: " + max);
		}
		this.maxRows = max;
	}

	@Override
	public int getMaxRows() throws SQLException {
		return maxRows;
	}

	/**
	 * No key is ever generated: always an empty result set (never null, callers iterate it after an INSERT).
	 */
	@Override
	public ResultSet getGeneratedKeys() throws SQLException {
		return CSVResultSet.empty("generatedKeys", "GENERATED_KEY");
	}

	@Override
	public void addBatch(String sql) throws SQLException {
		batch.add(sql);
	}

	@Override
	public void clearBatch() throws SQLException {
		batch.clear();
	}

	@Override
	public int[] executeBatch() throws SQLException {
		int[] results = new int[batch.size()];
		try {
			for (int i = 0; i < results.length; i++) {
				results[i] = executeUpdate(batch.get(i));
			}
		} finally {
			batch.clear();
		}
		return results;
	}

	private ResultSet query(String sql) throws SQLException {

		try {
			String stepRes = DummyJdbcDriver.getInMemoryTableResourceForCurrentStep();
			
			// match based on the current step (sequence number)
			if (stepRes!=null) {
				String tableName = DummyJdbcDriver.getStepName();
				return createResultSet(tableName);
			}
						
			// Try to check for a special heading comment within SQL
			String testCase = matchTestCase(sql);
			if (testCase != null) {
				return createResultSet(testCase);
			}
			
			// Try to interpret SQL as a SELECT on a table
			Matcher tableMatcher = TABLENAME_PATTERN.matcher(sql);
			if (tableMatcher.matches()) {
				String tableName = tableMatcher.group(1);
				return createResultSet(tableName);
			}

			// Try to interpret SQL as call of a stored procedure
			Matcher storedProcedureMatcher = STORED_PROCEDURE_PATTERN.matcher(sql);
			if (storedProcedureMatcher.matches()) {
				String storedProcedureName = storedProcedureMatcher.group(2);
				return createResultSet(storedProcedureName);
			}

			// Try to interpret SQL as a stored procedure call in the other forms (CALL, {call ...}, several lines)
			String procedure = matchStoredProcedureCall(sql);
			if (procedure != null) {
				return createResultSet(procedure);
			}

	        //  Try  to  interpret  SQL  as  a  pure  select
			Matcher  pureSelectMatcher  =  PURE_SELECT_PATTERN.matcher(sql);
			if  (pureSelectMatcher.matches())  {
				return  createPureResultSet();
			}

	        return new DummyResultSet();
	        
		} finally {
			// DummyJdbcDriver.nextStep();
		}
	}
	
	/**
	 * @param sql the SQL text
	 * @return the name declared by a heading <code>-- TESTCASE: name</code> comment (trimmed), or <code>null</code>
	 */
	static String matchTestCase(String sql) {
		Matcher commentMatcher = COMMENT_HEADLINE_PATTERN.matcher(sql);
		if (commentMatcher.matches()) {
			String testCase = commentMatcher.group(1).trim();
			return testCase.isEmpty() ? null : testCase;
		}
		return null;
	}

	/**
	 * @param sql the SQL text
	 * @return the name of the stored procedure called with EXEC, EXECUTE, CALL or <code>{call ...}</code>, or <code>null</code>
	 */
	static String matchStoredProcedureCall(String sql) {
		Matcher procedureMatcher = STORED_PROCEDURE_CALL_PATTERN.matcher(sql);
		return procedureMatcher.matches() ? procedureMatcher.group(1) : null;
	}

	static String matchTablename(String sql) {
		Matcher tableMatcher = TABLENAME_PATTERN.matcher(sql);
		if (tableMatcher.matches()) {
			return tableMatcher.group(1);
		} else {
			return null;
		}
	}

	private ResultSet createResultSet(String tableName) {
		return createResultSet(tableResources, tableName, paramsString);
	}

	/**
	 * Loads a table exactly as a <code>SELECT * FROM tableName</code> would, without running a statement (the step
	 * counter is not touched): in-memory resource (or Boomi Dynamic Process Property), registered file, or
	 * <code>/tables/&lt;name&gt;.csv</code>.
	 *
	 * @param tableResources {@link Map} of table name to CSV file of the connection
	 * @param tableName the table name
	 * @return a {@link CSVResultSet}, or an empty {@link DummyResultSet} if the table is not defined
	 */
	public static ResultSet loadTable(Map<String, File> tableResources, String tableName) {
		return createResultSet(tableResources, tableName, null);
	}

	private static ResultSet createResultSet(Map<String, File> tableResources, String tableName, String paramsString) {
		Reader inMemoryReader = null;
		String inMemoryCSV = null;
		
		// search for "tablename?param1,param2" etc...
		if (paramsString!=null) {
			inMemoryCSV = DummyJdbcDriver.getInMemoryTableResource(tableName+"?"+paramsString);
		} // then search just for "tablename"
		if (inMemoryCSV==null) {
			inMemoryCSV = DummyJdbcDriver.getInMemoryTableResource(tableName);			
		} // if any in memory CSV is found read it directly: a String needs no charset
		if (inMemoryCSV!=null) {
			inMemoryReader = new StringReader(inMemoryCSV);
		}
		
		// Does a text file for the dummy table exist?
		File resource = tableResources.get(tableName.toLowerCase());
		if (resource == null && inMemoryReader == null) {
			// Try to load a file from the ./tables/ directory
			CodeSource src = CsvStatement.class.getProtectionDomain().getCodeSource();

			String path = src.getLocation().getPath();
			path = path.substring(0, path.lastIndexOf("/"));
			try {
				URL url = CsvStatement.class.getResource("/tables/" + tableName.toLowerCase() + ".csv");
				if (url == null) {
					LOGGER.info("No table definition found for '{}', using DummyResultSet.", tableName);
					return new DummyResultSet();
				} else {
					resource = new File(url.toURI());
				}
			} catch (URISyntaxException e) {
				LOGGER.error("Error creating URI for table file: {}", e.getMessage(), e);
			}
		}

		Reader dummyTableReader = null;
		try {
			if (resource==null) {
				dummyTableReader = inMemoryReader;	// might be null => no inMemoryCSV
			} else {
				// files are read with the default charset of the VM
				dummyTableReader = new InputStreamReader(new FileInputStream(resource));
			}
			return createGenericResultSet(tableName, dummyTableReader);
		} catch (FileNotFoundException e) {
			LOGGER.info("No table definition found for '{}', using DummyResultSet.", tableName);
		} finally {
			if (dummyTableReader != null) {
				try {
					dummyTableReader.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}

		return new DummyResultSet();
	}

	private static ResultSet createGenericResultSet(String tableName, Reader dummyTableDataReader) {

		// Maps table columns to a number of available values.
		Collection<LinkedHashMap<String, String>> entries = new ArrayList<LinkedHashMap<String, String>>();

		CSVReader dummyTableReader = null;
		try {

			dummyTableReader = new CSVReader(dummyTableDataReader);

			// Read header
			String[] header = dummyTableReader.readNext();
			if (header == null) {
				LOGGER.info("Empty table definition for '{}', using DummyResultSet.", tableName);
				return new DummyResultSet();
			} else {

				String[] data;
				// Read data
				while ((data = dummyTableReader.readNext()) != null) {
					if (header.length == data.length) {
						LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
						for (int i = 0; i < header.length; i++) {
                            final String headerName = resolveHeaderName(header[i]);
							if (map.containsKey(headerName)) {
								String message = MessageFormat.format("Duplicate column in file ''{0}.txt: {1}",
										tableName, header[i]);
								throw new IllegalArgumentException(message);
							}
							map.put(headerName, data[i].trim());

						}
						entries.add(map);
					} else {
						throw new IllegalArgumentException("Length of data does not fit header length.");
					}

				}
			}
			return new CSVResultSet(tableName, new DummyResultSetMetaData(tableName, header), entries);

		} catch (IOException e) {
			LOGGER.error("Error while reading data from CSV", e);
		} finally {
			if (dummyTableReader != null) {
				try {
					dummyTableReader.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}

		return new DummyResultSet();
	}

	private DummyResultSet createPureResultSet() {

        // Maps table columns to a number of available values.
		Collection<LinkedHashMap<String, String>> entries = new ArrayList<LinkedHashMap<String, String>>();

		LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
		map.put("1", "1");

		entries.add(map);

		return new CSVResultSet(null, new DummyResultSetMetaData(null, new String[] { "1" }), entries);
	}

	private static String resolveHeaderName(String str) {
	    return str.trim().toUpperCase();
    }


}
