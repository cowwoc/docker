package com.github.cowwoc.docker.client;

import com.github.cowwoc.docker.internal.client.MainInternalClient;
import org.eclipse.jetty.io.Transport;
import org.eclipse.jetty.io.Transport.TCPUnix;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A client of the Docker REST API.
 */
public interface DockerClient extends AutoCloseable
{
	/**
	 * Creates a client that communicates with the server over a Unix socket.
	 *
	 * @param docker the URI of the Docker REST server (e.g. {@code unix:///var/run/docker.sock} or
	 *               {@code npipe://./pipe/docker_engine})
	 * @param mode   the runtime mode
	 * @return a new client
	 * @throws NullPointerException if any of the arguments are null
	 * @throws IOException          if an error occurs when connecting to the docker or BuildKit server
	 */
	static DockerClient connect(URI docker, RunMode mode) throws IOException
	{
		requireThat(docker, "docker").isNotNull();

		Transport dockerTransport = switch (docker.getScheme())
		{
			case "tcp", "npipe" -> Transport.TCP_IP;
			case "unix" -> new TCPUnix(Path.of(docker));
			default -> throw new IllegalArgumentException("Unsupported protocol: " + docker);
		};

		return new MainInternalClient(docker, dockerTransport, mode);
	}

	/**
	 * Determines if the client is closed.
	 *
	 * @return {@code true} if the client is closed
	 */
	boolean isClosed();

	/**
	 * Closes the client.
	 */
	@Override
	void close();
}