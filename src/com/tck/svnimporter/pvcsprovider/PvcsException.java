package com.tck.svnimporter.pvcsprovider;

public class PvcsException extends RuntimeException {
	public PvcsException() {
	}

	public PvcsException(String message) {
		super(message);
	}

	public PvcsException(String message, Throwable cause) {
		super(message, cause);
	}

	public PvcsException(Throwable cause) {
		super(cause);
	}
}