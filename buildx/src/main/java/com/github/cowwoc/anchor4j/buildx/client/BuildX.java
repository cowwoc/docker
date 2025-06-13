package com.github.cowwoc.anchor4j.buildx.client;

import com.github.cowwoc.anchor4j.buildx.internal.client.DefaultBuildX;
import com.github.cowwoc.anchor4j.core.client.Client;
import com.github.cowwoc.anchor4j.core.internal.util.Paths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A Docker BuildX client.
 */
public interface BuildX extends Client
{
	/**
	 * Creates a client that uses the {@code buildx} executable located in the {@code PATH} environment
	 * variable.
	 *
	 * @return a client
	 */
	static BuildX connect() throws IOException
	{
		return connect(getExecutableFromPath());
	}

	/**
	 * @return the path of the {@code buildx} or {@code docker} executable located in the {@code PATH}
	 * 	environment variable
	 */
	private static Path getExecutableFromPath() throws IOException
	{
		Path path = Paths.searchPath(List.of("buildx.exe", "docker-buildx.exe", "buildx", "docker-buildx"));
		if (path == null)
			path = Paths.searchPath(List.of("docker.exe", "docker"));
		if (path == null)
			throw new IOException("Could not find buildx or docker on the PATH");
		return path;
	}

	/**
	 * Creates a client that uses the specified executable.
	 *
	 * @param executable the path of the {@code buildx} or {@code docker} executable
	 * @return a client
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the path referenced by {@code executable} does not exist or is not a
	 *                                  file
	 * @throws IOException              if an I/O error occurs while reading {@code executable}'s attributes
	 */
	static BuildX connect(Path executable) throws IOException
	{
		return new DefaultBuildX(executable);
	}
}