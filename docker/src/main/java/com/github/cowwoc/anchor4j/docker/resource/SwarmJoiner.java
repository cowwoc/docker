package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.exception.AlreadySwarmMemberException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.requirements11.annotation.CheckReturnValue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Joins an existing Swarm.
 */
public final class SwarmJoiner
{
	private final InternalDocker client;
	private String advertiseAddress = "";
	private InetAddress dataPathAddress;
	private InetSocketAddress listenAddress;

	/**
	 * Creates a swarm joiner.
	 *
	 * @param client the client configuration
	 */
	SwarmJoiner(InternalDocker client)
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
	public SwarmJoiner advertiseAddress(InetSocketAddress advertiseAddress)
	{
		requireThat(advertiseAddress, "advertiseAddress").isNotNull();
		int port = advertiseAddress.getPort();
		if (port == 0)
			port = 2377;
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
	public SwarmJoiner advertiseAddress(String advertiseAddress)
	{
		requireThat(advertiseAddress, "advertiseAddress").doesNotContainWhitespace().isNotEmpty();
		this.advertiseAddress = advertiseAddress;
		return this;
	}

	/**
	 * Sets the address that the node uses to listen for inter-container communication (e.g., {@code 0.0.0.0}).
	 * <p>
	 * The port is {@link SwarmCreator#dataPathAddress(InetSocketAddress) configured} at swarm creation time and
	 * cannot be changed.
	 *
	 * @param dataPathAddress the address
	 * @return this
	 * @throws NullPointerException if {@code dataPathAddress} is null
	 */
	public SwarmJoiner dataPathAddress(InetAddress dataPathAddress)
	{
		requireThat(dataPathAddress, "dataPathAddress").isNotNull();
		this.dataPathAddress = dataPathAddress;
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
	public SwarmJoiner listenAddress(InetSocketAddress listenAddress)
	{
		requireThat(listenAddress, "listenAddress").isNotNull();
		this.listenAddress = listenAddress;
		return this;
	}

	/**
	 * Joins an existing swarm.
	 *
	 * @param joinToken the secret value needed to join the swarm
	 * @throws NullPointerException        if {@code joinToken} is null
	 * @throws IllegalArgumentException    if the join token is invalid
	 * @throws AlreadySwarmMemberException if the server is already a member of a swarm
	 * @throws FileNotFoundException       if a referenced unix socket was not found
	 * @throws ConnectException            if a referenced TCP/IP socket refused a connection
	 * @throws IOException                 if an I/O error occurs. These errors are typically transient, and
	 *                                     retrying the request may resolve the issue.
	 * @throws InterruptedException        if the thread is interrupted before the operation completes. This can
	 *                                     happen due to shutdown signals.
	 */
	@CheckReturnValue
	public void join(JoinToken joinToken) throws IOException, InterruptedException
	{
		requireThat(joinToken, "joinToken").isNotNull();

		// https://docs.docker.com/reference/cli/docker/swarm/join/
		List<String> arguments = new ArrayList<>(11);
		arguments.add("swarm");
		arguments.add("join");
		if (!advertiseAddress.isEmpty())
		{
			arguments.add("--advertise-addr");
			arguments.add(advertiseAddress);
		}
		if (dataPathAddress != null)
		{
			arguments.add("--data-path-addr");
			arguments.add(dataPathAddress.getHostAddress());
		}
		if (listenAddress != null)
		{
			arguments.add("--listen-addr");
			arguments.add(listenAddress.getHostString() + ":" + listenAddress.getPort());
		}
		arguments.add("--token");
		arguments.add(joinToken.token());
		InetSocketAddress managerAddress = joinToken.managerAddress();
		arguments.add(managerAddress.getHostString() + ":" + managerAddress.getPort());
		CommandResult result = client.run(arguments);
		client.getSwarmParser().join(result);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(SwarmJoiner.class).
			add("advertiseAddress", advertiseAddress).
			add("dataPathAddress", dataPathAddress).
			add("listenAddress", listenAddress).
			toString();
	}
}