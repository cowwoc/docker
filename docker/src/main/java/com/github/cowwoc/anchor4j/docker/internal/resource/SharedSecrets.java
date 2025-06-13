package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.Config;
import com.github.cowwoc.anchor4j.docker.resource.ConfigCreator;
import com.github.cowwoc.anchor4j.docker.resource.Container;
import com.github.cowwoc.anchor4j.docker.resource.Container.HostConfiguration;
import com.github.cowwoc.anchor4j.docker.resource.Container.NetworkConfiguration;
import com.github.cowwoc.anchor4j.docker.resource.ContainerCreator;
import com.github.cowwoc.anchor4j.docker.resource.ContainerLogGetter;
import com.github.cowwoc.anchor4j.docker.resource.ContainerRemover;
import com.github.cowwoc.anchor4j.docker.resource.ContainerStarter;
import com.github.cowwoc.anchor4j.docker.resource.ContainerStopper;
import com.github.cowwoc.anchor4j.docker.resource.Context;
import com.github.cowwoc.anchor4j.docker.resource.ContextCreator;
import com.github.cowwoc.anchor4j.docker.resource.ContextEndpoint;
import com.github.cowwoc.anchor4j.docker.resource.ContextRemover;
import com.github.cowwoc.anchor4j.docker.resource.Image;
import com.github.cowwoc.anchor4j.docker.resource.ImagePuller;
import com.github.cowwoc.anchor4j.docker.resource.ImagePusher;
import com.github.cowwoc.anchor4j.docker.resource.ImageRemover;
import com.github.cowwoc.anchor4j.docker.resource.Network;
import com.github.cowwoc.anchor4j.docker.resource.Network.Configuration;
import com.github.cowwoc.anchor4j.docker.resource.Node;
import com.github.cowwoc.anchor4j.docker.resource.Node.Availability;
import com.github.cowwoc.anchor4j.docker.resource.Node.Reachability;
import com.github.cowwoc.anchor4j.docker.resource.Node.Status;
import com.github.cowwoc.anchor4j.docker.resource.Node.Type;
import com.github.cowwoc.anchor4j.docker.resource.NodeRemover;
import com.github.cowwoc.anchor4j.docker.resource.Service;
import com.github.cowwoc.anchor4j.docker.resource.ServiceCreator;
import com.github.cowwoc.anchor4j.docker.resource.SwarmCreator;
import com.github.cowwoc.anchor4j.docker.resource.SwarmJoiner;
import com.github.cowwoc.anchor4j.docker.resource.SwarmLeaver;
import com.github.cowwoc.anchor4j.docker.resource.Task;
import com.github.cowwoc.anchor4j.docker.resource.Task.State;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Internal mechanism for granting privileged access to non-public members across package boundaries, without
 * using reflection.
 * <p>
 * This class maintains static references to access interfaces. These interfaces define methods that expose
 * non-public behavior or data of other classes within the module. Classes that wish to expose such internals
 * register implementations of these interfaces, typically during static initialization.
 * <p>
 * Consumers within the same module can retrieve these interfaces and invoke privileged methods, enabling
 * controlled access to internal functionality without breaking encapsulation or relying on reflection.
 * <p>
 * This mechanism resides in a non-exported package to restrict visibility and ensure that only trusted
 * classes within the same module can interact with it.
 */
public final class SharedSecrets
{
	private static final Lookup LOOKUP = MethodHandles.lookup();
	private static ConfigAccess configAccess;
	private static ContainerAccess containerAccess;
	private static ImageAccess imageAccess;
	private static ContextAccess contextAccess;
	private static NetworkAccess networkAccess;
	private static NodeAccess nodeAccess;
	private static ServiceAccess serviceAccess;
	private static SwarmAccess swarmAccess;

	/**
	 * Registers an implementation for the {@code ConfigAccess} interface.
	 *
	 * @param configAccess the implementation
	 */
	public static void setConfigAccess(ConfigAccess configAccess)
	{
		assert that(configAccess, "configAccess").isNotNull().elseThrow();
		SharedSecrets.configAccess = configAccess;
	}

	/**
	 * Creates a reference to a swarm's configuration.
	 *
	 * @param client the client configuration
	 * @param id     the config's ID
	 * @param name   the config's name
	 * @param value  the config's value
	 * @return the config
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain whitespace.</li>
	 *                                    <li>the {@code name} is empty.</li>
	 *                                  </ul>
	 */
	public static Config getConfig(InternalDocker client, String id, String name, ByteBuffer value)
	{
		ConfigAccess access = configAccess;
		if (access == null)
		{
			initialize(Config.class);
			access = configAccess;
			assert access != null;
		}
		return access.get(client, id, name, value);
	}

	/**
	 * Creates a config creator.
	 *
	 * @param client the client configuration
	 * @return the config creator
	 */
	public static ConfigCreator createConfig(InternalDocker client)
	{
		ConfigAccess access = configAccess;
		if (access == null)
		{
			initialize(Config.class);
			access = configAccess;
			assert access != null;
		}
		return access.create(client);
	}

	/**
	 * Registers an implementation for the {@code ContainerAccess} interface.
	 *
	 * @param containerAccess the implementation
	 */
	public static void setContainerAccess(ContainerAccess containerAccess)
	{
		assert that(containerAccess, "containerAccess").isNotNull().elseThrow();
		SharedSecrets.containerAccess = containerAccess;
	}

	/**
	 * Creates a reference to a container.
	 *
	 * @param client               the client configuration
	 * @param id                   the ID of the container
	 * @param name                 the name of the container
	 * @param hostConfiguration    the container's host configuration
	 * @param networkConfiguration the container's network configuration
	 * @param status               the container's status
	 * @return the container
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain whitespace or is empty
	 */
	public static Container getContainer(InternalDocker client, String id, String name,
		HostConfiguration hostConfiguration, NetworkConfiguration networkConfiguration, Container.Status status)
	{
		ContainerAccess access = containerAccess;
		if (access == null)
		{
			initialize(Container.class);
			access = containerAccess;
			assert access != null;
		}
		return access.get(client, id, name, hostConfiguration, networkConfiguration, status);
	}

	/**
	 * Creates a reference to a container remover.
	 *
	 * @param client the client configuration
	 * @param id     the container's ID or name
	 * @return the container remover
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	public static ContainerRemover removeContainer(InternalDocker client, String id)
	{
		ContainerAccess access = containerAccess;
		if (access == null)
		{
			initialize(Container.class);
			access = containerAccess;
			assert access != null;
		}
		return access.remove(client, id);
	}

	/**
	 * Creates a container creator.
	 *
	 * @param client  the client configuration
	 * @param imageId the image ID or {@link Image reference} to create the container from
	 * @return a container creator
	 * @throws NullPointerException     if {@code imageId} is null
	 * @throws IllegalArgumentException if {@code imageId}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 */
	public static ContainerCreator createContainer(InternalDocker client, String imageId)
	{
		ContainerAccess access = containerAccess;
		if (access == null)
		{
			initialize(Container.class);
			access = containerAccess;
			assert access != null;
		}
		return access.create(client, imageId);
	}

	/**
	 * Creates a reference to a container starter.
	 *
	 * @param client the client configuration
	 * @param id     the container's ID or name
	 * @return the container starter
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	public static ContainerStarter startContainer(InternalDocker client, String id)
	{
		ContainerAccess access = containerAccess;
		if (access == null)
		{
			initialize(Container.class);
			access = containerAccess;
			assert access != null;
		}
		return access.start(client, id);
	}

	/**
	 * Creates a reference to a container stopper.
	 *
	 * @param client the client configuration
	 * @param id     the container's ID or name
	 * @return the container stopper
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	public static ContainerStopper stopContainer(InternalDocker client, String id)
	{
		ContainerAccess access = containerAccess;
		if (access == null)
		{
			initialize(Container.class);
			access = containerAccess;
			assert access != null;
		}
		return access.stop(client, id);
	}

	/**
	 * Streams a container's logs.
	 *
	 * @param client the client configuration
	 * @param id     the container's ID or name
	 * @return the container logs
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	public static ContainerLogGetter getContainerLogs(InternalDocker client, String id)
	{
		ContainerAccess access = containerAccess;
		if (access == null)
		{
			initialize(Container.class);
			access = containerAccess;
			assert access != null;
		}
		return access.getLogs(client, id);
	}

	/**
	 * Looks up a container's status from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the container status
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	public static Container.Status getContainerStatus(JsonNode json)
	{
		ContainerAccess access = containerAccess;
		if (access == null)
		{
			initialize(Container.class);
			access = containerAccess;
			assert access != null;
		}
		return access.getStatus(json);
	}

	/**
	 * Registers an implementation for the {@code ContextAccess} interface.
	 *
	 * @param contextAccess the implementation
	 */
	public static void setContextAccess(ContextAccess contextAccess)
	{
		assert that(contextAccess, "contextAccess").isNotNull().elseThrow();
		SharedSecrets.contextAccess = contextAccess;
	}

	/**
	 * Creates a context creator.
	 *
	 * @param name     the name of the context
	 * @param client   the client configuration
	 * @param endpoint the configuration of the target Docker Engine
	 * @return a context creator
	 * @throws NullPointerException     if {@code name} or {@code endpoint} are null
	 * @throws IllegalArgumentException if {@code name} contains whitespace or is empty
	 * @see ContextEndpoint#builder(URI)
	 */
	public static ContextCreator createContext(InternalDocker client, String name,
		ContextEndpoint endpoint)
	{
		ContextAccess access = contextAccess;
		if (access == null)
		{
			initialize(Context.class);
			access = contextAccess;
			assert access != null;
		}
		return access.create(client, name, endpoint);
	}

	/**
	 * Creates a context remover.
	 *
	 * @param name   the name of the context
	 * @param client the client configuration
	 * @return a context remover
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name} contains whitespace or is empty
	 */
	public static ContextRemover removeContext(InternalDocker client, String name)
	{
		ContextAccess access = contextAccess;
		if (access == null)
		{
			initialize(Context.class);
			access = contextAccess;
			assert access != null;
		}
		return access.remove(client, name);
	}

	/**
	 * Creates a reference to a context.
	 *
	 * @param client      the client configuration
	 * @param name        the context's name
	 * @param description the context's description
	 * @param endpoint    the configuration of the target Docker Engine
	 * @return a context
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} or {@code endpoint} contains whitespace, or are empty
	 */
	public static Context getContext(InternalDocker client, String name, String description,
		String endpoint)
	{
		ContextAccess access = contextAccess;
		if (access == null)
		{
			initialize(Context.class);
			access = contextAccess;
			assert access != null;
		}
		return access.get(client, name, description, endpoint);
	}

	/**
	 * Registers an implementation for the {@code ImageAccess} interface.
	 *
	 * @param imageAccess the implementation
	 */
	public static void setImageAccess(ImageAccess imageAccess)
	{
		assert that(imageAccess, "imageAccess").isNotNull().elseThrow();
		SharedSecrets.imageAccess = imageAccess;
	}

	/**
	 * Creates a reference to an image.
	 *
	 * @param client            the client configuration
	 * @param reference         the reference of the image
	 * @param referenceToTags   a mapping from the image's name to its tags
	 * @param referenceToDigest a mapping from the image's name to its digest
	 * @return an image
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id}, the keys, or values of {@code referenceToTags} or
	 *                                  {@code referenceToDigest} contain whitespace, or are empty
	 */
	public static Image getImage(InternalDocker client, String reference,
		Map<String, Set<String>> referenceToTags, Map<String, String> referenceToDigest)
	{
		ImageAccess access = imageAccess;
		if (access == null)
		{
			initialize(Image.class);
			access = imageAccess;
			assert access != null;
		}
		return access.get(client, reference, referenceToTags, referenceToDigest);
	}

	/**
	 * Creates an image puller.
	 *
	 * @param client    the client configuration
	 * @param reference the image's reference
	 * @return the image puller
	 * @throws NullPointerException     if {@code reference} is null
	 * @throws IllegalArgumentException if {@code reference}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 */
	public static ImagePuller pullImage(InternalDocker client, String reference)
	{
		ImageAccess access = imageAccess;
		if (access == null)
		{
			initialize(Image.class);
			access = imageAccess;
			assert access != null;
		}
		return access.pull(client, reference);
	}

	/**
	 * Creates an image pusher.
	 *
	 * @param client    the client configuration
	 * @param reference the image's reference
	 * @return the image pusher
	 * @throws NullPointerException     if {@code reference} is null
	 * @throws IllegalArgumentException if {@code reference}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 */
	public static ImagePusher pushImage(InternalDocker client, String reference)
	{
		ImageAccess access = imageAccess;
		if (access == null)
		{
			initialize(Image.class);
			access = imageAccess;
			assert access != null;
		}
		return access.push(client, reference);
	}

	/**
	 * Creates an image remover.
	 *
	 * @param client    the client configuration
	 * @param reference the image's reference
	 * @return the image remover
	 * @throws NullPointerException     if {@code reference} is null
	 * @throws IllegalArgumentException if {@code reference}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 */
	public static ImageRemover removeImage(InternalDocker client, String reference)
	{
		ImageAccess access = imageAccess;
		if (access == null)
		{
			initialize(Image.class);
			access = imageAccess;
			assert access != null;
		}
		return access.remove(client, reference);
	}

	/**
	 * Registers an implementation for the {@code NetworkAccess} interface.
	 *
	 * @param networkAccess the implementation
	 */
	public static void setNetworkAccess(NetworkAccess networkAccess)
	{
		assert that(networkAccess, "networkAccess").isNotNull().elseThrow();
		SharedSecrets.networkAccess = networkAccess;
	}

	/**
	 * Creates a network.
	 *
	 * @param client         the client configuration
	 * @param id             the ID of the network
	 * @param name           the name of the network
	 * @param configurations the network configurations
	 * @return the network
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain whitespace or is empty
	 */
	public static Network createNetwork(InternalDocker client, String id, String name,
		List<Configuration> configurations)
	{
		NetworkAccess access = networkAccess;
		if (access == null)
		{
			initialize(Network.class);
			access = networkAccess;
			assert access != null;
		}
		return access.get(client, id, name, configurations);
	}

	/**
	 * Registers an implementation for the {@code NodeAccess} interface.
	 *
	 * @param nodeAccess the implementation
	 */
	public static void setNodeAccess(NodeAccess nodeAccess)
	{
		assert that(nodeAccess, "nodeAccess").isNotNull().elseThrow();
		SharedSecrets.nodeAccess = nodeAccess;
	}

	/**
	 * Creates a reference to a Node.
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
	public static Node getNode(InternalDocker client, String id, String hostname, Type type,
		boolean leader,
		Status status, Reachability reachability, Availability availability, String managerAddress,
		String address, List<String> labels, String engineVersion)
	{
		NodeAccess access = nodeAccess;
		if (access == null)
		{
			initialize(Node.class);
			access = nodeAccess;
			assert access != null;
		}
		return access.get(client, id, hostname, type, leader, status, reachability, availability, managerAddress,
			address, labels, engineVersion);
	}

	/**
	 * Removes a node from the swarm. If the node does not exist, this method has no effect.
	 *
	 * @param client the client configuration
	 * @return a node remover
	 */
	public static NodeRemover removeNode(InternalDocker client)
	{
		NodeAccess access = nodeAccess;
		if (access == null)
		{
			initialize(Node.class);
			access = nodeAccess;
			assert access != null;
		}
		return access.remove(client);
	}

	/**
	 * Looks up a node's {@code Availability} from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	public static Availability getNodeAvailabilityFromJson(JsonNode json)
	{
		NodeAccess access = nodeAccess;
		if (access == null)
		{
			initialize(Node.class);
			access = nodeAccess;
			assert access != null;
		}
		return access.getAvailabilityFromJson(json);
	}

	/**
	 * Looks up a node's {@code Reachability} from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	public static Reachability getNodeReachabilityFromJson(JsonNode json)
	{
		NodeAccess access = nodeAccess;
		if (access == null)
		{
			initialize(Node.class);
			access = nodeAccess;
			assert access != null;
		}
		return access.getReachabilityFromJson(json);
	}

	/**
	 * Looks up a node's {@code Status} from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	public static Status getNodeStatusFromJson(JsonNode json)
	{
		NodeAccess access = nodeAccess;
		if (access == null)
		{
			initialize(Node.class);
			access = nodeAccess;
			assert access != null;
		}
		return access.getStatusFromJson(json);
	}

	/**
	 * Looks up a node's {@code Type} from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	public static Type getNodeTypeFromJson(JsonNode json)
	{
		NodeAccess access = nodeAccess;
		if (access == null)
		{
			initialize(Node.class);
			access = nodeAccess;
			assert access != null;
		}
		return access.getTypeFromJson(json);
	}

	/**
	 * Registers an implementation for the {@code ServiceAccess} interface.
	 *
	 * @param serviceAccess the implementation
	 */
	public static void setServiceAccess(ServiceAccess serviceAccess)
	{
		assert that(serviceAccess, "serviceAccess").isNotNull().elseThrow();
		SharedSecrets.serviceAccess = serviceAccess;
	}

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
	public static ServiceCreator createService(InternalDocker client, String imageId)
	{
		ServiceAccess access = serviceAccess;
		if (access == null)
		{
			initialize(Service.class);
			access = serviceAccess;
			assert access != null;
		}
		return access.createService(client, imageId);
	}

	/**
	 * Creates a reference to a task.
	 *
	 * @param id    the task's ID
	 * @param name  the task's name
	 * @param state the task's state
	 * @return a task
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain whitespace or are empty
	 */
	public static Task createTask(String id, String name, Task.State state)
	{
		ServiceAccess access = serviceAccess;
		if (access == null)
		{
			initialize(Service.class);
			access = serviceAccess;
			assert access != null;
		}
		return access.getTask(id, name, state);
	}

	/**
	 * Looks up a task's {@code State} from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	public static State getTaskStateFromJson(JsonNode json)
	{
		ServiceAccess access = serviceAccess;
		if (access == null)
		{
			initialize(Service.class);
			access = serviceAccess;
			assert access != null;
		}
		return access.getStateFromJson(json);
	}

	/**
	 * Registers an implementation for the {@code SwarmAccess} interface.
	 *
	 * @param swarmAccess the implementation
	 */
	public static void setSwarmAccess(SwarmAccess swarmAccess)
	{
		assert that(swarmAccess, "swarmAccess").isNotNull().elseThrow();
		SharedSecrets.swarmAccess = swarmAccess;
	}

	/**
	 * Creates a swarm creator.
	 *
	 * @param client the client configuration
	 * @return a swarm creator
	 */
	public static SwarmCreator createSwarm(InternalDocker client)
	{
		SwarmAccess access = swarmAccess;
		if (access == null)
		{
			initialize(SwarmCreator.class);
			access = swarmAccess;
			assert access != null;
		}
		return access.create(client);
	}

	/**
	 * Joins a swarm.
	 *
	 * @param client the client configuration
	 * @return a swarm joiner
	 */
	public static SwarmJoiner joinSwarm(InternalDocker client)
	{
		SwarmAccess access = swarmAccess;
		if (access == null)
		{
			initialize(SwarmJoiner.class);
			access = swarmAccess;
			assert access != null;
		}
		return access.join(client);
	}

	/**
	 * Leaves a swarm.
	 *
	 * @param client the client configuration
	 * @return a swarm leaver
	 */
	public static SwarmLeaver leaveSwarm(InternalDocker client)
	{
		SwarmAccess access = swarmAccess;
		if (access == null)
		{
			initialize(SwarmJoiner.class);
			access = swarmAccess;
			assert access != null;
		}
		return access.leave(client);
	}

	/**
	 * Initializes a class. If the class is already initialized, this method has no effect.
	 *
	 * @param c the class
	 */
	private static void initialize(Class<?> c)
	{
		try
		{
			LOOKUP.ensureInitialized(c);
		}
		catch (IllegalAccessException e)
		{
			throw new AssertionError(e);
		}
	}

	private SharedSecrets()
	{
	}
}