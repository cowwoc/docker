package com.github.cowwoc.anchor4j.buildx.internal.client;

import com.github.cowwoc.anchor4j.core.internal.client.AbstractInternalClient;
import com.github.cowwoc.anchor4j.core.internal.util.Paths;
import com.github.cowwoc.pouch.core.ConcurrentLazyReference;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The default implementation of {@code InternalBuildX}.
 */
public final class DefaultBuildX extends AbstractInternalClient
	implements InternalBuildX
{
	private static final ConcurrentLazyReference<Path> EXECUTABLE_FROM_PATH = ConcurrentLazyReference.create(
		() ->
		{
			Path path = Paths.searchPath(List.of("buildx.exe", "docker-buildx.exe", "buildx", "docker-buildx"));
			if (path == null)
				path = Paths.searchPath(List.of("docker.exe", "docker"));
			if (path == null)
				throw new UncheckedIOException(new IOException("Could not find buildx or docker on the PATH"));
			return path;
		});

	/**
	 * @return the path of the {@code buildx} or {@code docker} executable located in the {@code PATH}
	 * 	environment variable
	 */
	private static Path getExecutableFromPath() throws IOException
	{
		try
		{
			return EXECUTABLE_FROM_PATH.getValue();
		}
		catch (UncheckedIOException e)
		{
			throw e.getCause();
		}
	}

	private final boolean executableIsBuildX;

	/**
	 * Creates a client that uses the {@code buildx} executable located in the {@code PATH} environment
	 * variable.
	 *
	 * @throws IOException if an I/O error occurs while reading file attributes
	 */
	public DefaultBuildX() throws IOException
	{
		this(getExecutableFromPath());
	}

	/**
	 * Creates a client.
	 *
	 * @param executable the path of the {@code buildx} executable
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the path referenced by {@code executable} does not exist or is not an
	 *                                  executable file
	 */
	public DefaultBuildX(Path executable) throws IOException
	{
		super(executable);
		String filename = executable.getFileName().toString();
		this.executableIsBuildX = filename.contains("buildx");
	}

	@Override
	public ProcessBuilder getProcessBuilder(List<String> arguments)
	{
		List<String> command = new ArrayList<>(arguments.size() + 3);
		if (executableIsBuildX)
		{
			// Remove "buildx" from the arguments as it will be replaced by the executable
			arguments = arguments.subList(1, arguments.size());
		}
		command.add(executable.toString());
		command.addAll(arguments);
		return new ProcessBuilder(command);
	}
}
