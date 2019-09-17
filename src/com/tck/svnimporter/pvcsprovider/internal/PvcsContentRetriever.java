package com.tck.svnimporter.pvcsprovider.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.polarion.svnimporter.common.IContentRetriever;
import org.polarion.svnimporter.common.Log;
import org.polarion.svnimporter.common.ZeroContentRetriever;
import com.tck.svnimporter.pvcsprovider.PvcsException;
import com.tck.svnimporter.pvcsprovider.PvcsProvider;
import com.tck.svnimporter.pvcsprovider.internal.model.PvcsRevision;

public class PvcsContentRetriever implements IContentRetriever {
	private static final Log LOG = Log.getLog(PvcsContentRetriever.class);
	private PvcsProvider provider;
	private PvcsRevision revision;

	public PvcsContentRetriever(PvcsProvider provider, PvcsRevision revision) {
		this.provider = provider;
		this.revision = revision;
	}

	public InputStream getContent() {
		try {
			File file = this.provider.checkout(this.revision);
			if (file == null) {
				LOG.warn("using zeroContent for \"" + this.revision.getPath() + "\" [" + this.revision.getNumber()
						+ "]");
				return this.zeroContent();
			} else {
				return new FileInputStream(file);
			}
		} catch (IOException var2) {
			throw new PvcsException(var2);
		}
	}

	private InputStream zeroContent() {
		return ZeroContentRetriever.getZeroContent();
	}
}