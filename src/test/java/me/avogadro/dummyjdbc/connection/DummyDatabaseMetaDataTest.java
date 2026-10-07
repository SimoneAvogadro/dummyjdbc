package me.avogadro.dummyjdbc.connection;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.dummyjdbc.DummyJdbcDriver;

/**
 * Tools such as the Boomi Database V2 connector read the metadata before running a query: nothing may be null.
 */
public final class DummyDatabaseMetaDataTest {

	private Connection connection;
	private DatabaseMetaData metaData;

	@Before
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName(DummyJdbcDriver.class.getCanonicalName());
		connection = DriverManager.getConnection("any");
		metaData = connection.getMetaData();
	}

	@Test
	public void testDescriptiveValues() throws SQLException {
		Assert.assertEquals("DummyJDBC", metaData.getDatabaseProductName());
		Assert.assertEquals("DummyJDBC", metaData.getDriverName());
		Assert.assertEquals("1.5", metaData.getDriverVersion());
		Assert.assertEquals("1.5", metaData.getDatabaseProductVersion());
		Assert.assertEquals(1, metaData.getDriverMajorVersion());
		Assert.assertEquals(5, metaData.getDriverMinorVersion());
		Assert.assertEquals("any", metaData.getURL());
		Assert.assertEquals("", metaData.getUserName());
		Assert.assertEquals(" ", metaData.getIdentifierQuoteString());
		Assert.assertSame(connection, metaData.getConnection());
		Assert.assertEquals(RowIdLifetime.ROWID_UNSUPPORTED, metaData.getRowIdLifetime());
	}

	@Test
	public void testUrlOfMockDatabase() throws SQLException {
		Assert.assertEquals("jdbc::mock::database",
				DriverManager.getConnection("jdbc::mock::database").getMetaData().getURL());
	}

	@Test
	public void testGetColumnsIsEmptyWithStandardColumns() throws SQLException {
		ResultSet columns = metaData.getColumns(null, null, "users", "%");

		Assert.assertFalse(columns.next());
		Assert.assertEquals(24, columns.getMetaData().getColumnCount());
		Assert.assertEquals("COLUMN_NAME", columns.getMetaData().getColumnName(4));
	}

	@Test
	public void testGetTablesAndPrimaryKeysAreEmpty() throws SQLException {
		Assert.assertFalse(metaData.getTables(null, null, "%", null).next());
		Assert.assertFalse(metaData.getPrimaryKeys(null, null, "users").next());
		Assert.assertFalse(metaData.getProcedures(null, null, "%").next());
	}

	@Test
	public void testUnwrap() throws SQLException {
		Assert.assertSame(metaData, metaData.unwrap(DatabaseMetaData.class));
	}

	@Test(expected = SQLException.class)
	public void testUnwrapUnsupportedClass() throws SQLException {
		metaData.unwrap(String.class);
	}

	/** every String or ResultSet returned by the metadata is not null */
	@Test
	public void testNoMethodReturnsNull() throws Exception {
		List<String> nulls = new ArrayList<String>();
		for (Method method : DatabaseMetaData.class.getMethods()) {
			Class<?> type = method.getReturnType();
			if (type != String.class && type != ResultSet.class) {
				continue;
			}
			Object[] args = new Object[method.getParameterTypes().length];
			for (int i = 0; i < args.length; i++) {
				Class<?> param = method.getParameterTypes()[i];
				args[i] = param == int.class ? Integer.valueOf(0) : param == boolean.class ? Boolean.FALSE : null;
			}
			if (method.invoke(metaData, args) == null) {
				nulls.add(method.getName());
			}
		}
		Assert.assertEquals("methods returning null", new ArrayList<String>(), nulls);
	}
}
