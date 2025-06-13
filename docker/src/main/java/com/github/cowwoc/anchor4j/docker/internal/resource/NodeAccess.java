package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.Node;
import com.github.cowwoc.anchor4j.docker.resource.Node.Availability;
import com.github.cowwoc.anchor4j.docker.resource.Node.Reachability;
import com.github.cowwoc.anchor4j.docker.resource.Node.Status;
import com.github.cowwoc.anchor4j.docker.resource.Node.Type;
import com.github.cowwoc.anchor4j.docker.resource.NodeRemover;

import java.util.List;

/**
 * Methods that expose non-public behavior or data of a node.
 */
public interface NodeAccess
{
	/**
	 * Returns a reference to a Node.
	 *
	 * @param client         the client configuration
	 * @param id             the node's ID
	 * @param hostname       the node's hostname
	 * @param type           the type of the node
	 * @param leader         {@code true} if the node is a swarm leader
	 * @param status         the status of the node
	 * @param reachability   indicates if the node is reachable ({@link Reachability#UNKNOWN UNKNOWN} for worker
	 *                       nodes)
	 * @param availability   indicates if the node is available to run tasks
	 * @param managerAddress the node's address for manager communication, or an empty string for worker nodes
	 * @param address        the node's address
	 * @param labels         values that are used to constrain task scheduling to specific nodes
	 * @param engineVersion  the version of docker engine that the node is running
	 * @return null if the server isn't a member of a swarm
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id} contains whitespace or is empty
	 */
	Node get(InternalDocker client, String id, String hostname, Type type, boolean leader, Status status,
		Reachability reachability, Availability availability, String managerAddress, String address,
		List<String> labels, String engineVersion);

	/**
	 * Removes a node from the swarm. If the node does not exist, this method has no effect.
	 *
	 * @param client the client configuration
	 * @return a node remover
	 */
	NodeRemover remove(InternalDocker client);

	/**
	 * Looks up a node's {@code Availability} from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	Availability getAvailabilityFromJson(JsonNode json);

	/**
	 * Looks up a node's {@code Reachability} from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	Reachability getReachabilityFromJson(JsonNode json);

	/**
	 * Looks up a node's {@code Status} from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	Status getStatusFromJson(JsonNode json);

	/**
	 * Looks up a node's {@code Type} from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	Type getTypeFromJson(JsonNode json);
}