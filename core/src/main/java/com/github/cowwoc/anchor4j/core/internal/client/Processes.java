package com.github.cowwoc.anchor4j.core.internal.client;

import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
	 * Discards the remaining data from the given reader and closes it.
	 * <p>
	 * This is typically used to drain a process's output or error reade to avoid deadlocks when the process
	 * writes more data than the reader's buffer can hold.
	 *
	 * @param reader the reader
	 * @param log    the logger used to record any exceptions that are thrown while reading from the reader
	 */
	public static void discard(BufferedReader reader, Logger log)
	{
		try (reader)
		{
			while (true)
			{
				String line = reader.readLine();
				if (line == null)
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
		catch (WrappedCheckedException e)
		{
			exceptions.add(e.getCause());
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
	public static Path getWorkingDirectory(ProcessBuilder processBuilder)
	{
		File workingDirectory = processBuilder.directory();
		if (workingDirectory != null)
			return workingDirectory.toPath();
		return Path.of(System.getProperty("user.dir"));
	}

	private Processes()
	{
	}
}