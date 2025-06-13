package com.github.cowwoc.anchor4j.core.resource;

import com.github.cowwoc.anchor4j.core.exception.BuilderNotFoundException;
import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.client.InternalClient;
import com.github.cowwoc.anchor4j.core.internal.client.Processes;
import com.github.cowwoc.anchor4j.core.internal.resource.BuildXParser;
import com.github.cowwoc.anchor4j.core.internal.resource.ErrorHandler;
import com.github.cowwoc.anchor4j.core.internal.util.Exceptions;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.requirements11.annotation.CheckReturnValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Represents an operation that builds an image.
 */
@SuppressWarnings("PMD.MoreThanOneLogger")
public final class ImageBuilder
{
	private static final Pattern ERROR_READING_PREFACE = Pattern.compile(".+? .+? http2: server: " +
		"error reading preface from client .+?: file has already been closed\n");
	private static final Pattern FILE_NOT_FOUND_PATTERN = Pattern.compile("ERROR: resolve : " +
		"CreateFile (.+?): The system cannot find the file specified\\.");

	private final InternalClient client;
	private final ErrorHandler errorHandler;
	private Path dockerfile;
	private final Set<String> platforms = new HashSet<>();
	private final Set<String> tags = new HashSet<>();
	private final Set<String> cacheFrom = new HashSet<>();
	private final Set<Exporter> exporters = new LinkedHashSet<>();
	private ProgressType progressType = ProgressType.PLAIN;
	private String builder = "";
	private CommandListener listener = new DefaultEventListener();
	private final Logger log = LoggerFactory.getLogger(ImageBuilder.class);
	private final Logger stdoutLog = LoggerFactory.getLogger(ImageBuilder.class.getName() + ".stdout");
	private final Logger stderrLog = LoggerFactory.getLogger(ImageBuilder.class.getName() + ".stderr");

	/**
	 * Creates an image builder.
	 *
	 * @param client       the client configuration
	 * @param errorHandler a callback that enables the listener to handle additional errors
	 */
	ImageBuilder(InternalClient client, ErrorHandler errorHandler)
	{
		assert that(client, "client").isNotNull().elseThrow();
		assert that(errorHandler, "errorHandler").isNotNull().elseThrow();
		this.client = client;
		this.errorHandler = errorHandler;
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
	 * Adds a platform to build the image for.
	 *
	 * @param platform the platform of the image
	 * @return this
	 * @throws NullPointerException     if {@code platform} is null
	 * @throws IllegalArgumentException if {@code platform} contains whitespace or is empty
	 */
	public ImageBuilder platform(String platform)
	{
		requireThat(platform, "platform").doesNotContainWhitespace().isNotEmpty();
		this.platforms.add(platform);
		return this;
	}

	/**
	 * Adds a reference to apply to the image.
	 *
	 * @param reference the reference
	 * @return this
	 * @throws NullPointerException     if {@code reference} is null
	 * @throws IllegalArgumentException if {@code reference}:
	 *                                  <ul>
	 *                                    <li>is empty.</li>
	 *                                    <li>contains any character other than lowercase letters (a–z),
	 *                                    digits (0–9), and the following characters: {@code '.'}, {@code '/'},
	 *                                    {@code ':'}, {@code '_'}, {@code '-'}, {@code '@'}.</li>
	 *                                  </ul>
	 */
	public ImageBuilder reference(String reference)
	{
		client.validateImageReference(reference, "reference");
		this.tags.add(reference);
		return this;
	}

	/**
	 * Adds an external cache source to use. By default, no external cache sources are used.
	 *
	 * @param source the external cache source
	 * @return this
	 * @throws IllegalArgumentException if {@code source} contains whitespace, or is empty
	 * @see <a href="https://docs.docker.com/reference/cli/docker/buildx/build/#cache-from">Possible values</a>
	 */
	public ImageBuilder cacheFrom(String source)
	{
		requireThat(source, "source").doesNotContainWhitespace().isNotEmpty();
		this.cacheFrom.add(source);
		return this;
	}

	/**
	 * Adds an output format and location for the image. By default, a build has no exporters, meaning the
	 * resulting image is discarded after the build completes. However, multiple exporters can be configured to
	 * export the image to one or more destinations.
	 *
	 * @param exporter the exporter
	 * @return this
	 * @throws NullPointerException if {@code exporter} is null
	 */
	public ImageBuilder export(Exporter exporter)
	{
		requireThat(exporter, "exporter").isNotNull();
		this.exporters.add(exporter);
		return this;
	}

	/**
	 * Determines the type of the progress that the build should output. By default, {@link ProgressType#PLAIN}
	 * is used.
	 *
	 * @param progressType the type of the progress output
	 * @return this
	 */
	public ImageBuilder progressType(ProgressType progressType)
	{
		this.progressType = progressType;
		return this;
	}

	/**
	 * Sets the builder instance to use for building the image.
	 *
	 * @param builder the name of the builder. The value must start with a letter, or digit, or underscore, and
	 *                may be followed by additional characters consisting of letters, digits, underscores,
	 *                periods or hyphens.
	 * @return this
	 * @throws NullPointerException     if {@code builder} is null
	 * @throws IllegalArgumentException if {@code builder}'s format is invalid
	 */
	public ImageBuilder builder(String builder)
	{
		client.validateName(builder, "builder");
		this.builder = builder;
		return this;
	}

	/**
	 * Sets the listener used to monitor the building of the image.
	 *
	 * @param listener the command listener
	 * @return this
	 * @throws NullPointerException if {@code listener} is null
	 */
	public ImageBuilder listener(CommandListener listener)
	{
		requireThat(listener, "listener").isNotNull();
		this.listener = listener;
		return this;
	}

	/**
	 * Builds the image.
	 * <p>
	 * <strong>Warning:</strong> This method does <em>not</em> export the built image by default.
	 * To specify and trigger export behavior, you must explicitly call {@link #export(Exporter)}.
	 *
	 * @param buildContext the build context, the directory relative to which paths in the Dockerfile are
	 *                     evaluated
	 * @return the ID of the new image, or null if none of the {@link #export(Exporter) exports} output an image
	 * @throws NullPointerException  if {@code buildContext} is null
	 * @throws FileNotFoundException if any of the referenced paths do not exist
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted before the operation completes. This can
	 *                               happen due to shutdown signals.
	 */
	public String build(Path buildContext) throws IOException, InterruptedException
	{
		// Path.relativize() requires both Paths to be relative or absolute
		Path absoluteBuildContext = buildContext.toAbsolutePath().normalize();

		// https://docs.docker.com/reference/cli/docker/buildx/build/
		List<String> arguments = new ArrayList<>(2 + cacheFrom.size() + 3 + exporters.size() * 2 + 1 +
			tags.size() * 2 + 2 + 1);
		arguments.add("buildx");
		arguments.add("build");
		if (!cacheFrom.isEmpty())
		{
			for (String source : cacheFrom)
				arguments.add("--cache-from=" + source);
		}
		if (dockerfile != null)
		{
			arguments.add("--file");
			arguments.add(dockerfile.toAbsolutePath().toString());
		}
		if (!platforms.isEmpty())
			arguments.add("--platform=" + String.join(",", platforms));

		boolean outputsImage = false;
		for (Exporter exporter : exporters)
		{
			arguments.add("--output");
			arguments.add(exporter.toCommandLine());
			outputsImage = exporter.outputsImage();
		}
		if (progressType != ProgressType.PLAIN)
			arguments.add("--progress=" + progressType.toCommandLine());
		for (String tag : tags)
		{
			arguments.add("--tag");
			arguments.add(tag);
		}
		if (!builder.isEmpty())
		{
			arguments.add("--builder");
			arguments.add(builder);
		}

		if (outputsImage)
		{
			// Write the imageId to a file and return it to the user
			Path imageIdFile = Files.createTempFile(null, null);
			try
			{
				arguments.add("--iidfile");
				arguments.add(imageIdFile.toString());
				buildPart2(arguments, absoluteBuildContext);
				return Files.readString(imageIdFile);
			}
			finally
			{
				// If the build fails, docker deletes the imageIdFile on exit so we shouldn't assume that the file
				// still exists.
				Files.deleteIfExists(imageIdFile);
			}
		}
		buildPart2(arguments, absoluteBuildContext);
		return null;
	}

	/**
	 * Common code at the end of the build step.
	 *
	 * @param arguments            the command-line arguments
	 * @param absoluteBuildContext the absolute path of the build context
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	@SuppressWarnings("BusyWait")
	private void buildPart2(List<String> arguments, Path absoluteBuildContext)
		throws IOException, InterruptedException
	{
		arguments.add(absoluteBuildContext.toString());
		Instant deadline = Instant.now().plusSeconds(10);
		while (true)
		{
			ProcessBuilder processBuilder = client.getProcessBuilder(arguments);
			log.debug("Running: {}", processBuilder.command());
			Process process = processBuilder.start();
			try
			{
				listener.accept(processBuilder, process);
				break;
			}
			catch (IOException e)
			{
				// WORKAROUND: https://github.com/moby/moby/issues/50160
				Instant now = Instant.now();
				if (now.isAfter(deadline))
					throw e;
				Thread.sleep(100);
			}
		}
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ImageBuilder.class).
			add("platforms", platforms).
			add("tags", tags).
			toString();
	}

	/**
	 * The default event listener for builds.
	 */
	private final class DefaultEventListener implements CommandListener
	{
		private final Logger log = LoggerFactory.getLogger(DefaultEventListener.class);

		@Override
		public void accept(ProcessBuilder processBuilder, Process process)
			throws IOException, InterruptedException
		{
			BlockingQueue<Throwable> exceptions = new LinkedBlockingQueue<>();
			StringJoiner stdoutJoiner = new StringJoiner("\n");
			StringJoiner stderrJoiner = new StringJoiner("\n");

			Thread parentThread = Thread.currentThread();
			try (BufferedReader stdoutReader = process.inputReader();
			     BufferedReader stderrReader = process.errorReader())
			{
				Thread stdoutThread = Thread.startVirtualThread(() ->
				{
					stdoutLog.info("Spawned by thread \"{}\"", parentThread.getName());
					Processes.consume(stdoutReader, exceptions, line ->
					{
						stdoutJoiner.add(line);
						stdoutLog.info(line);
					});
				});
				Thread stderrThread = Thread.startVirtualThread(() ->
					Processes.consume(stderrReader, exceptions, line ->
					{
						stderrLog.info("Spawned by thread \"{}\"", parentThread.getName());
						stderrJoiner.add(line);
						// Docker writes build progress to stderr; this does not indicate an error.
						stderrLog.info(line);
					}));

				// We have to invoke Thread.join() to ensure that all the data is read. Blocking on Process.waitFor()
				// does not guarantee this.
				stdoutThread.join();
				stderrThread.join();
				int exitCode = process.waitFor();
				if (exitCode != 0)
				{
					String stdout = stdoutJoiner.toString();
					String stderr = stderrJoiner.toString();
					if (progressType == ProgressType.TTY &&
						stderr.equals("ERROR: failed to get console: The handle is invalid."))
					{
						log.error("ImageBuilder.progressType() was set to TTY, but the current " +
							"environment does not support interactive terminals. Consider using a different progress " +
							"type, such as PLAIN.");
						return;
					}
					Matcher matcher = ERROR_READING_PREFACE.matcher(stderr);
					if (matcher.find())
					{
						// WORKAROUND: https://github.com/docker/buildx/issues/3238
						// Ignore intermittent warning that does not seem to impact the operation. Example:
						// "2025/06/11 15:53:20 http2: server: error reading preface from client //./pipe/dockerDesktopLinuxEngine: file has already been closed"
						stderr = stderr.substring(matcher.end());
					}
					matcher = FILE_NOT_FOUND_PATTERN.matcher(stderr);
					if (matcher.matches())
						throw new FileNotFoundException(matcher.group(1));
					matcher = BuildXParser.NOT_FOUND.matcher(stderr);
					if (matcher.matches())
						throw new BuilderNotFoundException(matcher.group(1));

					String workingDirectory;
					if (processBuilder.directory() != null)
						workingDirectory = processBuilder.directory().getAbsolutePath();
					else
						workingDirectory = System.getProperty("user.dir");
					CommandResult result = new CommandResult(processBuilder.command(), workingDirectory, stdout, stderr,
						exitCode);
					errorHandler.accept(result, stderr);
					throw result.unexpectedResponse();
				}
			}
			IOException exception = Exceptions.combineAsIOException(exceptions);
			if (exception != null)
				throw exception;
		}
	}

	/**
	 * The type of encoding used by progress output.
	 */
	public enum ProgressType
	{
		/**
		 * Output the build progress using ANSI control sequences for colors and to redraw lines.
		 */
		TTY,
		/**
		 * Output the build progress using a plain text format.
		 */
		PLAIN,
		/**
		 * Suppress the build output and print the image ID on success.
		 */
		QUIET,
		/**
		 * Output the build progress as <a href="https://jsonlines.org/">JSON lines</a>.
		 */
		RAW_JSON;

		/**
		 * Returns the command-line representation of this option.
		 *
		 * @return the command-line value
		 */
		public String toCommandLine()
		{
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * Transforms or transmits the build output.
	 */
	public sealed interface Exporter
	{
		/**
		 * Returns the command-line representation of this option.
		 *
		 * @return the command-line value
		 */
		String toCommandLine();

		/**
		 * Outputs the contents of the resulting image.
		 * <p>
		 * For multi-platform builds, a separate subdirectory will be created for each platform.
		 * <p>
		 * For example, the directory structure might look like:
		 * <pre>{@code
		 * /
		 * ├── linux_amd64/
		 * └── linux_arm64/
		 * }</pre>
		 *
		 * @param path the output location, which is either a TAR archive or a directory depending on whether
		 *             {@link ExportContentsToPathBuilder#directory() directory()} is invoked
		 * @return the exporter
		 * @throws NullPointerException     if {@code path} is null
		 * @throws IllegalArgumentException if {@code path} contains whitespace or is empty
		 */
		@CheckReturnValue
		static ExportContentsToPathBuilder contents(String path)
		{
			return new ExportContentsToPathBuilder(path);
		}

		/**
		 * Outputs the resulting image in Docker container format.
		 *
		 * @return the exporter
		 */
		@CheckReturnValue
		static ExportDockerImageBuilder dockerImage()
		{
			return new ExportDockerImageBuilder();
		}

		/**
		 * Outputs images to disk in the
		 * <a href="https://github.com/opencontainers/image-spec/blob/main/image-layout.md">OCI container
		 * format</a>.
		 * <p>
		 * For multi-platform builds, a separate subdirectory will be created for each platform.
		 * <p>
		 * For example, the directory structure might look like:
		 * <pre>{@code
		 * /
		 * ├── linux_amd64/
		 * └── linux_arm64/
		 * }</pre>
		 *
		 * @param path the output location, which is either a TAR archive or a directory depending on whether
		 *             {@link ExportOciImageBuilder#directory() directory()} is invoked
		 * @return the exporter
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if {@code path} contains whitespace or is empty
		 */
		@CheckReturnValue
		static ExportOciImageBuilder ociImage(String path)
		{
			return new ExportOciImageBuilder(path);
		}

		/**
		 * Pushes the resulting image to a registry.
		 *
		 * @return the exporter
		 */
		@CheckReturnValue
		static ExporterImageToRegistryBuilder registry()
		{
			return new ExporterImageToRegistryBuilder();
		}

		/**
		 * Returns the type of the exporter.
		 *
		 * @return the type
		 */
		String getType();

		/**
		 * Indicates if the exporter outputs an image.
		 *
		 * @return {@code true} if it outputs an image
		 */
		boolean outputsImage();
	}

	/**
	 * Represents the type of compression to apply to the output.
	 */
	public enum CompressionType
	{
		/**
		 * Do not compress the output.
		 */
		UNCOMPRESSED,
		/**
		 * Compress the output using <a href="https://en.wikipedia.org/wiki/Gzip">gzip</a>.
		 */
		GZIP,
		/**
		 * Compress the output using
		 * <a href="https://github.com/containerd/stargz-snapshotter/blob/main/docs/estargz.md">eStargz</a>.
		 * <p>
		 * The {@code eStargz} format transforms a gzip-compressed layer into an equivalent tarball where each
		 * file is compressed individually. The system can retrieve each file without having to fetch and
		 * decompress the entire tarball.
		 */
		ESTARGZ,
		/**
		 * Compress the output using <a href="https://en.wikipedia.org/wiki/Zstd">zstd</a>.
		 */
		ZSTD;

		/**
		 * Returns the command-line representation of this option.
		 *
		 * @return the command-line value
		 */
		public String toCommandLine()
		{
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * Builds an exporter that outputs the contents of images to disk.
	 */
	public static final class ExportContentsToPathBuilder
	{
		private final String path;
		private boolean directory;

		/**
		 * Creates a new instance.
		 *
		 * @param path the output location, which is either a TAR archive or a directory depending on whether
		 *             {@link #directory()} is invoked
		 * @throws NullPointerException     if {@code path} is null
		 * @throws IllegalArgumentException if {@code path} contains whitespace or is empty
		 */
		public ExportContentsToPathBuilder(String path)
		{
			requireThat(path, "path").doesNotContainWhitespace().isNotEmpty();
			this.path = path;
		}

		/**
		 * Specifies that the image files should be written to a directory. By default, the image is packaged as a
		 * TAR archive, with {@code path} representing the archive’s location. When this method is used,
		 * {@code path} is treated as a directory, and image files are written directly into it.
		 *
		 * @return this
		 */
		public ExportContentsToPathBuilder directory()
		{
			this.directory = true;
			return this;
		}

		/**
		 * Builds the exporter.
		 *
		 * @return the exporter
		 */
		public Exporter build()
		{
			return new ExporterAdapter();
		}

		private final class ExporterAdapter implements Exporter
		{
			@Override
			public String getType()
			{
				if (directory)
					return "local";
				return "tar";
			}

			@Override
			public boolean outputsImage()
			{
				return false;
			}

			@Override
			public String toCommandLine()
			{
				if (directory)
					return "type=local,dest=" + path;
				return "type=tar,dest=" + path;
			}
		}
	}

	/**
	 * Builds an exporter that outputs images.
	 */
	public static abstract class ExportImageBuilder
	{
		/**
		 * The name of the image.
		 */
		protected String name = "";
		/**
		 * The type of compression to use.
		 */
		protected CompressionType compressionType = CompressionType.GZIP;
		/**
		 * The compression level to use.
		 */
		protected int compressionLevel = -1;

		/**
		 * Sets the image reference of this output. By default, the output name is derived from the image's tag,
		 * if specified; otherwise, the output remains unnamed.
		 *
		 * @param name the image reference
		 * @return this
		 * @throws NullPointerException     if {@code name} is null
		 * @throws IllegalArgumentException if {@code name} contains whitespace or is empty
		 */
		public ExportImageBuilder name(String name)
		{
			requireThat(name, "name").doesNotContainWhitespace().isNotEmpty();
			this.name = name;
			return this;
		}

		/**
		 * Sets the compression type used by the output.
		 * <p>
		 * While the default values provide a good out-of-the-box experience, you may wish to tweak the parameters
		 * to optimize for storage vs compute costs.
		 * <p>
		 * Both Docker and OCI formats compress the image layers. Additionally, when outputting to a TAR archive,
		 * the OCI format supports compressing the entire TAR archive.
		 *
		 * @param type the type
		 * @return this
		 * @throws NullPointerException if {@code type} is null
		 */
		public ExportImageBuilder compressionType(CompressionType type)
		{
			requireThat(type, "type").isNotNull();
			this.compressionType = type;
			return this;
		}

		/**
		 * Sets the compression level used by the output.
		 * <p>
		 * As a general rule, the higher the number, the smaller the resulting file will be, and the longer the
		 * compression will take to run.
		 * <p>
		 * Valid compression level ranges depend on the selected {@code compressionType}:
		 * <ul>
		 *   <li>{@code gzip} and {@code estargz}: level must be between {@code 0} and {@code 9}.</li>
		 *   <li>{@code zstd}: level must be between {@code 0} and {@code 22}.</li>
		 * </ul>
		 * If {@code compressionType} is {@code uncompressed} then {@code compressionLevel} has no effect.
		 *
		 * @param compressionLevel the compression level, increasing the compression effort as the level
		 *                         increases
		 * @return this
		 * @throws IllegalArgumentException if {@code compressionLevel} is out of range
		 */
		public ExportImageBuilder compressionLevel(int compressionLevel)
		{
			switch (compressionType)
			{
				case UNCOMPRESSED ->
				{
				}
				case GZIP, ESTARGZ -> requireThat(compressionLevel, "compressionLevel").isBetween(0, 9);
				case ZSTD -> requireThat(compressionLevel, "compressionLevel").isBetween(0, 22);
			}
			this.compressionLevel = compressionLevel;
			return this;
		}
	}

	/**
	 * Builds an exporter that outputs images to Docker's image store or disk using the Docker container
	 * format.
	 */
	public static final class ExportDockerImageBuilder extends ExportImageBuilder
	{
		private String path;
		private String context = "";

		/**
		 * Creates a new instance.
		 */
		private ExportDockerImageBuilder()
		{
		}

		@Override
		public ExportDockerImageBuilder name(String name)
		{
			super.name(name);
			return this;
		}

		@Override
		public ExportDockerImageBuilder compressionType(CompressionType type)
		{
			super.compressionType(type);
			return this;
		}

		@Override
		public ExportDockerImageBuilder compressionLevel(int compressionLevel)
		{
			super.compressionLevel(compressionLevel);
			return this;
		}

		/**
		 * Indicates that the image should be exported to disk as a TAR archive, rather than being loaded into the
		 * Docker image store (which is the default behavior).
		 * <p>
		 * For multi-platform builds, the TAR archive will contain a separate subdirectory for each target
		 * platform.
		 * <p>
		 * For example, the directory structure might look like:
		 * <pre>{@code
		 * /
		 * ├── linux_amd64/
		 * └── linux_arm64/
		 * }</pre>
		 *
		 * @param path the path of the TAR archive
		 * @return this
		 */
		public ExportDockerImageBuilder path(String path)
		{
			requireThat(path, "path").doesNotContainWhitespace().isNotEmpty();
			this.path = path;
			return this;
		}

		/**
		 * Sets the Docker context into which the built image should be imported. If omitted, the image is
		 * imported into the same context in which the build was executed.
		 *
		 * @param context the name of the context
		 * @return this
		 * @throws NullPointerException     if {@code context} is null
		 * @throws IllegalArgumentException if {@code context}'s format is invalid
		 */
		public ExportDockerImageBuilder context(String context)
		{
			requireThat(context, "context").doesNotContainWhitespace().isNotEmpty();
			this.context = context;
			return this;
		}

		/**
		 * Builds the exporter.
		 *
		 * @return the exporter
		 */
		public Exporter build()
		{
			return new OutputAdapter();
		}

		private final class OutputAdapter implements Exporter
		{
			@Override
			public String getType()
			{
				return "docker";
			}

			@Override
			public boolean outputsImage()
			{
				return true;
			}

			@Override
			public String toCommandLine()
			{
				StringJoiner joiner = new StringJoiner(",");
				joiner.add("type=" + getType());
				if (path != null)
					joiner.add("dest=" + path);
				if (!name.isEmpty())
					joiner.add("name=" + name);
				if (compressionType != CompressionType.GZIP)
					joiner.add("compression=" + compressionType.toCommandLine());
				if (compressionLevel != -1)
					joiner.add("compression-level=" + compressionLevel);
				if (!context.isEmpty())
					joiner.add("context=" + context);
				return joiner.toString();
			}
		}
	}

	/**
	 * Builds an exporter that outputs images to disk using the OCI container format.
	 */
	public static final class ExportOciImageBuilder extends ExportImageBuilder
	{
		private final String path;
		private boolean directory;
		private String context = "";

		/**
		 * Creates a new instance.
		 * <p>
		 * For multi-platform builds, a separate subdirectory will be created for each platform.
		 * <p>
		 * For example, the directory structure might look like:
		 * <pre>{@code
		 * /
		 * ├── linux_amd64/
		 * └── linux_arm64/
		 * }</pre>
		 *
		 * @param path the output location, which is either a TAR archive or a directory depending on whether
		 *             {@link #directory() directory()} is invoked
		 */
		private ExportOciImageBuilder(String path)
		{
			requireThat(path, "path").doesNotContainWhitespace().isNotEmpty();
			this.path = path;
		}

		@Override
		public ExportOciImageBuilder name(String name)
		{
			super.name(name);
			return this;
		}

		@Override
		public ExportOciImageBuilder compressionType(CompressionType type)
		{
			super.compressionType(type);
			return this;
		}

		@Override
		public ExportOciImageBuilder compressionLevel(int compressionLevel)
		{
			super.compressionLevel(compressionLevel);
			return this;
		}

		/**
		 * Specifies that the image files should be written to a directory. By default, the image is packaged as a
		 * TAR archive, with {@code path} representing the archive’s location. When this method is used,
		 * {@code path} is treated as a directory, and image files are written directly into it.
		 *
		 * @return this
		 */
		public ExportOciImageBuilder directory()
		{
			this.directory = true;
			return this;
		}

		/**
		 * Sets the Docker context into which the built image should be imported. If omitted, the image is
		 * imported into the same context in which the build was executed.
		 *
		 * @param context the name of the context
		 * @return this
		 * @throws NullPointerException     if {@code context} is null
		 * @throws IllegalArgumentException if {@code context}'s format is invalid
		 */
		public ExportOciImageBuilder context(String context)
		{
			requireThat(context, "context").doesNotContainWhitespace().isNotEmpty();
			this.context = context;
			return this;
		}

		/**
		 * Builds the exporter.
		 *
		 * @return the exporter
		 */
		public Exporter build()
		{
			return new OutputAdapter();
		}

		private final class OutputAdapter implements Exporter
		{
			@Override
			public String getType()
			{
				return "oci";
			}

			@Override
			public boolean outputsImage()
			{
				return true;
			}

			@Override
			public String toCommandLine()
			{
				StringJoiner joiner = new StringJoiner(",");
				joiner.add("type=" + getType());
				if (path != null)
					joiner.add("dest=" + path);
				if (!name.isEmpty())
					joiner.add("name=" + name);
				if (directory)
					joiner.add("tar=false");
				if (compressionType != CompressionType.GZIP)
					joiner.add("compression=" + compressionType.toCommandLine());
				if (compressionLevel != -1)
					joiner.add("compression-level=" + compressionLevel);
				if (!context.isEmpty())
					joiner.add("context=" + context);
				return joiner.toString();
			}
		}
	}

	/**
	 * Builds an exporter that outputs images to a registry.
	 */
	public static final class ExporterImageToRegistryBuilder extends ExportImageBuilder
	{
		/**
		 * Creates a new instance.
		 */
		private ExporterImageToRegistryBuilder()
		{
		}

		@Override
		public ExporterImageToRegistryBuilder name(String name)
		{
			super.name(name);
			return this;
		}

		@Override
		public ExporterImageToRegistryBuilder compressionType(CompressionType type)
		{
			super.compressionType(type);
			return this;
		}

		@Override
		public ExporterImageToRegistryBuilder compressionLevel(int compressionLevel)
		{
			super.compressionLevel(compressionLevel);
			return this;
		}

		/**
		 * Builds the output.
		 *
		 * @return the output
		 */
		public Exporter build()
		{
			return new OutputAdapter();
		}

		private final class OutputAdapter implements Exporter
		{
			@Override
			public String getType()
			{
				return "registry";
			}

			@Override
			public boolean outputsImage()
			{
				return true;
			}

			@Override
			public String toCommandLine()
			{
				StringJoiner joiner = new StringJoiner(",");
				joiner.add("type=registry");
				if (!name.isEmpty())
					joiner.add("name=" + name);
				if (compressionType != CompressionType.GZIP)
					joiner.add("compression=" + compressionType.toCommandLine());
				if (compressionLevel != -1)
					joiner.add("compression-level=" + compressionLevel);
				return joiner.toString();
			}
		}
	}
}