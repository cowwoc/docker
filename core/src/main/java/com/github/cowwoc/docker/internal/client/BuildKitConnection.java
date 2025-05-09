package com.github.cowwoc.docker.internal.client;

import com.github.cowwoc.docker.internal.grpc.DockerGrpcRelay;
import io.grpc.ManagedChannel;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Connection to a BuildKit server.
 *
 * @param channel   a gRPC channel to the server
 * @param grpcRelay two-way communication between Docker's {@code /grpc} endpoint and a {@code LocalChannel}
 */
public record BuildKitConnection(ManagedChannel channel, DockerGrpcRelay grpcRelay) implements AutoCloseable
{
	/**
	 * Creates a new instance.
	 *
	 * @param channel   a gRPC channel to the server
	 * @param grpcRelay two-way communication between Docker's {@code /grpc} endpoint and a
	 *                  {@code LocalChannel}
	 * @throws NullPointerException if any of the arguments are null
	 */
	public BuildKitConnection
	{
		requireThat(channel, "channel").isNotNull();
		requireThat(grpcRelay, "grpcRelay").isNotNull();
	}

	@Override
	public void close()
	{
		channel.shutdown();
		grpcRelay.close();
	}
}