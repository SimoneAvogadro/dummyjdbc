package me.avogadro.mockjdbc.statement.impl;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.mockjdbc.MockJdbcDriver;

public final class NumericGettersTest {

	private ResultSet resultSet;

	@Before
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();
		MockJdbcDriver.addInMemoryTableResource("numbers",
				"id|integer, big, price|double, small, empty\n" +
				"1, 9223372036854775807, 12.5, -7, \n" +
				"2, -3, -0.25, 127, \n");
		resultSet = DriverManager.getConnection("any").createStatement().executeQuery("SELECT * FROM numbers");
	}

	@Test
	public void testNumericGettersByLabel() throws SQLException {
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals(9223372036854775807L, resultSet.getLong("big"));
		Assert.assertEquals(12.5d, resultSet.getDouble("price"), 0d);
		Assert.assertEquals(12.5f, resultSet.getFloat("price"), 0f);
		Assert.assertEquals((short) -7, resultSet.getShort("small"));
		Assert.assertEquals((byte) -7, resultSet.getByte("small"));
		Assert.assertEquals(1L, resultSet.getLong("id"));
	}

	@Test
	public void testNumericGettersByIndex() throws SQLException {
		Assert.assertTrue(resultSet.next());
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals(-3L, resultSet.getLong(2));
		Assert.assertEquals(-0.25d, resultSet.getDouble(3), 0d);
		Assert.assertEquals(-0.25f, resultSet.getFloat(3), 0f);
		Assert.assertEquals((short) 127, resultSet.getShort(4));
		Assert.assertEquals((byte) 127, resultSet.getByte(4));
	}

	@Test
	public void testEmptyValueIsZero() throws SQLException {
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals(0L, resultSet.getLong("empty"));
		Assert.assertEquals(0d, resultSet.getDouble("empty"), 0d);
		Assert.assertEquals(0f, resultSet.getFloat("empty"), 0f);
		Assert.assertEquals((short) 0, resultSet.getShort("empty"));
		Assert.assertEquals((byte) 0, resultSet.getByte("empty"));
	}

	@Test(expected = NumberFormatException.class)
	public void testInvalidNumber() throws SQLException {
		Assert.assertTrue(resultSet.next());
		resultSet.getLong("price");
	}

	@Test
	public void testGetRow() throws SQLException {
		Assert.assertEquals(0, resultSet.getRow());
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals(1, resultSet.getRow());
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals(2, resultSet.getRow());
		Assert.assertFalse(resultSet.next());
		Assert.assertEquals(0, resultSet.getRow());
	}
}
