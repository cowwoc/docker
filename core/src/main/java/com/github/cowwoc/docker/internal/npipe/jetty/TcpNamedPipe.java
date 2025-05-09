package com.github.cowwoc.docker.internal.npipe.jetty;

import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.io.SocketChannelEndPoint;
import org.eclipse.jetty.util.thread.Scheduler;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.file.Path;

/**
 * A TCP/IP transport over a Windows named pipe.
 */
public final class TcpNamedPipe extends NamedPipeSocket
{
	/**
	 * Creates a new instance.
	 *
	 * @param path the path of the named pipe
	 * @throws NullPointerException if {@code pipe} is null
	 */
	public TcpNamedPipe(Path path)
	{
		super(path);
	}

	@Override
	public SelectableChannel newSelectableChannel()
	{
		SelectorProvider provider = SelectorProvider.provider();
		return new NamedPipeChannel(provider);
	}

	@Override
	public EndPoint newEndPoint(Scheduler scheduler, ManagedSelector selector, SelectableChannel selectable,
		SelectionKey selectionKey)
	{
		return new SocketChannelEndPoint((SocketChannel) selectable, selector, selectionKey, scheduler);
	}
}