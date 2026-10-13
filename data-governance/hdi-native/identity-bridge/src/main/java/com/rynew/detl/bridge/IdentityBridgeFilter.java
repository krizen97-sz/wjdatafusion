package com.rynew.detl.bridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

/** One exact server-to-server exchange endpoint. Every other request keeps the original filter chain. */
final class IdentityBridgeFilter implements Filter {
    static final String SERVLET_PATH = "/_platform/session";
    private final TicketVerifier verifier;
    private final IdentityMappings mappings;
    private final NativeSessionIssuer issuer;
    IdentityBridgeFilter(TicketVerifier verifier, IdentityMappings mappings, NativeSessionIssuer issuer) {
        this.verifier = verifier; this.mappings = mappings; this.issuer = issuer;
    }
    @Override public void doFilter(ServletRequest rawRequest, ServletResponse rawResponse, FilterChain chain) throws IOException, ServletException {
        if (!(rawRequest instanceof HttpServletRequest) || !(rawResponse instanceof HttpServletResponse)) { chain.doFilter(rawRequest, rawResponse); return; }
        HttpServletRequest request = (HttpServletRequest)rawRequest; HttpServletResponse response = (HttpServletResponse)rawResponse;
        String expectedUri = request.getContextPath() + SERVLET_PATH;
        if (!SERVLET_PATH.equals(request.getServletPath())) { chain.doFilter(request, response); return; }
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Pragma", "no-cache"); response.setHeader("X-Content-Type-Options", "nosniff");
        response.setCharacterEncoding("UTF-8"); response.setContentType("application/json");
        try {
            if (!"POST".equals(request.getMethod()) || !expectedUri.equals(request.getRequestURI()) || request.getQueryString() != null
                || !Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr())
                || request.getHeader("Origin") != null || request.getHeader("Sec-Fetch-Site") != null
                || request.getHeader("Forwarded") != null || request.getHeader("X-Forwarded-For") != null
                || request.getHeader("Content-Encoding") != null || request.getContentLengthLong() > TicketVerifier.MAX_BODY_BYTES)
                throw TicketVerifier.denied();
            String type = request.getContentType();
            if (type == null || !type.toLowerCase(Locale.ROOT).matches("application/json(?:\\s*;\\s*charset=utf-8)?")) throw TicketVerifier.denied();
            byte[] body = request.getInputStream().readNBytes(TicketVerifier.MAX_BODY_BYTES + 1);
            String timestamp = singleHeader(request, "X-RYNEW-Timestamp"), nonce = singleHeader(request, "X-RYNEW-Nonce"), signature = singleHeader(request, "X-RYNEW-Signature");
            long expires = verifier.verify(request.getMethod(), expectedUri, body, timestamp, nonce, signature);
            IdentityMappings.Identity identity = mappings.resolve(body);
            verifier.consume(nonce, expires);
            issuer.establish(identity, request, response);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(IdentityMappings.JSON.writeValueAsString(Map.of("code", "0", "data", identity.response())));
        } catch (SecurityException denied) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); response.getWriter().write("{\"code\":\"BRIDGE_DENIED\",\"msg\":\"身份交换未获授权\"}");
        } catch (Exception unavailable) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); response.getWriter().write("{\"code\":\"BRIDGE_UNAVAILABLE\",\"msg\":\"身份交换暂不可用\"}");
        }
    }
    private static String singleHeader(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) throw TicketVerifier.denied();
        String value = values.nextElement(); if (values.hasMoreElements()) throw TicketVerifier.denied(); return value;
    }
}
