package com.github.cowwoc.docker.internal.util;

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
	 * If {@code input} ends with a newline character, remove it; otherwise, return {@code input} unchanged.
	 *
	 * @param input a string
	 * @return the updated string
	 * @throws NullPointerException if {@code input} is null
	 */
	public static String removeNewlineFromEnd(String input)
	{
		if (input.endsWith("\n"))
			return input.substring(0, input.length() - 1);
		return input;
	}

	private Strings()
	{
	}
}