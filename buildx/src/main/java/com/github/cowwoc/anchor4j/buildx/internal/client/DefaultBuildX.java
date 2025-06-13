package com.github.cowwoc.anchor4j.buildx.internal.client;

import com.github.cowwoc.anchor4j.core.internal.client.AbstractInternalClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The default implementation of {@code InternalBuildX}.
 */
public final class DefaultBuildX extends AbstractInternalClient
	implements InternalBuildX
{
	private final boolean executableIsBuildX;

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
