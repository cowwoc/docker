package com.github.cowwoc.anchor4j.core.internal.client;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.cowwoc.anchor4j.core.internal.resource.BuildXParser;
import com.github.cowwoc.anchor4j.core.internal.resource.SharedSecrets;
import com.github.cowwoc.anchor4j.core.internal.util.Exceptions;
import com.github.cowwoc.anchor4j.core.resource.Builder;
import com.github.cowwoc.anchor4j.core.resource.Builder.Status;
import com.github.cowwoc.anchor4j.core.resource.BuilderCreator;
import com.github.cowwoc.anchor4j.core.resource.ImageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.cowwoc.anchor4j.core.internal.resource.AbstractParser.CONNECTION_RESET;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Common implementation shared by all {@code InternalClient}s.
 */
@SuppressWarnings("PMD.MoreThanOneLogger")
public abstract class AbstractInternalClient implements InternalClient
{
	private final static ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);
	// Based on https://github.com/distribution/reference/blob/727f80d42224f6696b8e1ad16b06aadf2c6b833b/regexp.go#L85
	private final static Pattern ID_VALIDATOR = Pattern.compile("[a-f0-9]{64}");
	// Based on https://github.com/moby/moby/blob/13879e7b496d14fb0724719c49c858731c9e7f60/daemon/names/names.go#L6
	private final static Pattern NAME_VALIDATOR = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_.-]+");
	/**
	 * The path of the command-line executable.
	 */
	protected final Path executable;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	@SuppressWarnings("this-escape")
	private final BuildXParser buildXParser = new BuildXParser(this);
	private final Logger log = LoggerFactory.getLogger(AbstractInternalClient.class);
	private final Logger stdoutLog = LoggerFactory.getLogger(AbstractInternalClient.class.getName() +
		".stdout");
	private final Logger stderrLog = LoggerFactory.getLogger(AbstractInternalClient.class.getName() +
		".stderr");

	/**
	 * Creates a new instance.
	 *
	 * @param executable the path of the command-line executable
	 * @throws NullPointerException     if {@code executable} is null
	 * @throws IllegalArgumentException if the path referenced by {@code executable} does not exist or is not an
	 *                                  executable file
	 * @throws IOException              if an I/O error occurs while reading {@code executable}'s attributes
	 */
	public AbstractInternalClient(Path executable) throws IOException
	{
		requireThat(executable, "executable").exists().isRegularFile().isExecutable();
		this.executable = executable;
	}

	@Override
	public JsonMapper getJsonMapper()
	{
		return jsonMapper;
	}

	@Override
	public CommandResult run(List<String> arguments) throws IOException, InterruptedException
	{
		return run(arguments, EMPTY_BYTE_BUFFER);
	}

	@SuppressWarnings("BusyWait")
	@Override
	public CommandResult run(List<String> arguments, ByteBuffer stdin) throws IOException, InterruptedException
	{
		ProcessBuilder processBuilder = getProcessBuilder(arguments);
		Instant deadline = Instant.now().plusSeconds(10);
		while (true)
		{
			log.debug("Running: {}", processBuilder.command());
			Process process = processBuilder.start();
			StringJoiner stdoutJoiner = new StringJoiner("\n");
			StringJoiner stderrJoiner = new StringJoiner("\n");
			BlockingQueue<Throwable> exceptions = new LinkedBlockingQueue<>();

			writeIntoStdin(stdin, process, exceptions);
			Thread parentThread = Thread.currentThread();
			try (BufferedReader stdoutReader = process.inputReader();
			     BufferedReader stderrReader = process.errorReader())
			{
				Thread stdoutThread = Thread.startVirtualThread(() ->
				{
					stdoutLog.info("Spawned by thread \"{}\"", parentThread.getName());
					Processes.consume(stdoutReader, exceptions, line ->
					{
						stdoutJoiner.add(line);
						stdoutLog.info(line);
					});
				});
				Thread stderrThread = Thread.startVirtualThread(() ->
				{
					stderrLog.info("Spawned by thread \"{}\"", parentThread.getName());
					Processes.consume(stderrReader, exceptions, line ->
					{
						stderrJoiner.add(line);
						stderrLog.info(line);
					});
				});

				// We have to invoke Thread.join() to ensure that all the data is read. Blocking on Process.waitFor()
				// does not guarantee this.
				stdoutThread.join();
				stderrThread.join();
				int exitCode = process.waitFor();
				IOException exception = Exceptions.combineAsIOException(exceptions);
				if (exception != null)
					throw exception;
				String stdout = stdoutJoiner.toString();
				String stderr = stderrJoiner.toString();

				Path workingDirectory = Processes.getWorkingDirectory(processBuilder);
				CommandResult result = new CommandResult(processBuilder.command(), workingDirectory, stdout, stderr,
					exitCode);
				if (shouldRetry(result))
				{
					Instant now = Instant.now();
					if (now.isAfter(deadline))
						throw result.unexpectedResponse();
					Thread.sleep(100);
					continue;
				}
				return result;
			}
		}
	}

	/**
	 * @param result the result of executing a command
	 * @return {@code true} if the command should be repeated
	 */
	protected boolean shouldRetry(CommandResult result)
	{
		if (result.exitCode() == 0)
			return false;
		String stderr = result.stderr();
		Matcher matcher = CONNECTION_RESET.matcher(stderr);
		return matcher.matches() || (Processes.isWindows() && stderr.endsWith("being used by another process."));
	}

	/**
	 * Writes data into a process' {@code stdin} stream.
	 *
	 * @param bytes      the bytes to write
	 * @param process    the process to write into
	 * @param exceptions the queue to add any thrown exceptions to
	 */
	private static void writeIntoStdin(ByteBuffer bytes, Process process, BlockingQueue<Throwable> exceptions)
	{
		if (bytes.hasRemaining())
		{
			Thread.startVirtualThread(() ->
			{
				try (OutputStream os = process.getOutputStream();
				     WritableByteChannel stdin = Channels.newChannel(os))
				{
					while (bytes.hasRemaining())
						stdin.write(bytes);
				}
				catch (IOException | RuntimeException e)
				{
					exceptions.add(e);
				}
			});
		}
	}

	@Override
	public void validateName(String value, String name)
	{
		assert that(name, "name").isNotNull().elseThrow();
		requireThat(value, name).isNotNull();
		if (!NAME_VALIDATOR.matcher(value).matches())
			throw getNameException(value, name);
	}

	/**
	 * @param value the value of the name
	 * @param name  the name of the value parameter
	 * @return the exception to throw if a name's format is invalid
	 */
	private IllegalArgumentException getNameException(String value, String name)
	{
		return new IllegalArgumentException(name + " must begin with a letter or number and may include " +
			"letters, numbers, underscores, periods, or hyphens. No other characters are allowed.\n" +
			"Value: " + value);
	}

	@Override
	public void validateImageReference(String value, String name)
	{
		assert that(name, "name").isNotNull().elseThrow();
		requireThat(value, name).isNotNull();
		ImageReferenceValidator.validate(value, name);
	}

	@Override
	public void validateImageIdOrReference(String value, String name)
	{
		assert that(name, "name").isNotNull().elseThrow();
		requireThat(value, name).isNotNull();
		if (ID_VALIDATOR.matcher(value).matches())
			return;
		ImageReferenceValidator.validate(value, name);
	}

	@Override
	public void validateContainerIdOrName(String value, String name)
	{
		assert that(name, "name").isNotNull().elseThrow();
		requireThat(value, name).isNotNull();
		if (ID_VALIDATOR.matcher(value).matches())
			return;
		if (!NAME_VALIDATOR.matcher(value).matches())
			throw getNameException(value, name);
	}

	@Override
	public BuildXParser getBuildXParser()
	{
		return buildXParser;
	}

	@Override
	public Builder getBuilder() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/buildx/inspect/
		List<String> arguments = new ArrayList<>(2);
		arguments.add("buildx");
		arguments.add("inspect");
		CommandResult result = run(arguments);
		return getBuildXParser().get(result);
	}

	@Override
	public BuilderCreator createBuilder()
	{
		return SharedSecrets.createBuilder(this);
	}

	@Override
	public Builder getBuilder(String name) throws IOException, InterruptedException
	{
		requireThat(name, "name").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/buildx/inspect/
		List<String> arguments = new ArrayList<>(3);
		arguments.add("buildx");
		arguments.add("inspect");
		arguments.add(name);
		CommandResult result = run(arguments);
		return getBuildXParser().get(result);
	}

	@Override
	@SuppressWarnings("BusyWait")
	public Builder waitUntilBuilderIsReady(Instant deadline)
		throws IOException, InterruptedException, TimeoutException
	{
		while (true)
		{
			Builder builder = getBuilder();
			if (builder != null && builder.getStatus() == Status.RUNNING)
				return builder;
			Instant now = Instant.now();
			if (builder == null)
			{
				log.debug("builder == null");
				if (now.isAfter(deadline))
					throw new TimeoutException("Default builder not found");
			}
			else
			{
				log.debug("builder.status: {}", builder.getStatus());
				if (now.isAfter(deadline))
				{
					throw new TimeoutException("Default builder " + builder.getName() + " has a state of " +
						builder.getStatus());
				}
			}
			Thread.sleep(100);
		}
	}

	@Override
	public Set<String> getSupportedBuildPlatforms() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/buildx/inspect/
		List<String> arguments = List.of("buildx", "inspect");
		CommandResult result = run(arguments);
		return getBuildXParser().getSupportedBuildPlatforms(result);
	}

	@Override
	public ImageBuilder buildImage()
	{
		return SharedSecrets.buildImage(this);
	}
}