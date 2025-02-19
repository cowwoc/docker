package com.github.cowwoc.docker.internal.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
	 * The file being parsed.
	 */
	private final Path dockerfile;
	/**
	 * The directory that contains the Dockerfile.
	 */
	private final Path directoryOfDockerfile;
	/**
	 * The build context.
	 */
	private final Path buildContext;
	/**
	 * {@code true} if the previous line ended with a backslash line continuation.
	 */
	private boolean lineEndedWithContinuation;
	/**
	 * The command being constructed by the parser.
	 * <p>
	 * For multi-line commands, this accumulates fragments until the entire command is complete and ready for
	 * processing.
	 */
	private final StringBuilder command = new StringBuilder();

	/**
	 * Creates a new Dockerfile parser.
	 *
	 * @param dockerfile   the path of a {@code Dockerfile}
	 * @param buildContext the path of the build context
	 * @throws NullPointerException if any of the arguments are null
	 */
	public DockerfileParser(Path dockerfile, Path buildContext)
	{
		// Evaluate Dockerfile relative to the build context
		assert that(dockerfile, "dockerfile").isRelative().elseThrow();
		assert that(buildContext, "buildContext").isAbsolute().elseThrow();
		this.dockerfile = dockerfile;

		Path directoryOfDockerfile = dockerfile.getParent();
		if (directoryOfDockerfile == null)
			directoryOfDockerfile = Path.of("");

		this.directoryOfDockerfile = directoryOfDockerfile;
		this.buildContext = buildContext;
	}

	/**
	 * @return a {@code Predicate<Path>} that returns {@code true} if a path should be included from the build
	 * 	context
	 * @throws IOException if an error occurs while reading the file
	 */
	public Predicate<Path> parse() throws IOException
	{
		patterns.add(new PatternPredicate("", buildContext, false, "it is the build context"));
		addPathsLeadingToDockerfile(dockerfile);

		int lineNumber = 0;
		try (BufferedReader reader = Files.newBufferedReader(buildContext.resolve(dockerfile)))
		{
			while (true)
			{
				++lineNumber;
				String line = reader.readLine();
				if (line == null)
					break;
				processLine(line, lineNumber);
			}
		}
		if (lineEndedWithContinuation)
			throw new IllegalArgumentException("Unexpected end of line: " + command);
		if (!command.isEmpty())
		{
			// Run the last command
			processCommand(lineNumber);
		}
		return getPredicate(patterns);
	}

	/**
	 * Processes a single line of a Dockerfile command.
	 *
	 * @param line       the line
	 * @param lineNumber the line number
	 * @throws NullPointerException if any of the arguments are null
	 */
	private void processLine(String line, int lineNumber)
	{
		// https://docs.docker.com/build/concepts/dockerfile/#dockerfile-syntax
		if (line.startsWith("#"))
			return;
		lineEndedWithContinuation = line.endsWith("\\");
		if (lineEndedWithContinuation)
		{
			command.append(line, 0, line.length() - 1);
			return;
		}

		String[] tokens = line.split("\\s+");
		// Commands that may span multiple lines without a backslash at the end of the line
		boolean commandMaySpanLines = switch (tokens[0])
		{
			case "LABEL", "ENV", "ARG", "VOLUME" -> true;
			default -> false;
		};
		if (commandMaySpanLines)
		{
			if (!command.isEmpty())
			{
				processCommand(lineNumber);
				command.delete(0, command.length());
			}
			command.append(line);
			return;
		}
		if (line.startsWith(" "))
		{
			if (command.isEmpty())
				throw new IllegalArgumentException("Invalid command on line " + line + ": " + line);
			// The command is spanning multiple lines
			command.append(line);
			return;
		}
		// We have detected the end of a command
		processCommand(lineNumber);
		command.delete(0, command.length());
		command.append(line);
	}

	/**
	 * Adds predicates for the Dockerfile and the directories leading to it.
	 *
	 * @param dockerfile the path of the Dockerfile relative to the build context
	 * @throws NullPointerException if {@code dockerfile} is null
	 */
	private void addPathsLeadingToDockerfile(Path dockerfile)
	{
		// Include the Dockerfile and the directories leading to it
		patterns.add(new PatternPredicate(dockerfile, buildContext, false, "it is the Dockerfile"));

		Path path = directoryOfDockerfile;
		while (path != null)
		{
			patterns.add(new PatternPredicate(path, buildContext, false, "it contains the Dockerfile"));
			path = path.getParent();
		}
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
	 * @param lineNumber the number of the line
	 * @throws NullPointerException     if any of the values are null
	 * @throws IllegalArgumentException if the Dockerfile references paths outside the build context
	 */
	private void processCommand(int lineNumber)
	{
		String command = this.command.toString().strip();
		if (command.isEmpty())
			return;

		String[] tokens = command.split("\\s+");
		switch (tokens[0])
		{
			case "ADD", "COPY" ->
			{
				int sourceIndex = getIndexOfArgument(tokens, 1);
				if (sourceIndex == -1)
				{
					throw new IllegalArgumentException("dockerfile parse error on line " + lineNumber + ": " +
						tokens[0] + " requires at least two arguments, but none were provided: " + command);
				}
				int targetIndex = getIndexOfArgument(tokens, sourceIndex + 1);
				if (targetIndex == -1)
				{
					throw new IllegalArgumentException("dockerfile parse error on line " + lineNumber + ": " +
						tokens[0] + " requires at least two arguments, but only one was provided: " + command);
				}
				// ADD/COPY paths evaluated relative to the build context
				// We need to convert it to an absolute path, normalize and relativize it because the argument may
				// contain an absolute path.
				Path sourceAsPath = buildContext.relativize(buildContext.resolve(tokens[sourceIndex]).normalize());

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
				patterns.addAll(convertRegexToPredicates(regex, false, "it was referenced by the Dockerfile's " +
					tokens[0] + " command on line " + lineNumber, buildContext));
			}
			default ->
			{
			}
		}
	}

	/**
	 * Returns the index of the first command argument (any value that does not start with {@code --}).
	 *
	 * @param tokens zero or more String values
	 * @param start  the index to start searching at
	 * @return -1 if no match is found
	 */
	private int getIndexOfArgument(String[] tokens, int start)
	{
		for (int i = start; i < tokens.length; ++i)
		{
			if (!tokens[i].startsWith("--"))
				return i;
		}
		return -1;
	}
}