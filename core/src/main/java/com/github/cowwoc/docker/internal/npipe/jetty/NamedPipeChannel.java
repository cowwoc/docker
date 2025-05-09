package com.github.cowwoc.docker.internal.npipe.jetty;

import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.InterruptibleChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * A {@code SelectableChannel} implementation for Windows named pipes.
 */
public class NamedPipeChannel extends AbstractSelectableChannel
	implements ByteChannel, Channel, GatheringByteChannel, InterruptibleChannel, ReadableByteChannel,
	ScatteringByteChannel, WritableByteChannel
{
	private Pipe.SourceChannel source;
	private Pipe.SinkChannel sink;
	private AsynchronousFileChannel namedPipe;
	private boolean connected;
	private final Logger log = LoggerFactory.getLogger(NamedPipeChannel.class);

	/**
	 * Creates a new channel.
	 *
	 * @param provider the provider that created this channel
	 * @throws NullPointerException if {@code provider} is null
	 */
	public NamedPipeChannel(SelectorProvider provider)
	{
		super(provider);
		requireThat(provider, "provider").isNotNull();
	}

	/**
	 * Connects to the named pipe.
	 * <p>
	 * An invocation of this method will block until the connection is established or an I/O error occurs.
	 * <p> This method may be invoked at any time.  If a read or write
	 * operation upon this channel is invoked while an invocation of this method is in progress then that
	 * operation will first block until this invocation is complete.  If a connection attempt is initiated but
	 * fails, that is, if an invocation of this method throws a checked exception, then the channel will be
	 * closed.  </p>
	 *
	 * @param path the path of the named pipe to which this channel is to be connected
	 * @return {@code true} if a connection was established, {@code false} if this channel is in non-blocking
	 * 	mode and the connection operation is in progress
	 * @throws AlreadyConnectedException  if this channel is already connected
	 * @throws ClosedChannelException     if this channel is closed
	 * @throws AsynchronousCloseException if another thread closes this channel while the connect operation is
	 *                                    in progress
	 * @throws ClosedByInterruptException if another thread interrupts the current thread while the connect
	 *                                    operation is in progress, thereby closing the channel and setting the
	 *                                    current thread's interrupt status
	 * @throws IOException                if some other I/O error occurs
	 */
	@SuppressWarnings("BusyWait")
	public boolean connect(Path path) throws IOException
	{
		requireThat(path, "path").isNotNull();
		if (connected)
			throw new IllegalStateException("Already connected");

		while (isOpen())
		{
			try
			{
				this.namedPipe = AsynchronousFileChannel.open(path, READ, WRITE);
				Pipe pipe = Pipe.open();
				this.source = pipe.source();
				this.source.configureBlocking(false);

				this.sink = pipe.sink();
				startPipeReader();
				connected = true;
				return true;
			}
			catch (IOException e)
			{
				log.warn("Retrying after 1 second", e);
				try
				{
					Thread.sleep(1000);
				}
				catch (InterruptedException ex)
				{
					throw new ClosedByInterruptException();
				}
			}
		}
		if (!isOpen())
			throw new AsynchronousCloseException();
		return true;
	}

	/**
	 * Start a background task that reads from the named pipe and pushes the data into the pipe sink channel.
	 */
	private void startPipeReader()
	{
		Thread.startVirtualThread(() ->
		{
			ByteBuffer buffer = ByteBuffer.allocate(4096);
			long position = 0;

			try
			{
				try
				{
					while (isOpen())
					{
						// Asynchronously read from the named pipe
						Future<Integer> result = namedPipe.read(buffer, position);
						int bytesRead = result.get();
						if (bytesRead == -1)
							break;

						position += bytesRead;
						buffer.flip();

						// Push the data into the pipe's sink channel
						while (buffer.hasRemaining())
							sink.write(buffer);
						buffer.clear();
					}
				}
				finally
				{
					close();
				}
			}
			catch (IOException | InterruptedException | ExecutionException e)
			{
				throw WrappedCheckedException.wrap(e);
			}
		});
	}

	@Override
	protected void implConfigureBlocking(boolean block) throws IOException
	{
		if (!connected)
			throw new IllegalStateException("Not connected to the named pipe");
		source.configureBlocking(block);
	}

	@Override
	public int validOps()
	{
		return OP_READ | OP_WRITE;
	}

	@Override
	public long read(ByteBuffer[] dsts, int offset, int length) throws IOException
	{
		return source.read(dsts, offset, length);
	}

	@Override
	public long read(ByteBuffer[] dsts) throws IOException
	{
		return source.read(dsts);
	}

	@Override
	public int read(ByteBuffer dst) throws IOException
	{
		return source.read(dst);
	}

	@Override
	public int write(ByteBuffer src) throws IOException
	{
		return sink.write(src);
	}

	@Override
	public long write(ByteBuffer[] srcs, int offset, int length) throws IOException
	{
		return sink.write(srcs, offset, length);
	}

	@Override
	public long write(ByteBuffer[] srcs) throws IOException
	{
		return sink.write(srcs);
	}

	@Override
	protected void implCloseSelectableChannel() throws IOException
	{
		if (source != null)
			source.close();
		if (sink != null)
			sink.close();
		if (namedPipe != null)
			namedPipe.close();
	}
}