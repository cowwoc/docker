package com.github.cowwoc.docker.internal.buildkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringJoiner;

/**
 * Git helper functions.
 */
public final class Git
{
	/**
	 * Returns the repository's full commit hash.
	 *
	 * @return the commit hash
	 * @throws IOException if an error occurs while interacting with git
	 */
	public String getCommitHash() throws IOException
	{
		// https://github.com/docker/buildx/blob/2eaea647d8aba61686f88ecbb5148ae91e3ad6da/util/gitutil/gitutil.go#L104
		Process process = new ProcessBuilder("git", "show", "--format=full", "HEAD", "--quiet", "--").
			redirectErrorStream(true).start();
		return getOutput(process);
	}

	/**
	 * Returns the alias of the remote repository (e.g. {@code origin}).
	 *
	 * @return an empty string if there is no remote branch
	 * @throws IOException if an error occurs while interacting with git
	 */
	public String getAliasOfRemoteRepository() throws IOException
	{
		// https://github.com/docker/buildx/blob/2eaea647d8aba61686f88ecbb5148ae91e3ad6da/util/gitutil/gitutil.go#L168
		Process process = new ProcessBuilder("git", "symbolic-ref", "-q", "HEAD").
			redirectErrorStream(true).start();
		// Returns a symbolic link to the branch that the working tree is on, relative to the .git directory
		String symbolicRef = getOutput(process);
		if (symbolicRef.isEmpty())
			return "";

		// Look up the alias of the remote repository that is associated with this symbolic link
		process = new ProcessBuilder("git", "for-each-ref", "--format=%(upstream:remotename)", symbolicRef).
			redirectErrorStream(true).start();
		return getOutput(process);
	}

	/**
	 * Returns the repository's remote URL.
	 *
	 * @return an empty string if the repository is not associated with a remote repository
	 * @throws IOException if an error occurs while interacting with git
	 */
	public String getRemoteUrl() throws IOException
	{
		// https://github.com/docker/buildx/blob/2eaea647d8aba61686f88ecbb5148ae91e3ad6da/util/gitutil/gitutil.go#L85
		Process process = new ProcessBuilder("git", "remote", "get-url", getAliasOfRemoteRepository()).
			redirectErrorStream(true).start();
		String output = getOutput(process);
		if (!output.isEmpty())
			return output;
		process = new ProcessBuilder("git", "remote", "get-url", "origin").
			redirectErrorStream(true).start();
		output = getOutput(process);
		if (!output.isEmpty())
			return output;
		process = new ProcessBuilder("git", "remote", "get-url", "upstream").
			redirectErrorStream(true).start();
		return getOutput(process);
	}

	/**
	 * Returns the text output of a started process.
	 *
	 * @param process the process
	 * @return the output
	 * @throws IOException if an error occurs reading {@code process.getInputStream()}
	 */
	private String getOutput(Process process) throws IOException
	{
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
		{
			StringJoiner lines = new StringJoiner("\n");
			while (true)
			{
				String line = reader.readLine();
				if (line == null)
					break;
				lines.add(line);
			}
			return lines.toString();
		}
	}
}