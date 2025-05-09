package com.github.cowwoc.docker.internal.buildkit;

import com.github.cowwoc.docker.internal.client.BuildKitConnection;
import com.github.cowwoc.docker.internal.client.InternalClient;
import com.github.moby.buildkit.v1.ControlGrpc;
import com.github.moby.buildkit.v1.ControlGrpc.ControlStub;

import java.nio.file.Path;
import java.util.UUID;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;

/**
 * A session retains an authenticated connection and secrets across multiple builds.
 */
public final class Session
{
	private final InternalClient client;
	private final BuildKitConnection connection;
	private final ControlStub control;
	private final String id;

	/**
	 * Creates a new session.
	 *
	 * @param client     the client configuration
	 * @param connection a connection to the server
	 * @param control    the control API
	 * @param id         the ID of the session
	 * @throws AssertionError if:
	 *                        <ul>
	 *                        <li>any of the arguments are null</li>
	 *                        <li>{@code id} contains leading or trailing whitespace or is empty</li>
	 *                        </ul>
	 */
	private Session(InternalClient client, BuildKitConnection connection, ControlStub control, String id)
	{
		assert that(client, "client").isNotNull().elseThrow();
		assert that(connection, "connection").isNotNull().elseThrow();
		assert that(control, "control").isNotNull().elseThrow();
		assert that(id, "id").isStripped().isNotEmpty().elseThrow();
		this.client = client;
		this.connection = connection;
		this.control = control;
		this.id = id;
	}

	/**
	 * Returns the client configuration.
	 *
	 * @return the configuration
	 */
	public InternalClient getClient()
	{
		return client;
	}

	/**
	 * Returns the connection to the server.
	 *
	 * @return the connection
	 */
	public BuildKitConnection getConnection()
	{
		return connection;
	}

	/**
	 * Returns the control API.
	 *
	 * @return the control
	 */
	public ControlStub getControl()
	{
		return control;
	}

	/**
	 * Returns the session's ID.
	 *
	 * @return the id
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Creates a new Session.
	 *
	 * @param client     the client configuration
	 * @param connection a connection to the server
	 * @return the session
	 * @throws AssertionError if any of the arguments are null
	 */
	public static Session create(InternalClient client, BuildKitConnection connection)
	{
		String id = UUID.randomUUID().toString();
		ControlStub control = ControlGrpc.newStub(connection.channel());

		return new Session(client, connection, control, id);
	}

	/**
	 * Creates a new build.
	 *
	 * @param buildContext the path of the build context
	 * @param dockerfile   the path of the Dockerfile
	 * @return the build
	 */
	public Build createBuild(Path buildContext, Path dockerfile)
	{
		return new Build(this, buildContext, dockerfile);
	}
}