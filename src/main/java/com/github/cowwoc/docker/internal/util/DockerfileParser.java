package com.github.cowwoc.docker.internal.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;

/**
 * Converts Dockerfile into a {@code Predicate<Path>} that indicates which paths to include in the build
 * context.
 */
public final class DockerfileParser extends GlobParser
{
	/**
	 * Creates a new instance.
	 */
	public DockerfileParser()
	{
	}

	/**
	 * @param dockerfile   the path of a {@code Dockerfile}
	 * @param buildContext the path of the build context
	 * @return a {@code Predicate<Path>} that returns {@code true} if a path should be included from the build
	 * 	context
	 * @throws NullPointerException if any of the arguments are null
	 * @throws IOException          if an error occurs while reading the file
	 */
	public Predicate<Path> parse(Path dockerfile, Path buildContext) throws IOException
	{
		// Evaluate Dockerfile relative to the build context
		assert that(dockerfile, "dockerfile").isRelative().elseThrow();
		assert that(buildContext, "buildContext").isAbsolute().elseThrow();

		Path directoryOfDockerfile = dockerfile.getParent();
		if (directoryOfDockerfile == null)
			directoryOfDockerfile = Path.of("");
		Set<PatternPredicate> patterns = new HashSet<>();
		patterns.add(new PatternPredicate("", buildContext, false, "it is the build context"));
		patterns.addAll(getPathsLeadingToDockerfile(dockerfile, directoryOfDockerfile, buildContext));


		List<String> lines = Files.readAllLines(buildContext.resolve(dockerfile));
		int lineNumber = 0;
		for (String line : lines)
		{
			++lineNumber;
			// https://docs.docker.com/build/concepts/dockerfile/#dockerfile-syntax
			if (line.startsWith("#"))
				continue;
			patterns.addAll(processLine(line, lineNumber, directoryOfDockerfile, buildContext));
		}
		return getPredicate(patterns);
	}

	/**
	 * Returns predicates for the Dockerfile and the directories leading to it.
	 *
	 * @param dockerfile            the path of the Dockerfile relative to the build context
	 * @param directoryOfDockerfile the directory of the Dockerfile
	 * @param buildContext          the build context
	 * @return a set of {@code PatternPredicate}s that include Dockerfile and the directories leading to it
	 * @throws NullPointerException if any of the arguments are null
	 */
	private Set<PatternPredicate> getPathsLeadingToDockerfile(Path dockerfile, Path directoryOfDockerfile,
		Path buildContext)
	{
		Set<PatternPredicate> patterns = new HashSet<>();

		// Include the Dockerfile and the directories leading to it
		patterns.add(new PatternPredicate(dockerfile, buildContext, false, "it is the Dockerfile"));

		Path path = directoryOfDockerfile;
		while (path != null)
		{
			patterns.add(new PatternPredicate(path, buildContext, false, "it contains the Dockerfile"));
			path = path.getParent();
		}
		return patterns;
	}

	/**
	 * Returns a predicate that returns {@code true} if a path should be included in the build context.
	 *
	 * @param patterns the predicate patterns to evaluate against the path
	 * @return {@code true} if the path should be included in the build context
	 */
	private Predicate<Path> getPredicate(Set<PatternPredicate> patterns)
	{
		return candidate ->
		{
			for (PatternPredicate pattern : patterns)
			{
				if (pattern.predicate().test(candidate))
					return true;
			}
			return false;
		};
	}

	/**
	 * Processes a single line.
	 *
	 * @param line                  the line
	 * @param lineNumber            the number of the line
	 * @param directoryOfDockerfile the directory that contains the Dockerfile
	 * @param buildContext          the build context
	 * @return the predicates generated by the line
	 * @throws NullPointerException     if any of the values are null
	 * @throws IllegalArgumentException if the Dockerfile references paths outside the build context
	 */
	private Set<PatternPredicate> processLine(String line, int lineNumber, Path directoryOfDockerfile,
		Path buildContext)
	{
		// Evaluate directoryOfDockerfile relative to the build context
		assert that(directoryOfDockerfile, "directoryOfDockerfile").isRelative().elseThrow();
		assert that(buildContext, "buildContext").isAbsolute().elseThrow();

		line = line.strip();
		if (line.isEmpty())
			return Set.of();

		String[] tokens = line.split("\\s+");
		return switch (tokens[0])
		{
			// Include the added or copied files or directories
			case "ADD", "COPY" ->
			{
				if (tokens.length < 2)
				{
					throw new IllegalArgumentException("dockerfile parse error on line " + lineNumber + ": " +
						tokens[0] + " requires at least two arguments, but only one was provided.");
				}
				// ADD/COPY paths evaluated relative to the build context
				// We need to convert it to an absolute path, normalize and relativize it because the argument may
				// contain an absolute path.
				Path sourceAsPath = buildContext.relativize(buildContext.resolve(tokens[1]).normalize());

				// Per https://github.com/docker/docs/issues/21827 if you specify a path outside the build context,
				// such as "ADD ../something /something" then the CLI will strip out the parent directories until the
				// resulting path is within the build context. The docker server rejects such paths.
				// We prefer the server's behavior.
				if (sourceAsPath.startsWith("../"))
				{
					throw new IllegalArgumentException(tokens[0] + " failed: forbidden path outside the build " +
						"context: " + sourceAsPath);
				}

				String source = sourceAsPath.toString();
				if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows"))
				{
					// Per https://pkg.go.dev/path/filepath#Match:
					// On Windows, escaping is disabled. Instead, '\\' is treated as path separator.
					source = source.replace('\\', '/');
				}
				StringBuilder glob = new StringBuilder(source);

				// Per: https://docs.docker.com/reference/dockerfile/#source
				// Leading and trailing slashes are ignored.
				if (glob.charAt(0) == '/')
					glob.deleteCharAt(0);
				if (glob.charAt(glob.length() - 1) == '/')
					glob.deleteCharAt(glob.length() - 1);

				StringBuilder regex = globToRegex(glob);
				yield convertRegexToPredicates(regex, false, "it was referenced by the Dockerfile's " +
					tokens[0] + " " + tokens[1] + " command", buildContext);
			}
			default -> Set.of();
		};
	}
}