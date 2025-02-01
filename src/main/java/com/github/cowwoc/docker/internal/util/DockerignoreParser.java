package com.github.cowwoc.docker.internal.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;

/**
 * Converts .dockerignore into a {@code Predicate<Path>} that indicates which paths to exclude from the build
 * context.
 */
public final class DockerignoreParser extends GlobParser
{
	/**
	 * Creates a new instance.
	 */
	public DockerignoreParser()
	{
	}

	/**
	 * @param dockerignore the path of a {@code .dockerignore} file
	 * @param buildContext the path of the build context
	 * @return a {@code Predicate<Path>} that returns {@code true} if a path should be excluded from the build
	 * 	context
	 * @throws IOException if an error occurs while reading the file
	 */
	public Predicate<Path> parse(Path dockerignore, Path buildContext) throws IOException
	{
		// Evaluate .dockerignore relative to the build context
		assert that(dockerignore, "dockerignore").isRelative().elseThrow();
		assert that(buildContext, "buildContext").isAbsolute().elseThrow();

		List<String> lines = Files.readAllLines(buildContext.resolve(dockerignore));
		for (String line : lines)
		{
			// Per https://docs.docker.com/build/concepts/context/#syntax:
			// If a line in .dockerignore file starts with # in column 1, then this line is considered as a comment
			// and is ignored.
			if (line.startsWith("#"))
				continue;
			processLine(line, dockerignore, buildContext);
		}
		return getPredicate(patterns);
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
			boolean includeInBuildContext = true;
			for (PatternPredicate pattern : patterns)
			{
				if (pattern.predicate().test(candidate))
					includeInBuildContext = !pattern.excludes();
			}
			return includeInBuildContext;
		};
	}

	/**
	 * Processes a single line.
	 *
	 * @param line         the line
	 * @param dockerignore the path of the {@code .dockerignore} file
	 * @param buildContext the build context
	 * @throws NullPointerException if any of the values are null
	 */
	private void processLine(String line, Path dockerignore, Path buildContext)
	{
		// Evaluate dockerignore relative to the build context
		assert that(dockerignore, "dockerignore").isRelative().elseThrow();
		assert that(buildContext, "buildContext").isAbsolute().elseThrow();

		// Per https://docs.docker.com/build/concepts/context/#syntax:
		// A preprocessing step uses Go's filepath.Clean function to trim whitespace and remove . and ..
		line = line.strip();
		if (line.isEmpty())
			return;
		line = Path.of(line).normalize().toString();
		// Per https://docs.docker.com/build/concepts/context/#syntax:
		// For historical reasons, the pattern . is ignored.
		if (line.isEmpty())
			return;

		if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows"))
		{
			// Per https://pkg.go.dev/path/filepath#Match:
			// On Windows, escaping is disabled. Instead, '\\' is treated as path separator.
			line = line.replace('\\', '/');
		}

		// Negation
		StringBuilder glob = new StringBuilder(line);
		boolean negation = line.charAt(0) == '!';
		if (negation)
			glob.deleteCharAt(0);

		// Per https://docs.docker.com/build/concepts/context/#syntax:
		// Leading and trailing slashes are ignored.
		if (glob.charAt(0) == '/')
			glob.deleteCharAt(0);
		if (glob.charAt(glob.length() - 1) == '/')
			glob.deleteCharAt(glob.length() - 1);

		StringBuilder regex = globToRegex(glob);
		patterns.addAll(convertRegexToPredicates(regex, !negation, "it was referenced by " + dockerignore,
			buildContext));
	}
}