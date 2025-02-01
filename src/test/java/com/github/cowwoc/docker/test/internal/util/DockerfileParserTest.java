package com.github.cowwoc.docker.test.internal.util;

import com.github.cowwoc.docker.internal.util.DockerfileParser;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class DockerfileParserTest
{
	@Test
	public void addDirectory() throws IOException
	{
		DockerfileParser parser = new DockerfileParser();

		Path buildContext = Files.createTempDirectory("docker-tests");
		Path dockerfile = buildContext.resolve("Dockerfile");
		Path libDirectory = buildContext.resolve("lib");
		Files.createDirectory(libDirectory);
		Path library1 = Files.createFile(libDirectory.resolve("library1.jar"));
		Path library2 = Files.createFile(libDirectory.resolve("library2.jar"));
		Path library3 = Files.createFile(libDirectory.resolve("library3.jar"));
		Files.writeString(dockerfile, """
			FROM ghcr.io/bell-sw/liberica-runtime-container:jre-musl
			
			ADD lib /app/lib
			""", UTF_8);

		library1 = buildContext.relativize(library1);
		library2 = buildContext.relativize(library2);
		library3 = buildContext.relativize(library3);
		dockerfile = buildContext.relativize(dockerfile);

		Predicate<Path> dockerfilePredicate = parser.parse(dockerfile, buildContext);

		Set<Path> filesLeft = new HashSet<>(Set.of(dockerfile, library1, library2, library3));
		Files.walkFileTree(buildContext, new SimpleFileVisitor<>()
		{
			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc)
			{
				if (dockerfilePredicate.test(dir))
					return FileVisitResult.CONTINUE;
				return FileVisitResult.SKIP_SUBTREE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
			{
				if (dockerfilePredicate.test(file))
				{
					Path relativeFile = buildContext.relativize(file);
					if (!filesLeft.remove(relativeFile))
						throw new IOException("Predicate included unwanted file: " + file);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		requireThat(filesLeft, "filesLeft").isEmpty();
		try (Stream<Path> walk = Files.walk(buildContext))
		{
			//noinspection ResultOfMethodCallIgnored
			walk.sorted(Comparator.reverseOrder()).
				forEach(path -> path.toFile().delete());
		}
	}
}
