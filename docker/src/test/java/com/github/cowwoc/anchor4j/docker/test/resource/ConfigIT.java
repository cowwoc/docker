package com.github.cowwoc.anchor4j.docker.test.resource;

import com.github.cowwoc.anchor4j.docker.client.Docker;
import com.github.cowwoc.anchor4j.docker.exception.NotSwarmManagerException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.resource.Config;
import com.github.cowwoc.anchor4j.docker.resource.ConfigElement;
import com.github.cowwoc.anchor4j.docker.resource.SwarmCreator.WelcomePackage;
import com.github.cowwoc.anchor4j.docker.test.IntegrationTestContainer;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;

public final class ConfigIT
{
	@Test
	public void create() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		client.createSwarm().create();

		String value = "key=value";
		Config config = client.createConfig().create(it.getName(), value);
		requireThat(config.getValueAsString(), "config.getValueAsString").isEqualTo(value, "value");
		it.onSuccess();
	}

	@Test(expectedExceptions = NotSwarmManagerException.class)
	public void createNotSwarmManager() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		WelcomePackage welcomePackage = manager.getClient().createSwarm().create();

		IntegrationTestContainer worker = new IntegrationTestContainer("worker");
		worker.getClient().joinSwarm().join(welcomePackage.workerJoinToken());
		try
		{
			worker.getClient().createConfig().create(manager.getName(), "key=value");
		}
		catch (NotSwarmManagerException e)
		{
			manager.onSuccess();
			worker.onSuccess();
			throw e;
		}
	}

	@Test(expectedExceptions = ResourceInUseException.class)
	public void createExistingConfig() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		client.createSwarm().create();

		client.createConfig().create(it.getName(), "key=value");
		try
		{
			client.createConfig().create(it.getName(), "key=value");
		}
		catch (ResourceInUseException e)
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
		client.createSwarm().create();

		List<ConfigElement> configs = client.listConfigs();
		requireThat(configs, "configs").isEmpty();
		it.onSuccess();
	}

	@Test
	public void list() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		client.createSwarm().create();

		Config config = client.createConfig().create(it.getName(), "key=value");
		List<ConfigElement> configs = client.listConfigs();
		requireThat(configs, "configs").isEqualTo(List.of(new ConfigElement(config.getId(), config.getName())));
		it.onSuccess();
	}

	@Test(expectedExceptions = NotSwarmManagerException.class)
	public void listNotSwarmManager() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		try
		{
			client.listConfigs();
		}
		catch (NotSwarmManagerException e)
		{
			it.onSuccess();
			throw e;
		}
	}

	@Test
	public void get() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		client.createSwarm().create();
		Config expected = client.createConfig().create(it.getName(), "key=value");
		Config actual = client.getConfig(expected.getId());
		requireThat(actual, "actual").isEqualTo(expected, "expected");
		it.onSuccess();
	}

	@Test(expectedExceptions = NotSwarmManagerException.class)
	public void getNotSwarmManager() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		client.createSwarm().create();
		Config expected = client.createConfig().create(it.getName(), "key=value");
		client.leaveSwarm().force().leave();

		try
		{
			client.getConfig(expected.getId());
		}
		catch (NotSwarmManagerException e)
		{
			it.onSuccess();
			throw e;
		}
	}

	@Test
	public void getMissingConfig() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		client.createSwarm().create();
		Config actual = client.getConfig("missing");
		requireThat(actual, "actual").isNull();
		it.onSuccess();
	}
}