package com.financetracker.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

public class SameOriginFilter implements Filter {
    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpRequest.setCharacterEncoding("UTF-8");
        httpResponse.setHeader("Cache-Control", "no-store");
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        String method = httpRequest.getMethod();
        if (!method.equals("GET") && !method.equals("HEAD")) {
            // JSON-only writes cannot be submitted by cross-site HTML forms.
            boolean logout = "/auth/logout".equals(httpRequest.getServletPath() + (httpRequest.getPathInfo() == null ? "" : httpRequest.getPathInfo()));
            String contentType = httpRequest.getContentType();
            if ((method.equals("POST") || method.equals("PUT")) && !logout
                    && (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json"))) {
                httpResponse.setStatus(415);
                httpResponse.setContentType("application/json;charset=UTF-8");
                httpResponse.getWriter().write("{\"error\":\"Use application/json for this request.\"}");
                return;
            }
            String origin = httpRequest.getHeader("Origin");
            String fetchSite = httpRequest.getHeader("Sec-Fetch-Site");
            boolean rejected = "cross-site".equals(fetchSite);
            if (origin != null) {
                try {
                    URI uri = URI.create(origin);
                    int originPort = uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
                    rejected |= !httpRequest.getScheme().equalsIgnoreCase(uri.getScheme())
                            || !httpRequest.getServerName().equalsIgnoreCase(uri.getHost())
                            || httpRequest.getServerPort() != originPort;
                } catch (IllegalArgumentException exception) { rejected = true; }
            }
            if (rejected) {
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.setContentType("application/json;charset=UTF-8");
                httpResponse.getWriter().write("{\"error\":\"Cross-site requests are not allowed.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
