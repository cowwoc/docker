package com.github.cowwoc.docker.internal.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * String helper functions.
 */
public final class Strings
{
	private static final ThreadLocal<DecimalFormat> FORMATTER = ThreadLocal.withInitial(() ->
	{
		DecimalFormat formatter = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
		DecimalFormatSymbols symbols = formatter.getDecimalFormatSymbols();

		symbols.setGroupingSeparator('_');
		formatter.setDecimalFormatSymbols(symbols);
		return formatter;
	});

	/**
	 * @param value a number
	 * @return a string representation of the number with a visual separator every 3 digits
	 */
	public static String format(long value)
	{
		return FORMATTER.get().format(value);
	}

	/**
	 * Splits a String into multiple lines, omitting any empty lines.
	 *
	 * @param lines zero or more lines of text
	 * @return a collection of lines
	 * @throws NullPointerException if {@code text} is null
	 */
	public static List<String> split(String lines)
	{
		List<String> result = new ArrayList<>();
		while (true)
		{
			int index = lines.indexOf('\n');
			if (index == -1)
				break;
			String line = lines.substring(0, index);
			if (!line.isEmpty())
				result.add(line);
			lines = lines.substring(index + 1);
		}
		if (!lines.isEmpty())
			result.add(lines);
		return result;
	}

	private Strings()
	{
	}
}