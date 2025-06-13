package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.Container;
import com.github.cowwoc.anchor4j.docker.resource.Container.HostConfiguration;
import com.github.cowwoc.anchor4j.docker.resource.Container.NetworkConfiguration;
import com.github.cowwoc.anchor4j.docker.resource.Container.Status;
import com.github.cowwoc.anchor4j.docker.resource.ContainerCreator;
import com.github.cowwoc.anchor4j.docker.resource.ContainerLogGetter;
import com.github.cowwoc.anchor4j.docker.resource.ContainerRemover;
import com.github.cowwoc.anchor4j.docker.resource.ContainerStarter;
import com.github.cowwoc.anchor4j.docker.resource.ContainerStopper;
import com.github.cowwoc.anchor4j.docker.resource.Image;

/**
 * Methods that expose non-public behavior or data of containers.
 */
public interface ContainerAccess
{
	/**
	 * Returns a reference to a container.
	 *
	 * @param client               the client configuration
	 * @param id                   the ID of the container
	 * @param name                 the name of the container
	 * @param hostConfiguration    the container's host configuration
	 * @param networkConfiguration the container's network configuration
	 * @param status               the container's status
	 * @return the {@code Container}
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain whitespace.</li>
	 *                                    <li>{@code id} is empty.</li>
	 *                                  </ul>
	 */
	Container get(InternalDocker client, String id, String name, HostConfiguration hostConfiguration,
		NetworkConfiguration networkConfiguration, Status status);

	/**
	 * Returns a reference to a container's logs.
	 *
	 * @param client the client configuration
	 * @param id     the ID of the container
	 * @return the {@code ContainerLogs}
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id} contains whitespace or is empty
	 */
	ContainerLogGetter getLogs(InternalDocker client, String id);

	/**
	 * Returns a container creator.
	 *
	 * @param client  the client configuration
	 * @param imageId the image ID or {@link Image reference} to create the container from
	 * @return a container creator
	 * @throws NullPointerException     if {@code imageId} is null
	 * @throws IllegalArgumentException if {@code imageId}'s format is invalid
	 */
	ContainerCreator create(InternalDocker client, String imageId);

	/**
	 * Returns a container starter.
	 *
	 * @param client the client configuration
	 * @param id     the container's ID or name
	 * @return the container starter
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	ContainerStarter start(InternalDocker client, String id);

	/**
	 * Returns a container stopper.
	 *
	 * @param client the client configuration
	 * @param id     the container's ID or name
	 * @return the container stopper
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	ContainerStopper stop(InternalDocker client, String id);

	/**
	 * Returns a container remover.
	 *
	 * @param client the client configuration
	 * @param id     the container's ID or name
	 * @return the container remover
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	ContainerRemover remove(InternalDocker client, String id);

	/**
	 * Looks up a container's status from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the container status
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	Status getStatus(JsonNode json);
}