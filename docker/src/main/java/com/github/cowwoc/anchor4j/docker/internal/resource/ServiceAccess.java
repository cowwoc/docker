package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.Image;
import com.github.cowwoc.anchor4j.docker.resource.ServiceCreator;
import com.github.cowwoc.anchor4j.docker.resource.Task;
import com.github.cowwoc.anchor4j.docker.resource.Task.State;

/**
 * Methods that expose non-public behavior or data of a task running on a swarm node.
 */
public interface ServiceAccess
{
	/**
	 * Creates a service.
	 *
	 * @param client  the client configuration
	 * @param imageId the image ID or {@link Image reference} to create the service from
	 * @return a service creator
	 * @throws NullPointerException     if {@code imageId} is null
	 * @throws IllegalArgumentException if {@code imageId}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 */
	ServiceCreator createService(InternalDocker client, String imageId);

	/**
	 * Returns a reference to a task.
	 *
	 * @param id   the task's ID
	 * @param name the task's name
	 * @param state the task's state
	 * @return a task
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain whitespace or are empty
	 */
	Task getTask(String id, String name, State state);

	/**
	 * Looks up a task's {@code State} from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	State getStateFromJson(JsonNode json);
}