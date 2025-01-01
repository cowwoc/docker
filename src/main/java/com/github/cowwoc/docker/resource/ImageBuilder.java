package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.internal.util.ClientRequests;
import com.github.cowwoc.docker.internal.util.StreamListener;
import com.github.cowwoc.docker.internal.util.Strings;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import java.util.zip.GZIPOutputStream;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.util.BufferUtil.EMPTY_BUFFER;

/**
 * Builds an image.
 */
public final class ImageBuilder
{
	private final DockerClient client;
	private Path buildContext = Path.of(".").toAbsolutePath().normalize();
	private Path dockerFile = buildContext.resolve("Dockerfile");
	private final Set<String> platforms = new HashSet<>();
	private final Set<String> tags = new HashSet<>();
	private final Logger log = LoggerFactory.getLogger(ImageBuilder.class);

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
	 * @param dockerFile the path of the {@code Dockerfile}
	 * @return this
	 * @throws NullPointerException if {@code dockerFile} is null
	 */
	public ImageBuilder dockerFile(Path dockerFile)
	{
		requireThat(dockerFile, "dockerFile").isNotNull();
		this.dockerFile = dockerFile;
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
	 * @throws IllegalArgumentException if {@code dockerFile} is not {@code buildContext}
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
		requireThat(buildContext, "buildContext").contains(dockerFile, "dockerFile");

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageBuild
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		ClientRequests clientRequests = client.getClientRequests();
		String uri = client.getUri() + "/build";
		Request request = httpClient.newRequest(uri).
			transport(client.getTransport());
		request.param("platform", String.join(",", platforms));
		String relativeDockerFile = dockerFile.relativize(buildContext).toString();
		if (!relativeDockerFile.equals("Dockerfile"))
			request.param("dockerfile", relativeDockerFile);
		for (String tag : tags)
			request.param("t", tag);
		byte[] buildContextAsTar = getBuildContextAsTar(buildContext);
		ImageBuildListener responseListener = new ImageBuildListener();
		clientRequests.send(request.method(POST).
				body(new BytesRequestContent("application/x-tar", buildContextAsTar)),
			responseListener);
		if (!responseListener.responseComplete.await(5, TimeUnit.MINUTES))
			throw new TimeoutException();
		if (responseListener.exception != null)
			throw responseListener.exception;
		return new Image(client, responseListener.imageId, Map.of(), Map.of());
	}

	/**
	 * Logs the output of "docker build" incrementally.
	 */
	private final class ImageBuildListener extends StreamListener
	{
		public String imageId;
		private final StringBuilder linesToLog = new StringBuilder();

		/**
		 * Creates a new instance.
		 */
		public ImageBuildListener()
		{
		}

		@Override
		protected void processObject(String jsonAsString)
		{
			try
			{
				JsonNode json = client.getObjectMapper().readTree(jsonAsString);
				JsonNode streamNode = json.get("stream");
				if (streamNode != null)
				{
					String line = streamNode.textValue();
					linesToLog.append(line);
					Strings.logLines(linesToLog, log);
				}
				JsonNode auxNode = json.get("aux");
				if (auxNode != null)
				{
					assert (imageId == null);
					JsonNode idNode = auxNode.get("ID");
					Set<String> fieldNames = new HashSet<>();
					auxNode.fieldNames().forEachRemaining(fieldNames::add);
					fieldNames.remove("ID");
					if (!fieldNames.isEmpty())
						log.warn("Unexpected fields: {}", fieldNames);
					imageId = idNode.textValue();
				}
			}
			catch (JsonProcessingException e)
			{
				if (exception != null)
					e.addSuppressed(exception);
				exception = e;
			}
		}

		@Override
		public void onComplete(Result result)
		{
			decodeObjects(EMPTY_BUFFER, true);
			processObject(true);
			if (!linesToLog.isEmpty())
				log.info(linesToLog.toString());

			Response serverResponse = result.getResponse();
			if (serverResponse.getStatus() != OK_200)
			{
				IOException ioe = new IOException("Unexpected response: " + serverResponse.getStatus());
				if (exception != null)
					ioe.addSuppressed(exception);
				exception = ioe;
			}
			responseComplete.countDown();
		}
	}

	/**
	 * Creates a TAR archive containing the contents of the build context.
	 *
	 * @param buildContext the path of the build context
	 * @return the contents of the TAR archive
	 * @throws NullPointerException if {@code buildContext} is null
	 * @throws IOException          if an I/O error occurs while reading files or writing to the stream
	 */
	private byte[] getBuildContextAsTar(Path buildContext) throws IOException
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
						addToTar(tos, file, buildContext.relativize(file).toString());
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
					{
						addToTar(tos, dir, buildContext.relativize(dir) + "/");
						return FileVisitResult.CONTINUE;
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
		var entry = new TarArchiveEntry(path.toFile(), entryName);
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