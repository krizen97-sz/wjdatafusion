package com.hm.manage.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Isolates document uploads from the application-wide multipart size limits.
 * User-specific file and storage limits remain enforced by the document service.
 */
@Configuration(proxyBeanMethods = false)
public class DocumentUploadServletConfiguration
{
    public static final String UPLOAD_PATH = "/document/workspace/documents/upload";

    @Bean
    public ServletRegistrationBean<DispatcherServlet> documentUploadServletRegistration(
        WebApplicationContext applicationContext, MultipartProperties multipartProperties)
    {
        DispatcherServlet servlet = new DispatcherServlet();
        // Share the existing MVC handlers and mark the context as externally owned.
        // Constructor injection would let this servlet close the root context on destroy.
        servlet.setApplicationContext(applicationContext);
        ServletRegistrationBean<DispatcherServlet> registration =
            new ServletRegistrationBean<>(servlet, UPLOAD_PATH);
        registration.setName("documentUploadServlet");
        // Exact mapping preserves the controller's complete request path. Other routes
        // keep the default DispatcherServlet and its configured multipart limits.
        registration.setMultipartConfig(new MultipartConfigElement(
            multipartProperties.getLocation(), -1, -1,
            Math.toIntExact(multipartProperties.getFileSizeThreshold().toBytes())));
        return registration;
    }
}
