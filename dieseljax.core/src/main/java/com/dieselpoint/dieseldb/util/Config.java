package com.dieselpoint.dieseldb.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Reads a properties file from disk, provides static methods to
 * get config values.
 * @author ccleve
 */
public class Config {

	private static Config conf = new Config();
	private Properties props = new Properties();

	public Config() {

		// use a development config file first, if it exists
		File file = new File("./etc/devconfig.txt");
		if (!file.exists()) {
			file = new File("./etc/config.txt");
			if (!file.exists()) {
				return;
			}
		}
		
		try {
			FileInputStream fis = new FileInputStream(file);
			props.load(fis);
			fis.close();
		} catch (IOException e) {
			throw new RuntimeException("Could not load config from " + file.getAbsolutePath());
		}
	}

	public static String getString(String key) {
		return getString(key, null);
	}

	public static String getString(String key, String defaultVal) {

		// allow command line props to override
		String sysprop = System.getProperty(key);
		if (sysprop != null) {
			return sysprop;
		}
		return conf.props.getProperty(key, defaultVal);
	}

	public static int getInt(String key, int defaultVal) {
		String str = getString(key);
		if (str == null) {
			return defaultVal;
		}
		return Integer.parseInt(str);
	}

	public static int getInt(String key) {
		return getInt(key, -1);
	}

	public static boolean getBoolean(String key, boolean defaultVal) {
		String val = getString(key);
		if (val == null) {
			return defaultVal;
		}
		String lowerVal = val.toLowerCase();
		if (lowerVal.startsWith("t") || lowerVal.startsWith("y")) {
			return true;
		}
		return false;
	}

}
