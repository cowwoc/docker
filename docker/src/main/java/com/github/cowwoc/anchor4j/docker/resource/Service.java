package com.github.cowwoc.anchor4j.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.internal.resource.ServiceAccess;
import com.github.cowwoc.anchor4j.docker.internal.resource.SharedSecrets;
import com.github.cowwoc.anchor4j.docker.resource.Task.State;

/**
 * Represents a high-level definition of a task or application you want to run in a Swarm. It defines:
 * <ul>
 * <li>The desired state, such as the number of replicas (containers).</li>
 * <li>The container image to use.</li>
 * <li>The command to run.</li>
 * <li>Ports to expose.</li>
 * <li>Update and restart policies, etc.</li>
 * </ul>
 */
public final class Service
{
	static
	{
		SharedSecrets.setServiceAccess(new ServiceAccess()
		{
			@Override
			public ServiceCreator createService(InternalDocker client, String imageId)
			{
				return new ServiceCreator(client, imageId);
			}

			@Override
			public Task getTask(String id, String name, State state)
			{
				return new Task(id, name, state);
			}

			@Override
			public State getStateFromJson(JsonNode json)
			{
				return State.fromJson(json);
			}
		});
	}
}
