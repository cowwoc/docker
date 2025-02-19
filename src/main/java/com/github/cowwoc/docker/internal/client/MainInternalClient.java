package com.github.cowwoc.docker.internal.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.cowwoc.docker.client.RunMode;
import com.github.cowwoc.docker.internal.util.Exceptions;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import com.github.cowwoc.requirements10.jackson.DefaultJacksonValidators;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Request.Content;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content.Source;
import org.eclipse.jetty.io.Transport;
import org.eclipse.jetty.io.content.ByteBufferContentSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

/**
 * The client used by the application.
 */
public final class MainInternalClient implements InternalClient
{
	/**
	 * Content-types that are known to be textual.
	 */
	private static final Set<String> TEXTUAL_CONTENT_TYPES = Set.of(
		"text/plain",
		"text/html",
		"text/css",
		"application/javascript",
		"text/javascript",
		"application/json",
		"application/xml",
		"text/xml",
		"text/csv",
		"text/markdown",
		"application/x-yaml",
		"application/rtf",
		"application/pdf",
		"text/sgml",
		"application/xhtml+xml",
		"application/ld+json");
	private final URI server;
	private final Transport transport;
	private final RunMode mode;
	private final HttpClientFactory httpClient;
	private final JsonMapper jsonMapper = JsonMapper.builder().
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
	 * @param server    the URI of the REST API server. For unix sockets, use {@code http://localhost/}.
	 * @param transport the {@code Transport} used to communicate with the Docker server. For TCP sockets use
	 *                  {@code Transport.TCP_IP}. For Unix sockets use {@code new Transport.TCPUnix(path)}.
	 * @throws NullPointerException if any of the arguments are null
	 */
	public MainInternalClient(URI server, Transport transport, RunMode mode)
	{
		requireThat(server, "server").isNotNull();
		requireThat(transport, "transport").isNotNull();
		this.server = server;
		this.transport = transport;
		this.mode = mode;
		this.httpClient = new HttpClientFactory(this);
	}

	@Override
	public RunMode getRunMode()
	{
		return mode;
	}

	@Override
	public JsonMapper getJsonMapper()
	{
		ensureOpen();
		return jsonMapper;
	}

	@Override
	public URI getServer()
	{
		return server;
	}

	@Override
	public Request createRequest(URI uri)
	{
		return getHttpClient().newRequest(uri).
			transport(transport);
	}

	@Override
	public Request createRequest(URI uri, JsonNode requestBody)
	{
		String requestBodyAsString;
		try
		{
			requestBodyAsString = jsonMapper.writeValueAsString(requestBody);
		}
		catch (JsonProcessingException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
		return createRequest(uri).
			body(new StringRequestContent("application/json", requestBodyAsString));
	}

	@Override
	public ContentResponse send(Request request) throws IOException, TimeoutException, InterruptedException
	{
		if (mode == RunMode.DEBUG)
			convertToRewindableContent(request);
		try
		{
			return request.send();
		}
		catch (ExecutionException e)
		{
			Throwable cause = e.getCause();
			if (cause instanceof IOException ioe)
			{
				// Ensure that the returned exception stack trace contains a reference to the current method
				throw new IOException(ioe);
			}
			throw new AssertionError(toString(request), e);
		}
	}

	@Override
	public void send(Request request, Response.Listener listener) throws IOException
	{
		if (mode == RunMode.DEBUG)
			convertToRewindableContent(request);
		request.send(listener);
	}

	@Override
	public JsonNode getResponseBody(ContentResponse serverResponse)
	{
		try
		{
			return jsonMapper.readTree(serverResponse.getContentAsString());
		}
		catch (JsonProcessingException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
	}

	@Override
	public String toString(Request request)
	{
		StringBuilder result = new StringBuilder("< HTTP ");
		result.append(request.getMethod()).append(' ').
			append(request.getURI()).append('\n');

		HttpFields requestHeaders = request.getHeaders();
		if (requestHeaders.size() > 0)
		{
			result.append("<\n");
			for (HttpField header : requestHeaders)
			{
				StringJoiner values = new StringJoiner(",");
				for (String value : header.getValues())
					values.add(value);
				result.append("< ").append(header.getName()).append(": ").append(values).append('\n');
			}
		}
		try
		{
			String body = getBodyAsString(request);
			if (!body.isEmpty())
			{
				if (!result.isEmpty())
					result.append("<\n");
				body = "< " + body.replaceAll("\n", "\n< ");
				result.append(body);
				if (!body.endsWith("\n"))
					result.append('\n');
			}
		}
		catch (IOException e)
		{
			result.append(Exceptions.toString(e));
		}
		return result.toString();
	}

	@Override
	public String toString(Response response)
	{
		requireThat(response, "response").isNotNull();
		StringJoiner resultAsString = new StringJoiner("\n");
		resultAsString.add(getStatusLine(response)).
			add(getHeadersAsString(response));
		if (response instanceof ContentResponse contentResponse)
		{
			String responseBody = contentResponse.getContentAsString();
			if (!responseBody.isEmpty())
			{
				resultAsString.add("");
				resultAsString.add(responseBody);
			}
		}
		return resultAsString.toString();
	}

	@Override
	public int getVersion(JsonNode json)
	{
		JsonNode versionNode = json.get("Version");
		return toInt(versionNode.get("Index"), "Version.Index");
	}

	@Override
	public int toInt(JsonNode node, String name)
	{
		DefaultJacksonValidators.requireThat(node, name).isIntegralNumber();
		requireThat(node.canConvertToInt(), name + ".canConvertToInt()").isTrue();
		return node.intValue();
	}

	@Override
	public long toLong(JsonNode node, String name)
	{
		DefaultJacksonValidators.requireThat(node, name).isIntegralNumber();
		requireThat(node.canConvertToLong(), name + ".canConvertToLong()").isTrue();
		return node.longValue();
	}

	@Override
	public void expectOk200(Request request, ContentResponse serverResponse)
	{
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
			}
			default ->
			{
				throw new AssertionError("Unexpected response: " + toString(serverResponse) + "\n" +
					"Request: " + toString(request));
			}
		}
	}

	@Override
	public List<String> arrayToListOfString(JsonNode array, String name)
	{
		DefaultJacksonValidators.requireThat(array, "array").isArray();
		List<String> strings = new ArrayList<>();
		for (JsonNode element : array)
		{
			DefaultJacksonValidators.requireThat(element, name).isString();
			strings.add(element.textValue());
		}
		return strings;
	}

	/**
	 * Returns the HTTP client.
	 *
	 * @return the HTTP client
	 * @throws IllegalStateException if the client is closed
	 */
	public HttpClient getHttpClient()
	{
		ensureOpen();
		return httpClient.getValue();
	}

	/**
	 * @param request a client request
	 * @return the response body (an empty string if empty)
	 * @throws IOException if an I/O error occurs while reading the request body
	 */
	private String getBodyAsString(Request request) throws IOException
	{
		Content body = request.getBody();
		if (body == null || body.getLength() == 0)
			return "";
		// Per https://datatracker.ietf.org/doc/html/rfc9110#name-field-values values consist of ASCII
		// characters, so it's safe to convert them to lowercase.
		String contentType = body.getContentType();
		if (contentType != null)
		{
			contentType = contentType.toLowerCase(Locale.ROOT);
			if (!TEXTUAL_CONTENT_TYPES.contains(contentType))
				return "[" + body.getLength() + " bytes]";
		}
		convertToRewindableContent(request);
		if (!body.rewind())
			throw new AssertionError("Unable to rewind body: " + body);
		return Source.asString(body);
	}

	/**
	 * Converts the request body to a format that is rewindable.
	 *
	 * @param request the client request
	 * @throws NullPointerException if {@code request} is null
	 * @throws IOException          if an error occurs while reading the body
	 */
	private void convertToRewindableContent(Request request) throws IOException
	{
		Content body = request.getBody();
		if (body == null || body instanceof ByteBufferContentSource)
			return;
		byte[] byteArray;
		try (InputStream in = Source.asInputStream(body))
		{
			byteArray = in.readAllBytes();
		}
		request.body(new BytesRequestContent(body.getContentType(), byteArray));
	}

	/**
	 * @param response the server response
	 * @return the string representation of the response's headers
	 */
	private String getHeadersAsString(Response response)
	{
		StringJoiner result = new StringJoiner("\n");
		for (HttpField header : response.getHeaders())
			result.add(header.toString());
		return result.toString();
	}

	/**
	 * @param response the server response
	 * @return the response HTTP version, status code and reason phrase
	 */
	private String getStatusLine(Response response)
	{
		String reason = response.getReason();
		if (reason == null)
		{
			// HTTP "reason" was removed in HTTP/2. See: https://github.com/jetty/jetty.project/issues/11593
			reason = HttpStatus.getCode(response.getStatus()).getMessage();
		}
		return response.getVersion() + " " + response.getStatus() + " (\"" + reason + "\")";
	}

	/**
	 * Ensures that the client is open.
	 *
	 * @throws IllegalStateException if the client is closed
	 */
	private void ensureOpen()
	{
		if (isClosed())
			throw new IllegalStateException("client is closed");
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