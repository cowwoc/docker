package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.docker.resource.Node.Type;

import java.net.InetSocketAddress;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;

/**
 * The information needed to join an existing swarm.
 *
 * @param type           the type of node that may use this token
 * @param token          the secret value needed to join the swarm
 * @param managerAddress the {@link SwarmCreator#listenAddress(InetSocketAddress) listenAddress} of a manager
 *                       node that is a member of the swarm
 */
public record JoinToken(Type type, String token, InetSocketAddress managerAddress)
{
	/**
	 * Creates a token.
	 *
	 * @param type           the type of node that may use this token
	 * @param token          the secret value needed to join the swarm
	 * @param managerAddress the {@link SwarmCreator#listenAddress(InetSocketAddress) listenAddress} of a
	 *                       manager node that is a member of the swarm
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain whitespace or are empty
	 */
	public JoinToken
	{
		requireThat(type, "type").isNotNull();
		requireThat(token, "token").doesNotContainWhitespace().isNotNull();
		requireThat(managerAddress, "managerAddress").isNotNull();
	}
}