package com.j256.simplecsv.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.text.ParseException;

import org.junit.Test;

import com.j256.simplecsv.FieldInfo;
import com.j256.simplecsv.ParseError;
import com.j256.simplecsv.converter.BooleanConverter.ConfigInfo;

public class BooleanConverterTest extends AbstractConverterTest {

	@Test
	public void testStuff() throws ParseException {
		BooleanConverter converter = new BooleanConverter();
		ConfigInfo configInfo = converter.configure(null, 0, null);
		testConverter(converter, configInfo, true);
		testConverter(converter, configInfo, false);
		testConverter(converter, configInfo, null);
		converter = new BooleanConverter();
		converter.configure("1,0", 0, null);
		testConverter(converter, configInfo, true);
		testConverter(converter, configInfo, false);
		testConverter(converter, configInfo, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testBadFormat() {
		BooleanConverter converter = new BooleanConverter();
		converter.configure("1", 0, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testEmptyTrue() {
		BooleanConverter converter = new BooleanConverter();
		converter.configure(",F", 0, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testEmptyFalse() {
		BooleanConverter converter = new BooleanConverter();
		converter.configure("T,", 0, null);
	}

	@Test
	public void testParseErrorOnBadValue() {
		BooleanConverter converter = new BooleanConverter();
		ConfigInfo configInfo = converter.configure(null, 0, null);
		FieldInfo fieldInfo = FieldInfo.forTests(converter, configInfo);
		ParseError parseError = new ParseError();
		assertEquals(false, converter.stringToJava("line", 1, fieldInfo, "unknown", parseError));
		assertFalse(parseError.isError());

		configInfo = converter.configure(null, BooleanConverter.PARSE_ERROR_ON_INVALID_VALUE, null);
		fieldInfo = FieldInfo.forTests(converter, configInfo);
		parseError.reset();
		assertNull(converter.stringToJava("line", 1, fieldInfo, "unknown", parseError));
		assertTrue(parseError.isError());
	}

	@Test
	public void testNull() {
		BooleanConverter converter = new BooleanConverter();
		assertNull(converter.javaToString(null, null));
	}
}
