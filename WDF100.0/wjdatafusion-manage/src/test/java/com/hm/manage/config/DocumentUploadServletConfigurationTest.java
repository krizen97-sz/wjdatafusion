package com.hm.manage.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.servlet.autoconfigure.MultipartAutoConfiguration;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.hm.manage.controller.DocumentWorkspaceController;
import com.hm.manage.service.IDocumentWorkspaceService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentUploadServletConfigurationTest
{
    private static ServletWebServerApplicationContext context;
    private static HttpClient client;
    private static String origin;

    @BeforeAll
    static void startServer()
    {
        SpringApplication app = new SpringApplication(TestApplication.class);
        context = (ServletWebServerApplicationContext) app.run(
            "--server.address=127.0.0.1", "--server.port=0", "--spring.main.banner-mode=off",
            "--spring.servlet.multipart.max-file-size=1024B",
            "--spring.servlet.multipart.max-request-size=1536B");
        client = HttpClient.newHttpClient();
        origin = "http://127.0.0.1:" + context.getWebServer().getPort();
    }

    @AfterAll
    static void stopServer()
    {
        if (context != null)
        {
            context.close();
        }
    }

    @Test
    void documentUploadUsesExistingControllerAndAcceptsFileAboveDefaultLimit() throws Exception
    {
        HttpResponse<String> response = upload(DocumentUploadServletConfiguration.UPLOAD_PATH, "editor", 2048);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"bytes\":2048"), response.body());
        assertTrue(response.body().contains("\"folderId\":7"), response.body());
        assertNotSame(context.getBean("dispatcherServlet"), registration().getServlet());
    }

    @Test
    void otherUploadRoutesKeepTheirDefaultContainerLimit() throws Exception
    {
        assertEquals(200, upload("/test/common-upload", "editor", 128).statusCode());
        assertEquals(413, upload("/test/common-upload", "editor", 2048).statusCode());
    }

    @Test
    void documentUploadStillRequiresAuthenticationAndControllerPermission() throws Exception
    {
        assertEquals(401, upload(DocumentUploadServletConfiguration.UPLOAD_PATH, null, 2048).statusCode());
        assertEquals(403, upload(DocumentUploadServletConfiguration.UPLOAD_PATH, "viewer", 2048).statusCode());
    }

    @Test
    void destroyingDocumentServletDoesNotCloseSharedApplicationContext() throws Exception
    {
        // Initialize this servlet even if JUnit chooses this test before the upload test.
        assertEquals(200, upload(DocumentUploadServletConfiguration.UPLOAD_PATH, "editor", 128).statusCode());
        registration().getServlet().destroy();
        assertTrue(context.isActive());
        assertEquals(200, upload("/test/common-upload", "editor", 128).statusCode());
    }

    private static ServletRegistrationBean<?> registration()
    {
        return context.getBean("documentUploadServletRegistration", ServletRegistrationBean.class);
    }

    private static HttpResponse<String> upload(String path, String user, int fileSize) throws Exception
    {
        String boundary = "DocumentUploadTestBoundary";
        String body = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"folderId\"\r\n\r\n7\r\n"
            + "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"archive.zip\"\r\n"
            + "Content-Type: application/zip\r\n\r\n" + "x".repeat(fileSize)
            + "\r\n--" + boundary + "--\r\n";
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(origin + path))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (user != null)
        {
            String credentials = Base64.getEncoder().encodeToString((user + ":test").getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + credentials);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({DocumentUploadServletConfiguration.class, DocumentWorkspaceController.class, OtherUploadController.class})
    @ImportAutoConfiguration({TomcatServletWebServerAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class, MultipartAutoConfiguration.class,
        WebMvcAutoConfiguration.class, JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
    static class TestApplication
    {
        @Bean
        IDocumentWorkspaceService workspaceService()
        {
            IDocumentWorkspaceService service = mock(IDocumentWorkspaceService.class);
            when(service.uploadDocument(any(MultipartFile.class), eq(7L))).thenAnswer(invocation ->
                Map.of("bytes", ((MultipartFile) invocation.getArgument(0)).getSize(), "folderId", 7));
            return service;
        }

        @Bean
        InMemoryUserDetailsManager testUsers()
        {
            return new InMemoryUserDetailsManager(
                User.withUsername("editor").password("{noop}test").authorities("document:file:manage").build(),
                User.withUsername("viewer").password("{noop}test").authorities("test:read").build());
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception
        {
            return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .httpBasic(basic -> { }).build();
        }

        @Bean("ss")
        TestPermissions permissions()
        {
            return new TestPermissions();
        }
    }

    public static class TestPermissions
    {
        public boolean hasPermi(String permission)
        {
            return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
        }
    }

    @RestController
    static class OtherUploadController
    {
        @PostMapping("/test/common-upload")
        public String upload(@RequestParam("file") MultipartFile file)
        {
            return Long.toString(file.getSize());
        }
    }
}
