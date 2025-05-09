package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.cowwoc.requirements10.jackson.validator.JsonNodeValidator;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

import static com.github.cowwoc.requirements10.jackson.DefaultJacksonValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Helper functions for exceptions.
 */
public final class Exceptions
{
	private Exceptions()
	{
	}

	/**
	 * Returns the stack trace of a {@code Throwable}.
	 *
	 * @param t the {@code Throwable}
	 * @return the stack trace
	 * @see <a href="https://stackoverflow.com/a/1149712/14731">https://stackoverflow.com/a/1149712/14731</a>
	 */
	public static String toString(Throwable t)
	{
		if (t == null)
			return "null";
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		return sw.toString();
	}

	/**
	 * Combines multiple exceptions into an {@code IOException}.
	 *
	 * @param throwables zero or more exceptions
	 * @return null if no errors occurred
	 */
	public static IOException combineAsIOException(Collection<Throwable> throwables)
	{
		if (throwables.isEmpty())
			return null;
		if (throwables.size() == 1)
		{
			Throwable first = throwables.iterator().next();
			if (first instanceof IOException ioe)
				return ioe;
			return new IOException(first);
		}
		StringBuilder combinedMessage = new StringBuilder(38).append("The operation threw ").
			append(throwables.size()).append(" exceptions.\n");
		int i = 1;
		for (Throwable exception : throwables)
		{
			combinedMessage.append(i).append(". ").append(exception.getClass().getName());
			String message = exception.getMessage();
			if (message != null)
			{
				combinedMessage.append(": ").
					append(message).
					append('\n');
			}
			++i;
		}
		return new IOException(combinedMessage.toString());
	}

	/**
	 * Converts a gRPC exception to an {@code IOException}.
	 *
	 * @param sre        the {@code StatusRuntimeException}
	 * @param jsonMapper a JsonMapper
	 * @return the IOException
	 * @throws NullPointerException if any of the arguments are null
	 */
	public static IOException fromGrpc(StatusRuntimeException sre, JsonMapper jsonMapper)
	{
		try
		{
			Metadata trailers = sre.getTrailers();
			if (trailers != null && !trailers.keys().isEmpty())
			{
				// Based on https://github.com/grpc/grpc-web/issues/399
				assert that(trailers.keys(), "trailers.keys()").containsExactly(
					List.of("grpc-status-details-bin")).elseThrow();
				byte[] grpcStatusDetailsBin = trailers.get(
					Key.of("grpc-status-details-bin", Metadata.BINARY_BYTE_MARSHALLER));
				Status status = Status.parseFrom(grpcStatusDetailsBin);
				Code code = Code.forNumber(status.getCode());

				int numberOfExceptions = 0;
				StringJoiner exceptions = new StringJoiner("\n\n");
				Logger log = LoggerFactory.getLogger(Exceptions.class);
				for (Any exception : status.getDetailsList())
				{
					++numberOfExceptions;
					requireThat(exception.getTypeUrl(), "typeUrl").isEqualTo(
						"github.com/moby/buildkit/stack.Stack+json");
					JsonNode json = jsonMapper.readTree(exception.getValue().toString(UTF_8));

					Json.warnOnUnexpectedProperties(log, json, "frames", "cmdline", "pid");
					StringJoiner command = new StringJoiner(" ");
					for (JsonNode entry : json.get("cmdline"))
						command.add(entry.textValue());
					int pid = requireThat(json, "json").property("pid").isIntegralNumber().getValue().
						intValue();
					requireThat(pid, "pid").isPositive();

					JsonNode framesNode = json.get("frames");
					StringJoiner stackTrace = new StringJoiner("\n\tat ");
					for (JsonNode frame : framesNode)
					{
						JsonNodeValidator<JsonNode> frameValidator = requireThat(frame, "frame");

						String name = frameValidator.property("Name").isString().getValue().textValue();
						requireThat(name, "name").isStripped().isNotEmpty();
						name = Strings.getLastComponent(name, '/');

						String file = frameValidator.property("File").isString().getValue().textValue();
						requireThat(file, "file").isStripped().isNotEmpty();
						file = Strings.getLastComponent(file, '/');

						int line = frameValidator.property("Line").isIntegralNumber().getValue().intValue();
						requireThat(line, "line").isPositive();

						stackTrace.add(name + "(" + file + ":" + line + ")");
					}
					exceptions.add(numberOfExceptions + ". " + command + " (pid " + pid + ")\n" +
						"Stack: " + stackTrace);
				}
				return new IOException(status.getMessage() + "\n" +
					"Code: " + code.name() + "\n" +
					"There are " + numberOfExceptions + " nested exceptions: \n" + exceptions);
			}
			return new IOException(sre);
		}
		catch (InvalidProtocolBufferException | JsonProcessingException e)
		{
			return new IOException(e);
		}
	}
}