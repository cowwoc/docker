package com.github.cowwoc.anchor4j.docker.client;

import com.github.cowwoc.anchor4j.core.client.Client;
import com.github.cowwoc.anchor4j.docker.exception.LastManagerException;
import com.github.cowwoc.anchor4j.docker.exception.NotSwarmManagerException;
import com.github.cowwoc.anchor4j.docker.exception.NotSwarmMemberException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.internal.client.DefaultDocker;
import com.github.cowwoc.anchor4j.docker.resource.Config;
import com.github.cowwoc.anchor4j.docker.resource.ConfigCreator;
import com.github.cowwoc.anchor4j.docker.resource.ConfigElement;
import com.github.cowwoc.anchor4j.docker.resource.Container;
import com.github.cowwoc.anchor4j.docker.resource.ContainerCreator;
import com.github.cowwoc.anchor4j.docker.resource.ContainerElement;
import com.github.cowwoc.anchor4j.docker.resource.ContainerLogGetter;
import com.github.cowwoc.anchor4j.docker.resource.ContainerRemover;
import com.github.cowwoc.anchor4j.docker.resource.ContainerStarter;
import com.github.cowwoc.anchor4j.docker.resource.ContainerStopper;
import com.github.cowwoc.anchor4j.docker.resource.Context;
import com.github.cowwoc.anchor4j.docker.resource.ContextCreator;
import com.github.cowwoc.anchor4j.docker.resource.ContextElement;
import com.github.cowwoc.anchor4j.docker.resource.ContextEndpoint;
import com.github.cowwoc.anchor4j.docker.resource.ContextRemover;
import com.github.cowwoc.anchor4j.docker.resource.Image;
import com.github.cowwoc.anchor4j.docker.resource.ImageElement;
import com.github.cowwoc.anchor4j.docker.resource.ImagePuller;
import com.github.cowwoc.anchor4j.docker.resource.ImagePusher;
import com.github.cowwoc.anchor4j.docker.resource.ImageRemover;
import com.github.cowwoc.anchor4j.docker.resource.JoinToken;
import com.github.cowwoc.anchor4j.docker.resource.Network;
import com.github.cowwoc.anchor4j.docker.resource.Node;
import com.github.cowwoc.anchor4j.docker.resource.Node.Type;
import com.github.cowwoc.anchor4j.docker.resource.NodeElement;
import com.github.cowwoc.anchor4j.docker.resource.NodeRemover;
import com.github.cowwoc.anchor4j.docker.resource.ServiceCreator;
import com.github.cowwoc.anchor4j.docker.resource.SwarmCreator;
import com.github.cowwoc.anchor4j.docker.resource.SwarmJoiner;
import com.github.cowwoc.anchor4j.docker.resource.SwarmLeaver;
import com.github.cowwoc.anchor4j.docker.resource.Task;
import com.github.cowwoc.requirements11.annotation.CheckReturnValue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * A Docker client.
 */
public interface Docker extends Client
{
	/**
	 * Creates a client that uses the {@code docker} executable located in the {@code PATH} environment
	 * variable.
	 *
	 * @return a client
	 * @throws IOException if an I/O error occurs while reading file attributes
	 */
	static Docker connect() throws IOException
	{
		return new DefaultDocker();
	}

	/**
	 * Creates a client that uses the specified executable.
	 *
	 * @param executable the path of the {@code docker} executable
	 * @return a client
	 * @throws NullPointerException     if {@code executable} is null
	 * @throws IllegalArgumentException if the path referenced by {@code executable} does not exist or is not a
	 *                                  file
	 * @throws IOException              if an I/O error occurs while reading {@code executable}'s attributes
	 */
	static Docker connect(Path executable) throws IOException
	{
		return new DefaultDocker(executable);
	}

	/**
	 * Authenticates with the Docker Hub registry.
	 *
	 * @param username the user's name
	 * @param password the user's password
	 * @return this
	 * @throws NullPointerException     if any of the mandatory parameters are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain whitespace.</li>
	 *                                    <li>{@code username} or {@code password} is empty.</li>
	 *                                  </ul>
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	Docker login(String username, String password)
		throws IOException, InterruptedException;

	/**
	 * Authenticates with a registry.
	 *
	 * @param username      the user's name
	 * @param password      the user's password
	 * @param serverAddress the name of a registry server
	 * @return this
	 * @throws NullPointerException     if any of the mandatory parameters are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain whitespace.</li>
	 *                                    <li>any of the arguments are empty.</li>
	 *                                  </ul>
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	Docker login(String username, String password, String serverAddress)
		throws IOException, InterruptedException;

	/**
	 * Lists all the configs.
	 *
	 * @return an empty list if no match is found
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	List<ConfigElement> listConfigs() throws IOException, InterruptedException;

	/**
	 * Looks up a config by its ID or name.
	 *
	 * @param id the config's ID or name
	 * @return null if no match is found
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id} contains whitespace or is empty
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	Config getConfig(String id) throws IOException, InterruptedException;

	/**
	 * Creates a config.
	 *
	 * @return a config creator
	 */
	@CheckReturnValue
	ConfigCreator createConfig();

	/**
	 * Lists all the containers.
	 *
	 * @return an empty list if no match is found
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	List<ContainerElement> listContainers() throws IOException, InterruptedException;

	/**
	 * Looks up a container by its ID or name.
	 *
	 * @param id the ID or name
	 * @return null if no match is found
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id} contains whitespace, or is empty
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	Container getContainer(String id) throws IOException, InterruptedException;

	/**
	 * Creates a container.
	 *
	 * @param imageId the image ID or {@link Image reference} to create the container from
	 * @return a container creator
	 * @throws NullPointerException     if {@code imageId} is null
	 * @throws IllegalArgumentException if {@code imageId}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 */
	@CheckReturnValue
	ContainerCreator createContainer(String imageId);

	/**
	 * Returns a container.
	 *
	 * @param oldName the container's existing name
	 * @param newName the container's new name
	 * @return this
	 * @throws IllegalArgumentException  if {@code oldName} or {@code newName}:
	 *                                   <ul>
	 *                                     <li>are empty.</li>
	 *                                     <li>contain any character other than lowercase letters (a–z),
	 *                                     digits (0–9), and the following characters: {@code '.'}, {@code '/'},
	 *                                     {@code ':'}, {@code '_'}, {@code '-'}, {@code '@'}.</li>
	 *                                   </ul>
	 * @throws ResourceNotFoundException if the container does not exist
	 * @throws ResourceInUseException    if the requested name is in use by another container
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws InterruptedException      if the thread is interrupted before the operation completes. This can
	 *                                   happen due to shutdown signals.
	 */
	Docker renameContainer(String oldName, String newName)
		throws IOException, InterruptedException;

	/**
	 * Starts a container.
	 *
	 * @param id the container's ID or name
	 * @return a container starter
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	@CheckReturnValue
	ContainerStarter startContainer(String id);

	/**
	 * Stops a container.
	 *
	 * @param id the container's ID or name
	 * @return a container stopper
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	@CheckReturnValue
	ContainerStopper stopContainer(String id);

	/**
	 * Removes a container.
	 *
	 * @param id the container's ID or name
	 * @return a container remover
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	@CheckReturnValue
	ContainerRemover removeContainer(String id);

	/**
	 * Waits until a container stops.
	 * <p>
	 * If the container has already stopped, this method returns immediately.
	 *
	 * @param id the container's ID or name
	 * @return the exit code returned by the container
	 * @throws NullPointerException      if {@code id} is null
	 * @throws IllegalArgumentException  if {@code id}'s format is invalid
	 * @throws ResourceNotFoundException if the container does not exist
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws InterruptedException      if the thread is interrupted before the operation completes. This can
	 *                                   happen due to shutdown signals.
	 */
	int waitUntilContainerStops(String id) throws IOException, InterruptedException;

	/**
	 * Retrieves a container's logs.
	 *
	 * @param id the container's ID or name
	 * @return logs
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	ContainerLogGetter getContainerLogs(String id);

	/**
	 * Lists all the contexts.
	 *
	 * @return the contexts
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	List<ContextElement> listContexts() throws IOException, InterruptedException;

	/**
	 * Looks up a context by its name.
	 *
	 * @param name the name
	 * @return null if no match is found
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name}'s format is invalid
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	Context getContext(String name) throws IOException, InterruptedException;

	/**
	 * Creates a context.
	 *
	 * @param name     the name of the context
	 * @param endpoint the configuration of the target Docker Engine
	 * @return a context creator
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains whitespace or is empty
	 * @see ContextEndpoint#builder(URI)
	 */
	@CheckReturnValue
	ContextCreator createContext(String name, ContextEndpoint endpoint);

	/**
	 * Removes an existing context.
	 *
	 * @param name the name of the context
	 * @return the context remover
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name} contains whitespace or is empty
	 */
	@CheckReturnValue
	ContextRemover removeContext(String name);

	/**
	 * Returns the client's current context.
	 *
	 * @return an empty string if the client is using the user's context
	 * @see <a href="https://docs.docker.com/engine/security/protect-access/">Protect the Docker daemon
	 * 	socket</a>
	 * @see <a href="https://docs.docker.com/engine/manage-resources/contexts/">global --context flag</a>
	 * @see #getUserContext()
	 */
	String getClientContext();

	/**
	 * Sets the client's current context. Unlike {@link #setUserContext(String)}, this method only updates the
	 * current client's configuration and does not affect other processes or shells.
	 *
	 * @param name the name of the context, or an empty string to use the user's context
	 * @return this
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name}'s format is invalid
	 */
	Docker setClientContext(String name);

	/**
	 * Returns the current user's current context.
	 *
	 * @return the name
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 * @see <a href="https://docs.docker.com/engine/security/protect-access/">Protect the Docker daemon
	 * 	socket</a>
	 * @see <a href="https://docs.docker.com/reference/cli/docker/context/use/">docker context use</a>
	 * @see #getClientContext()
	 */
	String getUserContext() throws IOException, InterruptedException;

	/**
	 * Sets the current user's current context. Unlike {@link #setClientContext(String)}, this method updates
	 * the persistent Docker CLI configuration and affects all future Docker CLI invocations by the user across
	 * all shells.
	 *
	 * @param name the name of the context
	 * @return this
	 * @throws NullPointerException      if {@code name} is null
	 * @throws IllegalArgumentException  if {@code name}'s format is invalid
	 * @throws ResourceNotFoundException if the context does not exist
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws InterruptedException      if the thread is interrupted before the operation completes. This can
	 *                                   happen due to shutdown signals.
	 */
	Docker setUserContext(String name) throws IOException, InterruptedException;

	/**
	 * Lists all the images.
	 *
	 * @return the images
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	List<ImageElement> listImages() throws IOException, InterruptedException;

	/**
	 * Looks up an image by its ID or reference.
	 *
	 * @param id the image's ID or {@link Image reference}
	 * @return null if no match is found
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	Image getImage(String id) throws IOException, InterruptedException;

	/**
	 * Creates a new reference to an image.
	 * <p>
	 * If the target reference already exists, this method has no effect.
	 *
	 * @param source the ID or existing reference of the image
	 * @param target the new reference to create
	 * @throws NullPointerException      if any of the arguments are null
	 * @throws IllegalArgumentException  if {@code source} or {@code target}'s format are invalid
	 * @throws ResourceNotFoundException if the image does not exist
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws InterruptedException      if the thread is interrupted before the operation completes. This can
	 *                                   happen due to shutdown signals.
	 */
	void tagImage(String source, String target)
		throws IOException, InterruptedException;

	/**
	 * Pulls an image from a registry.
	 *
	 * @param reference the image's reference
	 * @return an image puller
	 * @throws NullPointerException     if {@code reference} is null
	 * @throws IllegalArgumentException if {@code reference}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 */
	@CheckReturnValue
	ImagePuller pullImage(String reference);

	/**
	 * Pushes an image to a registry.
	 *
	 * @param reference the image's reference
	 * @return an image pusher
	 * @throws NullPointerException     if {@code reference} is null
	 * @throws IllegalArgumentException if {@code reference}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 */
	@CheckReturnValue
	ImagePusher pushImage(String reference) throws IOException, InterruptedException;

	/**
	 * Removes an image.
	 *
	 * @param id the image's ID or {@link Image reference}
	 * @return an image remover
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 */
	@CheckReturnValue
	ImageRemover removeImage(String id);

	/**
	 * Looks up a network by its ID or name.
	 *
	 * @param id the ID or name
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id} contains whitespace, or is empty
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	Network getNetwork(String id) throws IOException, InterruptedException;

	/**
	 * Lists all the nodes.
	 *
	 * @return the nodes
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	List<NodeElement> listNodes() throws IOException, InterruptedException;

	/**
	 * Lists the manager nodes in the swarm.
	 *
	 * @return the manager nodes
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	List<NodeElement> listManagerNodes() throws IOException, InterruptedException;

	/**
	 * Lists the worker nodes in the swarm.
	 *
	 * @return the worker nodes
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	List<NodeElement> listWorkerNodes() throws IOException, InterruptedException;

	/**
	 * Looks up the current node's ID.
	 *
	 * @return the ID
	 * @throws NotSwarmMemberException if the current node is not a member of a swarm
	 * @throws IOException             if an I/O error occurs. These errors are typically transient, and
	 *                                 retrying the request may resolve the issue.
	 * @throws InterruptedException    if the thread is interrupted before the operation completes. This can
	 *                                 happen due to shutdown signals.
	 */
	String getNodeId() throws IOException, InterruptedException;

	/**
	 * Looks up a node by its ID or hostname.
	 *
	 * @return the node
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	Node getNode() throws IOException, InterruptedException;

	/**
	 * Looks up a node by its ID or hostname.
	 *
	 * @param id the node's ID or hostname
	 * @return null if no match is found
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id} contains whitespace or is empty
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws FileNotFoundException    if the node's unix socket endpoint does not exist
	 * @throws ConnectException         if the node's TCP/IP socket refused a connection
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	Node getNode(String id) throws IOException, InterruptedException;

	/**
	 * Lists the tasks that are currently assigned to the current node.
	 * <p>
	 * This includes tasks in active lifecycle states such as {@code New}, {@code Allocated}, {@code Pending},
	 * {@code Assigned}, {@code Accepted}, {@code Preparing}, {@code Ready}, {@code Starting}, and
	 * {@code Running}. These states represent tasks that are in progress or actively running and are reliably
	 * returned by this command.
	 * <p>
	 * However, tasks that have reached a terminal state—such as {@code Complete}, {@code Failed}, or
	 * {@code Shutdown}— are often pruned by Docker shortly after they exit, and are therefore not guaranteed to
	 * appear in the results, even if they completed very recently.
	 * <p>
	 * Note that Docker prunes old tasks aggressively from this command, so {@link #listTasksByService(String)}
	 * will often provide more comprehensive historical data by design.
	 *
	 * @return the tasks
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	List<Task> listTasksByNode() throws IOException, InterruptedException;

	/**
	 * Lists the tasks that are currently assigned to a node.
	 * <p>
	 * Note that Docker prunes old tasks aggressively from this command, so {@link #listTasksByService(String)}
	 * will often provide more comprehensive historical data by design.
	 *
	 * @param id the node's ID or hostname
	 * @return the tasks
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id} contains whitespace or is empty
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	List<Task> listTasksByNode(String id) throws IOException, InterruptedException;

	/**
	 * Lists a service's tasks.
	 *
	 * @param id the service's ID or name
	 * @return the tasks
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id} contains whitespace or is empty
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	List<Task> listTasksByService(String id) throws IOException, InterruptedException;

	/**
	 * Creates a service.
	 *
	 * @param imageId the image ID or {@link Image reference} to create the service from
	 * @return a service creator
	 * @throws NullPointerException     if {@code imageId} is null
	 * @throws IllegalArgumentException if {@code imageId}'s format is invalid
	 */
	ServiceCreator createService(String imageId);

	/**
	 * Begins gracefully removing tasks from this node and redistribute them to other active nodes.
	 *
	 * @param id the node's ID or hostname
	 * @return the node's updated ID
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id} contains whitespace or is empty
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	String drainNode(String id) throws IOException, InterruptedException;

	/**
	 * Changes the type of the node.
	 *
	 * @param id   the node's ID or hostname
	 * @param type the new type
	 * @return the node's updated ID
	 * @throws NullPointerException if {@code type} is null
	 * @throws LastManagerException if the request demotes the last manager of the swarm
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	String setNodeType(String id, Type type) throws IOException, InterruptedException;

	/**
	 * Removes a node from the swarm.
	 *
	 * @return an node remover
	 */
	@CheckReturnValue
	NodeRemover removeNode();

	/**
	 * Creates a swarm.
	 *
	 * @return a swarm creator
	 */
	@CheckReturnValue
	SwarmCreator createSwarm();

	/**
	 * Joins an existing swarm.
	 *
	 * @return a swarm joiner
	 */
	@CheckReturnValue
	SwarmJoiner joinSwarm();

	/**
	 * Leaves a swarm.
	 *
	 * @return a swarm leaver
	 */
	@CheckReturnValue
	SwarmLeaver leaveSwarm();

	/**
	 * Returns the secret value needed to join the swarm as a manager.
	 *
	 * @return the join token
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	JoinToken getManagerJoinToken() throws IOException, InterruptedException;

	/**
	 * Returns the secret value needed to join the swarm as a worker.
	 *
	 * @return the join token
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	JoinToken getWorkerJoinToken() throws IOException, InterruptedException;
}