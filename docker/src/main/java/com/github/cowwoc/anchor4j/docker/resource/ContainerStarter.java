package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Starts a container.
 */
public final class ContainerStarter
{
	private final InternalDocker client;
	private final String id;
	private final Logger log = LoggerFactory.getLogger(ContainerStarter.class);

	/**
	 * Creates a container starter.
	 *
	 * @param client the client configuration
	 * @param id     the container's ID or name
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	ContainerStarter(InternalDocker client, String id)
	{
		assert that(client, "client").isNotNull().elseThrow();
		client.validateContainerIdOrName(id, "id");
		this.client = client;
		this.id = id;
	}

	/**
	 * Starts a container. If the container is already started, this method has no effect.
	 *
	 * @throws ResourceNotFoundException if the image or container no longer exist
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws InterruptedException      if the thread is interrupted before the operation completes. This can
	 *                                   happen due to shutdown signals.
	 */
	public void start() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/container/start/
		List<String> arguments = List.of("container", "start", id);
		CommandResult result = client.run(arguments);
		client.getContainerParser().start(result);
	}

	/**
	 * Starts the container and attaches its streams and exit code. If the container is already started, this
	 * method has no effect. If the operation fails, {@code stderr} will return an error message and
	 * {@link ContainerStreams#waitFor()} will return a non-zero exit code.
	 *
	 * @param attachInput  {@code true} to attach the {@code stdin} stream
	 * @param attachOutput {@code true} to attach the {@code stdout}, {@code stderr} streams and exit code
	 * @return the streams
	 * @throws IllegalArgumentException if {@code attachInput} and {@code attachOutput} are both {@code false}
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 */
	public ContainerStreams startAndAttachStreams(boolean attachInput, boolean attachOutput) throws IOException
	{
		// https://docs.docker.com/reference/cli/docker/container/start/
		List<String> arguments = new ArrayList<>(5);
		arguments.add("container");
		arguments.add("start");
		if (attachOutput)
			arguments.add("--attach");
		if (attachInput)
			arguments.add("--interactive");
		arguments.add(id);

		ProcessBuilder processBuilder = client.getProcessBuilder(arguments);
		log.debug("Running: {}", processBuilder.command());
		Process process = processBuilder.start();
		return new ContainerStreams(process);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ContainerStarter.class).
			toString();
	}

	/**
	 * A container's stdin, stdout, and stderr streams.
	 */
	public static final class ContainerStreams implements AutoCloseable
	{
		private final Process process;
		private final OutputStream stdin;
		private final InputStream stdout;
		private final InputStream stderr;

		/**
		 * Creates a container's streams.
		 *
		 * @param process the docker process
		 */
		public ContainerStreams(Process process)
		{
			this.process = process;
			this.stdin = process.getOutputStream();
			this.stdout = process.getInputStream();
			this.stderr = process.getErrorStream();
		}

		/**
		 * Returns the standard input stream of the container if
		 * {@link #startAndAttachStreams(boolean, boolean) attachInput} is {@code true} or the docker command if
		 * it is {@code false}.
		 *
		 * @return the stream
		 */
		public OutputStream getStdin()
		{
			return stdin;
		}

		/**
		 * Returns the standard output stream of the container if
		 * {@link #startAndAttachStreams(boolean, boolean) attachOutput} is {@code true} or the docker command if
		 * it is {@code false}.
		 *
		 * @return the stream
		 */
		public InputStream getStdout()
		{
			return stdout;
		}

		/**
		 * Returns the standard error stream of the container if
		 * {@link #startAndAttachStreams(boolean, boolean) attachOutput} is {@code true} or the docker command if
		 * it is {@code false}.
		 *
		 * @return the stream
		 */
		public InputStream getStderr()
		{
			return stderr;
		}

		/**
		 * Blocks until the operation completes.
		 *
		 * @return the exit code of the container if {@link #startAndAttachStreams(boolean, boolean) attachOutput}
		 * 	is {@code true} or the docker command if it is {@code false}
		 * @throws InterruptedException if the thread is interrupted before the operation completes
		 */
		public int waitFor() throws InterruptedException
		{
			return process.waitFor();
		}

		/**
		 * Releases the streams.
		 *
		 * @throws IOException if an I/O error occurs while closing the streams
		 */
		@Override
		@SuppressWarnings("EmptyTryBlock")
		public void close() throws IOException
		{
			try (stdin; stdout; stderr)
			{
			}
		}
	}
}