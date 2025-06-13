package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.SwarmCreator;
import com.github.cowwoc.anchor4j.docker.resource.SwarmJoiner;
import com.github.cowwoc.anchor4j.docker.resource.SwarmLeaver;

/**
 * Methods that expose non-public behavior or data of a swarm.
 */
public interface SwarmAccess
{
	/**
	 * Returns a swarm creator.
	 *
	 * @param client the client configuration
	 * @return a swarm creator
	 */
	SwarmCreator create(InternalDocker client);

	/**
	 * Returns a swarm joiner.
	 *
	 * @param client the client configuration
	 * @return a swarm joiner
	 */
	SwarmJoiner join(InternalDocker client);

	/**
	 * Returns a swarm leaver.
	 *
	 * @param client the client configuration
	 * @return a swarm leaver
	 */
	SwarmLeaver leave(InternalDocker client);
}