package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.docker.internal.client.InternalClient;
import com.github.cowwoc.docker.resource.ContainerLogs.Streams;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpHeader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jetty.http.HttpStatus.FORBIDDEN_403;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.util.BufferUtil.EMPTY_BUFFER;

/**
 * Logs the output of a docker container incrementally.
 */
@SuppressWarnings("ClassEscapesDefinedScope")
public final class LogListener extends AsyncResponseListener
{
	private final OutputStream stdout;
	private final OutputStream stderr;
	private ContentType contentType;
	private StreamType streamType;
	private long bytesLeftInPayload;
	private final ByteBuffer header = ByteBuffer.allocate(8);
	private final byte[] buffer = new byte[10 * 1024];
	private final CharsetDecoder decoder = UTF_8.newDecoder();
	private final CharBuffer charBuffer = CharBuffer.allocate(200);
	private final StringBuilder responseAsString = new StringBuilder();

	/**
	 * Creates a new instance.
	 *
	 * @param client  the client configuration
	 * @param stdout  a stream to capture the container's standard output, or {@code null} to disable capturing
	 * @param stderr  a stream to capture the container's standard error, or {@code null} to disable capturing
	 * @param streams the streams returned to the user
	 * @throws NullPointerException if {@code client} or {@code streams} are null
	 */
	public LogListener(InternalClient client, OutputStream stdout, OutputStream stderr, Streams streams)
	{
		super(client, streams.getExceptions());
		this.stdout = stdout;
		this.stderr = stderr;
	}

	@Override
	protected void processUnknownProperties(JsonNode json)
	{
		exceptions.add(new IOException("Unexpected response: " + json.toPrettyString()));
	}

	@Override
	public void onBegin(Response response)
	{
		if (response.getStatus() == OK_200)
		{
			// Return right away from ContainerLogs.stream(). Users will continue listening to logs asynchronously.
			responseReady.countDown();
		}
	}

	@Override
	public void onHeaders(Response response)
	{
		String mediaType = response.getHeaders().get(HttpHeader.CONTENT_TYPE);
		switch (mediaType)
		{
			case "application/vnd.docker.raw-stream" -> contentType = ContentType.RAW_STREAM;
			case "application/vnd.docker.multiplexed-stream" -> contentType = ContentType.MULTIPLEXED_STREAM;
			default -> exceptions.add(new IOException("Unexpected response: " + client.toString(response) + "\n" +
				"Request: " + client.toString(response.getRequest())));
		}
	}

	@Override
	public void onContent(Response response, ByteBuffer content)
	{
		if (response.getStatus() != OK_200)
		{
			decodeBytes(content, false);
			return;
		}
		try
		{
			switch (contentType)
			{
				case RAW_STREAM ->
				{
					int length = Math.min(content.remaining(), buffer.length);
					stdout.write(buffer, 0, length);
				}
				case MULTIPLEXED_STREAM ->
				{
					while (content.hasRemaining())
					{
						if (streamType == null && !readHeader(content))
							return;
						int length = Math.min(content.remaining(), buffer.length);
						int payloadBytesLeftInContent = (int) Math.min(bytesLeftInPayload, Integer.MAX_VALUE);
						length = Math.min(length, payloadBytesLeftInContent);

						content.get(buffer, 0, length);
						switch (streamType)
						{
							case STDIN -> throw new AssertionError("Unexpected stream type: " + streamType);
							case STDOUT -> stdout.write(buffer, 0, length);
							case STDERR -> stderr.write(buffer, 0, length);
						}
						bytesLeftInPayload -= length;
						if (bytesLeftInPayload == 0)
							streamType = null;
					}
				}
			}
		}
		catch (IOException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
	}

	/**
	 * Decodes the server response into a String.
	 *
	 * @param input      the bytes to decode
	 * @param endOfInput {@code true} if the end of the input stream has been reached
	 */
	private void decodeBytes(ByteBuffer input, boolean endOfInput)
	{
		try
		{
			while (true)
			{
				CoderResult result = decoder.decode(input, charBuffer, endOfInput);
				charBuffer.flip();
				responseAsString.append(charBuffer);
				charBuffer.clear();
				if (result.isError())
					result.throwException();
				if (result.isUnderflow())
					break;
			}
		}
		catch (CharacterCodingException e)
		{
			exceptions.add(e);
		}
	}

	/**
	 * Reads a frame's header.
	 *
	 * @param content the server response
	 * @return {@code true} if the header is complete, {@code false} if {@code content} did not contain enough
	 * 	bytes to complete the header
	 */
	private boolean readHeader(ByteBuffer content)
	{
		if (content.remaining() > header.remaining())
		{
			int length = header.remaining();
			ByteBuffer subContent = content.slice(content.position(), length);
			header.put(subContent);
			content.position(content.position() + length);
		}
		else
			header.put(content);
		if (header.remaining() > 0)
			return false;
		header.flip();
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Container/operation/ContainerAttach
		// header := [8]byte{STREAM_TYPE, 0, 0, 0, SIZE1, SIZE2, SIZE3, SIZE4}
		byte streamTypeIndex = header.get();
		this.streamType = StreamType.values()[streamTypeIndex];
		// Skip the zeros
		header.position(header.position() + 3);
		this.bytesLeftInPayload = Integer.toUnsignedLong(header.getInt());
		header.flip();
		return true;
	}

	@Override
	public void onComplete(Result result)
	{
		RequestAbortedException requestAborted = handleRequestException(result);
		try
		{
			if (result.getResponseFailure() != null)
				exceptions.add(result.getResponseFailure());
			Response response = result.getResponse();
			int statusCode = response.getStatus();
			if (statusCode == OK_200)
			{
				onSuccess();
				return;
			}
			onFailure(result);
		}
		finally
		{
			if (requestAborted != null)
				requestAborted.onComplete();
		}
	}

	/**
	 * Handles any exception thrown by the request.
	 *
	 * @param result the result of the operation
	 * @return {@code RequestAbortedException} if the request was aborted; otherwise, {@code null}
	 */
	private RequestAbortedException handleRequestException(Result result)
	{
		if (result.getRequestFailure() == null)
			return null;
		Throwable requestFailure = result.getRequestFailure();
		if (requestFailure instanceof RequestAbortedException rae)
			return rae;
		exceptions.add(requestFailure);
		return null;
	}

	/**
	 * Invoked if the request completes successfully.
	 */
	@SuppressWarnings("EmptyTryBlock")
	private void onSuccess()
	{
		try (stdout; stderr)
		{
		}
		catch (IOException e)
		{
			exceptions.add(e);
		}
	}

	/**
	 * Invoked on a request failure.
	 *
	 * @param result the result of the request
	 */
	private void onFailure(Result result)
	{
		try
		{
			decodeBytes(EMPTY_BUFFER, true);
			Response response = result.getResponse();
			int statusCode = response.getStatus();
			switch (statusCode)
			{
				case FORBIDDEN_403 ->
				{
					// Example: Surpassed storage quota
					JsonNode body = getResponseBody();
					exceptions.add(new IOException(body.get("message").textValue()));
				}
				case NOT_FOUND_404 ->
				{
					JsonNode body = getResponseBody();
					exceptions.add(new ResourceNotFoundException(body.get("message").textValue()));
				}
				case INTERNAL_SERVER_ERROR_500 -> exceptions.add(new IOException(responseAsString.toString()));
				default -> throw new AssertionError("Unexpected response: " + client.toString(response) + "\n" +
					"Request: " + client.toString(result.getRequest()));
			}
		}
		finally
		{
			try (stdout; stderr)
			{
			}
			catch (IOException e)
			{
				exceptions.add(e);
			}
			responseReady.countDown();
		}
	}

	/**
	 * @return the server response as JSON
	 */
	private JsonNode getResponseBody()
	{
		try
		{
			return client.getJsonMapper().readTree(responseAsString.toString());
		}
		catch (JsonProcessingException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
	}

	/**
	 * The server response's media type.
	 */
	enum ContentType
	{
		/**
		 * {@code ContentType: application/vnd.docker.raw-stream}.
		 */
		RAW_STREAM,
		/**
		 * {@code ContentType: application/vnd.docker.multiplexed-stream}.
		 */
		MULTIPLEXED_STREAM
	}

	/**
	 * The type of stream that is contained within the current payload.
	 */
	enum StreamType
	{
		STDIN,
		STDOUT,
		STDERR
	}
}