package me.avogadro.mockjdbc;

import java.io.File;
import java.io.FileFilter;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Logger;

import me.avogadro.mockjdbc.boomi.BoomiExecutionUtil;
import me.avogadro.mockjdbc.connection.impl.MockConnection;
import me.avogadro.mockjdbc.utils.FilenameUtils;
import me.avogadro.mockjdbc.utils.StringUtils;

/**
 * The {@link MockJdbcDriver}. The {@link #connect(String, Properties)} method returns the {@link MockConnection}.
 *
 * @author Kai Winter
 */
public final class MockJdbcDriver implements Driver {

	/**
	 * The date format for parsing a date from a CSV file.
	 */
	private static final String DATE_FORMAT = "dd-MMM-yy";
	private static final String TIME_FORMAT = "HH:mm";
	private static final String TIMESTAMP_FORMAT = "yyyyMMdd HHmmss.SSS";

	public static final String STEP_PREFIX = "##STEP";

	/** Version of the driver, also reported by the database metadata */
	public static final int VERSION_MAJOR = 1;
	public static final int VERSION_MINOR = 5;
	
	/**
	 * Counter for the number of statements being executed
	 * Used to give a sequential number to each query
	 * Starts from -1 because it's pre-incremented once each statement is created
	 */
	static int step = -1;
	
	/**
	 * CSV files stored into memory 
	 */
	static Map<String,String> inMemoryTableResources = new HashMap<String,String>();

	public static final ThreadLocal<DateFormat> THREAD_LOCAL_DATEFORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat(DATE_FORMAT);
		}
	};

	public static final ThreadLocal<DateFormat> THREAD_LOCAL_TIMEFORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat(TIME_FORMAT);
		}
	};

	public static final ThreadLocal<DateFormat> THREAD_LOCAL_TIMESTAMPFORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat(TIMESTAMP_FORMAT);
		}
	};

	private final static String DEFAULT_DATABASE = "any";

	private static Map<String, Map<String, File>> tableResources = Collections.synchronizedMap(new HashMap<String, Map<String, File>>());

	static {
		try {
			// Register this with the DriverManager
			DriverManager.registerDriver(new MockJdbcDriver());
		} catch (SQLException e) {
			// ignore
		}
	}
	
	/**
	 * Reset the internal data structures to restart counting
	 */
	public static void reset() {
		step = -1;
		inMemoryTableResources = new HashMap<String,String>();
		// keep the per-database maps (connections hold a reference to them), just empty them
		synchronized (tableResources) {
			for (Map<String, File> databaseMap : tableResources.values()) {
				databaseMap.clear();
			}
		}
	}
	
	public static String getStepName() {
		return STEP_PREFIX+step;
	}

	/**
	 * Registers a CSV file for a database table. When a Query is executed like <code>SELECT * FROM ADDRESSES</code> the
	 * given <code>csvFile</code> for the given <code>tablename</code> <code>addresses</code> will be used.
	 *
	 * @param tablename
	 *            The name of the database table like in the SQL statement (e.g. addresses).
	 * @param csvFile
	 *            A {@link File} object of a CSV file which should be parsed in order to return table data.
	 */
	public static void addTableResource(String tablename, File csvFile) {
		Map<String, File> databaseMap;
		if (tableResources.containsKey(DEFAULT_DATABASE)) {
			databaseMap = tableResources.get(DEFAULT_DATABASE);
		} else {
			databaseMap = Collections.synchronizedMap(new HashMap<String, File>());
		}
		databaseMap.put(tablename.toLowerCase(), csvFile);
		tableResources.put(DEFAULT_DATABASE, databaseMap);
	}


	@Override
	public int getMajorVersion() {
		return VERSION_MAJOR;
	}

	@Override
	public int getMinorVersion() {
		return VERSION_MINOR;
	}

	@Override
	public boolean jdbcCompliant() {
		return false;
	}

	@Override
	public boolean acceptsURL(String url) throws SQLException {
		return
			url.equals("any") ||	// used by JUnit test cases
			url.toLowerCase().startsWith("jdbc::mock::");
	}

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		String database = parseConnectUrl(url);

		loadTableResources(database);

		// Hand over a map that is registered in the driver, so tables added after connecting are visible
		Map<String, File> databaseMap;
		synchronized (tableResources) {
			databaseMap = tableResources.get(database);
			if (databaseMap == null) {
				databaseMap = Collections.synchronizedMap(new HashMap<String, File>());
				tableResources.put(database, databaseMap);
			}
		}

		return new MockConnection(databaseMap, url);
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties props) throws SQLException {
		return new DriverPropertyInfo[0];
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return null;
	}

	/**
	 * Used for parsing CSV
	 * 
	 * @param format {@link SimpleDataFormat} pattern
	 */
	public static void setDateFormat(String format) {
		THREAD_LOCAL_DATEFORMAT.set(new SimpleDateFormat(format));
	}
	
	/**
	 * Used for parsing CSV
	 * 
	 * @param format {@link SimpleDataFormat} pattern
	 */
	public static void setTimeFormat(String format) {
		THREAD_LOCAL_TIMEFORMAT.set(new SimpleDateFormat(format));
	}

	/**
	 * Used for parsing CSV
	 * 
	 * @param format {@link SimpleDataFormat} pattern
	 */
	public static void setTimestampFormat(String format) {
		THREAD_LOCAL_TIMESTAMPFORMAT.set(new SimpleDateFormat(format));
	}

	/**
	 * Parse jdbc url to database file path
	 *
	 * @param url 	jdbc url
	 * @return database file path
	 */
	private String parseConnectUrl(String url) {
		if (url == null) {
			throw new RuntimeException("You should defined jdbc url first");
		}

		final int index = url.indexOf("jdbc::mock::");
		if (index == -1) {
			return DEFAULT_DATABASE;
		}

		final String others = url.substring("jdbc::mock::".length());
		final String[] items = others.split("::");
		switch(items.length) {
			case 0:
				throw new RuntimeException("No database directory defined");
			default:
				return StringUtils.join(items, "/");
		}

	}

	/**
	 * load table resources from database directory
	 *
	 * @param database database path
	 */
	private void loadTableResources(String database) {
		// ignore database name is any
		if (DEFAULT_DATABASE.equals(database)) {
			return;
		}

		// check database is exists
		URL dirUrl = getClass().getClassLoader().getResource(database);
		if (dirUrl == null) {
			throw new RuntimeException("The database directory is not exists");
		}

		File dir = new File(dirUrl.getFile());
		if (!dir.canRead() || !dir.isDirectory()) {
			throw new RuntimeException("The database directory is not a directory or cannot read");
		}

		// get all table files
		File[] files = dir.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				return pathname.isFile() && FilenameUtils.isExtension(pathname.getName(), "csv");
			}

		});

		// registry table resources
		for (File file : files) {
			Map<String, File> databaseMap = tableResources.get(database);
			if (databaseMap == null) {
				databaseMap = Collections.synchronizedMap(new HashMap<String, File>());
				tableResources.put(database, databaseMap);
			}
			databaseMap.put(FilenameUtils.getBaseName(file.getName()), file);
		}
	}


	/**
	 * Get the current value of the resource, used mainly to examine the parameters used for INSERT/UPDATE queries.
	 * When running inside Boomi and the resource has not been added in memory, the Dynamic Process Property
	 * <code>mockjdbc_&lt;testID&gt;</code> is used instead (as written, then in lower case).
	 * @param testID
	 * @return
	 */
	public static String getInMemoryTableResource(String testID) {
		String value = inMemoryTableResources.get(testID.toLowerCase().trim());
		if (value == null) {
			value = BoomiExecutionUtil.getResourceProperty(testID);
		}
		return value;
	}
	
	/**
	 * Get the current value of the resource, used mainly to examine the parameters used for INSERT/UPDATE queries
	 * @param testID
	 * @return
	 */
	public static String getInMemoryTableResourceForCurrentStep() {
		return getInMemoryTableResource(getStepName());
	}
	
	/**
	 * Signal to update to the next step
	 */
	public static void nextStep() {
		step++;
	}



	public static void clearInMemoryTableResources() {
		inMemoryTableResources.clear();
	}


	/**
	 * Add the CSV contained the string 'value' to the list of available resultsets
	 *  
	 * @param testID
	 * @param value
	 */
	public static void addInMemoryTableResource(String testID, String value) {
		inMemoryTableResources.put(testID.toLowerCase().trim(), value.trim());
	}

	/**
	 * Add the CSV contained the string 'value' to the list of available resultsets
	 *  
	 * @param testID
	 * @param value
	 */
	public static void addInMemoryTableResource(int step, String value) {
		addInMemoryTableResource(STEP_PREFIX+step, value);
	}

	/**
	 * Add the CSV contained the InputStream 'valueStream' to the list of available resultsets
	 *  
	 * @param testID
	 * @param value
	 */
	@SuppressWarnings("resource")
	public static void addInMemoryTableResource(String testID, InputStream valueStream) {
		// search for "end of stream" => read all the stream into the string !
		Scanner s = new Scanner(valueStream).useDelimiter("\\A");
	    String value = s.hasNext() ? s.next() : "";
	    s.close();
	    MockJdbcDriver.addInMemoryTableResource(testID,value);
	}
	
	/**
	 * Add the CSV contained the InputStream 'valueStream' to the list of available resultsets
	 *  
	 * @param step number of step for using this resource
	 * @param value
	 */
	public static void addInMemoryTableResource(int step, InputStream valueStream) {
		addInMemoryTableResource(STEP_PREFIX+step,valueStream);
	}


}
