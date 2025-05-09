package com.github.cowwoc.docker.internal.grpc;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * Adapt HTTP upgrade requests to work with Docker server.
 */
public class DockerUpgradeInitializer extends ChannelInitializer<Channel>
{
	private final Logger log = LoggerFactory.getLogger(DockerUpgradeInitializer.class);

	@Override
	protected void initChannel(Channel ch) throws Exception
	{
		// Add a handler that intercepts the HTTP Upgrade request
		ch.pipeline().addFirst(new ChannelInboundHandlerAdapter()
		{
			@Override
			public void channelActive(ChannelHandlerContext ctx) throws Exception
			{
				// Create an HTTP upgrade request
				FullHttpRequest upgradeRequest = new DefaultFullHttpRequest(
					HttpVersion.HTTP_1_1, HttpMethod.POST, "/grpc");

				// Modify headers for HTTP Upgrade
				InetSocketAddress address = (InetSocketAddress) ch.remoteAddress();
				upgradeRequest.headers().set(HttpHeaderNames.HOST, address.getHostString() + ":" + address.getPort());
				upgradeRequest.headers().set(HttpHeaderNames.CONNECTION, "Upgrade");
				upgradeRequest.headers().set(HttpHeaderNames.UPGRADE, "h2c");

				// Send the HTTP Upgrade request to initiate the connection
				ctx.writeAndFlush(upgradeRequest);
				super.channelActive(ctx);
			}

			@Override
			public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			{
				log.error("", cause);
				ctx.close();
			}
		});
	}
}