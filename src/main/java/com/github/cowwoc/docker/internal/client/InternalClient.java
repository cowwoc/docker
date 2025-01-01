package com.github.cowwoc.docker.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cowwoc.docker.internal.util.ClientRequests;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.io.Transport;

import java.net.URI;

/**
 * The internals of a {@code DockerClient}.
 */
public interface InternalClient
{
	/**
	 * Returns the HTTP client.
	 *
	 * @return the HTTP client
	 * @throws IllegalStateException if the client was closed
	 */
	HttpClient getHttpClient();

	/**
	 * Returns utility methods for HTTP requests and responses.
	 *
	 * @return utility methods for HTTP requests and responses
	 * @throws IllegalStateException if the client was closed
	 */
	ClientRequests getClientRequests();

	/**
	 * Returns the JSON configuration.
	 *
	 * @return the JSON configuration
	 * @throws IllegalStateException if the client was closed
	 */
	ObjectMapper getObjectMapper();

	/**
	 * Returns the URI of the REST API server.
	 *
	 * @return the URI of the REST API server
	 * @throws IllegalStateException if the client was closed
	 */
	URI getUri();

	/**
	 * Returns the transport used to communicate with the Docker server (e.g. TCP/IP or a Unix Socket).
	 *
	 * @return the transport used to communicate with the Docker server
	 */
	Transport getTransport();
}