package com.dieselpoint.dieseldb.server;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import com.dieselpoint.dieseljax.server.Server;

public class TestServer {

	public static void main(String[] args) throws Exception {
		Server server = Server.builder()
				.register(HelloService.class)
				.build();
		server.start();
	}

	@Path("/")
	public static class HelloService {
		
		@GET
		public String get() {
			return "Hello";
		}
	}
	
	
	
}
