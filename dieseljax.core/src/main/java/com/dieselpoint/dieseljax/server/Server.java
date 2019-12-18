package com.dieselpoint.dieseljax.server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collections;

import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.message.GZipEncoder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.EncodingFilter;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;

/**
 * This is a generic http / jax rs server suitable for use in lots of
 * applications.
 * 
 * @author ccleve
 */
public class Server {

	private Logger logger;
	private org.eclipse.jetty.server.Server jettyServer;
	private ResourceConfig app;

	public static class Builder {

		private String homeDir = "./";
		private String host;
		private int port = 8080;
		private boolean cors;
		private boolean gzip = true;
		private String staticFileDir;
		private String staticContextPath;
		private String serviceContextPath = "/";
		private RequestLog requestLog;
		private boolean standardExceptionMappers = true;
		private ObjectMapper objectMapper;
		private ResourceConfig app = new ResourceConfig();

		private Builder() {
		}

		/**
		 * Set the home directory. Paths to the /etc and other directories are relative
		 * to this dir. Default "./";
		 */
		public Builder homeDir(String homeDir) {
			this.homeDir = homeDir;
			return this;
		}

		/**
		 * Add CORS support. Default false.
		 */
		public Builder cors(boolean cors) {
			this.cors = cors;
			return this;
		}

		/**
		 * Add gzip support. Default true.
		 */
		public Builder gzip(boolean gzip) {
			this.gzip = gzip;
			return this;
		}

		/**
		 * Set the host name. Defaults to the local IP address.
		 */
		public Builder host(String host) {
			this.host = host;
			return this;
		}

		/**
		 * Set the port. Default 8088.
		 */
		public Builder port(int port) {
			this.port = port;
			return this;
		}

		/**
		 * Serve static files from this directory at this path. No default values.
		 * 
		 * @param staticFileDir     path to files, can be relative to home dir or
		 *                          absolute
		 * @param staticContextPath /context at which static files are served
		 */
		public Builder staticFiles(String staticFileDir, String staticContextPath) {
			this.staticFileDir = staticFileDir;
			this.staticContextPath = staticContextPath;
			return this;
		}

		/**
		 * Set the prefix for the path at which services will appear. For example,
		 * .serviceContextPath("/api") would mean that any service that had
		 * an @Path("/foo") would appear at http://host:port/api/foo.
		 * 
		 * @param serviceContextPath
		 * @return
		 */
		public Builder serviceContextPath(String serviceContextPath) {
			this.serviceContextPath = serviceContextPath;
			return this;
		}

		/**
		 * Set a different RequestLog implementation. To disable request logging
		 * altogether, turn it off in the Logback config file (or other logging config
		 * setup).
		 */
		public Builder requestLog(RequestLog requestLog) {
			this.requestLog = requestLog;
			return this;
		}

		/**
		 * Add standard exception mappers. Default true. Set false to disable, and then
		 * register custom mappers by calling .service() or .singleton().
		 */
		public Builder standardExceptionMappers(boolean standardExceptionMappers) {
			this.standardExceptionMappers = standardExceptionMappers;
			return this;
		}

		/**
		 * Customize how Jersey serializes and deserializes json.
		 */
		public Builder objectMapper(ObjectMapper objectMapper) {
			this.objectMapper = objectMapper;
			return this;
		}

		/**
		 * Add a JAX-RS rs service class.
		 */
		public Builder register(Class<?> service) {
			/*
			 * Because of type erasure, you can't just accumulate the list of services to
			 * register in a List and apply them later. Must do it this way.
			 */
			app.register(service);
			return this;
		}

		/**
		 * Add a JAX-RS service class as a singleton object. Note that this singleton
		 * must be thread-safe.
		 */
		public Builder register(Object service) {
			app.register(service);
			return this;
		}

		public Server build() {

			homeDir = new File(homeDir).getAbsolutePath();

			Server.initLogging(homeDir);

			Server server = new Server();
			server.app = app;
			server.logger = LoggerFactory.getLogger(this.getClass());

			if (standardExceptionMappers) {
				ExceptionMappers.addExceptionMappers(app);
			}

			if (cors) {
				// add CORS support
				CorsFilter cf = new CorsFilter();
				cf.getAllowedOrigins().add("*");
				cf.setCorsMaxAge(7200); // seconds, is the max value accepted by Chrome.
				app.register(cf);
			}

			if (gzip) {
				// enable gzip encoding
				// TODO test this
				EncodingFilter.enableFor(app, GZipEncoder.class);
			}

			setupObjectMapper(app, objectMapper);

			if (host == null) {
				try {
					host = InetAddress.getLocalHost().getHostAddress();
				} catch (UnknownHostException e) {
					throw new RuntimeException(e);
				}
			}

			URI uri = URI.create("http://" + host + ":" + port + "/");

			String msg = "Initializing server at " + uri.toString() + " in " + homeDir;
			System.out.println(msg);
			server.logger.info(msg);

			org.eclipse.jetty.server.Server jettyServer = new org.eclipse.jetty.server.Server(port);

			/*
			 * How to do all this:
			 * https://www.eclipse.org/jetty/documentation/current/embedding-jetty.html
			 */

			// change this to enable sessions or change security
			int options = ServletContextHandler.NO_SESSIONS | ServletContextHandler.NO_SECURITY
					| ServletContextHandler.GZIP;
			ServletContextHandler context = new ServletContextHandler(options);
			context.setContextPath("/");
			jettyServer.setHandler(context);

			// add the jersey servlet
			ServletContainer jerseyServlet = new ServletContainer(app);
			ServletHolder holder = new ServletHolder(jerseyServlet);
			context.addServlet(holder, serviceContextPath + "/*");

			// add static file serving
			if (staticFileDir != null) {
				if (!(new File(staticFileDir).isAbsolute())) {
					staticFileDir = getCanonicalPath(new File(homeDir, staticFileDir));
				}

				String path = this.staticContextPath;
				if (path == null) {
					path = "/";
				}

				// TODO this path doesn't seem to work unless it is "/"

				ServletHolder defaultServletHolder = context.addServlet(DefaultServlet.class, path);
				defaultServletHolder.setInitParameter("resourceBase", staticFileDir);
				defaultServletHolder.setInitParameter("dirAllowed", "false");
				// defaultServletHolder.setInitParameter("useFileMappedBuffer", "false");
			}

			setupRequestLog(jettyServer, requestLog);
			removeJettyServerHeader(jettyServer);
			jettyServer.setStopAtShutdown(true);

			server.jettyServer = jettyServer;

			return server;
		}

		private String getCanonicalPath(File file) {
			try {
				return file.getCanonicalPath();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		private void setupObjectMapper(ResourceConfig app, ObjectMapper objectMapper) {
			// see
			// https://stackoverflow.com/questions/18872931/custom-objectmapper-with-jersey-2-2-and-jackson-2-1
			// answer by svenwltr

			// disable all autodiscovery so it doesn't find the wrong implementation
			app.addProperties(Collections.singletonMap(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true));

			if (objectMapper == null) {
				objectMapper = new ObjectMapper();
				objectMapper.setSerializationInclusion(Include.NON_NULL); // also see NON_EMPTY
				// mapper.setSerializationInclusion(Include.NON_DEFAULT);
				objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			}

			JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();
			provider.setMapper(objectMapper);
			app.register(provider);
		}

		/**
		 * Obsolete, but leave it here for now. / private void
		 * setupStaticFiles(org.eclipse.jetty.server.Server jettyServer, String
		 * staticFileDir) { if (staticFileDir == null) { return; }
		 * 
		 * if (!(new File(staticFileDir).isAbsolute())) { staticFileDir = (new
		 * File(homeDir, staticFileDir)).getAbsolutePath(); }
		 * 
		 * // this section adds a handler for static files Handler jaxHandler =
		 * jettyServer.getHandler(); ResourceHandler resourceHandler = new
		 * ResourceHandler(); resourceHandler.setDirectoriesListed(false);
		 * resourceHandler.setWelcomeFiles(new String[] { "index.html" });
		 * resourceHandler.setResourceBase(staticFileDir);
		 * resourceHandler.setRedirectWelcome(true); // avoid NPE, see //
		 * https://github.com/eclipse/jetty.project/issues/1856, which // really isn't
		 * fixed HandlerList handlers = new HandlerList();
		 * handlers.addHandler(resourceHandler); // resourceHandler must be first
		 * handlers.addHandler(jaxHandler);
		 * 
		 * jettyServer.setHandler(handlers); }
		 */

		private void setupRequestLog(org.eclipse.jetty.server.Server jettyServer, RequestLog requestLog) {
			if (requestLog == null) {

				String[] ignorePaths = { "/images/*", "/img/*", "*.css", "*.jpg", "*.JPG", "*.gif", "*.GIF", "*.ico",
						"*.ICO", "*.js" };

				/*-
				 * This is the newer way of handling request logs. Slf4jRequestLog is deprecated
				 * in the newest version of Jetty. Unfortunately, the development of Jersey is 
				 * lagging and uses an older Jetty, so we have to use the old request logger.
				 * Current Jersey version is 2.28. Wait to see what 2.29 does.
				 */

				Slf4jRequestLogWriter writer = new Slf4jRequestLogWriter();
				CustomRequestLog crl = new CustomRequestLog(writer, CustomRequestLog.EXTENDED_NCSA_FORMAT);
				crl.setIgnorePaths(ignorePaths);
				requestLog = crl;

				/*-
				 *  older version of requestlog. keep it here for now.
				Slf4jRequestLog rl = new Slf4jRequestLog();
				rl.setExtended(true);
				rl.setLogTimeZone(TimeZone.getDefault().getID());
				rl.setLogLatency(true);
				rl.setIgnorePaths(ignorePaths);
				requestLog = rl;
				*/
			}
			jettyServer.setRequestLog(requestLog);
		}

		/**
		 * Remove header that announces that this is a jetty server. See:
		 * https://stackoverflow.com/questions/15652902/remove-the-http-server-header-in-jetty-9
		 */
		private void removeJettyServerHeader(org.eclipse.jetty.server.Server server) {
			for (Connector y : server.getConnectors()) {
				for (ConnectionFactory x : y.getConnectionFactories()) {
					if (x instanceof HttpConnectionFactory) {
						((HttpConnectionFactory) x).getHttpConfiguration().setSendServerVersion(false);
					}
				}
			}
		}

	}

	private Server() {
	}

	public static Builder builder() {
		return new Builder();
	}

	public void start() throws Exception {
		jettyServer.start();
		System.out.println("Started.");
	}

	/**
	 * Stops the server immediately. Can't be called while processing a request, so
	 * wrap it in a new thread if you need to do that.
	 */
	public void stopNow() throws Exception {
		System.out.println("Stopping server...");
		jettyServer.stop();
	}

	/**
	 * Call this if an slf4j logger must get used before the server class is built.
	 * Sets up logging properly.
	 */
	public static void initLogging(String homeDir) {

		// uncomment this to debug logback problems
		// System.setProperty("logback.statusListenerClass",
		// "ch.qos.logback.core.status.OnConsoleStatusListener");

		// must init the logger *after* system properties set up
		File logbackConfFile = new File(homeDir + "/etc/logback.xml");
		System.setProperty("logback.configurationFile", logbackConfFile.getAbsolutePath());

		// redirects java.util.logging (jul) to slf4j. Jersey uses jul.
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}

	public ResourceConfig getApp() {
		return app;
	}
}
