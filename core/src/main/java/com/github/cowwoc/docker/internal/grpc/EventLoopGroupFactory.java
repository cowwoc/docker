package com.github.cowwoc.docker.internal.grpc;

import com.github.cowwoc.pouch.core.WrappedCheckedException;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * Returns the best {@code EventLoopGroup} based on the runtime platform.
 */
public final class EventLoopGroupFactory
{
	private record Cache(IoHandlerFactory ioHandlerFactory, Class<? extends SocketChannel> channelClass)
	{
	}

	private static final Cache CACHE;

	static
	{
		Cache cache = getEpollIoHandler();
		if (cache == null)
			cache = getKqueueIoHandler();
		if (cache == null)
		{
			// Fallback: NIO
			cache = new Cache(NioIoHandler.newFactory(), NioSocketChannel.class);
		}
		CACHE = cache;
	}

	private static Cache getEpollIoHandler()
	{
		try
		{
			Class<?> epoll = Class.forName("io.netty.channel.epoll.Epoll");
			boolean isAvailable = (boolean) epoll.getMethod("isAvailable").invoke(null);
			if (isAvailable)
			{
				IoHandlerFactory ioHandlerFactory = (IoHandlerFactory) Class.forName(
						"io.netty.channel.epoll.EpollIoHandler").
					getMethod("newFactory").invoke(null);
				@SuppressWarnings("unchecked")
				Class<? extends SocketChannel> safeChannelClass = (Class<? extends SocketChannel>) Class.forName(
					"io.netty.channel.epoll.EpollSocketChannel");
				return new Cache(ioHandlerFactory, safeChannelClass);
			}
			return null;
		}
		catch (ClassNotFoundException _)
		{
			return null;
		}
		catch (ReflectiveOperationException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
	}

	private static Cache getKqueueIoHandler()
	{
		// Try KQueue
		try
		{
			Class<?> kqueue = Class.forName("io.netty.channel.kqueue.KQueue");
			boolean isAvailable = (boolean) kqueue.getMethod("isAvailable").invoke(null);
			if (isAvailable)
			{
				IoHandlerFactory ioHandlerFactory = (IoHandlerFactory) Class.forName(
						"io.netty.channel.kqueue.KQueueIoHandler").
					getMethod("newFactory").invoke(null);
				@SuppressWarnings("unchecked")
				Class<? extends SocketChannel> channelClass = (Class<? extends SocketChannel>) Class.forName(
					"io.netty.channel.kqueue.KQueueSocketChannel");
				return new Cache(ioHandlerFactory, channelClass);
			}
			return null;
		}
		catch (ClassNotFoundException _)
		{
			return null;
		}
		catch (Exception e)
		{
			throw WrappedCheckedException.wrap(e);
		}
	}

	/**
	 * Returns a new EventLoopGroup.
	 *
	 * @return the EventLoopGroup
	 */
	public static EventLoopGroup createEventLoopGroup()
	{
		return new MultiThreadIoEventLoopGroup(CACHE.ioHandlerFactory);
	}

	/**
	 * Returns the type of channels supported by the event loop group.
	 *
	 * @return the type of the channels
	 */
	public static Class<? extends SocketChannel> getChannelClass()
	{
		return CACHE.channelClass;
	}
}