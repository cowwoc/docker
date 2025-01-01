package com.github.cowwoc.docker.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.internal.util.ClientRequests;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.io.Transport;

import java.net.URI;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * The client used by the application.
 */
public final class MainDockerClient implements DockerClient
{
	private final URI uri;
	private final Transport transport;
	private final HttpClientFactory httpClient;
	private final ClientRequests clientRequests = new ClientRequests();
	private final ObjectMapper objectMapper = JsonMapper.builder().
		addModule(new JavaTimeModule()).
		disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).
		build();
	/**
	 * Indicates that the client has shut down.
	 */
	private boolean closed;

	/**
	 * Creates a new instance.
	 *
	 * @param uri       the URI of the REST API server. For unix sockets, use {@code http://localhost/}.
	 * @param transport the {@code Transport} used to communicate with the Docker server. For TCP sockets use
	 *                  {@code Transport.TCP_IP}. For Unix sockets use {@code new Transport.TCPUnix(path)}.
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code baseUrl} contains leading or trailing whitespace or is
	 *                                    empty.</li>
	 *                                  </ul>
	 */
	public MainDockerClient(URI uri, Transport transport)
	{
		requireThat(uri, "uri").isNotNull();
		requireThat(transport, "transport").isNotNull();
		this.uri = uri;
		this.transport = transport;
		this.httpClient = new HttpClientFactory();
	}

	@Override
	public URI getUri()
	{
		return uri;
	}

	@Override
	public HttpClient getHttpClient()
	{
		ensureOpen();
		return httpClient.getValue();
	}

	/**
	 * Ensures that the client is open.
	 *
	 * @throws IllegalStateException if the client was closed
	 */
	private void ensureOpen()
	{
		if (isClosed())
			throw new IllegalStateException("client was closed");
	}

	@Override
	public Transport getTransport()
	{
		return transport;
	}

	@Override
	public ClientRequests getClientRequests()
	{
		ensureOpen();
		return clientRequests;
	}

	@Override
	public ObjectMapper getObjectMapper()
	{
		ensureOpen();
		return objectMapper;
	}

	@Override
	public boolean isClosed()
	{
		return closed;
	}

	@Override
	public void close()
	{
		if (closed)
			return;
		httpClient.close();
		closed = true;
	}
}