package com.github.cowwoc.anchor4j.core.internal.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.client.InternalClient;
import com.github.cowwoc.anchor4j.core.resource.Builder;
import com.github.cowwoc.anchor4j.core.resource.Builder.Status;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Parses server responses to {@code BuildService} commands.
 */
public final class BuildXParser extends AbstractParser
{
	// Known variants:
	// ERROR: open (.+?): Access is denied\.
	// ERROR: failed to read metadata: open (.+?): Access is denied\.
	private static final Pattern ACCESS_DENIED = Pattern.compile("ERROR: (?:.*?: )?open (.+?): " +
		"Access is denied\\.");
	public static final Pattern NOT_FOUND = Pattern.compile("ERROR: no builder (\"[^\"]+\") found");

	/**
	 * Creates a parser.
	 *
	 * @param client the client configuration
	 */
	public BuildXParser(InternalClient client)
	{
		super(client);
	}

	/**
	 * Returns the platforms that images can be built for.
	 *
	 * @param result the result of executing a command
	 * @return the platforms
	 */
	public Set<String> getSupportedBuildPlatforms(CommandResult result)
	{
		if (result.exitCode() != 0)
			throw result.unexpectedResponse();
		Set<String> platforms = new HashSet<>();
		for (String line : SPLIT_LINES.split(result.stdout()))
		{
			if (line.isBlank() || !line.startsWith("Platforms:"))
				continue;
			line = line.substring("Platforms:".length());
			for (String platform : line.split(","))
				platforms.add(platform.strip());
		}
		return platforms;
	}

	/**
	 * Looks up a builder by its name.
	 *
	 * @param result the result of executing a command
	 * @return the builder, or {@code null} if no match is found
	 * @throws IOException if an I/O error occurs. These errors are typically transient, and retrying the
	 *                     request may resolve the issue.
	 */
	public Builder get(CommandResult result) throws IOException
	{
		if (result.exitCode() != 0)
		{
			String stderr = result.stderr();
			Matcher matcher = ACCESS_DENIED.matcher(stderr);
			if (matcher.matches())
				throw new IOException("Access is denied: " + matcher.group(1));
			matcher = NOT_FOUND.matcher(stderr);
			if (matcher.matches())
				return null;
			throw result.unexpectedResponse();
		}
		String name = null;
		Builder.Status status = null;
		String error = "";
		for (String line : SPLIT_LINES.split(result.stdout()))
		{
			if (line.isBlank())
				continue;
			if (line.startsWith("Name:"))
				name = line.substring("Name:".length()).strip();
			if (line.startsWith("Status:"))
				status = SharedSecrets.getBuilderStatusFromString(line.substring("Status:".length()).strip());
			if (line.startsWith("Error:"))
				error = line.substring("Error:".length()).strip();
		}
		if (status == null && !error.isEmpty())
			status = Status.ERROR;
		assert that(status, "status").withContext(result.stdout(), "stdout").isNotNull().elseThrow();
		return SharedSecrets.getBuildXBuilder(client, name, status, error);
	}

	/**
	 * Creates a builder.
	 *
	 * @param result the result of executing a command
	 * @return the name of the builder
	 */
	public String create(CommandResult result)
	{
		if (result.exitCode() != 0)
			throw result.unexpectedResponse();
		return result.stdout();
	}
}