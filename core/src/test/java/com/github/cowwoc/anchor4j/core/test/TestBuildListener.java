package com.github.cowwoc.anchor4j.core.test;

import com.github.cowwoc.anchor4j.core.resource.BuildListener;
import com.github.cowwoc.anchor4j.core.resource.WaitFor;

import java.io.BufferedReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A build listener that monitors which callback methods get invoked.
 */
public final class TestBuildListener implements BuildListener
{
	public final AtomicBoolean buildStarted = new AtomicBoolean();
	public final AtomicBoolean waitUntilBuildCompletes = new AtomicBoolean();
	public final AtomicBoolean buildSucceeded = new AtomicBoolean();
	public final AtomicBoolean buildFailed = new AtomicBoolean();
	public final AtomicBoolean buildCompleted = new AtomicBoolean();
	private WaitFor waitFor;

	@Override
	public void buildStarted(BufferedReader stdoutReader, BufferedReader stderrReader, WaitFor waitFor)
	{
		buildStarted.set(true);
		this.waitFor = waitFor;
	}

	@Override
	public Output waitUntilBuildCompletes() throws InterruptedException
	{
		waitUntilBuildCompletes.set(true);
		int exitCode = waitFor.apply();
		return new Output("stdout", "stderr", exitCode);
	}

	@Override
	public void buildPassed()
	{
		buildSucceeded.set(true);
	}

	@Override
	public void buildFailed(List<String> command, Path workingDirectory, int exitCode)
	{
		buildFailed.set(true);
	}

	@Override
	public void buildCompleted()
	{
		buildCompleted.set(true);
	}
}