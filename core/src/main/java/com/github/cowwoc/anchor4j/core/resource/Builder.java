package com.github.cowwoc.anchor4j.core.resource;

import com.github.cowwoc.anchor4j.core.internal.client.InternalClient;
import com.github.cowwoc.anchor4j.core.internal.resource.BuildXAccess;
import com.github.cowwoc.anchor4j.core.internal.resource.ErrorHandler;
import com.github.cowwoc.anchor4j.core.internal.resource.SharedSecrets;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.requirements11.annotation.CheckReturnValue;

import java.io.IOException;
import java.util.Locale;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Represents a service that builds image.
 * <p>
 * <b>Thread Safety</b>: This class is immutable and thread-safe.
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public final class Builder
{
	static
	{
		SharedSecrets.setBuildXAccess(new BuildXAccess()
		{
			@Override
			public Builder get(InternalClient client, String name, Status status, String error)
			{
				return new Builder(client, name, status, error);
			}

			@Override
			public ImageBuilder buildImage(InternalClient client, ErrorHandler errorHandler)
			{
				return new ImageBuilder(client, errorHandler);
			}

			@Override
			public Status getStatusFromString(String value)
			{
				return Status.fromString(value);
			}
		});
	}

	private final InternalClient client;
	private final String name;
	private final Status status;
	private final String error;

	/**
	 * Creates a reference to a container.
	 *
	 * @param client the client configuration
	 * @param name   the name of the builder. The value must start with a letter, or digit, or underscore, and
	 *               may be followed by additional characters consisting of letters, digits, underscores,
	 *               periods or hyphens.
	 * @param status the builder's status
	 * @param error  an explanation of the builder's error status, or an empty string if the status is not
	 *               {@code ERROR}
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name}'s format is invalid
	 */
	private Builder(InternalClient client, String name, Status status, String error)
	{
		assert that(client, "client").isNotNull().elseThrow();
		client.validateName(name, "name");
		assert that(status, "status").isNotNull().elseThrow();
		assert that(error, "error").isNotNull().elseThrow();

		this.client = client;
		this.name = name;
		this.status = status;
		this.error = error;
	}

	/**
	 * Returns the name of the builder.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the status of the builder.
	 *
	 * @return the status
	 */
	public Status getStatus()
	{
		return status;
	}

	/**
	 * Returns an explanation of the builder's error status.
	 *
	 * @return an empty string if the status is not {@code ERROR}
	 */
	public String getError()
	{
		return error;
	}

	/**
	 * Reloads the builder's status.
	 *
	 * @return the updated status
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	@CheckReturnValue
	public Builder reload() throws IOException, InterruptedException
	{
		return client.getBuilder(name);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder().
			add("name", name).
			add("status", status).
			toString();
	}

	/**
	 * Represents the status of a builder.
	 * <p>
	 * <b>Thread Safety</b>: This class is immutable and thread-safe.
	 */
	public enum Status
	{
		/**
		 * The builder is running.
		 */
		RUNNING,
		/**
		 * The builder is unavailable due to an error.
		 */
		ERROR;

		/**
		 * Looks up a value from its String representation.
		 *
		 * @param value the String representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code value} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static Status fromString(String value)
		{
			return valueOf(value.toUpperCase(Locale.ROOT));
		}
	}
}