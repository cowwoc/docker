package com.github.cowwoc.docker.internal.buildkit;

import com.github.moby.buildkit.v1.apicaps.Caps.APICap;
import com.github.moby.buildkit.v1.frontend.Gateway;
import com.github.moby.buildkit.v1.frontend.Gateway.PingRequest;
import com.github.moby.buildkit.v1.frontend.Gateway.PongResponse;
import com.github.moby.buildkit.v1.frontend.Gateway.Result;
import com.github.moby.buildkit.v1.frontend.Gateway.ReturnResponse;
import com.github.moby.buildkit.v1.frontend.Gateway.SolveRequest;
import com.github.moby.buildkit.v1.frontend.Gateway.SolveResponse;
import com.github.moby.buildkit.v1.frontend.LLBBridgeGrpc;
import com.github.moby.buildkit.v1.frontend.LLBBridgeGrpc.LLBBridgeStub;
import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;

/**
 * The gateway API's view of a build.
 */
public final class GatewayBuild
{
	private static final APICap DISABLED = APICap.newBuilder().setEnabled(false).build();
	private static final Map<String, APICap> DEFAULT_FRONTEND_CAPS = getDefaultFrontendCaps();
	private final Path buildContext;
	private final Path dockerfile;

	private static Map<String, APICap> getDefaultFrontendCaps()
	{
		APICap enabled = APICap.newBuilder().setEnabled(true).build();
		return Map.of("solve.base", enabled,
			"solve.inlinereturn", enabled,
			"resolveimage", enabled,
			"readfile", enabled);
	}

	private final Build build;
	private final Queue<Throwable> exceptions;
	private final Session session;
	private final AtomicReference<SolveResponse> response = new AtomicReference<>();
	private final CountDownLatch stopped = new CountDownLatch(1);

	/**
	 * Creates a new instance.
	 *
	 * @param build        the build to monitor
	 * @param exceptions   the queue to add any exceptions to
	 * @param buildContext the path of the build context
	 * @param dockerfile   the path of the Dockerfile
	 */
	public GatewayBuild(Build build, Queue<Throwable> exceptions, Path buildContext, Path dockerfile)
	{
		assert that(build, "build").isNotNull().elseThrow();
		assert that(exceptions, "exceptions").isNotNull().elseThrow();
		assert that(buildContext, "buildContext").isNotNull().elseThrow();
		assert that(dockerfile, "dockerfile").isNotNull().elseThrow();
		this.build = build;
		this.exceptions = exceptions;
		this.session = build.getSession();
		this.buildContext = buildContext;
		this.dockerfile = dockerfile;
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
				LLBBridgeStub gateway = LLBBridgeGrpc.newStub(session.getConnection().channel()).
					withCallCredentials(addBuildId());

				Map<String, APICap> capabilityToState = getCapabilities(gateway);
				response.set(executeBuild(gateway));
				// Ensure that the frontend supports returning a build result directly to the client as an in-memory
				// object instead of producing an image or writing to a file system.
				assert capabilityToState.getOrDefault("return", DISABLED).getEnabled() : capabilityToState;
				if (exceptions.isEmpty())
				{
					// Ensure that the server supports returning multiple outputs
					assert capabilityToState.getOrDefault("proto.refarray", DISABLED).getEnabled() : capabilityToState;
					Result result = Result.getDefaultInstance();
					ReturnResponse endOfStream = ReturnResponse.newBuilder().build();
					QueuingStreamObserver<ReturnResponse> observer = new QueuingStreamObserver<>(
						session.getClient(), endOfStream);
					gateway.return_(Gateway.ReturnRequest.newBuilder().setResult(result).build(), observer);
					BlockingQueue<ReturnResponse> elements = observer.getElements();
					ReturnResponse response = elements.take();
					ReturnResponse secondResponse = elements.take();
					assert secondResponse == endOfStream : "Expected a single response. Got: " + secondResponse;
					assert elements.isEmpty() : elements;
					exceptions.addAll(observer.getExceptions());
				}
			}
			catch (IOException | InterruptedException e)
			{
				exceptions.add(e);
			}
			finally
			{
				stopped.countDown();
			}
		});
	}

	/**
	 * @return metadata that contains the build ID
	 */
	private CallCredentials addBuildId()
	{
		return new CallCredentials()
		{
			@Override
			public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor,
				MetadataApplier applier)
			{
				Metadata metadata = new Metadata();
				Key<String> key = Key.of("buildkit-controlapi-buildid ", Metadata.ASCII_STRING_MARSHALLER);
				metadata.put(key, build.getId());
				applier.apply(metadata);
			}
		};
	}

	/**
	 * @param gateway the gateway client
	 * @return a map from the ID of a capability to its state
	 * @throws InterruptedException if the thread is interrupted before the operation is complete
	 */
	private Map<String, APICap> getCapabilities(LLBBridgeStub gateway) throws InterruptedException
	{
		PongResponse endOfStream = PongResponse.newBuilder().build();
		QueuingStreamObserver<PongResponse> observer = new QueuingStreamObserver<>(session.getClient(),
			endOfStream);
		gateway.ping(PingRequest.getDefaultInstance(), observer);

		BlockingQueue<PongResponse> elements = observer.getElements();
		PongResponse response = elements.take();
		PongResponse secondResponse = elements.take();
		assert secondResponse == endOfStream : "Expected a single response. Got: " + secondResponse;
		assert elements.isEmpty() : elements;

		List<APICap> frontendCaps = response.getFrontendAPICapsList();
		Map<String, APICap> capabilityToState = new ConcurrentHashMap<>();
		if (frontendCaps.isEmpty())
			capabilityToState.putAll(DEFAULT_FRONTEND_CAPS);
		else
		{
			for (APICap capability : frontendCaps)
			{
				String id = capability.getID();
				if (!id.isEmpty())
					capabilityToState.put(id, capability);
			}
		}
		return capabilityToState;
	}

	/**
	 * Executes the build.
	 *
	 * @param gateway the gateway client
	 * @return the value returned by the build
	 * @throws InterruptedException if the thread is interrupted before the operation is complete
	 * @throws IOException          if an I/O error occurs when reading the build context or dockerfile
	 */
	private SolveResponse executeBuild(LLBBridgeStub gateway) throws InterruptedException, IOException
	{
		SolveResponse endOfStream = SolveResponse.newBuilder().build();
		QueuingStreamObserver<SolveResponse> observer = new QueuingStreamObserver<>(
			session.getClient(), endOfStream);
		gateway.solve(createSolveRequest(), observer);

		BlockingQueue<SolveResponse> elements = observer.getElements();
		SolveResponse response = elements.take();
		SolveResponse secondResponse = elements.take();
		assert secondResponse == endOfStream : "Expected a single response. Got: " + secondResponse;
		assert elements.isEmpty() : elements;
		return response;
	}

	/**
	 * @return the solve request
	 * @throws IOException if an I/O error occurs when reading the build context or dockerfile
	 */
	private SolveRequest createSolveRequest() throws IOException
	{
		Git git = new Git();
		SolveRequest.Builder requestBuilder = SolveRequest.newBuilder().
			setFrontend("dockerfile.v0").
			putFrontendOpt("filename", dockerfile.toString()).
			putFrontendOpt("image-resolve-mode", ImageResolveMode.LOCAL.toGrpc()).
			putFrontendOpt("vcs:revision", git.getCommitHash()).
			putFrontendOpt("vcs:source", git.getRemoteUrl());
		Map<String, Path> nameToLocalMount = new HashMap<>();
		nameToLocalMount.put("context", buildContext);
		nameToLocalMount.put("dockerfile", dockerfile);

		for (Entry<String, Path> entry : nameToLocalMount.entrySet())
		{
			String name = entry.getKey();
			Path localMount = entry.getValue();
			Path relativePath = buildContext.relativize(localMount.toAbsolutePath().normalize());
			requestBuilder.putFrontendOpt("vcs:localdir:" + name, relativePath.toString());
		}
		return requestBuilder.build();
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

	/**
	 * Blocks until the build completes.
	 *
	 * @throws InterruptedException if the thread is interrupted before the operation is complete
	 */
	public void waitForCompletion() throws InterruptedException
	{
		stopped.await();
	}
}