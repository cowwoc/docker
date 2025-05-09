package com.github.cowwoc.docker.internal.npipe.jetty;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A Windows named pipe address.
 * <p>
 * A Windows named pipe address encapsulates a file-system path that Windows named pipes bind or connect to.
 *
 * <p> An <a id="unnamed"></a><i>unnamed</i> {@code NamedPipeAddress} has an empty path. The local address of
 * a {@link SocketChannel} to a Windows named pipe that is <i>automatically</i> or <i>implicitly</i> bound
 * will be unnamed.
 *
 * <p> {@link Path} objects used to create instances of this class must be obtained
 * from the {@linkplain FileSystems#getDefault system-default} file system.
 *
 * @see java.nio.channels.SocketChannel
 * @see java.nio.channels.ServerSocketChannel
 * @since 16
 */
public final class NamedPipeAddress extends SocketAddress
{
	@Serial
	private static final long serialVersionUID = 0L;
	private final transient Path path;

	/**
	 * A serial proxy for all {@link NamedPipeAddress} instances. It captures the file path name and
	 * reconstructs using the public {@link NamedPipeAddress#NamedPipeAddress(Path) constructor}.
	 *
	 * @serial include
	 */
	private static final class Serializer implements Serializable
	{
		@Serial
		private static final long serialVersionUID = 0L;

		/**
		 * The path name.
		 *
		 * @serial
		 */
		private final String path;

		/**
		 * Creates a new Serializer.
		 *
		 * @param path the path of the named pipe
		 * @throws NullPointerException     if {@code path} is null
		 * @throws IllegalArgumentException if {@code path} contains leading or trailing whitespace or is empty
		 */
		private Serializer(String path)
		{
			requireThat(path, "path").isStripped().isNotEmpty();
			this.path = path;
		}

		/**
		 * Creates a {@link NamedPipeAddress} instance, by an invocation of the
		 * {@link NamedPipeAddress#NamedPipeAddress(Path) constructor} passing the path.
		 *
		 * @return a NamedPipeAddress
		 */
		@Serial
		private Object readResolve()
		{
			assert path != null;
			return new NamedPipeAddress(Path.of(path));
		}
	}

	/**
	 * Returns a
	 * <a href="{@docRoot}/serialized-form.html#NamedPipeAddress.Ser">
	 * Ser</a> containing the path name of this instance.
	 *
	 * @return a {@link NamedPipeAddress.Serializer} representing the path name of this instance
	 * @throws ObjectStreamException if an error occurs
	 */
	@Serial
	private Object writeReplace() throws ObjectStreamException
	{
		return new NamedPipeAddress.Serializer(path.toString());
	}

	/**
	 * Throws InvalidObjectException, always.
	 *
	 * @param s the stream
	 * @throws InvalidObjectException always
	 */
	@Serial
	private void readObject(ObjectInputStream s)
		throws InvalidObjectException
	{
		throw new InvalidObjectException("Proxy required");
	}

	/**
	 * Throws InvalidObjectException, always.
	 *
	 * @throws InvalidObjectException always
	 */
	// WORKAROUND: https://github.com/pmd/pmd/pull/5687
	@SuppressWarnings("PMD.UnusedPrivateMethod")
	@Serial
	private void readObjectNoData() throws InvalidObjectException
	{
		throw new InvalidObjectException("Proxy required");
	}

	/**
	 * Creates a {@code NamedPipeAddress} from the path of the named pipe.
	 *
	 * @param path the path of the named pipe
	 * @throws NullPointerException if path is {@code null}
	 */
	public NamedPipeAddress(Path path)
	{
		requireThat(path, "path").isNotNull();
		this.path = path;
	}

	/**
	 * Returns this named pipe's path.
	 *
	 * @return the path
	 */
	public Path getPath()
	{
		return path;
	}

	@Override
	public int hashCode()
	{
		return path.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof NamedPipeAddress other && other.path.equals(this.path);
	}

	/**
	 * Returns a string representation of this {@code NamedPipeAddress}.
	 *
	 * @return this address's path which may be empty for an unnamed address
	 */
	@Override
	public String toString()
	{
		return path.toString();
	}
}