package me.avogadro.dummyjdbc.statement.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.dummyjdbc.DummyJdbcDriver;

/**
 * How table names and test cases are recognized in the SQL text (INSERT, UPDATE, TESTCASE comments).
 */
public final class QueryPatternsTest {

	private Connection connection;

	@Before
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName(DummyJdbcDriver.class.getCanonicalName());
		DummyJdbcDriver.reset();
		connection = DriverManager.getConnection("any");
	}

	@Test
	public void testUpdateWithoutTestCaseIsCaptured() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("UPDATE Customers SET city = ? WHERE id = ?");
		statement.setString(1, "Rome");
		statement.setInt(2, 42);

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("Rome,42", DummyJdbcDriver.getInMemoryTableResource("Customers_PARAMS"));
	}

	@Test
	public void testUpdateOnSeveralLinesAfterAComment() throws SQLException {
		PreparedStatement statement = connection.prepareStatement(
				"-- change the city\r\nUPDATE customers\r\nSET city = ?\r\nWHERE id = ?");
		statement.setString(1, "Rome");
		statement.setInt(2, 42);

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("Rome,42", DummyJdbcDriver.getInMemoryTableResource("customers_PARAMS"));
	}

	@Test
	public void testUpdateOfTableWithUnsupportedNameIsNotCapturedUnderAWrongName() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("UPDATE user_roles SET role = ?");
		statement.setString(1, "admin");

		Assert.assertEquals(0, statement.executeUpdate());
		Assert.assertNull(DummyJdbcDriver.getInMemoryTableResource("user_PARAMS"));
	}

	@Test
	public void testInsertOnSeveralLinesIsCaptured() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("INSERT INTO orders (a, b)\nVALUES (?, ?)");
		statement.setString(1, "x");
		statement.setInt(2, 7);

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("x,7", DummyJdbcDriver.getInMemoryTableResource("orders_PARAMS"));
	}

	@Test
	public void testInsertWithWindowsLineEndingsIsCaptured() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("INSERT INTO orders\r\n(a, b)\r\nVALUES (?, ?)");
		statement.setString(1, "x");
		statement.setInt(2, 7);

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("x,7", DummyJdbcDriver.getInMemoryTableResource("orders_PARAMS"));
	}

	@Test
	public void testTestCaseWithWindowsLineEndingAndParameters() throws SQLException {
		DummyJdbcDriver.addInMemoryTableResource("T_014a?42", "name\nspecific");
		DummyJdbcDriver.addInMemoryTableResource("T_014a", "name\ngeneric");

		PreparedStatement statement = connection.prepareStatement("-- TESTCASE: T_014a\r\nSELECT * FROM x WHERE id = ?");
		statement.setInt(1, 42);
		ResultSet resultSet = statement.executeQuery();

		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("specific", resultSet.getString("name"));
	}

	@Test
	public void testTestCaseFollowedByMultiLineQuery() throws SQLException {
		DummyJdbcDriver.addInMemoryTableResource("T1", "name\nfrom test case");

		ResultSet resultSet = connection.createStatement()
				.executeQuery("-- TESTCASE: T1  \nSELECT *\nFROM some_table\nWHERE 1 = 1");

		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("from test case", resultSet.getString("name"));
	}

	@Test
	public void testUpdateWithTestCaseAndWindowsLineEnding() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("-- TESTCASE: T_015\r\nUPDATE Customers SET city = ?");
		statement.setString(1, "Rome");
		statement.executeUpdate();

		Assert.assertEquals("Rome", DummyJdbcDriver.getInMemoryTableResource("T_015_PARAMS"));
	}
}
