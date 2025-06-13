package com.github.cowwoc.anchor4j.docker.test.resource;

import com.github.cowwoc.anchor4j.docker.exception.NotSwarmManagerException;
import com.github.cowwoc.anchor4j.docker.resource.SwarmCreator.WelcomePackage;
import com.github.cowwoc.anchor4j.docker.resource.Task;
import com.github.cowwoc.anchor4j.docker.test.IntegrationTestContainer;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.anchor4j.docker.test.resource.ImageIT.EXISTING_IMAGE;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;

public final class ServiceIT
{
	@Test
	public void createServiceFromManager() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		WelcomePackage welcomePackage = manager.getClient().createSwarm().create();

		IntegrationTestContainer worker = new IntegrationTestContainer("worker");
		worker.getClient().joinSwarm().join(welcomePackage.workerJoinToken());
		String id = manager.getClient().createService(EXISTING_IMAGE).arguments("sleep", "2").
			updateMonitor(Duration.ofSeconds(1)).create();
		requireThat(id, "id").isNotNull();
		manager.onSuccess();
		worker.onSuccess();
	}

	@Test(expectedExceptions = NotSwarmManagerException.class)
	public void createServiceFromWorker() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		WelcomePackage welcomePackage = manager.getClient().createSwarm().create();

		IntegrationTestContainer worker = new IntegrationTestContainer("worker");
		worker.getClient().joinSwarm().join(welcomePackage.workerJoinToken());
		try
		{
			worker.getClient().createService(EXISTING_IMAGE).arguments("sleep", "2").
				updateMonitor(Duration.ofSeconds(1)).create();
		}
		catch (NotSwarmManagerException e)
		{
			manager.onSuccess();
			worker.onSuccess();
			throw e;
		}
	}

	@Test
	public void listTasksByServiceFromManager() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		WelcomePackage welcomePackage = manager.getClient().createSwarm().create();

		IntegrationTestContainer worker = new IntegrationTestContainer("worker");
		worker.getClient().joinSwarm().join(welcomePackage.workerJoinToken());
		String serviceId = manager.getClient().createService(EXISTING_IMAGE).arguments("sleep", "2").
			updateMonitor(Duration.ofSeconds(1)).create();
		List<Task> tasksByService = manager.getClient().listTasksByService(serviceId);
		requireThat(tasksByService, "tasksByService").isNotEmpty();
		manager.onSuccess();
		worker.onSuccess();
	}

	@Test(expectedExceptions = NotSwarmManagerException.class)
	public void listTasksByServiceFromWorker() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		WelcomePackage welcomePackage = manager.getClient().createSwarm().create();

		IntegrationTestContainer worker = new IntegrationTestContainer("worker");
		worker.getClient().joinSwarm().join(welcomePackage.workerJoinToken());
		String serviceId = manager.getClient().createService(EXISTING_IMAGE).arguments("sleep", "2").
			updateMonitor(Duration.ofSeconds(1)).create();
		try
		{
			worker.getClient().listTasksByService(serviceId);
		}
		catch (NotSwarmManagerException e)
		{
			manager.onSuccess();
			worker.onSuccess();
			throw e;
		}
	}

	@Test
	@SuppressWarnings("BusyWait")
	public void listTasksByNodeFromManager() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		WelcomePackage welcomePackage = manager.getClient().createSwarm().create();

		IntegrationTestContainer worker = new IntegrationTestContainer("worker");
		worker.getClient().joinSwarm().join(welcomePackage.workerJoinToken());
		manager.getClient().createService(EXISTING_IMAGE).arguments("sleep", "10").
			updateMonitor(Duration.ofSeconds(1)).
			runOnce().
			create();
		String workerNodeId = worker.getClient().getNodeId();
		List<Task> tasksByNode = manager.getClient().listTasksByNode(workerNodeId);
		Instant deadline = Instant.now().plusSeconds(10);
		while (tasksByNode.isEmpty())
		{
			Instant now = Instant.now();
			if (now.isAfter(deadline))
				break;
			Thread.sleep(100);
			tasksByNode = manager.getClient().listTasksByNode();
		}
		requireThat(tasksByNode, "tasksByNode").isNotEmpty();
		manager.onSuccess();
		worker.onSuccess();
	}

	@Test(expectedExceptions = NotSwarmManagerException.class)
	public void listTasksByNodeFromWorker() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		WelcomePackage welcomePackage = manager.getClient().createSwarm().create();

		IntegrationTestContainer worker = new IntegrationTestContainer("worker");
		worker.getClient().joinSwarm().join(welcomePackage.workerJoinToken());
		manager.getClient().createService(EXISTING_IMAGE).arguments("sleep", "2").
			updateMonitor(Duration.ofSeconds(1)).create();
		try
		{
			worker.getClient().listTasksByNode();
		}
		catch (NotSwarmManagerException e)
		{
			manager.onSuccess();
			worker.onSuccess();
			throw e;
		}
	}
}