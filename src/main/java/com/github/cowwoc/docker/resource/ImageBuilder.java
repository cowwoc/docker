package com.github.cowwoc.docker.resource;

import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.internal.util.DockerfileParser;
import com.github.cowwoc.docker.internal.util.DockerignoreParser;
import com.github.cowwoc.docker.internal.util.ImageBuildListener;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.Request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.POST;

/**
 * Builds an image.
 */
public final class ImageBuilder
{
	private final DockerfileParser dockerfileParser = new DockerfileParser();
	private final DockerignoreParser dockerignoreParser = new DockerignoreParser();
	private final DockerClient client;
	private Path buildContext = Path.of(".").toAbsolutePath().normalize();
	private Path dockerfile = buildContext.resolve("Dockerfile");
	private final Set<String> platforms = new HashSet<>();
	private final Set<String> tags = new HashSet<>();

	/**
	 * Creates a new instance.
	 *
	 * @param client the client configuration
	 * @throws NullPointerException if {@code client} is null
	 */
	public ImageBuilder(DockerClient client)
	{
		requireThat(client, "client").isNotNull();
		this.client = client;
	}

	/**
	 * Sets the build context, the directory relative to which paths in the Dockerfile are evaluated. By
	 * default, this path is the current working directory.
	 *
	 * @param buildContext the path of the build context
	 * @return this
	 * @throws NullPointerException if {@code buildContext} is null
	 */
	public ImageBuilder buildContext(Path buildContext)
	{
		requireThat(buildContext, "buildContext").isNotNull();
		this.buildContext = buildContext;
		return this;
	}

	/**
	 * Sets the path of the {@code Dockerfile}. By default, the builder looks for the file in the current
	 * working directory.
	 *
	 * @param dockerfile the path of the {@code Dockerfile}
	 * @return this
	 * @throws NullPointerException if {@code dockerFile} is null
	 */
	public ImageBuilder dockerfile(Path dockerfile)
	{
		requireThat(dockerfile, "dockerfile").isNotNull();
		this.dockerfile = dockerfile;
		return this;
	}

	/**
	 * Adds the platform to build the image for.
	 *
	 * @param platform the platform of the image
	 * @return this
	 * @throws NullPointerException     if {@code platform} is null
	 * @throws IllegalArgumentException if {@code platform} contains leading or trailing whitespace or is empty
	 */
	public ImageBuilder platform(String platform)
	{
		requireThat(platform, "platform").isStripped().isNotEmpty();
		this.platforms.add(platform);
		return this;
	}

	/**
	 * Adds a tag to apply to the image.
	 *
	 * @param tag the tag
	 * @return this
	 * @throws NullPointerException     if {@code tag} is null
	 * @throws IllegalArgumentException if {@code tag} contains leading or trailing whitespace or is empty
	 */
	public ImageBuilder tag(String tag)
	{
		requireThat(tag, "tag").isStripped().isNotEmpty();
		this.tags.add(tag);
		return this;
	}

	/**
	 * Builds the image.
	 *
	 * @return the new image
	 * @throws IllegalArgumentException if {@code dockerFile} is not in the
	 *                                  {@link #buildContext(Path) buildContext}
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public Image build() throws IOException, TimeoutException, InterruptedException
	{
		requireThat(buildContext, "buildContext").contains(dockerfile, "dockerFile");

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageBuild
		URI uri = client.getServer().resolve("build");
		Request request = client.createRequest(uri).
			param("platform", String.join(",", platforms));
		// Path.relativize() requires both Paths to be relative or absolute
		Path absoluteBuildContext = buildContext.toAbsolutePath().normalize();
		Path dockerfile = absoluteBuildContext.relativize(this.dockerfile.toAbsolutePath().normalize());

		// TarArchiveEntry converts Windows-style slashes to / so we need to do the same
		String dockerfileAsString = dockerfile.toString().replace('\\', '/');
		if (!dockerfileAsString.equals("Dockerfile"))
			request.param("dockerfile", dockerfileAsString);
		for (String tag : tags)
			request.param("t", tag);

		// Per https://docs.docker.com/build/concepts/context/#filename-and-location:
		// A Dockerfile-specific ignore-file takes precedence over the .dockerignore file at the root of the
		// build context if both exist.
		Path dockerignore = this.dockerfile.resolveSibling(this.dockerfile.getFileName() + ".dockerignore");
		if (Files.notExists(dockerignore))
			dockerignore = buildContext.resolve(".dockerignore");

		Predicate<Path> dockerFilePredicate = dockerfileParser.parse(dockerfile, absoluteBuildContext);
		Predicate<Path> buildContextPredicate;
		if (Files.exists(dockerignore))
		{
			buildContextPredicate = dockerignoreParser.parse(dockerignore, absoluteBuildContext).negate().
				and(dockerFilePredicate);
		}
		else
			buildContextPredicate = dockerFilePredicate;

		byte[] buildContextAsTar = getBuildContextAsTar(buildContextPredicate, absoluteBuildContext);
		request.body(new BytesRequestContent("application/x-tar", buildContextAsTar)).
			method(POST);

		ImageBuildListener responseListener = new ImageBuildListener(client);
		client.send(request, responseListener);
		if (!responseListener.getExceptionReady().await(5, TimeUnit.MINUTES))
			throw new TimeoutException();
		IOException exception = responseListener.getException();
		if (exception != null)
		{
			// Need to wrap the exception to ensure that it contains stack trace elements from the current thread
			throw new IOException(exception);
		}
		return new Image(client, responseListener.imageId, Map.of(), Map.of());
	}

	/**
	 * Creates a TAR archive containing the contents of the build context.
	 *
	 * @param predicate    a function that returns {@code true} to include a path in the build context and
	 *                     {@code false} to exclude it
	 * @param buildContext the build context
	 * @return the contents of the TAR archive
	 * @throws NullPointerException if any of the arguments are null
	 * @throws IOException          if an I/O error occurs while reading files or writing to the stream
	 */
	private byte[] getBuildContextAsTar(Predicate<Path> predicate, Path buildContext) throws IOException
	{
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
		{
			// Need two separate try-with-resource blocks to flush/close the archive before accessing its bytes
			try (GZIPOutputStream gzos = new GZIPOutputStream(baos);
			     TarArchiveOutputStream tos = new TarArchiveOutputStream(gzos))
			{
				tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
				tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
				Files.walkFileTree(buildContext, new SimpleFileVisitor<>()
				{
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
					{
						if (predicate.test(file))
						{
							Path fileRelativeToBuildContext = buildContext.relativize(file.toAbsolutePath());
							addToTar(tos, file, fileRelativeToBuildContext.toString());
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
					{
						if (predicate.test(dir))
						{
							Path dirRelativeToBuildContext = buildContext.relativize(dir.toAbsolutePath());
							addToTar(tos, dir, dirRelativeToBuildContext + "/");
							return FileVisitResult.CONTINUE;
						}
						return FileVisitResult.SKIP_SUBTREE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException
					{
						throw e;
					}
				});
			}
			return baos.toByteArray();
		}
	}

	/**
	 * Adds a file to a TAR stream.
	 *
	 * @param tos       the stream
	 * @param path      the file to add
	 * @param entryName the name of the entry in the stream
	 * @throws IOException if an I/O error occurs while reading files or writing to the stream
	 */
	private static void addToTar(TarArchiveOutputStream tos, Path path, String entryName) throws IOException
	{
		TarArchiveEntry entry = new TarArchiveEntry(path.toFile(), entryName);
		if (Files.isSymbolicLink(path))
		{
			entry.setLinkName(Files.readSymbolicLink(path).toString());
			entry.setMode(TarArchiveEntry.LF_SYMLINK);
		}
		tos.putArchiveEntry(entry);
		if (!Files.isDirectory(path) && !Files.isSymbolicLink(path))
			Files.copy(path, tos);
		tos.closeArchiveEntry();
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ImageBuilder.class).
			add("platforms", platforms).
			add("tags", tags).
			toString();
	}
}