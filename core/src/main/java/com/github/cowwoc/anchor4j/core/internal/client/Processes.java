package com.github.cowwoc.anchor4j.core.internal.client;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * Helper functions for processes.
 */
public final class Processes
{
	private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).
		startsWith("windows");

	/**
	 * Indicates if the JVM is running on the Windows operating system.
	 *
	 * @return {@code true} on Windows; {@code false} otherwise
	 */
	public static boolean isWindows()
	{
		return IS_WINDOWS;
	}

	/**
	 * A command that does not return a value.
	 */
	@FunctionalInterface
	public interface RunnableCommand
	{
		/**
		 * Runs the task.
		 *
		 * @throws IOException if the command fails
		 */
		void run() throws IOException, InterruptedException;
	}

	/**
	 * Consumes and discards all remaining data from the given stream to prevent the associated process from
	 * blocking.
	 * <p>
	 * This is typically used to drain a process's output or error stream to avoid deadlocks when the process
	 * writes more data than the stream's buffer can hold.
	 *
	 * @param stream the stream
	 * @param log    the logger used to record any exceptions that are thrown while reading from the stream
	 */
	public static void discard(InputStream stream, Logger log)
	{
		try (stream)
		{
			byte[] buffer = new byte[10 * 1024];
			while (true)
			{
				int count = stream.read(buffer);
				if (count == -1)
					break;
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.error("", e);
		}
	}

	/**
	 * Consumes a stream's output.
	 *
	 * @param reader     the {@code BufferedReader}
	 * @param exceptions a queue to append any thrown exceptions to
	 * @param consumer   a function that consumes the stream's output, one line at a time
	 */
	public static void consume(BufferedReader reader, Queue<Throwable> exceptions, Consumer<String> consumer)
	{
		try (reader)
		{
			while (true)
			{
				String line = reader.readLine();
				if (line == null)
					break;
				consumer.accept(line);
			}
		}
		catch (IOException | RuntimeException e)
		{
			exceptions.add(e);
		}
	}

	/**
	 * Returns the working directory of a {@code ProcessBuilder}.
	 *
	 * @param processBuilder the {@code ProcessBuilder}
	 * @return the working directory
	 */
	public static String getWorkingDirectory(ProcessBuilder processBuilder)
	{
		File workingDirectory = processBuilder.directory();
		if (workingDirectory != null)
			return workingDirectory.getAbsolutePath();
		return System.getProperty("user.dir");
	}

	private Processes()
	{
	}
}