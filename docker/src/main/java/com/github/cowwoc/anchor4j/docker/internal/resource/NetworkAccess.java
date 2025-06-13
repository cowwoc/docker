package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.Network;
import com.github.cowwoc.anchor4j.docker.resource.Network.Configuration;

import java.util.List;

/**
 * Methods that expose non-public behavior or data of a network.
 */
@FunctionalInterface
public interface NetworkAccess
{
	/**
	 * Returns a reference to a network.
	 *
	 * @param client         the client configuration
	 * @param id             the ID of the network
	 * @param name           the name of the network
	 * @param configurations the network configurations
	 * @return the network
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain whitespace or is empty
	 */
	Network get(InternalDocker client, String id, String name, List<Configuration> configurations);
}