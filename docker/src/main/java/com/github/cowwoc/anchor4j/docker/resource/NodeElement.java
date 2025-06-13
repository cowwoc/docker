package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.docker.client.Docker;
import com.github.cowwoc.anchor4j.docker.resource.Node.Availability;
import com.github.cowwoc.anchor4j.docker.resource.Node.Reachability;
import com.github.cowwoc.anchor4j.docker.resource.Node.Status;
import com.github.cowwoc.anchor4j.docker.resource.Node.Type;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * An element returned by {@link Docker#listNodes()}.
 *
 * @param id            the node's ID
 * @param hostname      the node's hostname
 * @param type          the type of the node
 * @param leader        {@code true} if the node is a swarm leader
 * @param status        the status of the node
 * @param reachability  indicates if the node is reachable ({@link Reachability#UNKNOWN UNKNOWN} for worker
 *                      nodes)
 * @param availability  indicates if the node is available to run tasks
 * @param engineVersion the version of docker engine that the node is running
 */
public record NodeElement(String id, String hostname, Type type, boolean leader, Status status,
                          Reachability reachability, Availability availability, String engineVersion)
{
	/**
	 * Creates a node element.
	 *
	 * @param id            the node's ID
	 * @param hostname      the node's hostname
	 * @param type          the type of the node
	 * @param leader        {@code true} if the node is a swarm leader
	 * @param status        the status of the node
	 * @param reachability  indicates if the node is reachable ({@link Reachability#UNKNOWN UNKNOWN} for worker
	 *                      nodes)
	 * @param availability  indicates if the node is available to run tasks
	 * @param engineVersion the version of docker engine that the node is running
	 */
	public NodeElement
	{
		assert that(id, "id").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert that(hostname, "hostname").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert that(type, "type").isNotNull().elseThrow();
		assert that(status, "status").isNotNull().elseThrow();
		assert that(reachability, "reachability").isNotNull().elseThrow();
		assert that(availability, "availability").isNotNull().elseThrow();
		assert that(engineVersion, "engineVersion").doesNotContainWhitespace().elseThrow();
	}
}
