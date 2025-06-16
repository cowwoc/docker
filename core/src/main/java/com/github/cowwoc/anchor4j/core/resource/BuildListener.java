package com.github.cowwoc.anchor4j.core.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A listener that observes and reacts to an image build process.
 */
public interface BuildListener
{
	/**
	 * Invoked after the build starts.
	 *
	 * @param stdoutReader the standard output stream of the command
	 * @param stderrReader the standard error stream of the command
	 * @param waitFor      a blocking operation that waits for the process to terminate and returns its exit
	 *                     code
	 * @throws NullPointerException if any of the arguments are null
	 */
	void buildStarted(BufferedReader stdoutReader, BufferedReader stderrReader, WaitFor waitFor);

	/**
	 * Waits until the build completes.
	 *
	 * @return the build's output
	 * @throws IOException          if an error occurs while reading the build's standard output or error
	 * @throws InterruptedException if the thread is interrupted before the operation completes streams
	 */
	Output waitUntilBuildCompletes() throws IOException, InterruptedException;

	/**
	 * Invoked after the build succeeds.
	 */
	void buildPassed();

	/**
	 * Invoked after the build fails.
	 *
	 * @param command          the command that was executed
	 * @param workingDirectory the directory that the command was executed from
	 * @param exitCode         the exit code returned by the build process
	 * @throws IOException if the build failure is expected
	 */
	void buildFailed(List<String> command, Path workingDirectory, int exitCode) throws IOException;

	/**
	 * Invoked after {@code buildSucceeded} or {@code buildFailed}.
	 *
	 * @throws IOException if an error occurs while closing the build's standard output or error streams
	 */
	void buildCompleted() throws IOException;

	/**
	 * The build's output.
	 *
	 * @param stdout   the full contents of the build's standard output stream
	 * @param stderr   the full contents of the build's standard error stream
	 * @param exitCode the exit code returned by the build process
	 */
	record Output(String stdout, String stderr, int exitCode)
	{
		/**
		 * Creates a new instance.
		 *
		 * @param stdout   the full contents of the build's standard output stream
		 * @param stderr   the full contents of the build's standard error stream
		 * @param exitCode the exit code returned by the build process
		 * @throws NullPointerException if any of the arguments are null
		 */
		public Output
		{
		}
	}
}