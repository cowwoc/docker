package com.github.cowwoc.anchor4j.core.internal.client;

import java.util.List;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Represents the result of executing a command.
 *
 * @param command          the command that was executed
 * @param workingDirectory the directory that the command was invoked from
 * @param stdout           the stdout output of the command
 * @param stderr           the stderr output of the command
 * @param exitCode         the exit code returned by the command
 */
public record CommandResult(List<String> command, String workingDirectory, String stdout, String stderr,
                            int exitCode)
{
	/**
	 * @param command          the command that was executed
	 * @param workingDirectory the directory that the command was invoked from
	 * @param stdout           the stdout output of the command
	 * @param stderr           the stderr output of the command
	 * @param exitCode         the exit code returned by the command
	 */
	public CommandResult
	{
		assert that(command, "command").isNotNull().elseThrow();
		assert that(stdout, "stdout").isNotNull().elseThrow();
		assert that(stderr, "stderr").isNotNull().elseThrow();
	}

	/**
	 * Returns an AssertionError indicating that the command returned an unexpected response.
	 *
	 * @return an explanation of the failure
	 */
	public AssertionError unexpectedResponse()
	{
		return new AssertionError(command + " returned an unexpected response.\n" +
			"exitCode   : " + exitCode + ".\n" +
			"stdout     : " + stdout + "\n" +
			"stderr     : " + stderr + "\n" +
			"directory  : " + workingDirectory);
	}
}