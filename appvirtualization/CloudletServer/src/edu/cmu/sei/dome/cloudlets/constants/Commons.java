package edu.cmu.sei.dome.cloudlets.constants;


public final class Commons {

	private Commons() {
	}

	public static final String STORE = "uploads/";
	public static final String LOG = "log/";
	public static OS MY_OS = OS.linux;
	public static boolean DEBUG = false;
	public static boolean CACHING_ENABLED = false;
	public static final String NAME = "CloudletServer";
	public static final double VERSION = 1.0;
	public static int PORT = 8080;
	public static final String CODENAME = "SINAI";

	public static CloudletProperties PROPERTIES;

	// JSON keys
	public static final String JSON_CHECKSUM = "checksum";
	public static final String JSON_NAME = "name";
	public static final String JSON_OS = "os";
	public static final String JSON_SIZE = "size";
	public static final String JSON_TYPE = "type";

	// exception messages
	public static final String UnsupportedFileTypeException = "This file type is not supported.";
	public static final String GeneralException = "An exception occurred.";
	public static final String ChecksumException = "The uploaded file does not match the submitted checksum.";
	public static final String FilesizeException = "The uploaded file has not the specified size.";
	public static final String PackageNotFoundException = "This application package could not be found.";

	public static String InvalidCloudletException() {
		return "This cloudlet (" + PROPERTIES
				+ ") does not match the application requirements.";
	}

}