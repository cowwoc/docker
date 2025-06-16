package com.github.cowwoc.anchor4j.core.internal.resource;

import com.github.cowwoc.anchor4j.core.internal.client.InternalClient;
import com.github.cowwoc.anchor4j.core.resource.Builder;
import com.github.cowwoc.anchor4j.core.resource.Builder.Status;
import com.github.cowwoc.anchor4j.core.resource.BuilderCreator;
import com.github.cowwoc.anchor4j.core.resource.ImageBuilder;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Internal mechanism for granting privileged access to non-public members across package boundaries, without
 * using reflection.
 * <p>
 * This class maintains static references to access interfaces. These interfaces define methods that expose
 * non-public behavior or data of other classes within the module. Classes that wish to expose such internals
 * register implementations of these interfaces, typically during static initialization.
 * <p>
 * Consumers within the same module can retrieve these interfaces and invoke privileged methods, enabling
 * controlled access to internal functionality without breaking encapsulation or relying on reflection.
 * <p>
 * This mechanism resides in a non-exported package to restrict visibility and ensure that only trusted
 * classes within the same module can interact with it.
 */
public final class SharedSecrets
{
	private static final Lookup LOOKUP = MethodHandles.lookup();
	private static BuildXAccess buildXAccess;

	/**
	 * Registers an implementation for the {@code BuildAccess} interface.
	 *
	 * @param buildXAccess the implementation
	 */
	public static void setBuildXAccess(BuildXAccess buildXAccess)
	{
		assert that(buildXAccess, "buildXAccess").isNotNull().elseThrow();
		SharedSecrets.buildXAccess = buildXAccess;
	}

	/**
	 * Creates a builder.
	 *
	 * @param client the client configuration
	 * @return a builder creator
	 */
	public static BuilderCreator createBuilder(InternalClient client)
	{
		BuildXAccess access = buildXAccess;
		if (access == null)
		{
			initialize();
			access = buildXAccess;
			assert access != null;
		}
		return access.create(client);
	}

	/**
	 * Creates a reference to a builder.
	 *
	 * @param client the client configuration
	 * @param name   the name of the builder
	 * @param status the status of the builder
	 * @param error  an explanation of the builder's error status, or an empty string if the status is not
	 *               {@code ERROR}
	 * @return the builder
	 */
	public static Builder getBuildXBuilder(InternalClient client, String name, Builder.Status status,
		String error)
	{
		BuildXAccess access = buildXAccess;
		if (access == null)
		{
			initialize();
			access = buildXAccess;
			assert access != null;
		}
		return access.get(client, name, status, error);
	}

	/**
	 * Looks up a value from its String representation.
	 *
	 * @param value the String representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	public static Status getBuilderStatusFromString(String value)
	{
		BuildXAccess access = buildXAccess;
		if (access == null)
		{
			initialize();
			access = buildXAccess;
			assert access != null;
		}
		return access.getStatusFromString(value);
	}

	/**
	 * Creates an image builder.
	 *
	 * @param client the client configuration
	 * @return the builder
	 */
	public static ImageBuilder buildImage(InternalClient client)
	{
		BuildXAccess access = buildXAccess;
		if (access == null)
		{
			initialize();
			access = buildXAccess;
			assert access != null;
		}
		return access.buildImage(client);
	}

	/**
	 * Initializes a class. If the class is already initialized, this method has no effect.
	 */
	private static void initialize()
	{
		try
		{
			LOOKUP.ensureInitialized(Builder.class);
		}
		catch (IllegalAccessException e)
		{
			throw new AssertionError(e);
		}
	}

	private SharedSecrets()
	{
	}
}