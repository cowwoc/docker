package com.github.cowwoc.docker.internal.buildkit;

import com.github.moby.buildkit.v1.ControlOuterClass.CacheOptions;
import com.github.moby.buildkit.v1.ControlOuterClass.SolveRequest;
import com.github.moby.buildkit.v1.ControlOuterClass.SolveResponse;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;

/**
 * The control API's view of a build.
 */
public final class ControlBuild
{
	private final Build build;
	private final Queue<Throwable> exceptions;
	private final AtomicReference<SolveResponse> response = new AtomicReference<>();

	/**
	 * Creates a new instance.
	 *
	 * @param build      the build to monitor
	 * @param exceptions the queue to add any exceptions to
	 */
	public ControlBuild(Build build, Queue<Throwable> exceptions)
	{
		assert that(build, "build").isNotNull().elseThrow();
		assert that(exceptions, "exceptions").isNotNull().elseThrow();
		this.build = build;
		this.exceptions = exceptions;
	}

	/**
	 * Creates the build.
	 *
	 * @return the build listener's thread
	 * @throws InterruptedException if the thread is interrupted before the operation is complete
	 */
	public Thread start() throws InterruptedException
	{
		return Thread.startVirtualThread(() ->
		{
			try
			{
				SolveResponse endOfStream = SolveResponse.newBuilder().build();
				Session session = build.getSession();
				// WORKAROUND: https://github.com/moby/buildkit/issues/5922
				SolveRequest solveRequest = SolveRequest.newBuilder().
					setRef(build.getId()).
					setCache(CacheOptions.newBuilder().build()).
					setSession(session.getId()).
					build();
				QueuingStreamObserver<SolveResponse> observer = new QueuingStreamObserver<>(session.getClient(),
					endOfStream);
				session.getControl().solve(solveRequest, observer);

				BlockingQueue<SolveResponse> elements = observer.getElements();
				response.set(elements.take());
				SolveResponse secondResponse = elements.take();
				assert secondResponse == endOfStream : "Expected a single response. Got: " + secondResponse;
				assert elements.isEmpty() : elements;
			}
			catch (InterruptedException e)
			{
				exceptions.add(e);
			}
		});
	}

	/**
	 * Returns the value returned by the build.
	 *
	 * @return {@code null} if the response has not been received
	 */
	public SolveResponse getResponse()
	{
		return response.get();
	}
}