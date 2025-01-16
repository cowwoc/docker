package com.github.cowwoc.docker.client;

import com.github.cowwoc.docker.internal.client.InternalClient;
import com.github.cowwoc.docker.internal.client.MainDockerClient;
import org.eclipse.jetty.io.Transport;
import org.eclipse.jetty.io.Transport.TCPUnix;

import java.net.URI;
import java.nio.file.Path;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A client of the Docker REST API.
 */
public interface DockerClient extends AutoCloseable, InternalClient
{
	/**
	 * Creates a client that communicates with the server over a Unix socket.
	 *
	 * @param path the unix socket of the REST server (e.g. {@code /var/run/docker.sock})
	 * @return a new client
	 * @throws NullPointerException if {@code path} is null
	 */
	static DockerClient usingUnixSocket(Path path)
	{
		requireThat(path, "path").isNotNull();
		return new MainDockerClient(URI.create("http://localhost/"), new TCPUnix(path));
	}

	/**
	 * Creates a client that communicates with the server over TCP/IP.
	 *
	 * @param uri the URI of the REST server (e.g. {@code http://localhost:2375/})
	 * @return a new client
	 * @throws NullPointerException if {@code uri} is null
	 */
	static DockerClient usingTcpIp(URI uri)
	{
		return new MainDockerClient(uri, Transport.TCP_IP);
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