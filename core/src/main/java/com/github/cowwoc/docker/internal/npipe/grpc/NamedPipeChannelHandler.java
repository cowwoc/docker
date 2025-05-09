package com.github.cowwoc.docker.internal.npipe.grpc;

import com.github.cowwoc.docker.internal.npipe.jetty.NamedPipeChannel;
import com.github.cowwoc.docker.internal.util.RetryDelay;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A Netty handler for reading/writing from/to a named pipe.
 */
public class NamedPipeChannelHandler extends ChannelDuplexHandler
{
	private final NamedPipeChannel namedPipeChannel;
	private final Selector selector;
	private final ByteBuffer readBuffer = ByteBuffer.allocate(10 * 1024);
	private ByteBufAllocator allocator;
	private PendingWriteQueue pendingWrites;
	private final Thread readThread = Thread.ofVirtual().unstarted(this::readLoop);
	private final Thread writeThread = Thread.ofVirtual().unstarted(this::writeLoop);
	private volatile ChannelHandlerContext context;
	private volatile boolean shutdownRequested;
	private final CountDownLatch threadShutdown = new CountDownLatch(2);
	private final Logger log = LoggerFactory.getLogger(NamedPipeChannelHandler.class);

	/**
	 * Creates a new handler.
	 *
	 * @param namedPipeChannel the channel to handle
	 * @throws NullPointerException if {@code namedPipeChannel} is null
	 * @throws IOException          if an error occurs while registering a {@code Selector} with the channel
	 */
	public NamedPipeChannelHandler(NamedPipeChannel namedPipeChannel) throws IOException
	{
		requireThat(namedPipeChannel, "namedPipeChannel").isNotNull();
		this.namedPipeChannel = namedPipeChannel;
		this.selector = Selector.open();
		namedPipeChannel.register(selector, SelectionKey.OP_WRITE);
	}

	@Override
	public void handlerAdded(ChannelHandlerContext context)
	{
		this.context = context;
		this.pendingWrites = new PendingWriteQueue(context);
		this.allocator = context.alloc();
	}

	private void readLoop()
	{
		try
		{
			while (!shutdownRequested)
			{
				int bytesRead = namedPipeChannel.read(readBuffer);
				if (bytesRead == -1)
				{
					log.info("End of stream reached");
					break;
				}
				if (bytesRead > 0)
				{
					readBuffer.flip();
					ByteBuf byteBuf = allocator.buffer(bytesRead);
					byteBuf.writeBytes(readBuffer);
					readBuffer.clear();

					log.debug("Read {} bytes", bytesRead);
					context.fireChannelRead(byteBuf);
					context.fireChannelReadComplete();
				}
			}
		}
		catch (IOException e)
		{
			exceptionCaught(context, e);
		}
		finally
		{
			threadShutdown.countDown();
		}
	}

	private void writeLoop()
	{
		RetryDelay retry = new RetryDelay(Duration.ofMillis(100), Duration.ofSeconds(1), 2);
		try
		{
			while (!shutdownRequested)
			{
				selector.select();
				for (SelectionKey key : selector.selectedKeys())
				{
					assert key.isWritable() : key;
					Object message = pendingWrites.current();
					if (message != null && writeMessage(message))
					{
						pendingWrites.remove();
						retry.reset();
					}
					else
						retry.sleep();
				}
			}
		}
		catch (IOException | InterruptedException e)
		{
			exceptionCaught(context, e);
		}
		finally
		{
			threadShutdown.countDown();
		}
	}

	/**
	 * Writes a message.
	 *
	 * @param message the message
	 * @return {@code true} if the message was fully written
	 * @throws IOException if an I/O error occurs while writing to the named pipe
	 */
	private boolean writeMessage(Object message) throws IOException
	{
		ByteBuf byteBuf = (ByteBuf) message;
		for (ByteBuffer byteBuffer : byteBuf.nioBuffers())
		{
			while (byteBuffer.hasRemaining())
			{
				int bytesWritten = namedPipeChannel.write(byteBuffer);
				if (bytesWritten == 0)
					return false;
				log.debug("Wrote {} bytes", bytesWritten);
			}
		}
		return true;
	}

	@Override
	public void channelActive(ChannelHandlerContext context)
	{
		this.readThread.start();
		this.writeThread.start();
	}

	@Override
	public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception
	{
		if (message instanceof ByteBuf byteBuf)
		{
			pendingWrites.add(byteBuf, promise);
//			context.channel().config().getWriteBufferHighWaterMark()
//			if (pendingWrites.bytes() > WRITE_BUFFER_WATER_MARK)
//				context.channel().config().setAutoRead(false);
		}
		else
			super.write(context, message, promise);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext context, Throwable cause)
	{
		log.error("", cause);
		context.close();
	}

	@Override
	public void channelInactive(ChannelHandlerContext context) throws InterruptedException
	{
		shutdownRequested = true;
		try
		{
			selector.wakeup();
			selector.close();
		}
		catch (IOException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
		readThread.interrupt();
		writeThread.interrupt();
		threadShutdown.await();
		context.close();
	}
}