/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Global exception handler for DGFacade.
 *
 * <p>Catches all unhandled exceptions from both MVC controllers and REST endpoints
 * and renders a detailed, informative error page. Also handles Spring Boot's
 * default /error endpoint for 404s and other HTTP errors.</p>
 */
@ControllerAdvice
@Controller
public class GlobalExceptionHandler implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${dgfacade.app-name:DGFacade}")
    private String appName;

    @Value("${dgfacade.version:1.3.0}")
    private String version;

    /**
     * Handle all exceptions thrown by controllers.
     */
    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, HttpServletRequest request, Model model) {
        log.error("Unhandled exception at {} {}: {}", request.getMethod(),
                request.getRequestURI(), ex.getMessage(), ex);

        int statusCode = resolveStatusCode(ex);
        populateModel(model, statusCode, ex.getClass().getSimpleName(),
                ex.getMessage(), getStackTrace(ex), request);
        return "error";
    }

    /**
     * Handle Spring Boot's default /error route (404s, filter errors, etc.)
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Integer statusCode = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
        Throwable exception = (Throwable) request.getAttribute("jakarta.servlet.error.exception");
        String message = (String) request.getAttribute("jakarta.servlet.error.message");

        int code = statusCode != null ? statusCode : 500;
        String exType = exception != null ? exception.getClass().getSimpleName() : null;
        String msg = message != null && !message.isBlank() ? message :
                (exception != null ? exception.getMessage() : resolveDefaultMessage(code));
        String trace = exception != null ? getStackTrace(exception) : null;

        if (code >= 500 && exception != null) {
            log.error("Error {} at {}: {}", code, request.getAttribute("jakarta.servlet.error.request_uri"),
                    msg, exception);
        }

        populateModel(model, code, exType, msg, trace, request);
        return "error";
    }

    private void populateModel(Model model, int statusCode, String exceptionType,
                               String message, String stackTrace, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        // For /error endpoint, pull the original URI
        Object origUri = request.getAttribute("jakarta.servlet.error.request_uri");
        if (origUri != null) requestUri = origUri.toString();

        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        model.addAttribute("statusCode", statusCode);
        model.addAttribute("statusPhrase", resolvePhrase(statusCode));
        model.addAttribute("exceptionType", exceptionType);
        model.addAttribute("message", message != null ? message : "An unexpected error occurred");
        model.addAttribute("explanation", resolveExplanation(statusCode));
        model.addAttribute("suggestion", resolveSuggestion(statusCode));
        model.addAttribute("stackTrace", stackTrace);
        model.addAttribute("requestUri", requestUri);
        model.addAttribute("httpMethod", request.getMethod());
        model.addAttribute("queryString", request.getQueryString());
        model.addAttribute("timestamp",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
        model.addAttribute("serverInfo", System.getProperty("java.version") + " / " +
                request.getServerName() + ":" + request.getServerPort());
    }

    private int resolveStatusCode(Exception ex) {
        if (ex instanceof NoHandlerFoundException) return 404;
        if (ex instanceof IllegalArgumentException) return 400;
        if (ex instanceof SecurityException) return 403;
        return 500;
    }

    private String resolvePhrase(int code) {
        return switch (code) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 413 -> "Payload Too Large";
            case 415 -> "Unsupported Media Type";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> HttpStatus.valueOf(code).getReasonPhrase();
        };
    }

    private String resolveExplanation(int code) {
        return switch (code) {
            case 400 -> "The server could not understand the request due to invalid syntax or missing parameters.";
            case 401 -> "Authentication is required. Please log in with valid credentials.";
            case 403 -> "You do not have permission to access this resource. Admin role may be required.";
            case 404 -> "The requested page or resource could not be found. It may have been moved or deleted.";
            case 405 -> "The HTTP method used is not allowed for this endpoint.";
            case 500 -> "An unexpected error occurred on the server while processing your request.";
            case 502 -> "The gateway received an invalid response from an upstream server.";
            case 503 -> "The server is temporarily unavailable. It may be starting up or under heavy load.";
            default -> "An error occurred while processing your request.";
        };
    }

    private String resolveSuggestion(int code) {
        return switch (code) {
            case 400 -> "Check your request parameters and try again. Refer to the API documentation for correct formats.";
            case 401 -> "Log in at /login with your credentials. Default admin login: admin / admin123";
            case 403 -> "Contact your administrator to request access. This page requires the ADMIN role.";
            case 404 -> "Verify the URL is correct. Use the navigation menu to find the page you're looking for.";
            case 500 -> "This is a server-side issue. Check the stack trace below for details. If the problem persists, review the application logs.";
            case 503 -> "Wait a moment and try again. The system may be initializing.";
            default -> "Try again later. If the problem persists, contact your system administrator.";
        };
    }

    private String resolveDefaultMessage(int code) {
        return switch (code) {
            case 404 -> "The requested resource was not found";
            case 403 -> "Access denied";
            case 401 -> "Authentication required";
            default -> "An error occurred (HTTP " + code + ")";
        };
    }

    private String getStackTrace(Throwable ex) {
        if (ex == null) return null;
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
