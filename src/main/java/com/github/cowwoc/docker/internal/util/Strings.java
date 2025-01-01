package com.github.cowwoc.docker.internal.util;

import org.slf4j.Logger;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
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
	 * Logs lines, one at a time.
	 *
	 * @param lines zero or more lines of text
	 * @param log   the logger to log to
	 * @throws NullPointerException if any of the arguments are null
	 */
	public static void logLines(StringBuilder lines, Logger log)
	{
		while (true)
		{
			String line = nextLine(lines);
			if (line.isEmpty())
				return;
			log.info(line);
		}
	}

	/**
	 * Removes the next line and returns it. If there is only one line left, this method does nothing and
	 * returns an empty string.
	 *
	 * @param lines a {@code StringBuilder} that may contain multiple lines
	 * @return an empty string if there is only one line left
	 * @throws NullPointerException if {@code lines} is null
	 */
	private static String nextLine(StringBuilder lines)
	{
		int index = lines.indexOf("\r\n");
		if (index != -1)
		{
			String line = lines.substring(0, index);
			lines.delete(0, index + 2);
			return line;
		}
		index = lines.indexOf("\n");
		if (index != -1)
		{
			String line = lines.substring(0, index);
			lines.delete(0, index + 1);
			return line;
		}
		return "";
	}

	private Strings()
	{
	}
}