package com.dieselpoint.dieseljax.server;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class Message {

	private boolean success;
	private String message;
	private int statusCode;
	private String reasonPhrase;

	public Message() {
	}
	
	public Message(boolean success, String message) {
		this.success = success;
		this.message = message;
	}
	
	public Message(boolean success, String message, int statusCode) {
		this.success = success;
		this.message = message;
		this.statusCode = statusCode;
	}

	public boolean getSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	public String getReasonPhrase() {
		return reasonPhrase;
	}

	public void setReasonPhrase(String reasonPhrase) {
		this.reasonPhrase = reasonPhrase;
	}
	
	public static Message success() {
		return new Message(true, null, Status.OK.getStatusCode());
	}

	public static Message success(String message) {
		return new Message(true, message, Status.OK.getStatusCode());
	}

	public static Message failure(int statusCode, String message) {
		Message msg = new Message();
		msg.success = false;
		msg.statusCode = statusCode;
		msg.reasonPhrase = Status.fromStatusCode(statusCode).getReasonPhrase();
		msg.message = message;
		return msg;
	}

	public static Message failure(String message) {
		return new Message(false, message);
	}
	
	public static Response okResponse(Object o) {
		 return Response.ok(o).build();
	}

	public static Response okResponse(String message) {
		 return Response.ok(Message.success(message)).build();
	}

	public static Response failureResponse(WebApplicationException e) {
		Message msg = new Message();
		msg.success = false;
		msg.message = e.getMessage();
		msg.statusCode = e.getResponse().getStatus();
		msg.reasonPhrase = e.getResponse().getStatusInfo().getReasonPhrase();
		return Response.status(e.getResponse().getStatusInfo()).entity(msg).type(MediaType.APPLICATION_JSON).build();
	}
	
	public static Response failureResponse(Throwable t, Status status) {
		Message msg = new Message();
		msg.success = false;
		msg.message = t.getMessage();
		msg.statusCode = status.getStatusCode();
		msg.reasonPhrase = status.getReasonPhrase();
		return Response.status(status).entity(msg).type(MediaType.APPLICATION_JSON).build();
	}
	
	public static Response failureResponse(String msgStr, Status status) {
		Message msg = new Message();
		msg.success = false;
		msg.message = msgStr;
		msg.statusCode = status.getStatusCode();
		msg.reasonPhrase = status.getReasonPhrase();
		return Response.status(status).entity(msg).type(MediaType.APPLICATION_JSON).build();
	}
	
	
}
