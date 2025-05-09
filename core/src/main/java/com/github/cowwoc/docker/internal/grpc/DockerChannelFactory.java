package com.github.cowwoc.docker.internal.grpc;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;

/**
 * Creates channels to the docker server.
 */
public interface DockerChannelFactory extends AutoCloseable
{
	/**
	 * Returns the {@code EventLoopGroup} used by connections to the server.
	 *
	 * @return the {@code EventLoopGroup}
	 */
	EventLoopGroup getEventLoopGroup();

	/**
	 * Connect to the docker server.
	 *
	 * @param channelInitializer initializes the channel
	 * @return the connection
	 */
	ChannelFuture connect(ChannelInitializer<? extends Channel> channelInitializer);

	/**
	 * Returns the value of the {@code Host} header to send to the Docker server.
	 *
	 * @return the value
	 */
	String getHostHeader();

	@Override
	void close();
}