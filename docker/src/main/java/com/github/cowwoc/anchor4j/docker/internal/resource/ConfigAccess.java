package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.Config;
import com.github.cowwoc.anchor4j.docker.resource.ConfigCreator;

import java.nio.ByteBuffer;

/**
 * Methods that expose non-public behavior or data of a swarm's configurations.
 */
public interface ConfigAccess
{
	/**
	 * Returns a reference to a swarm's configuration.
	 *
	 * @param client the client configuration
	 * @param id     the config's ID
	 * @param name   the config's name
	 * @param value  the config's value
	 * @return the {@code Config}
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain whitespace.</li>
	 *                                    <li>the {@code name} is empty.</li>
	 *                                  </ul>
	 */
	Config get(InternalDocker client, String id, String name, ByteBuffer value);

	/**
	 * Returns a config creator.
	 *
	 * @param client the client configuration
	 * @return a config creator
	 */
	ConfigCreator create(InternalDocker client);
}