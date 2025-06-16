package com.github.cowwoc.anchor4j.core.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * An abstract adapter for {@code BuildListener} that provides no-op implementations of all callback methods.
 * <p>
 * This allows subclasses to override only the methods relevant to their use-case.
 */
@SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
public abstract class BuildListenerAdapter implements BuildListener
{
	private WaitFor waitFor;

	@Override
	public void buildStarted(BufferedReader stdoutReader, BufferedReader stderrReader, WaitFor waitFor)
	{
		this.waitFor = waitFor;
	}

	@Override
	public Output waitUntilBuildCompletes() throws InterruptedException
	{
		int exitCode = waitFor.apply();
		return new Output("N/A", "N/A", exitCode);
	}

	@Override
	public void buildPassed()
	{
	}

	@Override
	public void buildFailed(List<String> command, Path workingDirectory, int exitCode) throws IOException
	{
	}

	@Override
	public void buildCompleted() throws IOException
	{
	}
}