package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.exception.ImageNotFoundException;
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
	 * @param client the client configuration
	 * @param stdout the container's stdout stream, or null if disabled
	 * @param stderr the container's stderr stream, or null if disabled
	 * @throws NullPointerException if {@code client} is null
	 */
	public LogListener(DockerClient client, OutputStream stdout, OutputStream stderr)
	{
		super(client);
		this.stdout = stdout;
		this.stderr = stderr;
	}

	@Override
	protected void processUnknownProperties(JsonNode json)
	{
		IOException e = new IOException("Unexpected response: " + json.toPrettyString());
		if (exception != null)
			e.addSuppressed(exception);
		exception = e;
	}

	@Override
	public void onBegin(Response response)
	{
		if (response.getStatus() == OK_200)
			exceptionReady.countDown();
	}

	@Override
	public void onHeaders(Response response)
	{
		String mediaType = response.getHeaders().get(HttpHeader.CONTENT_TYPE);
		switch (mediaType)
		{
			case "application/vnd.docker.raw-stream" -> contentType = ContentType.RAW_STREAM;
			case "application/vnd.docker.multiplexed-stream" -> contentType = ContentType.MULTIPLEXED_STREAM;
			default -> exception = new IOException("Unexpected response: " + client.toString(response) + "\n" +
				"Request: " + client.toString(response.getRequest()));
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
			if (exception != null)
				e.addSuppressed(exception);
			exception = e;
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

	@SuppressWarnings("EmptyTryBlock")
	@Override
	public void onComplete(Result result)
	{
		Response response = result.getResponse();
		int statusCode = response.getStatus();
		if (statusCode == OK_200)
		{
			try (stdout; stderr)
			{
			}
			catch (IOException e)
			{
				throw WrappedCheckedException.wrap(e);
			}
			return;
		}

		try
		{
			decodeBytes(EMPTY_BUFFER, true);
			switch (statusCode)
			{
				case FORBIDDEN_403 ->
				{
					// Example: Surpassed storage quota
					JsonNode body = getResponseBody();
					IOException e = new IOException(body.get("message").textValue());
					if (exception != null)
						e.addSuppressed(exception);
					exception = e;
				}
				case NOT_FOUND_404 ->
				{
					JsonNode body = getResponseBody();
					ImageNotFoundException e = new ImageNotFoundException(body.get("message").textValue());
					if (exception != null)
						e.addSuppressed(exception);
					exception = e;
				}
				case INTERNAL_SERVER_ERROR_500 ->
				{
					IOException e = new IOException(responseAsString.toString());
					if (exception != null)
						e.addSuppressed(exception);
					exception = e;
				}
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
				if (exception != null)
					e.addSuppressed(exception);
				exception = e;
			}
			exceptionReady.countDown();
		}
	}

	/**
	 * @return the server response as JSON
	 */
	private JsonNode getResponseBody()
	{
		try
		{
			return client.getObjectMapper().readTree(responseAsString.toString());
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