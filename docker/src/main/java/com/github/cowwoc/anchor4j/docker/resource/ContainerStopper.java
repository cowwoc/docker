package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Stops a container.
 */
public final class ContainerStopper
{
	private final InternalDocker client;
	private final String id;
	private String signal = "";
	private Duration timeout;

	/**
	 * Creates a container stopper.
	 *
	 * @param client the client configuration
	 * @param id     the container's ID or name
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id} contains whitespace or is empty
	 */
	ContainerStopper(InternalDocker client, String id)
	{
		assert that(client, "client").isNotNull().elseThrow();
		client.validateContainerIdOrName(id, "id");
		this.client = client;
		this.id = id;
	}

	/**
	 * Sets the signal to send to the container.
	 *
	 * @param signal the signal to send to the container. Common values include {@code SIGTERM},
	 *               {@code SIGKILL}, {@code SIGHUP}, and {@code SIGINT}. If an empty string is provided, the
	 *               default signal will be used.
	 *               <p>
	 *               The default signal is determined by the container's configuration. It can be set using the
	 *               <a href="https://docs.docker.com/reference/dockerfile/#stopsignal">STOPSIGNAL</a>
	 *               instruction in the Dockerfile, or via the {@code stopSignal} option when creating the
	 *               container. If no default is specified, {@code SIGTERM} is used.
	 * @return this
	 * @throws NullPointerException     if {@code signal} is null
	 * @throws IllegalArgumentException if {@code signal} contains whitespace
	 * @see <a href="https://man7.org/linux/man-pages/man7/signal.7.html">the list of available signals</a>
	 */
	public ContainerStopper signal(String signal)
	{
		requireThat(signal, "signal").doesNotContainWhitespace();
		this.signal = signal;
		return this;
	}

	/**
	 * Sets the maximum duration to wait for the container to stop.
	 *
	 * @param timeout the maximum duration to wait for the container to stop after sending the specified
	 *                {@code signal}. If the container does not exit within this time, it will be forcibly
	 *                terminated with a {@code SIGKILL}.
	 *                <p>
	 *                If negative, the method will wait indefinitely for the container to exit.
	 *                <p>
	 *                If {@code null}, the default timeout will be used.
	 *                <p>
	 *                The default timeout can be configured using the {@code stopTimeout} option when the
	 *                container is created. If no default is configured for the container, the value defaults to
	 *                10 seconds for Linux containers and 30 seconds for Windows containers.
	 * @return this
	 */
	public ContainerStopper timeout(Duration timeout)
	{
		this.timeout = timeout;
		return this;
	}

	/**
	 * Stops a container. If the container is already stopped, this method has no effect.
	 *
	 * @throws ResourceNotFoundException if the container no longer exists
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws InterruptedException      if the thread is interrupted before the operation completes. This can
	 *                                   happen due to shutdown signals.
	 */
	public void stop() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/container/stop/
		List<String> arguments = new ArrayList<>(7);
		arguments.add("container");
		arguments.add("stop");
		arguments.add(id);
		if (!signal.isEmpty())
		{
			arguments.add("--signal");
			arguments.add(signal);
		}
		if (timeout != null)
		{
			arguments.add("--timeout");
			if (timeout.isNegative())
				arguments.add("-1");
			else
				arguments.add(String.valueOf(timeout.toSeconds()));
		}
		CommandResult result = client.run(arguments);
		client.getContainerParser().stop(result);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ContainerStopper.class).
			add("signal", signal).
			add("timeout", timeout).
			toString();
	}
}