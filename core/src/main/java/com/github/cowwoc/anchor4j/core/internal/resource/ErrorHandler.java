package com.github.cowwoc.anchor4j.core.internal.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;

import java.io.IOException;

/**
 * Converts an error message to an exception.
 */
@FunctionalInterface
public interface ErrorHandler
{
	/**
	 * Converts an error message to an exception.
	 *
	 * @param result the result of executing a command
	 * @param error  the String representation of the error
	 * @throws IOException if an error occurs
	 */
	void accept(CommandResult result, String error) throws IOException;
}