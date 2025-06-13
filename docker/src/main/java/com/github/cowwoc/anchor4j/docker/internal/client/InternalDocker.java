package com.github.cowwoc.anchor4j.docker.internal.client;

import com.github.cowwoc.anchor4j.core.internal.client.InternalClient;
import com.github.cowwoc.anchor4j.docker.client.Docker;
import com.github.cowwoc.anchor4j.docker.internal.resource.ConfigParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.ContainerParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.ContextParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.ImageParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.NetworkParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.NodeParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.ServiceParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.SwarmParser;

/**
 * The internals of a {@code Docker}.
 */
public interface InternalDocker extends InternalClient, Docker
{
	/**
	 * @return a {@code ContainerParser}
	 */
	ContainerParser getContainerParser();

	/**
	 * @return a {@code ConfigParser}
	 */
	ConfigParser getConfigParser();

	/**
	 * @return a {@code ImageParser}
	 */
	ImageParser getImageParser();

	/**
	 * @return a {@code ContextParser}
	 */
	ContextParser getContextParser();

	/**
	 * @return a {@code NetworkParser}
	 */
	NetworkParser getNetworkParser();

	/**
	 * @return a {@code ServiceParser}
	 */
	ServiceParser getServiceParser();

	/**
	 * @return a {@code NodeParser}
	 */
	NodeParser getNodeParser();

	/**
	 * @return a {@code SwarmParser}
	 */
	SwarmParser getSwarmParser();
}