package com.tck.svnimporter.pvcsprovider.internal.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.polarion.svnimporter.common.Log;
import org.polarion.svnimporter.common.model.Revision;

public class PvcsRevision extends Revision {
	private static final Log LOG = Log.getLog(PvcsRevision.class);
	private Map tags = new HashMap();
	private PvcsRevisionState state;

	public PvcsRevision(String number) {
		super(number);
	}

	public PvcsRevisionState getState() {
		return this.state;
	}

	public void setState(PvcsRevisionState state) {
		this.state = state;
	}

	public Collection getTags() {
		return this.tags.values();
	}

	public boolean addTag(PvcsTag tag) {
		if (this.tags.containsKey(tag.getName())) {
			LOG.error(this.getPath() + ": duplicate tag " + tag.getName());
			return false;
		} else {
			this.tags.put(tag.getName(), tag);
			return true;
		}
	}

	public String getDebugInfo() {
		String stateName = this.state == null ? null : this.state.getName();
		return super.getDebugInfo() + " s[" + stateName + "]";
	}
}