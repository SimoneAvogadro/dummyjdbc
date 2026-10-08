package me.avogadro.mockjdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Test;

import me.avogadro.mockjdbc.MockJdbcDriver;
import me.avogadro.mockjdbc.connection.impl.MockConnection;

public final class MockJdbcDriverTest {

	@Test
	public void testGetGenericConnection() throws ClassNotFoundException, SQLException {

		Class.forName(MockJdbcDriver.class.getCanonicalName());
		Connection connection = DriverManager.getConnection("any");

		Assert.assertTrue(connection instanceof MockConnection);
	}

	@Test
	public void testLoadDatabase() throws ClassNotFoundException, SQLException {

		Class.forName(MockJdbcDriver.class.getCanonicalName());
		Connection connection = DriverManager.getConnection("jdbc::mock::database");

		Assert.assertTrue(connection instanceof MockConnection);
	}

	@Test
	public void testTableAddedAfterConnect() throws Exception {

		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();

		// connection opened BEFORE any table is registered
		Connection connection = DriverManager.getConnection("any");

		MockJdbcDriver.addTableResource("test_table",
				new java.io.File(getClass().getResource("statement/impl/test_table.csv").toURI()));

		java.sql.ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM test_table");
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("Germany", resultSet.getString("country_name"));

		// reset() must not detach already open connections
		MockJdbcDriver.reset();
		MockJdbcDriver.addTableResource("test_table",
				new java.io.File(getClass().getResource("statement/impl/test_table.csv").toURI()));
		resultSet = connection.createStatement().executeQuery("SELECT * FROM test_table");
		Assert.assertTrue(resultSet.next());
	}

	@Test
	public void testTypedHeaderCanBeReadByPlainName() throws Exception {

		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();
		MockJdbcDriver.addInMemoryTableResource("t", "id|integer, name\n1, x");

		java.sql.ResultSet rs = DriverManager.getConnection("any").createStatement().executeQuery("SELECT * FROM t");
		Assert.assertTrue(rs.next());
		Assert.assertEquals(1, rs.getInt("id"));
		Assert.assertEquals(1, rs.getInt("ID|INTEGER"));
		Assert.assertEquals(1, rs.getInt(1));
		Assert.assertEquals("x", rs.getString("name"));
	}

	@Test
	public void testInMemoryStringKeepsUnicode() throws Exception {

		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();
		String text = "caff\u00e8, Zo\u00eb, \u20ac, \u65e5\u672c\u8a9e, \ud83d\ude00";
		MockJdbcDriver.addInMemoryTableResource("t", "name\n\"" + text + "\"");

		java.sql.ResultSet rs = DriverManager.getConnection("any").createStatement().executeQuery("SELECT * FROM t");
		Assert.assertTrue(rs.next());
		Assert.assertEquals(text, rs.getString(1));
	}

}
