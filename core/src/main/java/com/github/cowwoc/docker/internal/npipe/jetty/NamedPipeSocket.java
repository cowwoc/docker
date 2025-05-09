package com.github.cowwoc.docker.internal.npipe.jetty;

import org.eclipse.jetty.io.Transport.Socket;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A socket that wraps a Windows named pipe.
 */
public sealed class NamedPipeSocket extends Socket permits TcpNamedPipe
{
	private final NamedPipeAddress socketAddress;

	/**
	 * Creates a new instance.
	 *
	 * @param path the path of the named pipe
	 * @throws NullPointerException if {@code path} is null
	 */
	public NamedPipeSocket(Path path)
	{
		this.socketAddress = new NamedPipeAddress(path);
	}

	@Override
	public NamedPipeAddress getSocketAddress()
	{
		return socketAddress;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(this.socketAddress);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof NamedPipeSocket other && other.socketAddress.equals(this.socketAddress);
	}

	@Override
	public String toString()
	{
		return "%s[%s]".formatted(super.toString(), this.socketAddress.getPath());
	}
}