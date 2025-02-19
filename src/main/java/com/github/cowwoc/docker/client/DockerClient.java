package com.github.cowwoc.docker.client;

import com.github.cowwoc.docker.internal.client.MainInternalClient;
import org.eclipse.jetty.io.Transport;
import org.eclipse.jetty.io.Transport.TCPUnix;

import java.net.URI;
import java.nio.file.Path;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A client of the Docker REST API.
 */
public interface DockerClient extends AutoCloseable
{
	/**
	 * Creates a client that communicates with the server over a Unix socket, using
	 * {@link RunMode#RELEASE release} mode.
	 *
	 * @param path the unix socket of the REST server (e.g. {@code /var/run/docker.sock})
	 * @return a new client
	 * @throws NullPointerException if {@code path} is null
	 */
	static DockerClient usingUnixSocket(Path path)
	{
		return usingUnixSocket(path, RunMode.RELEASE);
	}

	/**
	 * Creates a client that communicates with the server over TCP/IP, using {@link RunMode#RELEASE release}
	 * mode..
	 *
	 * @param uri the URI of the REST server (e.g. {@code http://localhost:2375/})
	 * @return a new client
	 * @throws NullPointerException if {@code uri} is null
	 */
	static DockerClient usingTcpIp(URI uri)
	{
		return usingTcpIp(uri, RunMode.RELEASE);
	}

	/**
	 * Creates a client that communicates with the server over a Unix socket.
	 *
	 * @param path the unix socket of the REST server (e.g. {@code /var/run/docker.sock})
	 * @param mode the run mode
	 * @return a new client
	 * @throws NullPointerException if any of the arguments are null
	 */
	static DockerClient usingUnixSocket(Path path, RunMode mode)
	{
		requireThat(path, "path").isNotNull();
		return new MainInternalClient(URI.create("http://localhost/"), new TCPUnix(path), mode);
	}

	/**
	 * Creates a client that communicates with the server over TCP/IP.
	 *
	 * @param uri  the URI of the REST server (e.g. {@code http://localhost:2375/})
	 * @param mode the run mode
	 * @return a new client
	 * @throws NullPointerException if any of the arguments are null
	 */
	static DockerClient usingTcpIp(URI uri, RunMode mode)
	{
		return new MainInternalClient(uri, Transport.TCP_IP, mode);
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