package com.github.cowwoc.anchor4j.docker.test.resource;

import com.github.cowwoc.anchor4j.core.internal.util.Paths;
import com.github.cowwoc.anchor4j.core.resource.BuilderCreator.Driver;
import com.github.cowwoc.anchor4j.core.resource.DefaultBuildListener;
import com.github.cowwoc.anchor4j.core.resource.ImageBuilder;
import com.github.cowwoc.anchor4j.core.resource.ImageBuilder.Exporter;
import com.github.cowwoc.anchor4j.core.test.TestBuildListener;
import com.github.cowwoc.anchor4j.docker.client.Docker;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.resource.Image;
import com.github.cowwoc.anchor4j.docker.resource.ImageElement;
import com.github.cowwoc.anchor4j.docker.test.IntegrationTestContainer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;

public final class ImageIT
{
	// Use GitHub Container Registry because Docker Hub's rate-limits are too low
	static final String EXISTING_IMAGE = "ghcr.io/hlesey/busybox";
	static final String MISSING_IMAGE = "ghcr.io/cowwoc/missing";
	static final String FILE_IN_CONTAINER = "logback-test.xml";

	@Test
	public void pull() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String id = client.pullImage(EXISTING_IMAGE).pull();
		requireThat(id, "image").isNotNull();
		it.onSuccess();
	}

	@Test
	public void alreadyPulled() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String id1 = client.pullImage(EXISTING_IMAGE).pull();
		requireThat(id1, "id1").isStripped().isNotEmpty();
		String id2 = client.pullImage(EXISTING_IMAGE).pull();
		requireThat(id1, "id1").isEqualTo(id2, "id2");
		it.onSuccess();
	}

	@Test(expectedExceptions = ResourceNotFoundException.class)
	public void pullMissing() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		try
		{
			client.pullImage(MISSING_IMAGE).pull();
		}
		catch (ResourceNotFoundException e)
		{
			it.onSuccess();
			throw e;
		}
	}

	@Test
	public void listEmpty() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		List<ImageElement> images = client.listImages();
		requireThat(images, "images").isEmpty();
		it.onSuccess();
	}

	@Test
	public void list() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String id = client.pullImage(EXISTING_IMAGE).pull();
		requireThat(id, "id").isNotNull();

		List<ImageElement> images = client.listImages();
		requireThat(images, "images").size().isEqualTo(1);
		ImageElement element = images.getFirst();
		requireThat(element.referenceToTags().keySet(), "element.references()").
			isEqualTo(Set.of(EXISTING_IMAGE));
		requireThat(element.referenceToTags(), "element.referenceToTags()").
			isEqualTo(Map.of(EXISTING_IMAGE, Set.of("latest")));
		it.onSuccess();
	}

	@Test
	public void tag() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String id = client.pullImage(EXISTING_IMAGE).pull();
		requireThat(id, "image").isStripped().isNotEmpty();

		List<ImageElement> images = client.listImages();
		requireThat(images, "images").size().isEqualTo(1);
		ImageElement element = images.getFirst();
		requireThat(element.referenceToTags().keySet(), "element.references()").
			isEqualTo(Set.of(EXISTING_IMAGE));

		client.tagImage(id, "rocket-ship");

		images = client.listImages();
		requireThat(images, "images").size().isEqualTo(1);
		element = images.getFirst();
		requireThat(element.referenceToTags().keySet(), "element.references()").
			isEqualTo(Set.of(EXISTING_IMAGE, "rocket-ship"));
		it.onSuccess();
	}

	@Test
	public void alreadyTagged() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String id = client.pullImage(EXISTING_IMAGE).pull();
		requireThat(id, "image").isStripped().isNotEmpty();

		List<ImageElement> images = client.listImages();
		requireThat(images, "images").size().isEqualTo(1);
		ImageElement element = images.getFirst();
		requireThat(element.referenceToTags().keySet(), "element.references()").
			isEqualTo(Set.of(EXISTING_IMAGE));

		client.tagImage(id, "rocket-ship");
		client.tagImage(id, "rocket-ship");

		images = client.listImages();
		requireThat(images, "images").size().isEqualTo(1);
		element = images.getFirst();
		requireThat(element.referenceToTags().keySet(), "element.references()").
			isEqualTo(Set.of(EXISTING_IMAGE, "rocket-ship"));
		it.onSuccess();
	}

	@Test(expectedExceptions = ResourceNotFoundException.class)
	public void tagMissing() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String id = client.pullImage(EXISTING_IMAGE).pull();
		requireThat(id, "image").isStripped().isNotEmpty();

		client.removeImage(id).remove();
		try
		{
			client.tagImage(id, "rocket-ship");
		}
		catch (ResourceNotFoundException e)
		{
			it.onSuccess();
			throw e;
		}
	}

	@Test
	public void get() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String id = client.pullImage(EXISTING_IMAGE).pull();
		Image image = client.getImage(EXISTING_IMAGE);
		requireThat(id, "id").isEqualTo(image.getId(), "image.getId()");
		it.onSuccess();
	}

	@Test
	public void getMissing() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Image image = client.getImage(MISSING_IMAGE);
		requireThat(image, "missingImage").isNull();
		it.onSuccess();
	}

	@Test
	public void buildAndExportToDocker() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");
		String id = client.buildImage().export(Exporter.dockerImage().build()).build(buildContext);
		List<ImageElement> images = client.listImages();
		requireThat(images, "images").contains(new ImageElement(id, Map.of(), Map.of()));
		it.onSuccess();
	}

	@Test
	public void buildWithCustomDockerfile() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");
		String id = client.buildImage().dockerfile(buildContext.resolve("custom/Dockerfile")).
			export(Exporter.dockerImage().build()).
			build(buildContext);
		List<ImageElement> images = client.listImages();
		requireThat(images, "images").contains(new ImageElement(id, Map.of(), Map.of()));
		it.onSuccess();
	}

	@Test(expectedExceptions = FileNotFoundException.class)
	public void buildWithMissingDockerfile() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");
		try
		{
			client.buildImage().dockerfile(buildContext.resolve("missing/Dockerfile")).build(buildContext);
		}
		catch (FileNotFoundException e)
		{
			it.onSuccess();
			throw e;
		}
	}

	@Test
	public void buildWithSinglePlatform() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");
		String id = client.buildImage().platform("linux/amd64").
			export(Exporter.dockerImage().build()).
			build(buildContext);
		List<ImageElement> images = client.listImages();
		requireThat(images, "images").contains(new ImageElement(id, Map.of(), Map.of()));
		it.onSuccess();
	}

	@Test
	public void buildWithMultiplePlatform() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");
		String id = client.buildImage().platform("linux/amd64").platform("linux/arm64").
			export(Exporter.dockerImage().build()).
			build(buildContext);
		List<ImageElement> images = client.listImages();
		requireThat(images, "images").contains(new ImageElement(id, Map.of(), Map.of()));
		it.onSuccess();
	}

	@Test
	public void buildWithTag() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");
		String id = client.buildImage().reference("integration-test").export(Exporter.dockerImage().build()).
			build(buildContext);
		Image image = client.getImage(id);
		List<ImageElement> images = client.listImages();
		requireThat(images, "images").contains(new ImageElement(id, Map.of("integration-test", Set.of("latest")),
			image.getReferenceToDigest()));
		it.onSuccess();
	}

	@Test
	public void buildPassedWithCustomListener() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");

		TestBuildListener listener = new TestBuildListener();
		client.buildImage().listener(listener).build(buildContext);
		requireThat(listener.buildStarted.get(), "buildStarted").isTrue();
		requireThat(listener.waitUntilBuildCompletes.get(), "waitUntilBuildCompletes").isTrue();
		requireThat(listener.buildSucceeded.get(), "buildSucceeded").isTrue();
		requireThat(listener.buildFailed.get(), "buildSucceeded").isFalse();
		requireThat(listener.buildCompleted.get(), "buildCompleted").isTrue();
		it.onSuccess();
	}

	@Test
	public void buildWithCacheFrom() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");

		Path tempFile = Files.createTempFile("", ".tar");
		String id = client.buildImage().
			export(Exporter.ociImage(tempFile.toString()).build()).
			build(buildContext);
		requireThat(id, "id").isNotNull();

		AtomicBoolean cacheWasUsed = new AtomicBoolean(false);
		client.buildImage().cacheFrom(id).listener(new DefaultBuildListener()
		{
			@Override
			public void onStderrLine(String line)
			{
				super.onStderrLine(line);
				if (line.endsWith("CACHED"))
					cacheWasUsed.set(true);
			}
		}).build(buildContext);
		requireThat(cacheWasUsed.get(), "cacheWasUsed").isTrue();
		Files.delete(tempFile);
		it.onSuccess();
	}

	@Test(expectedExceptions = FileNotFoundException.class)
	public void buildWithDockerfileOutsideOfContextPath() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");
		try
		{
			client.buildImage().dockerfile(buildContext.resolve("../custom/Dockerfile")).build(buildContext);
		}
		catch (FileNotFoundException e)
		{
			it.onSuccess();
			throw e;
		}
	}

	@Test
	public void buildAndOutputContentsToDirectory() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");

		Path tempDirectory = Files.createTempDirectory("");
		String id = client.buildImage().
			export(Exporter.contents(tempDirectory.toString()).directory().build()).
			build(buildContext);
		requireThat(id, "id").isNull();

		requireThat(tempDirectory, "tempDirectory").contains(tempDirectory.resolve(FILE_IN_CONTAINER));
		it.onSuccess();
		Paths.deleteRecursively(tempDirectory);
	}

	@Test
	public void buildAndOutputContentsToDirectoryMultiplePlatforms() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");

		Path tempDirectory = Files.createTempDirectory("");
		List<String> platforms = List.of("linux/amd64", "linux/arm64");
		ImageBuilder imageBuilder = client.buildImage().
			export(Exporter.contents(tempDirectory.toString()).directory().build());
		for (String platform : platforms)
			imageBuilder.platform(platform);
		String id = imageBuilder.build(buildContext);
		requireThat(id, "id").isNull();

		List<Path> platformDirectories = new ArrayList<>(platforms.size());
		for (String platform : platforms)
			platformDirectories.add(tempDirectory.resolve(platform.replace('/', '_')));
		requireThat(tempDirectory, "tempDirectory").containsAll(platformDirectories);
		it.onSuccess();
		Paths.deleteRecursively(tempDirectory);
	}

	@Test
	public void buildAndOutputContentsToTarFile() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");

		Path tempFile = Files.createTempFile("", ".tar");
		String id = client.buildImage().
			export(Exporter.contents(tempFile.toString()).build()).
			build(buildContext);
		requireThat(id, "id").isNull();

		Set<String> entries = getTarEntries(tempFile.toFile());
		requireThat(entries, "entries").containsExactly(Set.of(FILE_IN_CONTAINER));
		it.onSuccess();
		Files.delete(tempFile);
	}

	@Test
	public void buildAndOutputContentsToTarFileMultiplePlatforms() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");

		Path tempFile = Files.createTempFile("", ".tar");
		List<String> platforms = List.of("linux/amd64", "linux/arm64");
		ImageBuilder imageBuilder = client.buildImage().
			export(Exporter.contents(tempFile.toString()).build());
		for (String platform : platforms)
			imageBuilder.platform(platform);
		String id = imageBuilder.build(buildContext);
		requireThat(id, "id").isNull();

		Set<String> entries = getTarEntries(tempFile.toFile());
		requireThat(entries, "entries").containsExactly(getExpectedTarEntries(List.of(FILE_IN_CONTAINER),
			platforms));
		it.onSuccess();
		Files.delete(tempFile);
	}

	/**
	 * Returns the entries that a TAR file is expected to contain.
	 *
	 * @param files     the files that each image contains
	 * @param platforms the image platforms
	 * @return the file entries
	 */
	private List<String> getExpectedTarEntries(Collection<String> files, Collection<String> platforms)
	{
		int numberOfPlatforms = platforms.size();
		List<String> result = new ArrayList<>(numberOfPlatforms + files.size() * numberOfPlatforms);
		for (String platform : platforms)
		{
			String directory = platform.replace('/', '_') + "/";
			result.add(directory);
			for (String file : files)
				result.add(directory + file);
		}
		return result;
	}

	@Test
	public void buildAndOutputOciImageToDirectory() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");

		Path tempDirectory = Files.createTempDirectory("");
		String id = client.buildImage().
			export(Exporter.ociImage(tempDirectory.toString()).directory().build()).
			build(buildContext);
		requireThat(id, "id").isNotNull();
		List<ImageElement> images = client.listImages();
		requireThat(images, "images").isEmpty();
		requireThat(tempDirectory, "tempDirectory").isNotEmpty();
		it.onSuccess();
		Paths.deleteRecursively(tempDirectory);
	}

	@Test
	public void buildAndOutputOciImageToDirectoryUsingDockerContainerDriver() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String builder = client.createBuilder().driver(Driver.dockerContainer().build()).
			context(it.getName()).
			create();

		Path buildContext = Path.of("src/test/resources");

		Path tempDirectory = Files.createTempDirectory("");
		String id = client.buildImage().
			export(Exporter.ociImage(tempDirectory.toString()).directory().build()).
			builder(builder).
			build(buildContext);
		requireThat(id, "id").isNotNull();

		List<ImageElement> images = client.listImages();
		requireThat(images, "images").isNotEmpty();
		requireThat(tempDirectory, "tempDirectory").isNotEmpty();
		it.onSuccess();
		Paths.deleteRecursively(tempDirectory);
	}

	@Test
	public void buildAndOutputOciImageToDirectoryMultiplePlatforms() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");

		Path tempDirectory = Files.createTempDirectory("");
		List<String> platforms = List.of("linux/amd64", "linux/arm64");
		ImageBuilder imageBuilder = client.buildImage().
			export(Exporter.ociImage(tempDirectory.toString()).directory().build());
		for (String platform : platforms)
			imageBuilder.platform(platform);
		String id = imageBuilder.build(buildContext);
		requireThat(id, "id").isNotNull();
		List<ImageElement> images = client.listImages();
		requireThat(images, "images").isEmpty();
		requireThat(tempDirectory, "tempDirectory").isNotEmpty();
		it.onSuccess();
		Paths.deleteRecursively(tempDirectory);
	}

	@Test
	public void buildAndOutputDockerImageToTarFile() throws IOException, InterruptedException, TimeoutException
	{
		// REMINDER: Docker exporter is not capable of exporting multi-platform images to the local store.
		//
		// It outputs: "ERROR: docker exporter does not support exporting manifest lists, use the oci exporter
		// instead"
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");

		Path tempFile = Files.createTempFile("", ".tar");
		String id = client.buildImage().
			export(Exporter.dockerImage().path(tempFile.toString()).build()).
			build(buildContext);
		requireThat(id, "id").isNotNull();
		List<ImageElement> images = client.listImages();
		requireThat(images, "images").isEmpty();
		Set<String> entries = getTarEntries(tempFile.toFile());
		requireThat(entries, "entries").isNotEmpty();
		it.onSuccess();
		Paths.deleteRecursively(tempFile);
	}

	@Test
	public void buildAndOutputOciImageToTarFile() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");

		Path tempFile = Files.createTempFile("", ".tar");
		String id = client.buildImage().
			export(Exporter.ociImage(tempFile.toString()).build()).
			build(buildContext);
		requireThat(id, "id").isNotNull();
		List<ImageElement> images = client.listImages();
		requireThat(images, "images").isEmpty();

		Set<String> entries = getTarEntries(tempFile.toFile());
		requireThat(entries, "entries").isNotEmpty();
		it.onSuccess();
		Files.delete(tempFile);
	}

	@Test
	public void buildAndOutputOciImageToTarFileMultiplePlatforms() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");

		Path tempFile = Files.createTempFile("", ".tar");
		String id = client.buildImage().
			export(Exporter.ociImage(tempFile.toString()).build()).
			platform("linux/amd64").
			platform("linux/arm64").
			build(buildContext);
		requireThat(id, "id").isNotNull();
		List<ImageElement> images = client.listImages();
		requireThat(images, "images").isEmpty();

		Set<String> entries = getTarEntries(tempFile.toFile());
		requireThat(entries, "entries").isNotEmpty();
		it.onSuccess();
		Files.delete(tempFile);
	}

	@Test
	public void buildWithMultipleOutputs() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		Path buildContext = Path.of("src/test/resources");

		Path tempFile1 = Files.createTempFile("", ".tar");
		Path tempFile2 = Files.createTempFile("", ".tar");
		String id = client.buildImage().
			export(Exporter.dockerImage().path(tempFile1.toString()).build()).
			export(Exporter.ociImage(tempFile2.toString()).build()).
			build(buildContext);
		requireThat(id, "id").isNotNull();
		List<ImageElement> images = client.listImages();
		requireThat(images, "images").isEmpty();

		Set<String> entries1 = getTarEntries(tempFile1.toFile());
		requireThat(entries1, "entries1").isNotEmpty();

		Set<String> entries2 = getTarEntries(tempFile1.toFile());
		requireThat(entries2, "entries2").isNotEmpty();
		it.onSuccess();
		Files.delete(tempFile1);
		Files.delete(tempFile2);
	}

	/**
	 * Returns the entries of a TAR archive.
	 *
	 * @param tar the path of the TAR archive
	 * @return the archive entries
	 * @throws IOException if an error occurs while reading the file
	 */
	private Set<String> getTarEntries(File tar) throws IOException
	{
		Set<String> entries = new HashSet<>();
		try (FileInputStream is = new FileInputStream(tar);
		     TarArchiveInputStream archive = new TarArchiveInputStream(is))
		{
			while (true)
			{
				TarArchiveEntry entry = archive.getNextEntry();
				if (entry == null)
					break;
				entries.add(entry.getName());
			}
		}
		return entries;
	}
}