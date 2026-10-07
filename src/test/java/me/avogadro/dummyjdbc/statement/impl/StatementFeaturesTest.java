package me.avogadro.dummyjdbc.statement.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.dummyjdbc.DummyJdbcDriver;

/**
 * Statement features used by the Boomi Database V2 connector: generated keys, batches ("Commit By Rows"), max rows.
 */
public final class StatementFeaturesTest {

	private Connection connection;

	@Before
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName(DummyJdbcDriver.class.getCanonicalName());
		DummyJdbcDriver.reset();
		DummyJdbcDriver.addInMemoryTableResource("customers", "id|integer, name\n1, John\n2, Mary\n3, Paul");
		connection = DriverManager.getConnection("any");
	}

	@Test
	public void testGeneratedKeysAreAnEmptyResultSet() throws SQLException {
		PreparedStatement insert = connection.prepareStatement("INSERT INTO customers (name) VALUES (?)",
				Statement.RETURN_GENERATED_KEYS);
		insert.setString(1, "Anna");
		insert.executeUpdate();

		ResultSet keys = insert.getGeneratedKeys();
		Assert.assertNotNull(keys);
		Assert.assertFalse(keys.next());
		Assert.assertEquals(1, keys.getMetaData().getColumnCount());

		Assert.assertNotNull(connection.createStatement().getGeneratedKeys());
	}

	@Test
	public void testBatchRunsEveryParameterSet() throws SQLException {
		PreparedStatement insert = connection.prepareStatement("INSERT INTO customers (id, name) VALUES (?, ?)");
		insert.setInt(1, 4);
		insert.setString(2, "Anna");
		insert.addBatch();
		insert.setInt(1, 5);
		insert.setString(2, "Luca");
		insert.addBatch();

		int[] results = insert.executeBatch();

		Assert.assertEquals("[1, 1]", Arrays.toString(results));
		// as for single executions, the latest parameters are kept
		Assert.assertEquals("5,Luca", DummyJdbcDriver.getInMemoryTableResource("customers_PARAMS"));
		// the batch is emptied
		Assert.assertEquals(0, insert.executeBatch().length);
	}

	@Test
	public void testBatchKeepsACopyOfTheParameters() throws SQLException {
		PreparedStatement update = connection.prepareStatement("UPDATE customers SET name = ? WHERE id = ?");
		update.setString(1, "first");
		update.setInt(2, 1);
		update.addBatch();
		update.setString(1, "second");	// the parameter 2 stays set, as in JDBC
		update.addBatch();

		Assert.assertEquals("[1, 1]", Arrays.toString(update.executeBatch()));
		Assert.assertEquals("second,1", DummyJdbcDriver.getInMemoryTableResource("customers_PARAMS"));
	}

	@Test
	public void testClearBatch() throws SQLException {
		PreparedStatement insert = connection.prepareStatement("INSERT INTO customers (id) VALUES (?)");
		insert.setInt(1, 4);
		insert.addBatch();
		insert.clearBatch();

		Assert.assertEquals(0, insert.executeBatch().length);
		Assert.assertNull(DummyJdbcDriver.getInMemoryTableResource("customers_PARAMS"));
	}

	@Test
	public void testBatchOfUnknownStatementsReturnsZeros() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("MERGE something ?");
		statement.setInt(1, 1);
		statement.addBatch();

		Assert.assertEquals("[0]", Arrays.toString(statement.executeBatch()));
	}

	@Test
	public void testStatementBatch() throws SQLException {
		Statement statement = connection.createStatement();
		statement.addBatch("UPDATE customers SET name = 'x'");
		statement.addBatch("DELETE FROM customers");

		Assert.assertEquals(2, statement.executeBatch().length);
	}

	@Test
	public void testMaxRowsOnPreparedStatement() throws SQLException {
		PreparedStatement select = connection.prepareStatement("SELECT * FROM customers");
		select.setMaxRows(2);
		Assert.assertEquals(2, select.getMaxRows());

		ResultSet resultSet = select.executeQuery();

		Assert.assertTrue(resultSet.next());
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals(2, resultSet.getRow());
		Assert.assertFalse(resultSet.next());
		Assert.assertFalse(resultSet.next());
		Assert.assertEquals(0, resultSet.getRow());
	}

	@Test
	public void testMaxRowsOnStatement() throws SQLException {
		Statement statement = connection.createStatement();
		statement.setMaxRows(1);

		ResultSet resultSet = statement.executeQuery("SELECT * FROM customers");

		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("John", resultSet.getString("name"));
		Assert.assertFalse(resultSet.next());
	}

	@Test
	public void testMaxRowsZeroMeansNoLimit() throws SQLException {
		Statement statement = connection.createStatement();
		statement.setMaxRows(0);

		ResultSet resultSet = statement.executeQuery("SELECT * FROM customers");
		int rows = 0;
		while (resultSet.next()) {
			rows++;
		}
		Assert.assertEquals(3, rows);
	}

	@Test(expected = SQLException.class)
	public void testNegativeMaxRows() throws SQLException {
		connection.createStatement().setMaxRows(-1);
	}

	@Test
	public void testAllParameterSettersAreCaptured() throws SQLException {
		PreparedStatement insert = connection.prepareStatement("INSERT INTO customers (a, b, c, d, e, f, g, h, i) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
		insert.setLong(1, 9223372036854775807L);
		insert.setBoolean(2, true);
		insert.setFloat(3, 1.5f);
		insert.setShort(4, (short) 7);
		insert.setByte(5, (byte) 8);
		insert.setNull(6, java.sql.Types.VARCHAR);
		insert.setObject(7, "obj", java.sql.Types.VARCHAR);
		insert.setObject(8, Integer.valueOf(3), java.sql.Types.INTEGER, 0);
		insert.setNull(9, java.sql.Types.VARCHAR, "VARCHAR");
		insert.executeUpdate();

		// NULL parameters are empty values; trailing empty values are dropped
		Assert.assertEquals("9223372036854775807,true,1.5,7,8,,obj,3",
				DummyJdbcDriver.getInMemoryTableResource("customers_PARAMS"));
	}

	@Test
	public void testClearParameters() throws SQLException {
		PreparedStatement insert = connection.prepareStatement("INSERT INTO customers (a, b) VALUES (?, ?)");
		insert.setString(1, "x");
		insert.clearParameters();
		insert.setString(2, "y");
		insert.executeUpdate();

		Assert.assertEquals(",y", DummyJdbcDriver.getInMemoryTableResource("customers_PARAMS"));
	}

	@Test
	public void testSelectOneHasMetadata() throws SQLException {
		ResultSet resultSet = connection.createStatement().executeQuery("select 1");

		Assert.assertEquals(1, resultSet.getMetaData().getColumnCount());
		Assert.assertEquals("1", resultSet.getMetaData().getColumnLabel(1));
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals(1, resultSet.getInt(1));
	}

	@Test
	public void testUnknownTableHasEmptyMetadata() throws SQLException {
		ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM nothing_here");

		Assert.assertFalse(resultSet.next());
		Assert.assertEquals(0, resultSet.getMetaData().getColumnCount());
	}

	@Test
	public void testFindColumn() throws SQLException {
		ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM customers");

		Assert.assertEquals(1, resultSet.findColumn("ID"));
		Assert.assertEquals(2, resultSet.findColumn("name"));
	}

	@Test(expected = SQLException.class)
	public void testFindUnknownColumn() throws SQLException {
		connection.createStatement().executeQuery("SELECT * FROM customers").findColumn("nothing");
	}
}
