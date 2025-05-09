package com.github.cowwoc.docker.internal.util;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A class that generates a String representation of an object using a JSON-like format.
 */
public final class ToStringBuilder
{
	private final Class<?> aClass;
	private final List<Entry<String, String>> properties = new ArrayList<>();

	/**
	 * Creates a new builder.
	 *
	 * @param theClass the type of object being processed
	 * @throws NullPointerException if {@code theClass} is null
	 */
	public ToStringBuilder(Class<?> theClass)
	{
		requireThat(theClass, "theClass").isNotNull();
		this.aClass = theClass;
	}

	/**
	 * Creates a new builder without an object type.
	 */
	public ToStringBuilder()
	{
		this.aClass = null;
	}

	/**
	 * Adds a property.
	 *
	 * @param name  the name of the property
	 * @param value the value of the property
	 * @return this
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name} contains leading, trailing whitespace or is blank
	 */
	public ToStringBuilder add(String name, Object value)
	{
		if (value instanceof List<?> list)
			return add(name, list);
		if (value instanceof Number number)
			return add(name, number.longValue());
		requireThat(name, "name").isStripped().isNotEmpty();
		properties.add(new SimpleImmutableEntry<>(name, String.valueOf(value)));
		return this;
	}

	/**
	 * Adds a property.
	 *
	 * @param name   the name of the property
	 * @param number the value of the property
	 * @return this
	 * @throws IllegalArgumentException if any of the arguments contain trailing whitespace or are blank
	 */
	public ToStringBuilder add(String name, long number)
	{
		requireThat(name, "name").isStripped().isNotEmpty();
		properties.add(new SimpleImmutableEntry<>(name, Strings.format(number)));
		return this;
	}

	/**
	 * Adds a property.
	 *
	 * @param name the name of the property
	 * @param list the value of the property
	 * @return this
	 * @throws IllegalArgumentException if any of the arguments contain leading, trailing whitespace or are
	 *                                  blank.
	 */
	public ToStringBuilder add(String name, List<?> list)
	{
		requireThat(name, "name").isStripped().isNotEmpty();
		StringJoiner joiner = new StringJoiner(", ", "[", "]");
		for (Object anObject : list)
			joiner.add(anObject.toString());
		add(name, joiner);
		return this;
	}

	/**
	 * Adds a property.
	 *
	 * @param name the name of the property
	 * @param map  the value of the property
	 * @return this
	 * @throws IllegalArgumentException if any of the arguments contain leading, trailing whitespace or are
	 *                                  blank
	 */
	public ToStringBuilder add(String name, Map<?, ?> map)
	{
		requireThat(name, "name").isStripped().isNotEmpty();
		ToStringBuilder entries = new ToStringBuilder();
		for (Entry<?, ?> entry : map.entrySet())
			entries.add(entry.getKey().toString(), entry.getValue());
		add(name, entries);
		return this;
	}

	/**
	 * @param text      the {@code String} to align
	 * @param minLength the minimum length of {@code text}
	 * @return {@code text} padded on the right with spaces until its length is greater than or equal to
	 *  {@code minLength}
	 */
	private static String alignLeft(String text, int minLength)
	{
		int actualLength = text.length();
		if (actualLength > minLength)
			return text;
		return text + " ".repeat(minLength - actualLength);
	}

	@Override
	public String toString()
	{
		int maxKeyLength = 0;
		for (Entry<String, String> entry : properties)
		{
			String key = entry.getKey();
			if (key.isBlank())
				continue;
			int length = key.length();
			if (length > maxKeyLength)
				maxKeyLength = length;
		}

		StringJoiner output = new StringJoiner(",\n");
		StringBuilder line = new StringBuilder();
		for (Entry<String, String> entry : properties)
		{
			line.delete(0, line.length());
			String key = entry.getKey();
			if (!key.isBlank())
				line.append(alignLeft(key, maxKeyLength)).append(": ");
			line.append(entry.getValue());
			output.add(line.toString());
		}
		String name;
		if (aClass != null)
		{
			Class<?> currentClass = aClass;
			Deque<String> names = new ArrayDeque<>();
			while (currentClass != null)
			{
				names.add(currentClass.getSimpleName());
				currentClass = currentClass.getEnclosingClass();
			}
			StringJoiner joiner = new StringJoiner(".");
			for (String simpleName : names.reversed())
				joiner.add(simpleName);
			name = joiner.toString();
		}
		else
			name = "";
		if (output.length() == 0)
			return name + ": {}";
		return name + "\n" +
			"{\n" +
			"\t" + output.toString().replaceAll("\n", "\n\t") + "\n" +
			"}";
	}
}