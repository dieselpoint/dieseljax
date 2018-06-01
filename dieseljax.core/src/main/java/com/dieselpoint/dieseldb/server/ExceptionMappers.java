package com.dieselpoint.dieseldb.server;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.ValidationException;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionMappers {

	public static void addExceptionMappers(ResourceConfig app) {
		app.register(WebAppExceptionMapper.class);
		app.register(ValidationExceptionMapper.class);
		app.register(OtherExceptionMapper.class);
		app.register(NotAllowedExceptionMapper.class);
		app.register(NotAuthorizedExceptionMapper.class);
		app.register(NotFoundExceptionMapper.class);
	}

	public static class NotAuthorizedExceptionMapper implements ExceptionMapper<NotAuthorizedException> {

		Logger logger = LoggerFactory.getLogger(this.getClass());

		@Override
		public Response toResponse(NotAuthorizedException e) {
			StringBuilder sb = new StringBuilder();
			for (Object o: e.getChallenges()) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(o.toString());
			}
			String msgs = sb.toString();
			
			logger.error(e.getMessage() + " " + msgs);
			return Message.failureResponse(msgs, Status.UNAUTHORIZED);
		}
	}

	
	public static class WebAppExceptionMapper implements ExceptionMapper<WebApplicationException> {

		Logger logger = LoggerFactory.getLogger(this.getClass());

		@Override
		public Response toResponse(WebApplicationException e) {
			Status status = Status.fromStatusCode(e.getResponse().getStatus());
			logger.error(e.getMessage(), e);
			return Message.failureResponse(unwrapException(e), status);
		}
	}

	public static class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

		@Override
		public Response toResponse(NotFoundException e) {
			Status status = Status.fromStatusCode(e.getResponse().getStatus());
			return Message.failureResponse(e, status);
		}
	}
	
	
	public static class OtherExceptionMapper implements ExceptionMapper<Throwable> {

		Logger logger = LoggerFactory.getLogger(this.getClass());

		@Override
		public Response toResponse(Throwable t) {
			logger.error(t.toString(), t);
			return Message.failureResponse(t, Status.INTERNAL_SERVER_ERROR);
		}
	}

	public static class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {

		Logger logger = LoggerFactory.getLogger(this.getClass());

		@Override
		public Response toResponse(ValidationException exception) {
			if (exception instanceof ConstraintViolationException) {

				StringBuffer buf = new StringBuffer();
				ConstraintViolationException cve = (ConstraintViolationException) exception;
				for (ConstraintViolation cv : cve.getConstraintViolations()) {
					if (buf.length() > 0) {
						buf.append(", ");
					}
					buf.append(cv.getMessage());
				}
				return Message.failureResponse(buf.toString(), Status.BAD_REQUEST);

			} else {
				logger.error(exception.getMessage(), exception);
				return Message.failureResponse(unwrapException(exception), Status.INTERNAL_SERVER_ERROR);
			}
		}


	}

	public static class NotAllowedExceptionMapper implements ExceptionMapper<NotAllowedException> {
		@Override
		public Response toResponse(NotAllowedException e) {
			// gets called if you try to do a GET and there are no methods marked GET (for example)
			return Message.failureResponse("The method you used on this resource or path is not implemented: " + e.getMessage(),
					Status.METHOD_NOT_ALLOWED);
		}
	}

	
	private static Throwable unwrapException(Throwable t) {
		while (t.getCause() != null && t != t.getCause()) {
			t = t.getCause();
		}
		return t;
	}

	/*
	private static void doUnwrapException(StringBuffer sb, Throwable t) {
		if (t == null) {
			return;
		}
		sb.append(t.toString());
		if (t.getCause() != null && t != t.getCause()) {
			sb.append('[');
			doUnwrapException(sb, t.getCause());
			sb.append(']');
		}
	}
	*/
}
