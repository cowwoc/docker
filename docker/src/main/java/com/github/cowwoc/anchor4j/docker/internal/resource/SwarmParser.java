package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.resource.AbstractParser;
import com.github.cowwoc.anchor4j.core.internal.util.Strings;
import com.github.cowwoc.anchor4j.docker.client.Docker;
import com.github.cowwoc.anchor4j.docker.exception.AlreadySwarmMemberException;
import com.github.cowwoc.anchor4j.docker.exception.NotSwarmManagerException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.JoinToken;
import com.github.cowwoc.anchor4j.docker.resource.Node.Type;
import com.github.cowwoc.anchor4j.docker.resource.SwarmCreator.WelcomePackage;
import com.github.cowwoc.anchor4j.docker.resource.SwarmLeaver;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.cowwoc.anchor4j.docker.internal.resource.NodeParser.NOT_SWARM_MANAGER;

/**
 * Parses server responses to {@code Swarm} commands.
 */
public class SwarmParser extends AbstractParser
{
	private static final Pattern CREATE_SWARM_PATTERN = Pattern.compile("""
		Swarm initialized: current node \\(([^)]+)\\) is now a manager\\.
		
		To add a worker to this swarm, run the following command:
		
		 *docker swarm join --token ([^ ]+) (.+?)
		
		To add a manager to this swarm, run 'docker swarm join-token manager' and follow the instructions\\.""");
	private static final Pattern JOIN_TOKEN_PATTERN = Pattern.compile("""
		To add a (?:manager|worker) to this swarm, run the following command:
		
		 *docker swarm join --token ([^ ]+) (.+?)
		""");
	private static final Pattern JOIN_SWARM_PATTERN = Pattern.compile("This node joined a swarm as a " +
		"(manager|worker)\\.");
	private static final String ALREADY_IN_SWARM = "Error response from daemon: This node is already part " +
		"of a swarm. Use \"docker swarm leave\" to leave this swarm and join another one.";
	private static final Pattern CONNECTION_REFUSED = Pattern.compile("Error response from daemon: " +
		"rpc error: code = Unavailable desc = connection error: desc = \"transport: Error while dialing: dial " +
		"(.+?): connect: connection refused\"");
	private static final String INVALID_JOIN_TOKEN = "Error response from daemon: invalid join token";

	/**
	 * Creates a parser.
	 *
	 * @param client the client configuration
	 */
	public SwarmParser(InternalDocker client)
	{
		super(client);
	}

	/**
	 * Creates and joins a new swarm.
	 *
	 * @param result the result of executing a command
	 * @return the manager ID and the worker's join token
	 * @throws AlreadySwarmMemberException if this node is already a member of a swarm
	 */
	public WelcomePackage create(CommandResult result)
	{
		if (result.exitCode() != 0)
		{
			if (result.stderr().equals(ALREADY_IN_SWARM))
				throw new AlreadySwarmMemberException();
			throw result.unexpectedResponse();
		}
		Matcher matcher = CREATE_SWARM_PATTERN.matcher(result.stdout());
		if (!matcher.matches())
			throw result.unexpectedResponse();
		String nodeId = matcher.group(1);
		String token = matcher.group(2);
		InetSocketAddress managerAddress = Strings.toInetSocketAddress(matcher.group(3));
		JoinToken joinToken = new JoinToken(Type.WORKER, token, managerAddress);
		return new WelcomePackage(nodeId, joinToken);
	}

	/**
	 * Returns the secret value needed to join the swarm as a manager or a worker.
	 *
	 * @param result the result of executing a command
	 * @param type   the type of the join token
	 * @return the join token
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 */
	public JoinToken getJoinToken(CommandResult result, Type type)
	{
		if (result.exitCode() != 0)
		{
			if (result.stderr().startsWith(NOT_SWARM_MANAGER))
				throw new NotSwarmManagerException();
			throw result.unexpectedResponse();
		}
		Matcher matcher = JOIN_TOKEN_PATTERN.matcher(result.stdout());
		if (!matcher.matches())
			throw result.unexpectedResponse();
		InetSocketAddress managerAddress = Strings.toInetSocketAddress(matcher.group(2));
		return new JoinToken(type, matcher.group(1), managerAddress);
	}

	/**
	 * Joins an existing swarm.
	 *
	 * @param result the result of executing a command
	 * @return the type of the current node
	 * @throws IllegalArgumentException    if the join token is invalid
	 * @throws AlreadySwarmMemberException if this node is already a member of a swarm
	 * @throws ConnectException            if the {@link Docker#getClientContext() referenced context}
	 *                                     referenced a TCP/IP socket that refused a connection
	 */
	public Type join(CommandResult result) throws ConnectException
	{
		if (result.exitCode() != 0)
		{
			String stderr = result.stderr();
			if (stderr.equals(ALREADY_IN_SWARM))
				throw new AlreadySwarmMemberException();
			Matcher matcher = CONNECTION_REFUSED.matcher(stderr);
			if (matcher.matches())
				throw new ConnectException("Connection refused: " + matcher.group(1));
			if (stderr.equals(INVALID_JOIN_TOKEN))
				throw new IllegalArgumentException("Invalid join token");
			throw result.unexpectedResponse();
		}
		Matcher matcher = JOIN_SWARM_PATTERN.matcher(result.stdout());
		if (!matcher.matches())
			throw result.unexpectedResponse();
		return Type.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
	}

	/**
	 * Leaves a swarm.
	 *
	 * @param result the result of executing a command
	 * @throws ResourceInUseException if the node is a manager and {@link SwarmLeaver#force()} was not used. The
	 *                                safe way to remove a manager from a swarm is to demote it to a worker and
	 *                                then direct it to leave the quorum without using {@code force}. Only use
	 *                                {@code force} in situations where the swarm will no longer be used after
	 *                                the manager leaves, such as in a single-node swarm.
	 */
	public void leave(CommandResult result) throws ResourceInUseException
	{
		if (result.exitCode() != 0)
		{
			if (result.stderr().equals("Error response from daemon: " +
				"You are attempting to leave the swarm on a node that is participating as a manager. " +
				"Removing the last manager erases all current state of the swarm. " +
				"Use `--force` to ignore this message."))
			{
				throw new ResourceInUseException("To safely remove this manager from the swarm, first demote it " +
					"to a worker, then leave the quorum. If you intend to remove the final manager and erase the " +
					"swarm's state, use SwarmLeaver.force().");
			}
			throw result.unexpectedResponse();
		}
		if (!result.stdout().equals("Node left the swarm."))
			throw result.unexpectedResponse();
	}
}