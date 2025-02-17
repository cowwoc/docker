package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.internal.client.InternalClient;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.eclipse.jetty.client.Response;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Processes JSON objects returned by a server response.
 */
@SuppressWarnings("ClassEscapesDefinedScope")
public abstract class JsonStreamListener extends AsyncResponseListener
{
	/**
	 * Defines the frequency at which it is acceptable to log the same message to indicate that the thread is
	 * still active. This helps in monitoring the progress and ensuring the thread has not become unresponsive.
	 */
	protected static final Duration PROGRESS_FREQUENCY = Duration.ofSeconds(2);
	private final CharsetDecoder decoder = UTF_8.newDecoder();
	private final CharBuffer charBuffer = CharBuffer.allocate(200);
	protected final StringBuilder responseAsString = new StringBuilder();
	/**
	 * Maps a message to the last time that it was logged.
	 */
	protected final ConcurrentMap<String, Instant> messageToTime = new ConcurrentHashMap<>();

	/**
	 * Creates a new instance.
	 *
	 * @param client     the client configuration
	 * @param exceptions a container for exceptions that are thrown by the operation
	 * @throws NullPointerException if any of the arguments are null
	 */
	protected JsonStreamListener(InternalClient client, BlockingQueue<Throwable> exceptions)
	{
		super(client, exceptions);
	}

	@Override
	public void onContent(Response response, ByteBuffer content)
	{
		decodeBytes(content, false);
		processResponse(false);
	}

	/**
	 * Decodes the server response into a String.
	 *
	 * @param input      the bytes to decode
	 * @param endOfInput {@code true} if the end of the input stream has been reached
	 */
	protected void decodeBytes(ByteBuffer input, boolean endOfInput)
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
	 * Processes the server response that has been decoded so far.
	 *
	 * @param endOfInput {@code true} if the end of the input stream has been reached
	 */
	protected void processResponse(boolean endOfInput)
	{
		String line;
		while (true)
		{
			int newline = responseAsString.indexOf(System.lineSeparator());
			if (newline == -1)
				break;
			line = responseAsString.substring(0, newline);
			responseAsString.delete(0, newline + System.lineSeparator().length());
			if (!line.isBlank())
				processObject(line);
		}
		if (endOfInput && !responseAsString.isEmpty())
			processObject(responseAsString.toString());
	}

	/**
	 * Process a "status" message returned by the server.
	 *
	 * @param node a node that contains a "status" property
	 * @throws NullPointerException if {@code node} is null
	 */
	protected void processStatus(JsonNode node)
	{
		warnOnUnexpectedProperties(node, "status", "id", "progress", "progressDetail");
		String message;
		if (node.has("progress"))
			message = node.get("progress").textValue() + "\n";
		else
			message = node.get("status").textValue() + "\n";
		if (node.has("id"))
			message = node.get("id").textValue() + ": " + message;

		Instant now = Instant.now();
		Instant lastTime = messageToTime.get(message);
		if (lastTime == null || Duration.between(lastTime, now).compareTo(PROGRESS_FREQUENCY) >= 0)
		{
			// Only log the status if it's changed or PROGRESS_FREQUENCY has elapsed
			messageToTime.put(message, now);
			for (String line : Strings.split(message))
				log.info(line);
		}
	}

	/**
	 * @return the server response as JSON
	 */
	protected JsonNode getResponseBody()
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
}