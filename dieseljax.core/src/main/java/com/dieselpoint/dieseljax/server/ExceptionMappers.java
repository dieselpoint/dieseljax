package com.dieselpoint.dieseljax.server;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.ValidationException;
import javax.ws.rs.BadRequestException;
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

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

public class ExceptionMappers {

	public static void addExceptionMappers(ResourceConfig app) {
		app.register(BadRequestExceptionMapper.class);
		app.register(WebAppExceptionMapper.class);
		app.register(ValidationExceptionMapper.class);
		app.register(NotAllowedExceptionMapper.class);
		app.register(NotAuthorizedExceptionMapper.class);
		app.register(NotFoundExceptionMapper.class);
		app.register(InvalidFormatExceptionMapper.class);
		app.register(OtherExceptionMapper.class);
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

	
	public static class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {

		Logger logger = LoggerFactory.getLogger(this.getClass());

		@Override
		public Response toResponse(BadRequestException e) {
			Status status = Status.fromStatusCode(e.getResponse().getStatus());
			logger.error(e.getMessage()); // log it, but not the whole stack trace
			return Message.failureResponse(unwrapException(e), status);
		}
	}
	
	
	public static class WebAppExceptionMapper implements ExceptionMapper<WebApplicationException> {

		Logger logger = LoggerFactory.getLogger(this.getClass());

		@Override
		public Response toResponse(WebApplicationException e) {
			Status status = Status.fromStatusCode(e.getResponse().getStatus());
			
			// don't log 404s
			if (status.getStatusCode() != 404) {
				logger.error(e.getMessage(), e);
			}
			
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

	/**
	 * Jackson exception when can't deserialize a field.
	 * @author ccleve
	 */
	public static class InvalidFormatExceptionMapper implements ExceptionMapper<InvalidFormatException> {

		@Override
		public Response toResponse(InvalidFormatException e) {
			// originalMessage() omits location information
			return Message.failureResponse(e.getOriginalMessage(), Status.BAD_REQUEST);
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
				for (ConstraintViolation<?> cv : cve.getConstraintViolations()) {
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

	
	public static Throwable unwrapException(Throwable t) {
		while (t.getCause() != null && t != t.getCause()) {
			t = t.getCause();
		}
		return t;
	}

}
