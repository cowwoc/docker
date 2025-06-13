package com.github.cowwoc.anchor4j.docker.test.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.client.Processes;
import com.github.cowwoc.anchor4j.docker.client.Docker;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.resource.Container;
import com.github.cowwoc.anchor4j.docker.resource.Container.Status;
import com.github.cowwoc.anchor4j.docker.resource.ContainerElement;
import com.github.cowwoc.anchor4j.docker.resource.ContainerLogGetter.LogStreams;
import com.github.cowwoc.anchor4j.docker.test.IntegrationTestContainer;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.anchor4j.docker.test.resource.ImageIT.EXISTING_IMAGE;
import static com.github.cowwoc.anchor4j.docker.test.resource.ImageIT.MISSING_IMAGE;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;

public final class ContainerIT
{
	/**
	 * We assume that this container name will never exist.
	 */
	private static final String MISSING_CONTAINER = "ContainerIT.missing-container";
	/**
	 * A command that prevents the container from exiting.
	 */
	private static final String[] KEEP_ALIVE = {"tail", "-f", "/dev/null"};

	@Test
	public void create() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		String containerId = client.createContainer(imageId).create();
		Container container = client.getContainer(containerId);
		Status status = container.getStatus();
		requireThat(status, "status").isEqualTo(Status.CREATED);
		it.onSuccess();
	}

	@Test
	public void createMultipleAnonymous() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		String containerId1 = client.createContainer(imageId).create();
		Container container1 = client.getContainer(containerId1);
		String containerId2 = client.createContainer(imageId).create();
		Container container2 = client.getContainer(containerId2);
		requireThat(container1, "container1").isNotEqualTo(container2, "container2");
		it.onSuccess();
	}

	@Test(expectedExceptions = ResourceInUseException.class)
	public void createWithConflictingName() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		client.createContainer(imageId).name(it.getName()).create();
		try
		{
			client.createContainer(imageId).name(it.getName()).create();
		}
		catch (ResourceInUseException e)
		{
			it.onSuccess();
			throw e;
		}
	}

	@Test(expectedExceptions = ResourceNotFoundException.class)
	public void createMissing() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		try
		{
			client.createContainer(MISSING_IMAGE).create();
		}
		catch (ResourceNotFoundException e)
		{
			it.onSuccess();
			throw e;
		}
	}

	@Test
	public void listEmpty() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		List<ContainerElement> containers = client.listContainers();
		requireThat(containers, "containers").isEmpty();
		it.onSuccess();
	}

	@Test
	public void list() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		String containerId = client.createContainer(imageId).create();
		Container container = client.getContainer(containerId);
		List<ContainerElement> containers = client.listContainers();
		requireThat(containers, "containers").size().isEqualTo(1);
		ContainerElement element = containers.getFirst();
		requireThat(element.id(), "element.id()").isEqualTo(container.getId(), "container.getId()");
		requireThat(element.name(), "element.name()").isEqualTo(container.getName(), "container.getName()");
		it.onSuccess();
	}

	@Test
	public void get() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		String containerId = client.createContainer(imageId).create();
		Container container1 = client.getContainer(containerId);
		Container container2 = client.getContainer(container1.getId());
		requireThat(container1, "container1").isEqualTo(container2, "container2");
		it.onSuccess();
	}

	@Test
	public void getMissing() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Container container = client.getContainer(MISSING_CONTAINER);
		requireThat(container, "container").isNull();
		it.onSuccess();
	}

	@Test
	public void start() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		String containerId = client.createContainer(imageId).arguments(KEEP_ALIVE).create();
		client.startContainer(containerId).start();
		Container container = client.getContainer(containerId);
		Status status = container.getStatus();
		requireThat(status, "status").isEqualTo(Status.RUNNING);
		it.onSuccess();
	}

	@Test
	public void alreadyStarted() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		String containerId = client.createContainer(imageId).arguments(KEEP_ALIVE).create();
		client.startContainer(containerId).start();
		client.startContainer(containerId).start();
		Container container = client.getContainer(containerId);
		Status status = container.getStatus();
		requireThat(status, "status").isEqualTo(Status.RUNNING);
		it.onSuccess();
	}

	@Test(expectedExceptions = ResourceNotFoundException.class)
	public void startMissing() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		String containerId = client.createContainer(imageId).create();
		client.removeContainer(containerId).remove();
		try
		{
			client.startContainer(containerId).start();
		}
		catch (ResourceNotFoundException e)
		{
			it.onSuccess();
			throw e;
		}
	}

	@Test
	public void stop() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		String containerId = client.createContainer(imageId).arguments(KEEP_ALIVE).create();
		client.startContainer(containerId).start();
		client.stopContainer(containerId).stop();
		Container container = client.getContainer(containerId);
		Status status = container.getStatus();
		requireThat(status, "status").isEqualTo(Status.EXITED);
		it.onSuccess();
	}

	@Test
	public void alreadyStopped() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		String containerId = client.createContainer(imageId).arguments(KEEP_ALIVE).create();
		client.startContainer(containerId).start();
		client.stopContainer(containerId).stop();
		Container container = client.getContainer(containerId);
		Status status = container.getStatus();
		requireThat(status, "status").isEqualTo(Status.EXITED);
		it.onSuccess();
	}

	@Test(expectedExceptions = ResourceNotFoundException.class)
	public void stopMissing() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		String containerId = client.createContainer(imageId).create();
		client.startContainer(containerId).start();
		client.removeContainer(containerId).kill().removeAnonymousVolumes().remove();
		try
		{
			client.stopContainer(containerId).stop();
		}
		catch (ResourceNotFoundException e)
		{
			it.onSuccess();
			throw e;
		}
	}

	@Test
	public void waitUntilStopped() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();

		int expected = 3;
		String containerId = client.createContainer(imageId).arguments("sh", "-c",
			"\"sleep 3; exit " + expected + "\"").create();
		client.startContainer(containerId).start();

		// Make sure we begin waiting before the container has shut down
		Container container = client.getContainer(containerId);
		requireThat(container.getStatus(), "container.getStatus()").isNotEqualTo(Status.EXITED);
		int actual = client.waitUntilContainerStops(container.getId());
		requireThat(actual, "actual").isEqualTo(expected, "expected");
		it.onSuccess();
	}

	@Test
	public void waitUntilAlreadyStopped() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		int expected = 1;
		String containerId = client.createContainer(imageId).arguments("sh", "-c", "\"exit " + expected +
			"\"").create();
		client.startContainer(containerId).start();
		int actual = client.waitUntilContainerStops(containerId);
		requireThat(actual, "actual").isEqualTo(expected, "expected");
		actual = client.waitUntilContainerStops(containerId);
		requireThat(actual, "actual").isEqualTo(expected, "expected");
		it.onSuccess();
	}

	@Test(expectedExceptions = ResourceNotFoundException.class)
	public void waitUntilMissingContainerStopped() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		int expected = 1;
		String containerId = client.createContainer(imageId).arguments("sh", "-c", "\"exit " + expected + "\"").
			create();
		client.startContainer(containerId).start();
		client.stopContainer(containerId).stop();
		client.removeContainer(containerId).removeAnonymousVolumes().remove();
		try
		{
			client.waitUntilContainerStops(containerId);
		}
		catch (ResourceNotFoundException e)
		{
			it.onSuccess();
			throw e;
		}
	}

	@Test
	public void getContainerLogs() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String imageId = client.pullImage(EXISTING_IMAGE).pull();
		List<String> command = List.of("sh", "-c", "\"echo This is stdout; echo This is stderr >&2; exit 123\"");
		String containerId = client.createContainer(imageId).arguments(command).create();
		client.startContainer(containerId).start();
		LogStreams containerLogs = client.getContainerLogs(containerId).follow().stream();

		BlockingQueue<Throwable> exceptions = new LinkedBlockingQueue<>();
		StringJoiner stdoutJoiner = new StringJoiner("\n");
		StringJoiner stderrJoiner = new StringJoiner("\n");
		try (BufferedReader stdoutReader = containerLogs.getOutputReader();
		     BufferedReader stderrReader = containerLogs.getErrorReader())
		{
			Thread stdoutThread = Thread.startVirtualThread(() ->
				Processes.consume(stdoutReader, exceptions, stdoutJoiner::add));
			Thread stderrThread = Thread.startVirtualThread(() ->
				Processes.consume(stderrReader, exceptions, stderrJoiner::add));

			// We have to invoke Thread.join() to ensure that all the data is read. Blocking on Process.waitFor()
			// does not guarantee this.
			stdoutThread.join();
			stderrThread.join();
			int exitCode = client.waitUntilContainerStops(containerId);
			String stdout = stdoutJoiner.toString();
			String stderr = stderrJoiner.toString();
			CommandResult result = new CommandResult(command, System.getProperty("user.dir"), stdout, stderr,
				exitCode);
			requireThat(stdout, "stdout").withContext(result, "result").isEqualTo("This is stdout");
			requireThat(stderr, "stderr").withContext(result, "result").isEqualTo("This is stderr");
			requireThat(exitCode, "exitCode").withContext(result, "result").isEqualTo(123);
		}
		it.onSuccess();
	}
}