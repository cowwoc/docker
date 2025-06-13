package com.github.cowwoc.anchor4j.core.internal.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;

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
	 * Parses an {@code InetSocketAddress}.
	 *
	 * @param address the textual representation of the address
	 * @return the InetSocketAddress
	 * @throws NullPointerException     if {@code address} is null
	 * @throws IllegalArgumentException if {@code address}:
	 *                                  <ul>
	 *                                    <li>contains whitespace, or is empty.</li>
	 *                                    <li>is missing a port number.</li>
	 *                                  </ul>
	 */
	public static InetSocketAddress toInetSocketAddress(String address)
	{
		requireThat(address, "address").doesNotContainWhitespace().isNotEmpty();

		// https://stackoverflow.com/a/2347356/14731
		URI uri = URI.create("tcp://" + address);
		if (uri.getPort() == -1 ||
			(uri.getHost() == null && InetAddress.ofLiteral(uri.getAuthority()) instanceof Inet6Address))
		{
			throw new IllegalArgumentException("Address must contain a port number.\n" +
				"Actual: " + address);
		}
		return InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
	}

	private Strings()
	{
	}
}