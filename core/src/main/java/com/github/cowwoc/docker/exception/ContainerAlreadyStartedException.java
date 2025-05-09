package com.github.cowwoc.docker.exception;

import com.github.cowwoc.docker.resource.Container;

import java.io.Serial;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Thrown if the user attempts to start a container that is already started.
 */
public class ContainerAlreadyStartedException extends Exception
{
	@Serial
	private static final long serialVersionUID = 0L;
	private transient final Container container;

	/**
	 * Creates a new instance.
	 *
	 * @param container the container
	 * @throws NullPointerException if {@code container} is null
	 */
	public ContainerAlreadyStartedException(Container container)
	{
		super(getMessage(container));
		this.container = container;
	}

	private static String getMessage(Container container)
	{
		requireThat(container, "container").isNotNull();
		return container.getName() + " (" + container.getId() + ") already started";
	}

	/**
	 * Returns the container.
	 *
	 * @return the container
	 */
	public Container getContainer()
	{
		return container;
	}
}