package com.github.cowwoc.anchor4j.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.internal.resource.NodeAccess;
import com.github.cowwoc.anchor4j.docker.internal.resource.SharedSecrets;
import com.github.cowwoc.requirements11.annotation.CheckReturnValue;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * A docker node.
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public final class Node
{
	static
	{
		SharedSecrets.setNodeAccess(new NodeAccess()
		{
			@Override
			public Node get(InternalDocker client, String id, String hostname, Type type, boolean leader,
				Status status, Reachability reachability, Availability availability, String managerAddress,
				String address, List<String> labels, String engineVersion)
			{
				return new Node(client, id, hostname, type, leader, status, reachability, availability,
					managerAddress, address, labels, engineVersion);
			}

			@Override
			public NodeRemover remove(InternalDocker client)
			{
				return new NodeRemover(client);
			}

			@Override
			public Availability getAvailabilityFromJson(JsonNode json)
			{
				return Availability.fromJson(json);
			}

			@Override
			public Reachability getReachabilityFromJson(JsonNode json)
			{
				return Reachability.fromJson(json);
			}

			@Override
			public Status getStatusFromJson(JsonNode json)
			{
				return Status.fromJson(json);
			}

			@Override
			public Type getTypeFromJson(JsonNode json)
			{
				return Type.fromJson(json);
			}
		});
	}

	private final InternalDocker client;
	private final String id;
	private final String hostname;
	private final Type type;
	private final boolean leader;
	private final Availability availability;
	private final Reachability reachability;
	private final Status status;
	private final String managerAddress;
	private final String address;
	private final List<String> labels;
	private final String engineVersion;

	/**
	 * Creates a reference to a node.
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
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain whitespace.</li>
	 *                                    <li>any of the mandatory arguments are empty.</li>
	 *                                  </ul>
	 */
	private Node(InternalDocker client, String id, String hostname, Type type, boolean leader,
		Status status,
		Reachability reachability, Availability availability, String managerAddress, String address,
		List<String> labels, String engineVersion)
	{
		assert that(client, "client").isNotNull().elseThrow();
		assert that(id, "id").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert that(hostname, "hostname").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert that(type, "type").isNotNull().elseThrow();
		assert that(status, "status").isNotNull().elseThrow();
		assert that(reachability, "reachability").isNotNull().elseThrow();
		assert that(availability, "availability").isNotNull().elseThrow();
		assert that(address, "address").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert that(engineVersion, "engineVersion").doesNotContainWhitespace().isNotEmpty().elseThrow();

		assert switch (type)
		{
			case MANAGER -> that(managerAddress, "managerAddress").doesNotContainWhitespace().isNotEmpty().
				elseThrow();
			// do nothing
			case WORKER -> true;
		};
		assert that(labels, "labels").isNotNull().elseThrow();
		this.client = client;
		this.id = id;
		this.hostname = hostname;
		this.type = type;
		this.leader = leader;
		this.status = status;
		this.reachability = reachability;
		this.availability = availability;
		this.managerAddress = managerAddress;
		this.address = address;
		this.labels = List.copyOf(labels);
		this.engineVersion = engineVersion;
	}

	/**
	 * Returns the node's id.
	 *
	 * @return the node's id
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the node's hostname.
	 *
	 * @return the hostname
	 */
	public String getHostname()
	{
		return hostname;
	}

	/**
	 * Returns the node's type.
	 *
	 * @return null if the node is not a member of a swarm
	 */
	public Type getType()
	{
		return type;
	}

	/**
	 * Indicates if the node is a swarm leader.
	 *
	 * @return {@code true} if the node is a swarm leader
	 */
	public boolean isLeader()
	{
		return leader;
	}

	/**
	 * Returns the status of the node.
	 *
	 * @return the status
	 */
	public Status getStatus()
	{
		return status;
	}

	/**
	 * Indicates whether it is possible to communicate with the node.
	 *
	 * @return {@link Reachability#UNKNOWN UNKNOWN} for worker nodes
	 */
	public Reachability getReachability()
	{
		return reachability;
	}

	/**
	 * Indicates if the node is available to run tasks.
	 *
	 * @return {@code true} if the node is available to run tasks
	 */
	public Availability getAvailability()
	{
		return availability;
	}

	/**
	 * Returns the node's address for manager communication.
	 *
	 * @return an empty string for worker nodes
	 */
	public String getManagerAddress()
	{
		assert type != Type.MANAGER || !managerAddress.isBlank() :
			"Node is a manager but its address is blank";
		return managerAddress;
	}

	/**
	 * Returns the node's address.
	 *
	 * @return the node's address
	 */
	public String getAddress()
	{
		return address;
	}

	/**
	 * Returns values that are used to constrain task scheduling to specific nodes.
	 *
	 * @return values that are used to constrain task scheduling to specific nodes
	 */
	public List<String> getLabels()
	{
		return labels;
	}

	/**
	 * Reloads the node's status.
	 *
	 * @return the updated status
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	@CheckReturnValue
	public Node reload() throws IOException, InterruptedException
	{
		return client.getNode(id);
	}

	/**
	 * Returns the {@code NodeElement} representation of this node.
	 *
	 * @return the {@code NodeElement} representation
	 */
	public NodeElement toNodeElement()
	{
		return new NodeElement(id, hostname, type, leader, status, reachability, availability, engineVersion);
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Node other && other.id.equals(id);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Node.class).
			add("id", id).
			add("type", type).
			add("leader", leader).
			add("availability", availability).
			add("reachability", reachability).
			add("status", status).
			add("managerAddress", managerAddress).
			add("workerAddress", address).
			add("hostname", hostname).
			add("labels", labels).
			toString();
	}

	/**
	 * Indicates if the node is available to run tasks.
	 */
	public enum Availability
	{
		// https://github.com/docker/engine-api/blob/4290f40c056686fcaa5c9caf02eac1dde9315adf/types/swarm/node.go#L34
		/**
		 * The node can accept new tasks.
		 */
		ACTIVE,
		/**
		 * The node is temporarily unavailable for new tasks, but existing tasks continue running.
		 */
		PAUSE,
		/**
		 * The node is unavailable for new tasks, and any existing tasks are being moved to other nodes in the
		 * swarm. This is typically used when preparing a node for maintenance.
		 */
		DRAIN;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static Availability fromJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}
	}

	/**
	 * Indicates if it is possible to communicate with the node.
	 */
	public enum Reachability
	{
		// https://github.com/docker/engine-api/blob/4290f40c056686fcaa5c9caf02eac1dde9315adf/types/swarm/node.go#L79
		/**
		 * There is insufficient information to determine if the node is reachable.
		 */
		UNKNOWN,
		/**
		 * The node is unreachable.
		 */
		UNREACHABLE,
		/**
		 * The node is reachable.
		 */
		REACHABLE;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static Reachability fromJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}
	}

	/**
	 * Indicates the overall health of the node.
	 */
	public enum Status
	{
		// https://github.com/docker/engine-api/blob/4290f40c056686fcaa5c9caf02eac1dde9315adf/types/swarm/node.go#L98
		/**
		 * There is insufficient information to determine the status of the node.
		 */
		UNKNOWN,
		/**
		 * The node is permanently unable to run tasks.
		 */
		DOWN,
		/**
		 * The node is reachable and ready to run tasks.
		 */
		READY,
		/**
		 * The node is temporarily unreachable but may still be running tasks.
		 */
		DISCONNECTED;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static Status fromJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}
	}

	/**
	 * The role of the node within the swarm.
	 */
	public enum Type
	{
		/**
		 * A node that participates in administrating the swarm.
		 */
		MANAGER,
		/**
		 * A node that runs tasks.
		 */
		WORKER;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static Type fromJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}

		/**
		 * Returns the command-line representation of this option.
		 *
		 * @return the command-line value
		 */
		public String toCommandLine()
		{
			return name().toLowerCase(Locale.ROOT);
		}
	}
}