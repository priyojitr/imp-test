package com.tck.svnimporter.pvcsprovider.internal.model;

public class PvcsRevisionState {
	public static final PvcsRevisionState ADD = new PvcsRevisionState("add");
	public static final PvcsRevisionState CHANGE = new PvcsRevisionState("change");
	private final String name;

	private PvcsRevisionState(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}
}