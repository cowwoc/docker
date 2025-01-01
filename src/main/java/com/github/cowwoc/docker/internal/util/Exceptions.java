package com.github.cowwoc.docker.internal.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Helper functions for exceptions.
 */
public final class Exceptions
{
	private Exceptions()
	{
	}

	/**
	 * Returns the stack trace of a {@code Throwable}.
	 *
	 * @param t the {@code Throwable}
	 * @return the stack trace
	 * @see <a href="https://stackoverflow.com/a/1149712/14731">https://stackoverflow.com/a/1149712/14731</a>
	 */
	public static String toString(Throwable t)
	{
		if (t == null)
			return "null";
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		return sw.toString();
	}
}