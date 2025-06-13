package com.github.cowwoc.anchor4j.core.internal.client;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.cowwoc.anchor4j.core.client.Client;
import com.github.cowwoc.anchor4j.core.internal.resource.BuildXParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * The internals shared by all clients.
 */
public interface InternalClient extends Client
{
	/**
	 * Validates a name.
	 *
	 * @param value the value of the name. The value must start with a letter, or digit, or underscore, and may
	 *              be followed by additional characters consisting of letters, digits, underscores, periods or
	 *              hyphens.
	 * @param name  the name of the value parameter
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code value}'s format is invalid
	 */
	void validateName(String value, String name);

	/**
	 * Validates an image reference.
	 *
	 * @param value the image's reference
	 * @param name  the name of the value parameter
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code value}'s format is invalid
	 */
	void validateImageReference(String value, String name);

	/**
	 * Validates an image ID or reference.
	 *
	 * @param value the image's ID or reference
	 * @param name  the name of the value parameter
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code value}'s format is invalid
	 */
	void validateImageIdOrReference(String value, String name);

	/**
	 * Validates a container ID or name.
	 * <p>
	 * Container names must start with a letter, or digit, or underscore, and may be followed by additional
	 * characters consisting of letters, digits, underscores, periods or hyphens. No other characters are
	 * allowed.
	 *
	 * @param value the container's ID or name
	 * @param name  the name of the value parameter
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code value}'s format is invalid
	 */
	void validateContainerIdOrName(String value, String name);

	/**
	 * Returns the JSON configuration.
	 *
	 * @return the configuration
	 */
	JsonMapper getJsonMapper();

	/**
	 * Returns a {@code ProcessBuilder} for running a command.
	 *
	 * @param arguments the command-line arguments to pass to the executable
	 * @return the {@code ProcessBuilder}
	 */
	ProcessBuilder getProcessBuilder(List<String> arguments);

	/**
	 * Runs a command and returns its output.
	 *
	 * @param arguments the command-line arguments to pass to the executable
	 * @return the output of the command
	 * @throws IOException          if the executable could not be found
	 * @throws InterruptedException if the thread was interrupted before the operation completed
	 */
	CommandResult run(List<String> arguments) throws IOException, InterruptedException;

	/**
	 * Runs a command and returns its output.
	 *
	 * @param arguments the command-line arguments to pass to the executable
	 * @param stdin     the bytes to pass into the command's stdin stream
	 * @return the output of the command
	 * @throws IOException          if the executable could not be found
	 * @throws InterruptedException if the thread was interrupted before the operation completed
	 */
	CommandResult run(List<String> arguments, ByteBuffer stdin) throws IOException, InterruptedException;

	/**
	 * @return a {@code BuildXParser}
	 */
	BuildXParser getBuildXParser();
}