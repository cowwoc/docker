package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A unit of work that executes on a node.
 */
public final class Task
{
	/**
	 * @param client the client configuration
	 * @param json   the JSON representation of the task
	 * @return the task
	 * @throws NullPointerException if any of the arguments are null
	 */
	static Task getByJson(DockerClient client, JsonNode json)
	{
		String id = json.get("ID").textValue();
		int version = client.getVersion(json);
		return new Task(id, version);
	}

	private final String id;
	private final int version;

	/**
	 * Creates a new reference to a task.
	 *
	 * @param id      the task's ID
	 * @param version the version number of the task. This is used to avoid conflicting writes.
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain leading or trailing whitespace.</li>
	 *                                    <li>any of the mandatory arguments are empty.</li>
	 *                                  </ul>
	 */
	public Task(String id, int version)
	{
		requireThat(id, "id").isStripped().isNotEmpty();
		this.id = id;
		this.version = version;
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Task other && other.id.equals(id);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Task.class).
			add("id", id).
			add("version", version).
			toString();
	}
}