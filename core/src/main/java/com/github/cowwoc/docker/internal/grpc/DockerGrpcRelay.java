package com.github.cowwoc.docker.internal.grpc;

import com.github.cowwoc.pouch.core.WrappedCheckedException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrame;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;

/**
 * Relays communication between Docker's {@code /grpc} endpoint and a gRPC client.
 */
public final class DockerGrpcRelay implements AutoCloseable
{
	private static final AtomicInteger ID_GENERATOR = new AtomicInteger();
	/**
	 * The address of this relay.
	 */
	private final LocalAddress address = new LocalAddress("grpc-relay-" + ID_GENERATOR.incrementAndGet());
	/**
	 * Creates channels to the docker server.
	 */
	private final DockerChannelFactory dockerChannelFactory;
	/**
	 * The server that GRPC clients connect to.
	 */
	private Channel grpcServer;
	/**
	 * Maps each grpcClient connection to its associated grpcServer connection.
	 */
	private final ConcurrentMap<Channel, Channel> clientToServerConnection = new ConcurrentHashMap<>();
	private final EventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(LocalIoHandler.newFactory());
	private final Logger log = LoggerFactory.getLogger(DockerGrpcRelay.class);

	/**
	 * Creates a new proxy.
	 *
	 * @param dockerChannelFactory creates connections to the docker server
	 * @throws NullPointerException if {@code dockerChannelFactory} is null
	 */
	public DockerGrpcRelay(DockerChannelFactory dockerChannelFactory)
	{
		requireThat(dockerChannelFactory, "dockerChannelFactory").isNotNull();
		this.dockerChannelFactory = dockerChannelFactory;
	}

	/**
	 * Starts the gRPC relay.
	 *
	 * @throws InterruptedException if the thread is interrupted before the operation completes
	 */
	public void start() throws InterruptedException
	{
		Http2FrameCodec http2Codec = Http2FrameCodecBuilder.forServer().
			frameLogger(new Http2FrameLogger(LogLevel.DEBUG)).
			build();

		Http2MultiplexHandler multiplex = new Http2MultiplexHandler(new GrpcMultiplexHandler());

		ServerBootstrap bootstrap = new ServerBootstrap();
		bootstrap.group(eventLoopGroup).
			channel(LocalServerChannel.class).
			childHandler(getGrpcConnectionInitializer(http2Codec, multiplex));

		this.grpcServer = bootstrap.bind(address).sync().channel();
	}

	/**
	 * @param http2Codec       the HTTP/2 codec
	 * @param multiplexHandler a handler that multiplexes HTTP/2 streams on the server side
	 * @return a channel initializer for new gRPC connections
	 */
	private ChannelInitializer<LocalChannel> getGrpcConnectionInitializer(Http2FrameCodec http2Codec,
		Http2MultiplexHandler multiplexHandler)
	{
		return new ChannelInitializer<>()
		{
			@Override
			protected void initChannel(LocalChannel grpcClient)
			{
				// grpcClient is an HTTP/2 connection
				log.debug("grpcClient opened: {}", grpcClient.id());
				grpcClient.pipeline().addLast(
					new LoggingHandler(getClass().getName() + ".grpcClient", LogLevel.DEBUG), http2Codec,
					multiplexHandler, getDiscardConnectionFrames());

				// Disable message processing until a connection is established to the docker server
				grpcClient.config().setAutoRead(false);
				connectToDocker().addListener((GenericFutureListener<? extends Future<Channel>>) future ->
				{
					Channel dockerServer = future.getNow();
					assert dockerServer != null;
					clientToServerConnection.put(grpcClient, dockerServer);
					// Re-enable message processing
					grpcClient.config().setAutoRead(true);
				});
			}

			@Override
			public void channelInactive(ChannelHandlerContext context) throws Exception
			{
				log.debug("grpcClient closed: {}", context.channel().id());
				Channel dockerServer = clientToServerConnection.remove(context.channel());
				if (dockerServer != null)
				{
					// Close the corresponding grpc connection
					dockerServer.close().addListener(future ->
					{
						if (!future.isSuccess())
							log.error("Failed to close grpcClient", future.cause());
					});
				}
				super.channelInactive(context);
			}
		};
	}

	/**
	 * Returns a new connection to the docker server.
	 *
	 * @return the connection
	 */
	private Promise<Channel> connectToDocker()
	{
		Promise<Channel> promise = dockerChannelFactory.getEventLoopGroup().next().newPromise();

		dockerChannelFactory.connect(new ChannelInitializer<>()
		{
			@Override
			protected void initChannel(Channel dockerServer)
			{
				log.debug("dockerServer opened: {}", dockerServer.id());
				dockerServer.pipeline().addLast(
					new LoggingHandler(getClass().getName() + ".dockerServer", LogLevel.DEBUG),
					new CloseOnException("dockerServer"));
				upgradeToH2c(dockerServer, promise);
			}

			@Override
			public void channelInactive(ChannelHandlerContext context) throws Exception
			{
				log.debug("dockerServer closed: {}", context.channel().id());
				super.channelInactive(context);
			}
		}).addListener((ChannelFuture future) ->
		{
			if (!future.isSuccess())
				log.error("Failed to connect to docker server", future.cause());
		});
		return promise;
	}

	/**
	 * Upgrades an HTTP/1.1 connection to HTTP/2 Cleartext.
	 *
	 * @param dockerServer the connection to the docker server
	 * @param promise      the promise to notify when the connection is ready for use
	 */
	private void upgradeToH2c(Channel dockerServer, Promise<Channel> promise)
	{
		// Based on https://github.com/jprante/netty-http/blob/ae1240822f522ce10c70692a44f5077e46b32a5a/netty-http-server/src/test/java/org/xbib/netty/http/server/test/hacks/MultiplexCodecCleartextHttp2Test.java
		Http2ConnectionHandler http2Codec = Http2FrameCodecBuilder.forClient().
			frameLogger(new Http2FrameLogger(LogLevel.DEBUG)).
			build();

		Http2MultiplexHandler multiplexHandler = getServerMultiplexHandler();
		SimpleChannelInboundHandler<FullHttpResponse> failureHandler = getUpgradeFailedHandler(promise);
		Http2ClientUpgradeCodec upgradeCodec = getHttp2UpgradeCodec(dockerServer, http2Codec, multiplexHandler,
			failureHandler, promise);
		HttpClientCodec http1Codec = new HttpClientCodec();
		HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(http1Codec, upgradeCodec, 65_536);
		dockerServer.pipeline().addLast(http1Codec, upgradeHandler, failureHandler, getDiscardConnectionFrames(),
			new ChannelInboundHandlerAdapter()
			{
				@Override
				public void channelActive(ChannelHandlerContext ctx) throws Exception
				{
					super.channelActive(ctx);
					// Based on https://github.com/moby/moby/blob/c3a7df35e7c024dca6fc43bf02dd30783e912825/client/hijack.go#L53
					FullHttpRequest upgradeRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
						"/grpc");
					upgradeRequest.headers().set(HttpHeaderNames.HOST, dockerChannelFactory.getHostHeader());
					// HttpClientUpgradeHandler will add the upgrade headers on our behalf

					dockerServer.writeAndFlush(upgradeRequest).addListener(future ->
					{
						if (!future.isSuccess())
							log.error("Failed to send upgrade request", future.cause());
					});
				}
			});
	}

	/**
	 * @return a handler that discards connection-level frames
	 */
	private ChannelInboundHandlerAdapter getDiscardConnectionFrames()
	{
		return new ChannelInboundHandlerAdapter()
		{
			@Override
			public void channelRead(ChannelHandlerContext context, Object message) throws Exception
			{
				if (isConnectionLevelFrame(message))
					return;
				super.channelRead(context, message);
			}
		};
	}

	/**
	 * @param dockerServer     the connection to the docker server
	 * @param http2Codec       the HTTP/2 codec
	 * @param multiplexHandler a handler that multiplexes HTTP/2 streams on the server side
	 * @param failureHandler   handles failed HTTP/2 upgrades
	 * @param promise          the callback to notify when the connection is upgrade completes
	 * @return the upgrade codec from HTTP/1 to HTTP/2
	 */
	private Http2ClientUpgradeCodec getHttp2UpgradeCodec(Channel dockerServer,
		Http2ConnectionHandler http2Codec, Http2MultiplexHandler multiplexHandler,
		SimpleChannelInboundHandler<FullHttpResponse> failureHandler, Promise<Channel> promise)
	{
		// HttpClientUpgradeHandler.decode() invokes sourceCodec.upgradeFrom() and upgradeCodec.upgradeTo().
		// This adds http2Codec, multiplexHandler and removes http1Codec from the pipeline.
		return new Http2ClientUpgradeCodec(http2Codec, multiplexHandler)
		{
			@Override
			public void upgradeTo(ChannelHandlerContext context, FullHttpResponse upgradeResponse) throws Exception
			{
				super.upgradeTo(context, upgradeResponse);

				// HttpClientUpgradeHandler.decode() invokes sourceCodec.upgradeFrom() and upgradeCodec.upgradeTo().
				// This adds http2Codec, multiplexHandler and removes http1Codec from the pipeline.
				context.pipeline().remove(failureHandler);
				promise.setSuccess(dockerServer);
			}
		};
	}

	/**
	 * @param promise the callback to notify of the failure
	 * @return a handler that consumes messages if the HTTP/2 upgrade fails
	 */
	private static SimpleChannelInboundHandler<FullHttpResponse> getUpgradeFailedHandler(
		Promise<Channel> promise)
	{
		return new SimpleChannelInboundHandler<>()
		{
			@Override
			protected void channelRead0(ChannelHandlerContext context, FullHttpResponse message)
			{
				String response = "Server replied with status: " + message.status() + "\n" +
					"Headers: " + message.headers() + "\n" +
					"Body: " + message.content().toString(StandardCharsets.UTF_8);
				context.close();
				promise.setFailure(new IllegalStateException("Unexpected response: " + response));
			}
		};
	}

	/**
	 * @return a handler that multiplexes HTTP/2 streams on the server side
	 */
	private Http2MultiplexHandler getServerMultiplexHandler()
	{
		return new Http2MultiplexHandler(new ChannelInitializer<>()
		{
			@Override
			protected void initChannel(Channel dockerStream)
			{
				log.debug("dockerStream opened: {}", dockerStream.id());
				dockerStream.pipeline().addLast(
					new LoggingHandler(getClass().getName() + ".dockerStream", LogLevel.DEBUG),
					new CloseOnException("dockerStream"));
			}

			@Override
			public void channelInactive(ChannelHandlerContext context) throws Exception
			{
				log.debug("dockerStream closed: {}", context.channel().id());
				super.channelInactive(context);
			}
		}, new ChannelInboundHandlerAdapter()
		{
		});
	}

	/**
	 * @param message the incoming frame
	 * @return {@code true} if the frame impacts the entire connection, {@code false} if it impacts a single
	 * 	HTTP/2 frame
	 */
	private boolean isConnectionLevelFrame(Object message)
	{
		return switch (message)
		{
			case Http2SettingsFrame _, Http2SettingsAckFrame _, DefaultHttp2GoAwayFrame _ -> true;
			default -> false;
		};
	}

	/**
	 * Returns the event loop group used by the relay.
	 *
	 * @return the group
	 */
	public EventLoopGroup getEventLoopGroup()
	{
		return eventLoopGroup;
	}

	/**
	 * Returns the address of this channel.
	 *
	 * @return the address
	 */
	public LocalAddress getAddress()
	{
		return address;
	}

	@Override
	public void close()
	{
		try
		{
			if (grpcServer != null)
				grpcServer.close().sync();
			eventLoopGroup.shutdownGracefully().sync();
			dockerChannelFactory.close();
		}
		catch (InterruptedException _)
		{
			// Source: https://mail.openjdk.org/pipermail/coin-dev/2011-March/003162.html
			// AutoCloseable.close() must not throw InterruptedException, as try-with-resources could suppress
			// it, causing higher-level catch statements to miss the interruption. The recommended approach is to
			// catch the exception, restore the thread's interrupt status, and complete close() as quickly and
			// safely as possible. Restoring the interrupt flag ensures that subsequent blocking calls respond
			// immediately to interruption.
			Thread.currentThread().interrupt();
		}
		catch (Exception e)
		{
			throw WrappedCheckedException.wrap(e);
		}
	}

	/**
	 * An HTTP/2 stream handler for gRPC clients.
	 */
	private final class GrpcMultiplexHandler extends ChannelInitializer<Channel>
	{
		@Override
		protected void initChannel(Channel grpcStream)
		{
			// grpcStream is an HTTP/2 stream within the grpcClient connection
			log.debug("grpcStream opened: {}", grpcStream.id());
			grpcStream.pipeline().addLast(
				new LoggingHandler(getClass().getName() + ".grpcStream", LogLevel.DEBUG));
			connectToDocker(grpcStream);
		}

		/**
		 * Creates a corresponding stream to the docker server.
		 *
		 * @param grpcStream an HTTP/2 stream to the gRPC client
		 */
		private void connectToDocker(Channel grpcStream)
		{
			// Disable message processing until a connection is established to the docker server
			grpcStream.config().setAutoRead(false);

			Channel grpcClient = grpcStream.parent();
			Channel dockerServer = clientToServerConnection.get(grpcClient);
			assert (dockerServer != null);
			Http2StreamChannelBootstrap streamBootstrap = new Http2StreamChannelBootstrap(dockerServer);
			streamBootstrap.handler(getDockerStreamInitializer((Http2StreamChannel) grpcStream)).open().
				addListener(future ->
				{
					if (!future.isSuccess())
					{
						log.error("Failed to open HTTP/2 stream to Docker", future.cause());
						grpcStream.close();
					}
				});
		}

		/**
		 * @param grpcStream an HTTP/2 stream to a gRPC client
		 * @return an initializer for the docker stream associated with {@code grpcStream}
		 */
		private ChannelInitializer<Channel> getDockerStreamInitializer(Http2StreamChannel grpcStream)
		{
			return new ChannelInitializer<>()
			{
				@Override
				protected void initChannel(Channel dockerStream)
				{
					log.debug("dockerStream opened: {}", dockerStream.id());
					dockerStream.pipeline().addLast(
						new LoggingHandler(getClass().getName() + ".dockerStream", LogLevel.DEBUG),
						new RelayData("dockerStream", grpcStream));
					grpcStream.pipeline().addLast(new RelayData("grpcStream", (Http2StreamChannel) dockerStream));

					// Re-enable message processing
					grpcStream.config().setAutoRead(true);
				}
			};
		}
	}

	/**
	 * Relays data to another stream.
	 */
	private final class RelayData extends ChannelInboundHandlerAdapter
	{
		private final String name;
		private final Http2StreamChannel remote;

		/**
		 * Creates a new instance.
		 *
		 * @param name   the name of the local stream
		 * @param remote the remote stream to relay data to
		 * @throws NullPointerException if any of the arguments are null
		 * @throws NullPointerException if {@code name} contains leading or trailing whitespace or is empty
		 */
		public RelayData(String name, Http2StreamChannel remote)
		{
			assert that(name, "name").isStripped().isNotEmpty().elseThrow();
			assert that(remote, "remote").isNotNull().elseThrow();
			this.name = name;
			this.remote = remote;
		}

		@Override
		public void channelRead(ChannelHandlerContext context, Object message)
		{
			assert (!isConnectionLevelFrame(message)) : message;
			// Allocate a new frame with the remote stream ID
			Http2StreamFrame copy;
			switch (message)
			{
				case Http2HeadersFrame headersFrame ->
				{
					copy = new DefaultHttp2HeadersFrame(headersFrame.headers(), headersFrame.isEndStream(),
						headersFrame.padding());
					copy.stream(remote.stream());
				}
				case Http2DataFrame dataFrame ->
				{
					copy = dataFrame.copy();
					copy.stream(remote.stream());
				}
				default -> throw new UnsupportedOperationException("Unsupported type of frame: " + message);
			}
			ReferenceCountUtil.release(message);
			remote.writeAndFlush(copy);
		}

		@Override
		public void channelInactive(ChannelHandlerContext context)
		{
			log.debug("{} closed: {}", name, context.channel().id());
			remote.close();
		}
	}

	/**
	 * Closes a channel if an exception is thrown.
	 */
	private final class CloseOnException extends ChannelInboundHandlerAdapter
	{
		private final String name;

		/**
		 * Creates a new instance.
		 *
		 * @param name the name of the channel
		 * @throws NullPointerException if any of the arguments are null
		 * @throws NullPointerException if {@code name} contains leading or trailing whitespace or is empty
		 */
		public CloseOnException(String name)
		{
			assert that(name, "name").isStripped().isNotEmpty().elseThrow();
			this.name = name;
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext context, Throwable cause)
		{
			log.error("Closing {}", name, cause);
			context.channel().close();
		}
	}
}