package com.github.cowwoc.docker.internal.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Parses and converts <a href="https://en.wikipedia.org/wiki/Glob_(programming)">glob</a> patterns.
 */
public abstract class GlobParser
{
	/**
	 * Creates a new instance.
	 */
	protected GlobParser()
	{
	}

	/**
	 * The characters that have a special meaning in regular expressions, except for {@code ?}, {@code [},
	 * {@code ]}, {@code ^} and {@code \\} which are allowed per Go's <a
	 * href="https://pkg.go.dev/path/filepath#Match">Match()</a> function.
	 */
	protected static final Set<Character> SPECIAL_CHARACTERS = Set.of('.', '+', '|', '(', ')', '{', '}', '$',
		'\\');
	protected final Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * Converts a line from glob to regex format.
	 *
	 * @param glob the line in glob format
	 * @return the line in regex format
	 */
	protected StringBuilder globToRegex(StringBuilder glob)
	{
		// Escape any regex special characters that the glob treats as literals
		StringBuilder regex = new StringBuilder();
		for (int i = 0; i < glob.length(); ++i)
		{
			char ch = glob.charAt(i);
			if (SPECIAL_CHARACTERS.contains(ch))
				regex.append('\\');
			regex.append(ch);
		}

		// Convert **, *, ? sequences from https://pkg.go.dev/path/filepath#Match to regex
		boolean escapeNext = false;
		for (int i = 0; i < regex.length(); ++i)
		{
			if (escapeNext)
			{
				escapeNext = false;
				continue;
			}
			char ch = regex.charAt(i);
			switch (ch)
			{
				case '\\' -> escapeNext = true;
				case '*' ->
				{
					// Per https://docs.docker.com/build/concepts/context/#syntax:
					// Docker also supports a special wildcard string ** that matches any number of directories
					// (including zero).
					// BUG: https://github.com/docker/docs/issues/21807
					if (i < regex.length() - 1 && regex.charAt(i + 1) == '*')
					{
						// Convert **
						regex.replace(i, i + 1, ".*/");
					}
					else
					{
						// Convert *
						regex.replace(i, i + 1, "[^/]*/");
					}
				}
				case '?' -> regex.replace(i, i + 1, "[^/]");
				default ->
				{
				}
			}
		}
		return regex;
	}

	/**
	 * Converts a regular expression into one or more pattern predicates.
	 *
	 * @param regex        the regular expression
	 * @param excludes     {@code true} if the predicate excludes paths from the build context or {@code false}
	 *                     if it includes them
	 * @param why          an explanation of why the path is being included or excluded
	 * @param buildContext the build context
	 * @return the predicates generated by the regex
	 * @throws NullPointerException if any of the values are null
	 */
	protected Set<PatternPredicate> convertRegexToPredicates(StringBuilder regex, boolean excludes, String why,
		Path buildContext)
	{
		Set<PatternPredicate> predicates = new HashSet<>();

		boolean escapeNext = false;
		boolean insideCharacterClass = false;
		boolean negatedCharacterClass = false;
		boolean characterClassIsSeparator = false;
		for (int i = 0; i < regex.length(); ++i)
		{
			if (escapeNext)
			{
				escapeNext = false;
				continue;
			}
			char ch = regex.charAt(i);
			switch (ch)
			{
				case '\\' -> escapeNext = true;
				case '[' -> insideCharacterClass = true;
				case '^' ->
				{
					if (insideCharacterClass)
						negatedCharacterClass = true;
				}
				case ']' ->
				{
					insideCharacterClass = false;
					negatedCharacterClass = false;
					if (!excludes && characterClassIsSeparator)
					{
						// We need to include all its parent directories as well. Intentionally exclude a slash from the
						// end of the regex.
						String parentDirectory = regex.substring(0, i);
						predicates.add(new PatternPredicate(parentDirectory, buildContext, false, why));
						characterClassIsSeparator = false;
					}
				}
				case '/' ->
				{
					if (!excludes)
					{
						if (insideCharacterClass)
							characterClassIsSeparator = !negatedCharacterClass;
						else
						{
							// We need to include all its parent directories as well. Intentionally exclude a slash from the
							// end of the regex.
							String parentDirectory = regex.substring(0, i);
							predicates.add(new PatternPredicate(parentDirectory, buildContext, false, why));
						}
					}
				}
				default ->
				{
				}
			}
		}

		predicates.add(new PatternPredicate(regex.toString(), buildContext, excludes, why));
		return predicates;
	}

	/**
	 * A predicate that either includes or excludes paths in the build context.
	 */
	public final class PatternPredicate
	{
		private final String regex;
		private final Path buildContext;
		private final boolean excludes;
		private final String why;

		/**
		 * Creates a new instance.
		 *
		 * @param path         the path to match
		 * @param buildContext the build context
		 * @param excludes     {@code true} if the predicate excludes paths from the build context or
		 *                     {@code false} if it includes them
		 * @param why          indicates why the pattern includes or excludes
		 * @throws NullPointerException     if {@code predicate} or {@code why} are null
		 * @throws IllegalArgumentException if:
		 *                                  <ul>
		 *                                    <li>{@code regex} contains leading or trailing whitespace.</li>
		 *                                    <li>{@code why} contains leading or trailing whitespace or is empty.</li>
		 *                                  </ul>
		 */
		public PatternPredicate(Path path, Path buildContext, boolean excludes, String why)
		{
			this(toRegex(path), buildContext, excludes, why);
		}

		private static String toRegex(Path path)
		{
			String regex = path.toString().replace('\\', '/');
			if (regex.endsWith("/"))
				return regex.substring(0, regex.length() - 1);
			return regex;
		}

		/**
		 * Creates a new instance.
		 *
		 * @param regex        that regular expression that determines if a path matches the predicate
		 * @param buildContext the build context
		 * @param excludes     {@code true} if the predicate excludes paths from the build context or
		 *                     {@code false} if it includes them
		 * @param why          indicates why the pattern includes or excludes
		 * @throws NullPointerException     if {@code predicate} or {@code why} are null
		 * @throws IllegalArgumentException if:
		 *                                  <ul>
		 *                                    <li>{@code regex} contains leading or trailing whitespace.</li>
		 *                                    <li>{@code why} contains leading or trailing whitespace or is empty.</li>
		 *                                  </ul>
		 */
		public PatternPredicate(String regex, Path buildContext, boolean excludes, String why)
		{
			requireThat(regex, "regex").isStripped();
			requireThat(buildContext, "buildContext").isNotNull();
			requireThat(why, "why").isStripped().isNotEmpty();
			this.regex = regex;
			this.buildContext = buildContext;
			this.excludes = excludes;
			this.why = why;
		}

		/**
		 * @return a predicate
		 */
		public Predicate<Path> predicate()
		{
			return candidate ->
			{
				String candidateAsString = buildContext.relativize(candidate.toAbsolutePath().normalize()).toString().
					replace('\\', '/');
				boolean matches = candidateAsString.matches(regex);
				if (matches)
				{
					String operation;
					if (excludes)
						operation = "Excluding";
					else
						operation = "Including";
					log.debug("{} {} because {}, regex: {}", operation, candidateAsString, regex, why);
				}
				return matches;
			};
		}

		/**
		 * @return {@code true} if the predicate excludes paths from the build context or {@code false} if it
		 * 	includes them
		 */
		public boolean excludes()
		{
			return excludes;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof PatternPredicate other && other.regex.equals(regex);
		}

		@Override
		public int hashCode()
		{
			return regex.hashCode();
		}
	}
}