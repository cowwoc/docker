package com.github.cowwoc.docker.internal.util;

import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Request.Content;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content.Source;
import org.eclipse.jetty.io.content.ByteBufferContentSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Utility methods for client-side HTTP requests and responses.
 */
public final class ClientRequests
{
	private static final RunMode RUN_MODE = RunMode.RELEASE;
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

	/**
	 * Creates a new instance.
	 */
	public ClientRequests()
	{
	}

	/**
	 * @param request a client request
	 * @return the String representation of the request
	 */
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

	/**
	 * @param request a client request
	 * @return the response body (an empty string if empty)
	 * @throws IOException if an I/O error occurs while reading the request body
	 */
	public String getBodyAsString(Request request) throws IOException
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
	 * Sends a request.
	 *
	 * @param request the client request
	 * @return the server response
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public ContentResponse send(Request request) throws IOException, TimeoutException, InterruptedException
	{
		if (RUN_MODE == RunMode.DEBUG)
			convertToRewindableContent(request);
		try
		{
			return request.send();
		}
		catch (ExecutionException e)
		{
			Throwable cause = e.getCause();
			if (cause instanceof IOException ioe)
				throw ioe;
			throw new AssertionError(toString(request), e);
		}
	}

	/**
	 * Sends a request with an asynchronous response listener.
	 *
	 * @param request  the client request
	 * @param listener the server response listener
	 * @throws IOException if an I/O error occurs. These errors are typically transient, and retrying the
	 *                     request may resolve the issue.
	 */
	public void send(Request request, Response.Listener listener)
		throws IOException
	{
		if (RUN_MODE == RunMode.DEBUG)
			convertToRewindableContent(request);
		request.send(listener);
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
	 * @return the response HTTP version, status code and reason phrase
	 */
	public String getStatusLine(Response response)
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
	 * @return the {@code String} representation of the response
	 */
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
}