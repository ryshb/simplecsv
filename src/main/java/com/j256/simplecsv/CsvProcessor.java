package com.j256.simplecsv;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.j256.simplecsv.ParseError.ErrorType;
import com.j256.simplecsv.converter.Converter;
import com.j256.simplecsv.converter.ConverterUtils;

/**
 * CSV reader and writer.
 * 
 * @param <T>
 *            Entity type that we are processing. It should have a public no-arg constructor so it can be created by
 *            this library.
 * 
 * @author graywatson
 */
public class CsvProcessor<T> {

	public static final char DEFAULT_CELL_SEPARATOR = ',';
	public static final char DEFAULT_CELL_QUOTE = '"';

	private char cellSeparator = DEFAULT_CELL_SEPARATOR;
	private char cellQuote = DEFAULT_CELL_QUOTE;
	private String lineTermination = System.getProperty("line.separator");
	private boolean allowPartialLines;

	private final FieldInfo[] fieldInfos;
	private final Constructor<T> constructor;

	private final Map<Class<?>, Converter<?, ?>> converterMap = new HashMap<Class<?>, Converter<?, ?>>();

	{
		ConverterUtils.addInternalConverters(converterMap);
	}

	/**
	 * Constructs a process with an entity class whose fields should be marked with {@link CvsField} annotations.
	 * 
	 * @throws IllegalArgumentException
	 */
	public CsvProcessor(Class<T> entityClass) throws IllegalArgumentException {
		List<FieldInfo> fieldInfos = new ArrayList<FieldInfo>();
		for (Class<?> clazz = entityClass; clazz != Object.class; clazz = clazz.getSuperclass()) {
			for (Field field : entityClass.getDeclaredFields()) {
				Converter<?, ?> converter = converterMap.get(field.getType());
				// NOTE: converter could be null in which case the CsvField.converterClass must be set
				@SuppressWarnings("unchecked")
				Converter<Object, Object> castConverter = (Converter<Object, Object>) converter;
				FieldInfo fieldInfo = FieldInfo.fromField(field, castConverter);
				if (fieldInfo != null) {
					fieldInfos.add(fieldInfo);
					field.setAccessible(true);
				}
			}
		}
		this.fieldInfos = fieldInfos.toArray(new FieldInfo[fieldInfos.size()]);
		try {
			this.constructor = entityClass.getConstructor();
		} catch (Exception e) {
			throw new IllegalArgumentException("Could not find public no-arg constructor for: " + entityClass);
		}
	}

	/**
	 * Register a converter class for all instances of the class argument. The converter can also be specified with the
	 * {@link CsvField#converterClass()} annotation field.
	 */
	public <FT> void registerConverter(Class<FT> clazz, Converter<FT, ?> converter) {
		converterMap.put(clazz, converter);
	}

	/**
	 * Process a header line and return the associated entity.
	 */
	public boolean validateHeader(String line) {
		// TODO:
		return false;
	}

	/**
	 * Read and process a line and return the associated entity.
	 */
	public T readLine(String line) throws ParseException {
		int fieldCount = 0;
		T result;
		try {
			result = constructor.newInstance();
		} catch (Exception e) {
			ParseException parseException =
					new ParseException("Could not construct instance of " + constructor.getDeclaringClass(), 0);
			parseException.initCause(e);
			throw parseException;
		}
		int linePos = 0;
		final ParseError parseError = new ParseError();
		for (FieldInfo fieldInfo : fieldInfos) {
			boolean atEnd = (linePos == line.length());
			parseError.reset();
			if (linePos < line.length() && line.charAt(linePos) == cellQuote) {
				linePos = processQuotedLine(line, 1, linePos, fieldInfo, result, parseError);
			} else {
				linePos = processUnquotedLine(line, 1, linePos, fieldInfo, result, parseError);
			}
			if (parseError.isError()) {
				throw new ParseException("Problems parsing line at position " + linePos + " (" + parseError + "): "
						+ line, linePos);
			}
			fieldCount++;
			if (atEnd) {
				break;
			}
		}
		if (fieldCount < fieldInfos.length && !allowPartialLines) {
			throw new ParseException("Line does not have " + fieldInfos.length + " cells: " + line, 0);
		}
		if (linePos < line.length()) {
			throw new ParseException("Line has extra information past last column: " + line, 0);
		}
		return result;
	}

	/**
	 * Convert the entity into a string suitable to be written.
	 */
	public String writeLine(T entity, boolean appendLineTermination) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (FieldInfo fieldInfo : fieldInfos) {
			if (first) {
				first = false;
			} else {
				sb.append(cellSeparator);
			}
			Field field = fieldInfo.getField();
			Object value;
			try {
				value = field.get(entity);
			} catch (Exception e) {
				throw new IllegalStateException("Could not get value from entity field: " + field);
			}
			@SuppressWarnings("unchecked")
			Converter<Object, Object> castConverter = (Converter<Object, Object>) fieldInfo.getConverter();
			String str = castConverter.javaToString(fieldInfo, value);
			// need to protect the cell if it contains a quote
			if (str.indexOf(cellQuote) >= 0) {
				writeQuoted(sb, str);
				continue;
			}
			boolean needsQuotes = fieldInfo.isNeedsQuotes();
			if (!needsQuotes) {
				for (int i = 0; i < str.length(); i++) {
					char ch = str.charAt(i);
					if (ch == cellSeparator || ch == '\r' || ch == '\n' || ch == '\t' || ch == '\b') {
						needsQuotes = true;
						break;
					}
				}
			}
			if (needsQuotes) {
				sb.append(cellQuote);
			}
			sb.append(str);
			if (needsQuotes) {
				sb.append(cellQuote);
			}
		}
		if (appendLineTermination) {
			sb.append(lineTermination);
		}
		return sb.toString();
	}

	/**
	 * String that separates cells in out CSV input and output.
	 */
	public void setCellSeparator(char cellSeparator) {
		this.cellSeparator = cellSeparator;
	}

	/**
	 * Quote character that is used to wrap each cell.
	 */
	public void setCellQuote(char cellQuote) {
		this.cellQuote = cellQuote;
	}

	/**
	 * Sets the character which is written at the end of the row. Default is to use
	 * System.getProperty("line.separator");.
	 */
	public void setLineTermination(String lineTermination) {
		this.lineTermination = lineTermination;
	}

	/**
	 * Set to true to allow lines that do not have values for all of the cells. Otherwise an IllegalArgumentException is
	 * thrown.
	 */
	public void setAllowPartialLines(boolean allowPartialLines) {
		this.allowPartialLines = allowPartialLines;
	}

	private int processQuotedLine(String line, int lineNumber, int linePos, FieldInfo fieldInfo, Object result,
			ParseError parseError) {

		// linePos is pointing at the first quote, move past it
		linePos++;
		int fieldStart = linePos;
		int fieldEnd = linePos;

		StringBuilder sb = null;
		while (linePos < line.length()) {

			// look for the next quote
			fieldEnd = line.indexOf(cellQuote, linePos);
			if (fieldEnd < 0) {
				parseError.setErrorType(ErrorType.TRUNCATED_VALUE);
				parseError.setMessage("Field not terminated with quote '" + cellQuote + "'");
				return line.length();
			}

			linePos = fieldEnd + 1;
			if (linePos == line.length()) {
				break;
			} else if (line.charAt(linePos) == cellSeparator) {
				linePos++;
				break;
			}

			// must have a quote following a quote if there wasn't a cellSeparator
			if (line.charAt(linePos) != cellQuote) {
				parseError.setErrorType(ErrorType.INVALID_FORMAT);
				parseError.setMessage("quote '" + cellQuote + "' is not followed up separator '" + cellSeparator + "'");
				return linePos;
			}

			fieldEnd = linePos;
			linePos++;
			if (linePos == line.length() || line.charAt(linePos) == cellSeparator) {
				linePos++;
				break;
			}

			// need to build the string dynamically now
			if (sb == null) {
				sb = new StringBuilder(32);
			}
			// add to the string-builder the field + 1 quote
			sb.append(line, fieldStart, fieldEnd);
			// line-pos is pointing past 2nd (maybe 3rd) quote
			fieldStart = linePos;
		}

		if (sb == null) {
			extractValue(line, lineNumber, fieldInfo, fieldStart, fieldEnd, result, parseError);
		} else {
			sb.append(line, fieldStart, fieldEnd);
			String str = sb.toString();
			extractValue(str, lineNumber, fieldInfo, 0, str.length(), result, parseError);
		}
		return linePos;
	}

	private int processUnquotedLine(String line, int lineNumber, int linePos, FieldInfo fieldInfo, Object result,
			ParseError parseError) {
		int fieldStart = linePos;
		linePos = line.indexOf(cellSeparator, fieldStart);
		if (linePos < 0) {
			linePos = line.length();
		}

		extractValue(line, lineNumber, fieldInfo, fieldStart, linePos, result, parseError);

		if (linePos < line.length()) {
			// skip over the separator
			linePos++;
		}
		return linePos;
	}

	private void writeQuoted(StringBuilder sb, String str) {
		sb.append(cellQuote);
		int start = 0;
		while (true) {
			int linePos = str.indexOf(cellQuote, start);
			if (linePos < 0) {
				sb.append(str, start, str.length());
				break;
			}
			// move past the quote so we can output it
			linePos++;
			sb.append(str, start, linePos);
			// output another quote
			sb.append(cellQuote);
			start = linePos;
		}
		sb.append(cellQuote);
	}

	private void extractValue(String line, int lineNum, FieldInfo fieldInfo, int fieldStart, int fieldEnd, Object obj,
			ParseError parseError) {

		String cellStr = line.substring(fieldStart, fieldEnd);
		if (fieldInfo.isTrimInput()) {
			cellStr = cellStr.trim();
		}
		if (cellStr.isEmpty() && fieldInfo.getDefaultValue() != null) {
			cellStr = fieldInfo.getDefaultValue();
		}
		if (cellStr.isEmpty() && fieldInfo.isRequired()) {

		}

		Object value;
		try {
			value = fieldInfo.getConverter().stringToJava(line, lineNum, fieldInfo, cellStr, parseError);
		} catch (ParseException e) {
			parseError.setErrorType(ErrorType.INVALID_FORMAT);
			parseError.setMessage(e.getMessage());
			// TODO: how to handle
			e.printStackTrace();
			return;
		} catch (Exception e) {
			parseError.setErrorType(ErrorType.INTERNAL_ERROR);
			parseError.setMessage(e.getMessage());
			// TODO: how to handle
			e.printStackTrace();
			return;
		}

		if (value == null) {
			if (parseError.isError()) {
				// TODO: how to handle
				System.err.println("got error: " + parseError);
				return;
			}
			// take the default value of the field
			return;
		}

		try {
			fieldInfo.getField().set(obj, value);
		} catch (Exception e) {
			parseError.setErrorType(ErrorType.INTERNAL_ERROR);
			parseError.setMessage(e.getMessage());
			// TODO: how to handle
			e.printStackTrace();
			return;
		}
	}
}
