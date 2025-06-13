package com.github.cowwoc.anchor4j.docker.test.resource;

import com.github.cowwoc.anchor4j.docker.client.Docker;
import com.github.cowwoc.anchor4j.docker.exception.AlreadySwarmMemberException;
import com.github.cowwoc.anchor4j.docker.exception.LastManagerException;
import com.github.cowwoc.anchor4j.docker.exception.NotSwarmManagerException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.resource.JoinToken;
import com.github.cowwoc.anchor4j.docker.resource.Node;
import com.github.cowwoc.anchor4j.docker.resource.Node.Type;
import com.github.cowwoc.anchor4j.docker.resource.SwarmCreator.WelcomePackage;
import com.github.cowwoc.anchor4j.docker.test.IntegrationTestContainer;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;

public final class SwarmIT
{
	@Test
	public void createSwarm() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		manager.getClient().createSwarm().create();
		manager.onSuccess();
	}

	@Test(expectedExceptions = AlreadySwarmMemberException.class)
	public void createSwarmAlreadyInSwarm() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		manager.getClient().createSwarm().create();
		try
		{
			manager.getClient().createSwarm().create();
		}
		catch (AlreadySwarmMemberException e)
		{
			manager.onSuccess();
			throw e;
		}
	}

	@Test
	public void joinSwarm() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		WelcomePackage welcomePackage = manager.getClient().createSwarm().create();

		IntegrationTestContainer worker = new IntegrationTestContainer("worker");
		worker.getClient().joinSwarm().join(welcomePackage.workerJoinToken());
		manager.onSuccess();
		worker.onSuccess();
	}

	@Test(expectedExceptions = AlreadySwarmMemberException.class)
	public void joinSwarmAlreadyJoined() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		WelcomePackage welcomePackage = manager.getClient().createSwarm().create();

		IntegrationTestContainer worker = new IntegrationTestContainer("worker");
		worker.getClient().joinSwarm().join(welcomePackage.workerJoinToken());
		try
		{
			worker.getClient().joinSwarm().join(welcomePackage.workerJoinToken());
		}
		catch (AlreadySwarmMemberException e)
		{
			manager.onSuccess();
			worker.onSuccess();
			throw e;
		}
	}

	@Test(expectedExceptions = NotSwarmManagerException.class)
	public void getNodeNotInSwarm() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer node = new IntegrationTestContainer();
		try
		{
			node.getClient().getNode();
		}
		catch (NotSwarmManagerException e)
		{
			node.onSuccess();
			throw e;
		}
	}

	@Test
	public void managerLeaveSwarm() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		Docker client = manager.getClient();
		client.createSwarm().create();
		client.leaveSwarm().force().leave();
		manager.onSuccess();
	}

	@Test
	public void demoteManagerBeforeLeaving() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager1 = new IntegrationTestContainer("manager1");
		WelcomePackage welcomePackage = manager1.getClient().createSwarm().create();
		JoinToken managerJoinToken = manager1.getClient().getManagerJoinToken();

		IntegrationTestContainer manager2 = new IntegrationTestContainer("manager2");
		manager2.getClient().joinSwarm().join(managerJoinToken);
		Node manager2AsNode = manager2.getClient().getNode();

		manager1.getClient().setNodeType(welcomePackage.nodeId(), Type.WORKER);
		manager1.getClient().leaveSwarm().leave();
		manager2AsNode = manager2AsNode.reload();

		requireThat(manager2.getClient().listManagerNodes(), "managerNodes").containsExactly(List.of(
			manager2AsNode.toNodeElement()));
		manager1.onSuccess();
		manager2.onSuccess();
	}

	@Test(expectedExceptions = LastManagerException.class)
	public void demoteLastManager() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		Docker client = manager.getClient();
		WelcomePackage welcomePackage = client.createSwarm().create();

		client.setNodeType(welcomePackage.nodeId(), Type.WORKER);
		try
		{
			client.leaveSwarm().leave();
		}
		catch (LastManagerException e)
		{
			manager.onSuccess();
			throw e;
		}
	}

	@Test(expectedExceptions = ResourceInUseException.class)
	public void managerLeaveSwarmWithoutForce() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		manager.getClient().createSwarm().create();
		try
		{
			manager.getClient().leaveSwarm().leave();
		}
		catch (ResourceInUseException e)
		{
			manager.onSuccess();
			throw e;
		}
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void joinSwarmInvalidToken() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		WelcomePackage welcomePackage = manager.getClient().createSwarm().create();
		JoinToken workerJoinToken = welcomePackage.workerJoinToken();
		workerJoinToken = new JoinToken(workerJoinToken.type(),
			workerJoinToken.token().substring(0, workerJoinToken.token().length() / 2),
			workerJoinToken.managerAddress());
		welcomePackage = new WelcomePackage(welcomePackage.nodeId(), workerJoinToken);
		manager.getClient().leaveSwarm().force().leave();

		IntegrationTestContainer worker = new IntegrationTestContainer("worker");
		try
		{
			worker.getClient().joinSwarm().join(welcomePackage.workerJoinToken());
		}
		catch (IllegalArgumentException e)
		{
			manager.onSuccess();
			worker.onSuccess();
			throw e;
		}
	}

	@Test(expectedExceptions = ConnectException.class)
	public void joinSwarmManagerIsDown() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer manager = new IntegrationTestContainer("manager");
		WelcomePackage welcomePackage = manager.getClient().createSwarm().create();
		manager.getClient().leaveSwarm().force().leave();

		IntegrationTestContainer worker = new IntegrationTestContainer("worker");
		try
		{
			worker.getClient().joinSwarm().join(welcomePackage.workerJoinToken());
		}
		catch (ConnectException e)
		{
			manager.onSuccess();
			worker.onSuccess();
			throw e;
		}
	}
}