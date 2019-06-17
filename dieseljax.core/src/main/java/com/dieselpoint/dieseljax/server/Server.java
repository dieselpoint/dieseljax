package com.dieselpoint.dieseljax.server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.TimeZone;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Slf4jRequestLog;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.jetty.JettyHttpContainerFactory;
import org.glassfish.jersey.message.GZipEncoder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.EncodingFilter;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.uri.UriComponent;
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

	public static class Builder {

		private String homeDir = "./";
		private String host;
		private int port = 8080;
		private boolean cors;
		private boolean gzip = true;
		private String staticFileDir;
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
		 * Serve static files from this directory. No default value.
		 * 
		 * @param staticFileDir path to files, can be relative to home dir or absolute
		 */
		public Builder staticFileDir(String staticFileDir) {
			this.staticFileDir = staticFileDir;
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
			server.logger = LoggerFactory.getLogger(this.getClass());

			if (standardExceptionMappers) {
				ExceptionMappers.addExceptionMappers(app);
			}

			if (cors) {
				// add CORS support
				app.register(new CorsFilter());
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

			/*-
			 * We use a full-blown WebAppContext instead of a lighter-weight http container
			 * for two reasons:
			 * 
			 * 1. You can't inject HttpServletRequest without a servlet container. See
			 * https://stackoverflow.com/questions/50591432/jersey-cant-inject-
			 * httpservletrequest-getting-hk2-errors for an alternative.
			 * 
			 * 2. The WebAppContext uses a DefaultServlet internally, which we use to handle
			 * serving of static resources.
			 * 
			 * Unresolved issue: haven't figured out how to configure DefaultServlet.
			 * See below.
			 */

			String path = String.format("/%s", UriComponent.decodePath(uri.getPath(), true).get(1).toString());
			WebAppContext context = new WebAppContext();
			context.setDisplayName("JettyContext");
			context.setContextPath(path);
			context.setConfigurations(new Configuration[] { new WebXmlConfiguration() });
			
			// add the jersey servlet
			ServletContainer servlet = new ServletContainer(app);
			ServletHolder holder = new ServletHolder(servlet);
			context.addServlet(holder, "/*");

			// add static file serving
			if (staticFileDir != null) {
				if (!(new File(staticFileDir).isAbsolute())) {
					staticFileDir = (new File(homeDir, staticFileDir)).getAbsolutePath();
				}
				context.setResourceBase(staticFileDir);
			}
			
			/*-
			 * Configure the DefaultServlet
			 * This does not work:
			 * context.setInitParameter("dirAllowed", "true");
			 * or this:
			 * context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "true");
			 * 
			 * Here are all the settings:
			 * http://www.eclipse.org/jetty/javadoc/9.4.12.v20180830/org/eclipse/jetty/servlet/DefaultServlet.html
			 * 
			 * This requires more research.
			 */

			server.jettyServer =  JettyHttpContainerFactory.createServer(uri, false);
			server.jettyServer.setHandler(context);

			setupRequestLog(server.jettyServer, requestLog);
			removeJettyServerHeader(server.jettyServer);

			server.jettyServer.setStopAtShutdown(true);
			return server;
		}


		private void setupObjectMapper(ResourceConfig app, ObjectMapper objectMapper) {
			// see
			// https://stackoverflow.com/questions/18872931/custom-objectmapper-with-jersey-2-2-and-jackson-2-1
			// answer by svenwltr

			if (objectMapper == null) {
				// disable all autodiscovery so it doesn't find the wrong implementation
				app.addProperties(Collections.singletonMap(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true));

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
		 * Obsolete, but leave it here for now.
		 * /
		private void setupStaticFiles(org.eclipse.jetty.server.Server jettyServer, String staticFileDir) {
			if (staticFileDir == null) {
				return;
			}

			if (!(new File(staticFileDir).isAbsolute())) {
				staticFileDir = (new File(homeDir, staticFileDir)).getAbsolutePath();
			}
			
			// this section adds a handler for static files
			Handler jaxHandler = jettyServer.getHandler();
			ResourceHandler resourceHandler = new ResourceHandler();
			resourceHandler.setDirectoriesListed(false);
			resourceHandler.setWelcomeFiles(new String[] { "index.html" });
			resourceHandler.setResourceBase(staticFileDir);
			resourceHandler.setRedirectWelcome(true); // avoid NPE, see
														// https://github.com/eclipse/jetty.project/issues/1856, which
														// really isn't fixed
			HandlerList handlers = new HandlerList();
			handlers.addHandler(resourceHandler); // resourceHandler must be first
			handlers.addHandler(jaxHandler);

			jettyServer.setHandler(handlers);
		}
		*/

		private void setupRequestLog(org.eclipse.jetty.server.Server jettyServer, RequestLog requestLog) {
			if (requestLog == null) {
				String[] ignorePaths = { "/images/*", "/img/*", "*.css", "*.jpg", "*.JPG", "*.gif", "*.GIF", "*.ico",
						"*.ICO", "*.js" };
				Slf4jRequestLog rl = new Slf4jRequestLog();
				rl.setExtended(true);
				rl.setLogTimeZone(TimeZone.getDefault().getID());
				rl.setLogLatency(true);
				rl.setIgnorePaths(ignorePaths);
				requestLog = rl;
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

		public class CorsFilter implements ContainerResponseFilter {
			@Override
			public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
					throws IOException {
				responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
				responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
				responseContext.getHeaders().add("Access-Control-Allow-Headers",
						"origin, content-type, accept, authorization, cache-control, x-requested-with");
				//responseContext.getHeaders().add("Access-Control-Allow-Headers", "*");  // does not work
				responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS, HEAD");
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

}
