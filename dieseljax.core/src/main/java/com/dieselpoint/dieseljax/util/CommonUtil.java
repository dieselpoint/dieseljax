package com.dieselpoint.dieseljax.util;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * Miscellaneous static utility methods that don't fit elsewhere.
 * @author ccleve
 */
public class CommonUtil {

	public static final boolean IS_WINDOWS = osMatch("Windows");
	public static final boolean IS_MAC = osMatch("Mac");
	public static final boolean IS_UNIX = !IS_WINDOWS;

	private static final SecureRandom random = new SecureRandom();

	
	private static boolean osMatch(String prefix) {
		String os = System.getProperty("os.name");
		return os.startsWith(prefix); 
	}
	
	/**
	 * Returns the time in millis when the next midnight will occur. We use this
	 * to tell when the day changes.
	 * 
	 * @param timeStamp
	 *            calculate the first midnight that will occur after this
	 *            timestamp
	 */
	public static long getNextMidnight(long timeStamp) {
		GregorianCalendar calendar = new GregorianCalendar();
		calendar.setTimeInMillis(timeStamp);
		int year = calendar.get(Calendar.YEAR);
		int month = calendar.get(Calendar.MONTH);
		int day = calendar.get(Calendar.DAY_OF_MONTH);
		calendar.clear();
		calendar.set(year, month, day);
		calendar.add(Calendar.DATE, 1);
		return calendar.getTimeInMillis();
	}
	

	public static boolean isEmpty(String str) {
		return (str == null || str.trim().length() == 0);
	}

	
	
	// matches ${some_chars}
	private static final Pattern BRACES_PATTERN = Pattern
			.compile("\\$\\{(.+?)\\}");
	
	/**
	 * If the string includes parameters in the form "${some_parameter}", then
	 * look in the params map and substitute the parameters found there.
	 * 
	 * @param str
	 *            the string to process
	 * @return a processed string
	 */
	public static String insertParams(String str, Map<String, String> params) {
		while (true) {
			Matcher m = BRACES_PATTERN.matcher(str);
			if (m.find()) {
				String var = m.group(1);
				String prop = params.get(var);
				str = str.substring(0, m.start()) + prop
						+ str.substring(m.end(), str.length());
			} else {
				break;
			}
		}

		return str;
	}

	/**
	 * Clone an object. Does a shallow copy. Object must have zero-arg constructor.
	 */
	public static Object clone(Object from) {
		try {
			Object to = from.getClass().getDeclaredConstructor().newInstance();
			copyBean(from, to);
			return to;

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void copyBean(Object from, Object to) {
		copyBean(from, to, false);
	}
	
	public static void copyBean(Object from, Object to, boolean skipNulls) {
		// See:
		// http://www.leveluplunch.com/java/examples/convert-object-bean-properties-map-key-value/
		// http://stackoverflow.com/questions/1432764/any-tool-for-java-object-to-object-mapping
		/*
		 * Could speed this up a lot by creating a class-to-class mapping object and caching it
		 */
		
		try {

			BeanInfo fromInfo = Introspector.getBeanInfo(from.getClass());
			BeanInfo toInfo = Introspector.getBeanInfo(to.getClass());

			for (PropertyDescriptor readProp : fromInfo.getPropertyDescriptors()) {

				PropertyDescriptor writeProp = findDescriptor(readProp.getName(), toInfo);
				if (writeProp == null) {
					continue;
				}
				
				Method readMethod = readProp.getReadMethod();
				Method writeMethod = writeProp.getWriteMethod();
				if (readMethod != null && writeMethod != null) {
					Object value = readMethod.invoke(from);
					if (value == null && skipNulls) {
						continue;
					}
					writeMethod.invoke(to, value);
				}
			}
			
			Class<?> toClass = to.getClass();
			
			for (Field fromField: from.getClass().getFields()) {
				
				try {
					Field toField = toClass.getField(fromField.getName());

					Object value = fromField.get(from);
					if (value == null && skipNulls) {
						continue;
					}
					toField.set(to, value);
				} catch (NoSuchFieldException nsfe) {
					// just continue.
				}
			}
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static PropertyDescriptor findDescriptor(String name, BeanInfo info) {
	    for (PropertyDescriptor prop : info.getPropertyDescriptors()) {
	    	if (prop.getName().equals(name)) {
	    		return prop;
	    	}
	    }
		return null;
	}


	/**
	 * Return true if the source consists entirely of letters or digits. Handles
	 * non-Western characters correctly.
	 */ 
	public static boolean isAllLettersOrDigits(String source) {
		final int len = source.length();
		for (int i = 0; i < len; i++) {
			if (!Character.isLetterOrDigit(source.charAt(i))) {
				return false;
			}
		}
		return true;
	}
	

	/**
	 * Return randomized CharSequence consisting of lower case letters.
	 */
	public static CharSequence getRandomString(int numChars) {
		StringBuilder sb = new StringBuilder(numChars);
		for (int i = 0; i < numChars; i++) {
			char ch = (char)('a' + random.nextInt(26));
			sb.append(ch);
		}
		return sb;
	}
	
	/**
	 * Return randomized CharSequence consisting of lower case letters with a 
	 * supplied randomizer
	 */
	public static CharSequence getRandomString(Random random, int numChars) {
		StringBuilder sb = new StringBuilder(numChars);
		for (int i = 0; i < numChars; i++) {
			char ch = (char)('a' + random.nextInt(26));
			sb.append(ch);
		}
		return sb;
	}

	
	/**
	 * Return true if the container exception or any of its chained getCause() exceptions is
	 * of the specified target type.
	 * @param containerException containing exception
	 * @param targetExceptionClasses the type we're looking for
	 * @return true if target type found
	 */
	@SafeVarargs
	public static boolean containsException(Throwable containerException, Class<? extends Throwable>... targetExceptionClasses) {
		Throwable current = containerException;
		while (current != null) {
			for (int i = 0; i < targetExceptionClasses.length; i++) {
				if (current.getClass().equals(targetExceptionClasses[i])) {
					return true;
				}
			}
			current = current.getCause();
		}
		return false;
	}

	/*
	public static void regexValidate(String input, String regex, String badRequestMsg) {
		regexValidate(input, regex, 0, badRequestMsg);
	}
	
	public static void regexValidate(String input, String regex, int flags, String badRequestMsg) {
		if (input == null) {
			throw new BadRequestException(badRequestMsg);
		}
		Pattern p = Pattern.compile(regex, flags);
		if (!p.matcher(input).matches()) {
			throw new BadRequestException(badRequestMsg);
		}
	}
	*/
	
}
