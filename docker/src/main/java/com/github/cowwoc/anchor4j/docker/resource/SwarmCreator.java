package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.exception.AlreadySwarmMemberException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.internal.resource.SharedSecrets;
import com.github.cowwoc.anchor4j.docker.internal.resource.SwarmAccess;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Creates a Swarm.
 */
public final class SwarmCreator
{
	static
	{
		SharedSecrets.setSwarmAccess(new SwarmAccess()
		{
			@Override
			public SwarmCreator create(InternalDocker client)
			{
				return new SwarmCreator(client);
			}

			@Override
			public SwarmJoiner join(InternalDocker client)
			{
				return new SwarmJoiner(client);
			}

			@Override
			public SwarmLeaver leave(InternalDocker client)
			{
				return new SwarmLeaver(client);
			}
		});
	}

	public static final int DEFAULT_LISTEN_PORT = 2377;
	private final InternalDocker client;
	private String advertiseAddress = "";
	private InetSocketAddress dataPathAddress;
	private final Set<String> defaultAddressPool = new HashSet<>();
	private int subnetSize = 24;
	private InetSocketAddress listenAddress;

	/**
	 * Creates a swarm creator.
	 *
	 * @param client the client configuration
	 */
	SwarmCreator(InternalDocker client)
	{
		assert that(client, "client").isNotNull().elseThrow();
		this.client = client;
	}

	/**
	 * Sets the externally reachable address that will be advertised to other members of the swarm for API
	 * access and overlay networking. If unspecified, Docker will check if the system has a single IP address,
	 * and use that IP address with the {@link #listenAddress listening port}. If the system has multiple IP
	 * addresses, {@code advertiseAddress} must be specified so that the correct address is chosen for
	 * inter-manager communication and overlay networking.
	 * <p>
	 * If the specified port is {@code 0}, the default port {@code 2377} will be used instead.
	 *
	 * @param advertiseAddress the address
	 * @return this
	 * @throws NullPointerException if {@code advertiseAddress} is null
	 */
	public SwarmCreator advertiseAddress(InetSocketAddress advertiseAddress)
	{
		requireThat(advertiseAddress, "advertiseAddress").isNotNull();
		int port = advertiseAddress.getPort();
		if (port == 0)
			port = DEFAULT_LISTEN_PORT;
		this.advertiseAddress = advertiseAddress.getHostString() + ":" + port;
		return this;
	}

	/**
	 * Sets the externally reachable address that will be advertised to other members of the swarm for API
	 * access and overlay networking. If unspecified, Docker will check if the system has a single IP address,
	 * and use that IP address with the {@link #listenAddress listening port}. If the system has multiple IP
	 * addresses, {@code advertiseAddress} must be specified so that the correct address is chosen for
	 * inter-manager communication and overlay networking.
	 * <p>
	 * It is also possible to specify a network interface to advertise that interface's address; for example
	 * {@code eth0:2377}.
	 * <p>
	 * Specifying a port is optional. If the value is a bare IP address or interface name, the default port
	 * {@code 2377} is used.
	 *
	 * @param advertiseAddress the address
	 * @return this
	 * @throws NullPointerException     if {@code advertiseAddress} is null
	 * @throws IllegalArgumentException if {@code advertiseAddress} contains whitespace, or is empty
	 */
	public SwarmCreator advertiseAddress(String advertiseAddress)
	{
		requireThat(advertiseAddress, "advertiseAddress").doesNotContainWhitespace().isNotEmpty();
		this.advertiseAddress = advertiseAddress;
		return this;
	}

	/**
	 * Sets the address that the node uses to listen for inter-container communication (e.g.,
	 * {@code 0.0.0.0:4789}).
	 * <p>
	 * If the specified port is {@code 0}, the default port {@code 4789} will be used instead; otherwise, the
	 * specified port must be within the range {@code 1024} to {@code 49151}, inclusive.
	 *
	 * @param dataPathAddress the address
	 * @return this
	 * @throws NullPointerException     if {@code dataPathAddress} is null
	 * @throws IllegalArgumentException if {@code dataPathAddress}'s port number is not zero and is less than
	 *                                  {@code 1024}, or is greater than {@code 49151}
	 */
	public SwarmCreator dataPathAddress(InetSocketAddress dataPathAddress)
	{
		requireThat(dataPathAddress, "dataPathAddress").isNotNull();
		if (dataPathAddress.getPort() != 0)
			requireThat(dataPathAddress.getPort(), "dataPathAddress.getPort()").isBetween(1024, true, 49_151, true);
		this.dataPathAddress = dataPathAddress;
		return this;
	}

	/**
	 * Adds a subnet in CIDR notation to the address pool used for allocating overlay network subnets. If
	 * omitted, internal defaults are used.
	 * <p>
	 * When you initialize a Docker Swarm, Docker automatically assigns overlay networks to services. These
	 * subnets are carved from the default address pools specified here.
	 *
	 * @param defaultAddressPool the address
	 * @return this
	 * @throws NullPointerException     if {@code defaultAddressPool} is null
	 * @throws IllegalArgumentException if {@code defaultAddressPool}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>is not a valid CIDR notation.</li>
	 *                                  </ul>
	 */
	public SwarmCreator defaultAddressPool(String defaultAddressPool)
	{
		requireThat(defaultAddressPool, "defaultAddressPool").doesNotContainWhitespace().isNotEmpty();
		String[] components = defaultAddressPool.split("/");
		if (components.length != 2)
		{
			throw new IllegalArgumentException("defaultAddressPool must contain exactly one slash.\n" +
				"Actual: " + defaultAddressPool);
		}
		try
		{
			InetAddress address = InetAddress.ofLiteral(components[0]);
			int prefixLength = Integer.parseInt(components[1]);
			if (address instanceof Inet4Address)
			{
				requireThat(prefixLength, "prefixLength").withContext(defaultAddressPool, "defaultAddressPool").
					isBetween(0, true, 32, true);
			}
			assert address instanceof Inet6Address : address.getClass().getName();
			requireThat(prefixLength, "prefixLength").withContext(defaultAddressPool, "defaultAddressPool").
				isBetween(0, true, 128, true);
		}
		catch (IllegalArgumentException e)
		{
			throw new IllegalArgumentException("defaultAddressPool must be in CIDR notation.\n" +
				"Actual: " + defaultAddressPool, e);
		}
		this.defaultAddressPool.add(defaultAddressPool);
		return this;
	}

	/**
	 * Sets the CIDR prefix length for each subnet allocated from the default address pool. The default value is
	 * {@code 24}.
	 * <p>
	 * When you initialize a Docker Swarm, Docker automatically assigns subnets for overlay networks. This value
	 * controls the size of each allocated subnet. Smaller values result in larger subnets, while larger values
	 * produce smaller subnets.
	 *
	 * @param subnetSize the prefix length (e.g., {@code 24} for a {@code /24} subnet)
	 * @return this
	 * @throws IllegalArgumentException if {@code subnetSize} is less than {@code 16} or greater than
	 *                                  {@code 28}
	 */
	public SwarmCreator subnetSize(int subnetSize)
	{
		requireThat(subnetSize, "subnetSize").isBetween(16, true, 28, true);
		this.subnetSize = subnetSize;
		return this;
	}

	/**
	 * Sets the address that the current node will use to listen for inter-manager communication. The default
	 * value is {@code 0.0.0.0:2377}).
	 *
	 * @param listenAddress the address
	 * @return this
	 * @throws NullPointerException if {@code listenAddress} is null
	 */
	public SwarmCreator listenAddress(InetSocketAddress listenAddress)
	{
		requireThat(listenAddress, "listenAddress").isNotNull();
		this.listenAddress = listenAddress;
		return this;
	}

	/**
	 * Creates a Swarm cluster.
	 * <p>
	 * This method sets up the current node as a Swarm manager, creating the foundation for a distributed
	 * cluster. It configures critical networking and operational parameters required for the Swarm's
	 * functionality. The cluster can subsequently be scaled by adding worker or manager nodes.
	 * </p>
	 * <p>
	 * Networking details:
	 * <ul>
	 *   <li>{@link #listenAddress Listen Address}: The address where the current node listens for
	 *   inter-manager communication, such as leader election and cluster state updates.</li>
	 *   <li>{@link #dataPathAddress Data Path Address}: The address where the current node handles
	 *   inter-container communication over Swarm overlay networks.</li>
	 *   <li>{@link #advertiseAddress Advertise Address}: The externally reachable address that other nodes
	 *   use for connecting to the manager API and joining the Swarm.</li>
	 * </ul>
	 *
	 * @return the node's ID and the worker's join token
	 * @throws AlreadySwarmMemberException if this node is already a member of a swarm
	 * @throws IOException                 if an I/O error occurs. These errors are typically transient, and
	 *                                     retrying the request may resolve the issue.
	 * @throws InterruptedException        if the thread is interrupted before the operation completes. This can
	 *                                     happen due to shutdown signals.
	 */
	public WelcomePackage create() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/swarm/init/
		List<String> arguments = new ArrayList<>(8 + defaultAddressPool.size() * 2 + 4);
		arguments.add("swarm");
		arguments.add("init");
		if (!advertiseAddress.isEmpty())
		{
			arguments.add("--advertise-addr");
			arguments.add(advertiseAddress);
		}
		if (dataPathAddress != null)
		{
			arguments.add("--data-path-addr");
			arguments.add(dataPathAddress.getHostString());
			int port = dataPathAddress.getPort();
			if (port != 0)
			{
				arguments.add("--data-path-port");
				arguments.add(dataPathAddress.getHostString());
			}
		}
		for (String address : defaultAddressPool)
		{
			arguments.add("--default-addr-pool");
			arguments.add(address);
		}
		if (subnetSize != 24)
		{
			arguments.add("--default-addr-pool-mask-length");
			arguments.add(String.valueOf(subnetSize));
		}
		if (listenAddress != null)
		{
			arguments.add("--listen-addr");
			arguments.add(listenAddress.getHostString() + ":" + listenAddress.getPort());
		}
		CommandResult result = client.run(arguments);
		return client.getSwarmParser().create(result);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(SwarmCreator.class).
			add("advertiseAddress", advertiseAddress).
			add("dataPathAddress", dataPathAddress).
			add("defaultAddressPool", defaultAddressPool).
			add("subnetSize", subnetSize).
			add("listenAddress", listenAddress).
			toString();
	}

	/**
	 * The information provided on successfully creating and joining a new swarm.
	 *
	 * @param nodeId          the current node's ID
	 * @param workerJoinToken the secret value needed to join the swarm as a worker
	 */
	public record WelcomePackage(String nodeId, JoinToken workerJoinToken)
	{
		/**
		 * @param nodeId          the current node's ID
		 * @param workerJoinToken the secret value needed to join the swarm as a worker
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if {@code nodeId} contains whitespace or is empty
		 */
		public WelcomePackage
		{
			requireThat(nodeId, "nodeId").doesNotContainWhitespace().isNotEmpty();
			requireThat(workerJoinToken, "workerJoinToken").isNotNull();
		}
	}
}