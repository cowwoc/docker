package com.github.cowwoc.anchor4j.docker.internal.client;

import com.github.cowwoc.anchor4j.core.internal.client.AbstractInternalClient;
import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.resource.ImageBuilder;
import com.github.cowwoc.anchor4j.docker.client.Docker;
import com.github.cowwoc.anchor4j.docker.exception.ContextNotFoundException;
import com.github.cowwoc.anchor4j.docker.exception.NotSwarmManagerException;
import com.github.cowwoc.anchor4j.docker.internal.resource.ConfigParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.ContainerParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.ContextParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.ImageParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.NetworkParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.NodeParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.ServiceParser;
import com.github.cowwoc.anchor4j.docker.internal.resource.SharedSecrets;
import com.github.cowwoc.anchor4j.docker.internal.resource.SwarmParser;
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The default implementation of {@code InternalDocker}.
 */
public final class DefaultDocker extends AbstractInternalClient
	implements InternalDocker
{
	// Variants seen:
	// ERROR: unable to parse docker host `([^`]+)`
	// ERROR: no valid drivers found: unable to parse docker host `([^`]+)`
	public static final Pattern UNABLE_TO_PARSE_DOCKER_HOST = Pattern.compile("ERROR: (?:.*?: )?" +
		"unable to parse docker host `([^`]+)`");
	private static final Pattern FILE_IN_USE_BY_ANOTHER_PROCESS = Pattern.compile("ERROR: open (.+?): " +
		"The process cannot access the file because it is being used by another process\\.");
	private String clientContext = "";
	@SuppressWarnings("this-escape")
	private final ConfigParser configParser = new ConfigParser(this);
	@SuppressWarnings("this-escape")
	private final ContainerParser containerParser = new ContainerParser(this);
	@SuppressWarnings("this-escape")
	private final ImageParser imageParser = new ImageParser(this);
	@SuppressWarnings("this-escape")
	private final ContextParser contextParser = new ContextParser(this);
	@SuppressWarnings("this-escape")
	private final NetworkParser networkParser = new NetworkParser(this);
	@SuppressWarnings("this-escape")
	private final ServiceParser serviceParser = new ServiceParser(this);
	@SuppressWarnings("this-escape")
	private final NodeParser nodeParser = new NodeParser(this);
	@SuppressWarnings("this-escape")
	private final SwarmParser swarmParser = new SwarmParser(this);

	/**
	 * Creates a client.
	 *
	 * @param executable the path of the Docker client
	 * @throws NullPointerException     if {@code executable} is null
	 * @throws IllegalArgumentException if the path referenced by {@code executable} does not exist or is not a
	 *                                  file
	 * @throws IOException              if an I/O error occurs while reading {@code executable}'s attributes
	 */
	public DefaultDocker(Path executable) throws IOException
	{
		super(executable);
	}

	@Override
	public ProcessBuilder getProcessBuilder(List<String> arguments)
	{
		List<String> command = new ArrayList<>(arguments.size() + 3);
		command.add(executable.toString());
		if (!clientContext.isEmpty())
		{
			command.add("--context");
			command.add(clientContext);
		}
		command.addAll(arguments);
		return new ProcessBuilder(command);
	}

	@Override
	public ImageBuilder buildImage()
	{
		return com.github.cowwoc.anchor4j.core.internal.resource.SharedSecrets.buildImage(this, (_, error) ->
		{
			// WORKAROUND: https://github.com/moby/moby/issues/50160
			Matcher matcher = UNABLE_TO_PARSE_DOCKER_HOST.matcher(error);
			if (matcher.matches())
				throw new ContextNotFoundException(matcher.group(1));
			matcher = FILE_IN_USE_BY_ANOTHER_PROCESS.matcher(error);
			if (matcher.matches())
			{
				throw new IOException("Failed to build the image because a file is being used by another " +
					"process.\n" +
					"File     : " + matcher.group(1));
			}
		});
	}

	@Override
	public ConfigParser getConfigParser()
	{
		return configParser;
	}

	@Override
	public ContainerParser getContainerParser()
	{
		return containerParser;
	}

	@Override
	public ImageParser getImageParser()
	{
		return imageParser;
	}

	@Override
	public ContextParser getContextParser()
	{
		return contextParser;
	}

	@Override
	public NetworkParser getNetworkParser()
	{
		return networkParser;
	}

	@Override
	public ServiceParser getServiceParser()
	{
		return serviceParser;
	}

	@Override
	public NodeParser getNodeParser()
	{
		return nodeParser;
	}

	@Override
	public SwarmParser getSwarmParser()
	{
		return swarmParser;
	}

	@Override
	public Docker login(String username, String password)
		throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/login/
		List<String> arguments = List.of("login", "--username", username, "--password-stdin");
		run(arguments, ByteBuffer.wrap(password.getBytes(UTF_8)));
		return this;
	}

	@Override
	public Docker login(String username, String password, String serverAddress)
		throws IOException, InterruptedException
	{
		requireThat(username, "username").doesNotContainWhitespace().isNotEmpty();
		requireThat(password, "password").doesNotContainWhitespace().isNotEmpty();
		requireThat(serverAddress, "serverAddress").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/login/
		List<String> arguments = List.of("login", "--username", username, "--password-stdin", serverAddress);
		run(arguments, ByteBuffer.wrap(password.getBytes(UTF_8)));
		return this;
	}

	@Override
	public List<ConfigElement> listConfigs() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/config/ls/
		List<String> arguments = List.of("config", "ls", "--format", "json");
		CommandResult result = run(arguments);
		return getConfigParser().list(result);
	}

	@Override
	public Config getConfig(String id) throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/config/inspect/
		List<String> arguments = List.of("config", "inspect", id);
		CommandResult result = run(arguments);
		return getConfigParser().get(result);
	}

	@Override
	public ConfigCreator createConfig()
	{
		return SharedSecrets.createConfig(this);
	}

	@Override
	public List<ContainerElement> listContainers() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/container/ls/
		List<String> arguments = List.of("container", "ls", "--format", "json", "--all", "--no-trunc");
		CommandResult result = run(arguments);
		return getContainerParser().list(result);
	}

	@Override
	public Container getContainer(String id) throws IOException, InterruptedException
	{
		requireThat(id, "id").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/container/inspect/
		List<String> arguments = List.of("container", "inspect", id);
		CommandResult result = run(arguments);
		return getContainerParser().get(result);
	}

	@Override
	public ContainerCreator createContainer(String imageId)
	{
		return SharedSecrets.createContainer(this, imageId);
	}

	@Override
	public Docker renameContainer(String oldName, String newName)
		throws IOException, InterruptedException
	{
		validateName(oldName, "oldName");
		validateName(newName, "newName");

		// https://docs.docker.com/reference/cli/docker/container/rename/
		List<String> arguments = List.of("container", "rename", oldName, newName);
		CommandResult result = run(arguments);
		getContainerParser().rename(result);
		return this;
	}

	@Override
	public ContainerStarter startContainer(String id)
	{
		return SharedSecrets.startContainer(this, id);
	}

	@Override
	public ContainerStopper stopContainer(String id)
	{
		return SharedSecrets.stopContainer(this, id);
	}

	@Override
	public ContainerRemover removeContainer(String id)
	{
		return SharedSecrets.removeContainer(this, id);
	}

	@Override
	public int waitUntilContainerStops(String id) throws IOException, InterruptedException
	{
		requireThat(id, "id").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/container/wait/
		List<String> arguments = List.of("container", "wait", id);
		CommandResult result = run(arguments);
		return getContainerParser().waitUntilStopped(result);
	}

	@Override
	public ContainerLogGetter getContainerLogs(String id)
	{
		return SharedSecrets.getContainerLogs(this, id);
	}

	@Override
	public List<ContextElement> listContexts() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/context/ls/
		List<String> arguments = List.of("context", "ls", "--format", "json");
		CommandResult result = run(arguments);
		return getContextParser().list(result);
	}

	@Override
	public Context getContext(String name) throws IOException, InterruptedException
	{
		requireThat(name, "name").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/context/inspect/
		List<String> arguments = List.of("context", "inspect", name);
		CommandResult result = run(arguments);
		return getContextParser().getByName(result);
	}

	@Override
	public ContextCreator createContext(String name, ContextEndpoint endpoint)
	{
		return SharedSecrets.createContext(this, name, endpoint);
	}

	@Override
	public ContextRemover removeContext(String name)
	{
		return SharedSecrets.removeContext(this, name);
	}

	@Override
	public String getClientContext()
	{
		return clientContext;
	}

	@Override
	public Docker setClientContext(String name)
	{
		requireThat(name, "name").doesNotContainWhitespace();
		this.clientContext = name;
		return this;
	}

	@Override
	public String getUserContext() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/context/show/
		List<String> arguments = List.of("context", "show");
		CommandResult result = run(arguments);
		return getContextParser().show(result);
	}

	@Override
	public Docker setUserContext(String name) throws IOException, InterruptedException
	{
		requireThat(name, "name").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/context/use/
		List<String> arguments = List.of("context", "use", name);
		CommandResult result = run(arguments);
		getContextParser().use(result);
		return this;
	}

	@Override
	public List<ImageElement> listImages() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/image/ls/
		List<String> arguments = List.of("image", "ls", "--format", "json", "--all", "--digests", "--no-trunc");
		CommandResult result = run(arguments);
		return getImageParser().list(result);
	}

	@Override
	public Image getImage(String id) throws IOException, InterruptedException
	{
		validateImageReference(id, "id");

		// https://docs.docker.com/reference/cli/docker/image/inspect/
		List<String> arguments = List.of("image", "inspect", "--format", "json", id);
		CommandResult result = run(arguments);
		return getImageParser().get(result);
	}

	@Override
	public void tagImage(String source, String target) throws IOException, InterruptedException
	{
		validateImageIdOrReference(source, "source");
		validateImageReference(target, "target");

		// https://docs.docker.com/reference/cli/docker/image/tag/
		List<String> arguments = List.of("image", "tag", source, target);
		CommandResult result = run(arguments);
		getImageParser().tag(result);
	}

	@Override
	public ImagePuller pullImage(String reference)
	{
		return SharedSecrets.pullImage(this, reference);
	}

	@Override
	public ImagePusher pushImage(String reference)
	{
		return SharedSecrets.pushImage(this, reference);
	}

	@Override
	public ImageRemover removeImage(String id)
	{
		return SharedSecrets.removeImage(this, id);
	}

	@Override
	public Network getNetwork(String id) throws IOException, InterruptedException
	{
		requireThat(id, "id").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/network/inspect/
		List<String> arguments = List.of("network", "inspect", id);
		CommandResult result = run(arguments);
		return getNetworkParser().get(result);
	}

	@Override
	public List<NodeElement> listNodes() throws IOException, InterruptedException
	{
		return listNodes(List.of());
	}

	/**
	 * Returns nodes that match the specified filters.
	 *
	 * @param filters the filters to apply to the list
	 * @return the matching nodes
	 * @throws NullPointerException     if {@code filters} is null
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	private List<NodeElement> listNodes(List<String> filters) throws IOException, InterruptedException
	{
		assert that(filters, "filters").isNotNull().elseThrow();

		// https://docs.docker.com/reference/cli/docker/node/ls/
		List<String> arguments = new ArrayList<>(4 + filters.size() * 2);
		arguments.add("node");
		arguments.add("ls");
		arguments.add("--format");
		arguments.add("json");
		for (String filter : filters)
		{
			arguments.add("--filter");
			arguments.add("\"" + filter + "\"");
		}
		CommandResult result = run(arguments);
		return getNodeParser().listNodes(result);
	}

	@Override
	public List<NodeElement> listManagerNodes() throws IOException, InterruptedException
	{
		return listNodes(List.of("role=manager"));
	}

	@Override
	public List<NodeElement> listWorkerNodes() throws IOException, InterruptedException
	{
		return listNodes(List.of("role=worker"));
	}

	@Override
	public String getNodeId() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/system/info/
		List<String> arguments = new ArrayList<>(4);
		arguments.add("system");
		arguments.add("info");
		arguments.add("--format");
		arguments.add("\"{{json .Swarm.NodeID}}\"");
		CommandResult result = run(arguments);
		return getNodeParser().getNodeId(result);
	}

	@Override
	public Node getNode() throws IOException, InterruptedException
	{
		return getNode("self");
	}

	@Override
	public Node getNode(String id) throws IOException, InterruptedException
	{
		requireThat(id, "id").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/node/inspect/
		List<String> arguments = List.of("node", "inspect", id);
		CommandResult result = run(arguments);
		return getNodeParser().get(result);
	}

	@Override
	public ServiceCreator createService(String imageId)
	{
		return SharedSecrets.createService(this, imageId);
	}

	@Override
	public List<Task> listTasksByNode() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/node/ps/
		List<String> arguments = new ArrayList<>(5);
		arguments.add("node");
		arguments.add("ps");
		arguments.add("--format");
		arguments.add("json");
		arguments.add("--no-trunc");
		CommandResult result = run(arguments);
		return getNodeParser().listTasksByNode(result);
	}

	@Override
	public List<Task> listTasksByNode(String id) throws IOException, InterruptedException
	{
		requireThat(id, "id").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/node/ps/
		List<String> arguments = new ArrayList<>(6);
		arguments.add("node");
		arguments.add("ps");
		arguments.add("--format");
		arguments.add("json");
		arguments.add("--no-trunc");
		arguments.add(id);
		CommandResult result = run(arguments);
		return getNodeParser().listTasksByNode(result);
	}

	@Override
	public List<Task> listTasksByService(String id) throws IOException, InterruptedException
	{
		requireThat(id, "id").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/service/ps/
		List<String> arguments = new ArrayList<>(3);
		arguments.add("service");
		arguments.add("ps");
		arguments.add("--format");
		arguments.add("json");
		arguments.add(id);
		CommandResult result = run(arguments);
		return getNodeParser().listTasksByService(result);
	}

	@Override
	public String setNodeType(String id, Type type) throws IOException, InterruptedException
	{
		requireThat(id, "id").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/node/update/
		List<String> arguments = List.of("node", "update", "--role=" + type.toCommandLine(), id);
		CommandResult result = run(arguments);
		return getNodeParser().update(result);
	}

	@Override
	public String drainNode(String id) throws IOException, InterruptedException
	{
		requireThat(id, "id").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/node/update/
		List<String> arguments = List.of("node", "update", "--availability=drain", id);
		CommandResult result = run(arguments);
		return getNodeParser().update(result);
	}

	@Override
	public NodeRemover removeNode()
	{
		return SharedSecrets.removeNode(this);
	}

	@Override
	public SwarmCreator createSwarm()
	{
		return SharedSecrets.createSwarm(this);
	}

	@Override
	public SwarmJoiner joinSwarm()
	{
		return SharedSecrets.joinSwarm(this);
	}

	@Override
	public SwarmLeaver leaveSwarm()
	{
		return SharedSecrets.leaveSwarm(this);
	}

	@Override
	public JoinToken getManagerJoinToken() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/swarm/join-token/
		List<String> arguments = List.of("swarm", "join-token", "manager");
		CommandResult result = run(arguments);
		return getSwarmParser().getJoinToken(result, Type.MANAGER);
	}

	@Override
	public JoinToken getWorkerJoinToken() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/swarm/join-token/
		List<String> arguments = List.of("swarm", "join-token", "worker");
		CommandResult result = run(arguments);
		return getSwarmParser().getJoinToken(result, Type.WORKER);
	}
}