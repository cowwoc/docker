package com.github.cowwoc.anchor4j.buildx.client;

import com.github.cowwoc.anchor4j.buildx.internal.client.DefaultBuildX;
import com.github.cowwoc.anchor4j.core.client.Client;

import java.io.IOException;
import java.nio.file.Path;

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
	 * @throws IOException if an I/O error occurs while reading file attributes
	 */
	static BuildX connect() throws IOException
	{
		return new DefaultBuildX();
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