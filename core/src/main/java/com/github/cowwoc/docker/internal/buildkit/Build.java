package com.github.cowwoc.docker.internal.buildkit;

import com.github.cowwoc.docker.internal.util.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;

/**
 * A build job.
 */
public final class Build
{
	private final Session session;
	private final Path buildContext;
	private final Path dockerfile;
	private final String id;
	private final BlockingQueue<Throwable> exceptions = new LinkedBlockingQueue<>();
	private final Logger log = LoggerFactory.getLogger(Build.class);

	/**
	 * Creates a new build.
	 *
	 * @param session      the session that created the build
	 * @param buildContext the path of the build context
	 * @param dockerfile   the path of the Dockerfile
	 * @throws AssertionError if:
	 *                        <ul>
	 *                        <li>any of the arguments are null</li>
	 *                        <li>{@code id} contains leading or trailing whitespace or is empty</li>
	 *                        </ul>
	 */
	Build(Session session, Path buildContext, Path dockerfile)
	{
		assert that(session, "session").isNotNull().elseThrow();
		assert that(buildContext, "buildContext").isNotNull().elseThrow();
		assert that(dockerfile, "dockerfile").isNotNull().elseThrow();
		this.session = session;
		// Path.relativize() requires the use of absolute paths
		this.buildContext = buildContext.toAbsolutePath();
		this.dockerfile = dockerfile;
		this.id = UUID.randomUUID().toString();
	}

	/**
	 * Returns the build's ID.
	 *
	 * @return the id
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Executes the build.
	 *
	 * @return the key-value pairs returned by the build
	 * @throws InterruptedException if the thread is interrupted before the operation completes
	 * @throws IOException          if the operation fails
	 */
	public Map<String, String> run() throws IOException, InterruptedException
	{
		log.info("Session id: {}", session.getId());
		log.info("Build id: {}", id);
		List<Thread> threads = new ArrayList<>();
		StatusListener statusListener = new StatusListener(this, exceptions);
		threads.add(statusListener.start());
		throwExceptions(threads);

		ControlBuild controlBuild = new ControlBuild(this, exceptions);
		threads.add(controlBuild.start());
		throwExceptions(threads);

		GatewayBuild gatewayBuild = new GatewayBuild(this, exceptions, buildContext, dockerfile);
		threads.add(gatewayBuild.start());
		gatewayBuild.waitForCompletion();
		throwExceptions(threads);
		return controlBuild.getResponse().getExporterResponseMap();
	}

	/**
	 * Throws any queued exceptions. If there are no exceptions, does nothing.
	 *
	 * @param threads the threads to interrupt before throwing an exception
	 * @throws NullPointerException if {@code threads} is null
	 * @throws IOException          if any exceptions were queued
	 */
	private void throwExceptions(Collection<Thread> threads) throws IOException
	{
		IOException exception = Exceptions.combineAsIOException(exceptions);
		if (exception != null)
		{
			for (Thread thread : threads)
				thread.interrupt();
			throw exception;
		}
	}

	/**
	 * Returns the session associated with this build.
	 *
	 * @return the session
	 */
	public Session getSession()
	{
		return session;
	}
}