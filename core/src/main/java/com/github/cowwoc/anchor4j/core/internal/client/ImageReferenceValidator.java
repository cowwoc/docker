package com.github.cowwoc.anchor4j.core.internal.client;

import java.util.regex.Pattern;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Validates an image reference.
 */
public final class ImageReferenceValidator
{
	// an alphanumeric, or an alphanumeric followed by a hyphen followed by another alphanumeric
	private static final Pattern DOMAIN_NAME_COMPONENT = Pattern.compile(
		"[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]");
	private static final Pattern IP_V6 = Pattern.compile("\\[[a-fA-F0-9:]+]");
	private static final String LOWERCASE_ALPHANUMERIC = "[a-z0-9]+";
	@SuppressWarnings("RegExpUnnecessaryNonCapturingGroup")
	private static final String PATH_SEPARATOR = "(?:[._]|__|-+)";
	private static final Pattern PATH_COMPONENT = Pattern.compile(LOWERCASE_ALPHANUMERIC +
		"(?:" + PATH_SEPARATOR + LOWERCASE_ALPHANUMERIC + ")*");
	private static final Pattern TAG = Pattern.compile("\\w[\\w.-]{0,127}");
	private static final Pattern DIGEST = Pattern.compile("[A-Za-z][A-Za-z0-9]*" +
		"(?:[-_+.][A-Za-z][A-Za-z0-9]*)*:\\p{XDigit}{32,}");
	private final String value;
	private final String name;

	/**
	 * Creates a new instance.
	 *
	 * @param value the value to validate
	 * @param name  the name of the parameter being validated
	 */
	private ImageReferenceValidator(String value, String name)
	{
		assert that(name, "name").isNotNull().elseThrow();

		this.value = value;
		this.name = name;
	}

	/**
	 * Validates the String representation of an image reference.
	 *
	 * @param value the value to validate
	 * @param name  the name of the parameter being validated
	 * @throws IllegalArgumentException if the value is not a valid reference
	 */
	@SuppressWarnings("PMD.ConfusingTernary")
	public static void validate(String value, String name)
	{
		// Based on https://github.com/distribution/reference/blob/727f80d42224f6696b8e1ad16b06aadf2c6b833b/regexp.go
		ImageReferenceValidator validator = new ImageReferenceValidator(value, name);
		int slash = value.indexOf('/');
		if (slash != -1)
			validator.validateHostAndPort(value.substring(0, slash));

		value = value.substring(slash + 1);
		int colon = value.indexOf(':');
		int atSymbol = value.indexOf('@');
		if (colon != -1 && atSymbol != -1)
		{
			throw new IllegalArgumentException(name + " may not contain both tag and digest components\n" +
				"Value: " + value);
		}
		String remoteName;
		if (colon != -1)
			remoteName = value.substring(0, colon);
		else if (atSymbol != -1)
			remoteName = value.substring(0, atSymbol);
		else
			remoteName = value;
		validator.validateRemoteName(remoteName);

		if (colon != -1)
			validator.validateTag(value.substring(colon + 1));
		else if (atSymbol != -1)
			validator.validateDigest(value.substring(atSymbol + 1));
	}

	private void validateHostAndPort(String hostAndPort)
	{
		// hostAndPort = HOST[:PORT]
		int colon = hostAndPort.indexOf(':');
		String host;
		if (colon == -1)
			host = hostAndPort;
		else
		{
			host = hostAndPort.substring(0, colon);
			try
			{
				Integer.parseInt(hostAndPort.substring(colon + 1));
			}
			catch (NumberFormatException e)
			{
				throw new IllegalArgumentException(name + " contains an invalid port number\n" +
					"Value: " + value, e);
			}
		}
		// host = `(?:` + domainName + `|` + ipv6address + `)`
		if (host.startsWith("["))
		{
			if (!IP_V6.matcher(host).matches())
			{
				throw new IllegalArgumentException(name + "'s host is not a valid IPv6 address\n" +
					"Value: " + value);
			}
		}
		else if (!validateDomainName(host))
		{
			throw new IllegalArgumentException(name + "'s host must consist of one or more components " +
				"separated by dots. Each component may only contain letters, digits, or hyphens, and must not start " +
				"or end with a hyphen.\n" +
				"Value: " + value);
		}
	}

	/**
	 * Validates a domain name or an IPv4 address.
	 *
	 * @param domainName the domain name
	 * @return {@code true} if the domain name was valid
	 */
	private boolean validateDomainName(String domainName)
	{
		// domainName = domainNameComponent + `(?:\.` + domainNameComponent + `)*`
		for (String domainNameComponent : domainName.split("\\."))
		{
			if (!DOMAIN_NAME_COMPONENT.matcher(domainNameComponent).matches())
				return false;
		}
		return true;
	}

	/**
	 * Validates the remote name of a repository.
	 *
	 * @param remoteName the value to validate
	 */
	private void validateRemoteName(String remoteName)
	{
		// [NAMESPACE/]REPOSITORY
		// remoteName = pathComponent[[/pathComponent] ...]
		for (String pathComponent : remoteName.split("/"))
		{
			if (!PATH_COMPONENT.matcher(pathComponent).matches())
			{
				throw new IllegalArgumentException(name + "'s path components must start with one or more " +
					"lowercase letters or digits, optionally followed by additional lowercase letters or digits " +
					"separated by '.', '_', '__', or one or more hyphens.\n" +
					"Value: " + value);
			}
		}
	}

	private void validateTag(String tag)
	{
		if (!TAG.matcher(tag).matches())
		{
			throw new IllegalArgumentException(name + "'s tag must start with an alphanumeric character or " +
				"underscore, followed by up to 127 characters that can be alphanumeric, underscore, dot (.), " +
				"or hyphen (-).\n" +
				"Value: " + value);
		}
	}

	private void validateDigest(String digest)
	{
		if (!DIGEST.matcher(digest).matches())
		{
			throw new IllegalArgumentException(name + "'s digest must start with a letter and may contain " +
				"alphanumeric segments separated by '-', '_', '+', or '.'. It must be followed by a colon (:) and " +
				"at least 32 hexadecimal characters (0–9, a–f, A–F).\n" +
				"Value: " + value);
		}
	}
}