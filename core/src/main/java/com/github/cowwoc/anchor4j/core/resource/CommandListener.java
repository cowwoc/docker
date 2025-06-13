package com.github.cowwoc.anchor4j.core.resource;

import java.io.IOException;

/**
 * A listener that observes and handles the execution of a command.
 */
@FunctionalInterface
public interface CommandListener
{
	/**
	 * Handles the execution of a Docker command. This method is expected to block until the process completes,
	 * and should throw an {@link IOException} if the command fails.
	 *
	 * @param processBuilder the {@code ProcessBuilder} used to launch the command
	 * @param process        the {@code Process} running the command
	 * @throws NullPointerException if any of the arguments are null
	 * @throws IOException          if the command execution fails
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	void accept(ProcessBuilder processBuilder, Process process) throws IOException, InterruptedException;
}