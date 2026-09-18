package com.hm.manage.config;

import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.Part;
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
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.hm.manage.controller.DocumentWorkspaceController;
import com.hm.manage.service.IDocumentWorkspaceService;
import com.hm.manage.domain.vo.DocWorkspaceSummaryVo;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.utils.SecurityUtils;
import com.hm.framework.web.service.PermissionService;

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
    private static final AtomicInteger multipartParses = new AtomicInteger();

    @BeforeAll
    static void startServer()
    {
        SpringApplication app = new SpringApplication(TestApplication.class);
        context = (ServletWebServerApplicationContext) app.run(
            "--server.address=127.0.0.1", "--server.port=0", "--spring.main.banner-mode=off",
            "--spring.security.filter.order=-50",
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
        int parsesBefore = multipartParses.get();
        assertEquals(401, upload(DocumentUploadServletConfiguration.UPLOAD_PATH, null, 2048).statusCode());
        assertEquals(403, upload(DocumentUploadServletConfiguration.UPLOAD_PATH, "viewer", 2048).statusCode());
        assertEquals(parsesBefore, multipartParses.get(), "Rejected users must not trigger multipart parsing");
    }

    @Test
    void fileLimitAndRemainingStorageRejectBeforeMultipartParsing() throws Exception
    {
        int parsesBefore = multipartParses.get();
        int oversized = (int) DocumentUploadAdmissionFilter.MULTIPART_OVERHEAD_BYTES + 2048;
        HttpResponse<String> limited = upload(DocumentUploadServletConfiguration.UPLOAD_PATH, "limited", oversized);
        assertEquals(413, limited.statusCode());
        assertTrue(limited.body().contains("单个文件上传上限"), limited.body());
        HttpResponse<String> full = upload(DocumentUploadServletConfiguration.UPLOAD_PATH, "full", oversized);
        assertEquals(413, full.statusCode());
        assertTrue(full.body().contains("剩余可用空间"), full.body());
        assertEquals(parsesBefore, multipartParses.get(), "Quota admission must run before multipart parsing");
    }

    @Test
    void unlimitedFilePolicyStillAdmitsWithinRemainingSpaceAndWildcardPermission() throws Exception
    {
        int fileSize = (int) DocumentUploadAdmissionFilter.MULTIPART_OVERHEAD_BYTES + 2048;
        HttpResponse<String> response = upload(DocumentUploadServletConfiguration.UPLOAD_PATH, "wildcard", fileSize);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"bytes\":" + fileSize), response.body());
    }

    @Test
    void unknownLengthUploadsAreRejectedBeforeParsing() throws Exception
    {
        int parsesBefore = multipartParses.get();
        HttpResponse<String> response = upload(DocumentUploadServletConfiguration.UPLOAD_PATH, "editor", 2048, true);
        assertEquals(411, response.statusCode(), response.body());
        assertTrue(response.body().contains("总长度"), response.body());
        assertEquals(parsesBefore, multipartParses.get());
    }

    @Test
    void hundredMegabyteUserRejectsOversizedHeadersWithoutReceivingFileBody() throws Exception
    {
        int parsesBefore = multipartParses.get();
        long contentLength = 100L * 1024 * 1024 + DocumentUploadAdmissionFilter.MULTIPART_OVERHEAD_BYTES + 1;
        try (Socket socket = new Socket("127.0.0.1", context.getWebServer().getPort()))
        {
            socket.setSoTimeout(5000);
            String headers = "POST " + DocumentUploadServletConfiguration.UPLOAD_PATH + " HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\nConnection: close\r\nExpect: 100-continue\r\n"
                + "Content-Type: multipart/form-data; boundary=HeadersOnlyBoundary\r\n"
                + "Content-Length: " + contentLength + "\r\nAuthorization: Basic "
                + Base64.getEncoder().encodeToString("hundred:test".getBytes(StandardCharsets.UTF_8)) + "\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            // Tomcat may send 100 immediately on headers; never send a file body.
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String status = reader.readLine();
            while (status != null && status.contains(" 100 "))
            {
                String header;
                while ((header = reader.readLine()) != null && !header.isEmpty()) { }
                status = reader.readLine();
            }
            assertTrue(status != null && status.contains(" 413 "), status);
        }
        assertEquals(parsesBefore, multipartParses.get());
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
        return upload(path, user, fileSize, false);
    }

    private static HttpResponse<String> upload(String path, String user, int fileSize, boolean chunked) throws Exception
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
            .POST(chunked ? HttpRequest.BodyPublishers.ofInputStream(
                () -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)))
                : HttpRequest.BodyPublishers.ofString(body));
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
            when(service.getWorkspaceSummary()).thenAnswer(invocation -> {
                DocWorkspaceSummaryVo summary = new DocWorkspaceSummaryVo();
                summary.setMaxUploadSize("limited".equals(SecurityUtils.getUsername()) ? 1024L : 0L);
                summary.setRemainingSize("full".equals(SecurityUtils.getUsername()) ? 1024L : 4L * 1024 * 1024);
                if ("hundred".equals(SecurityUtils.getUsername()))
                {
                    summary.setMaxUploadSize(100L * 1024 * 1024);
                    summary.setRemainingSize(200L * 1024 * 1024);
                }
                return summary;
            });
            return service;
        }

        @Bean
        UserDetailsService testUsers()
        {
            return username -> {
                SysUser user = new SysUser();
                user.setUserId(9L);
                user.setUserName(username);
                user.setPassword("{noop}test");
                Set<String> permissions = "viewer".equals(username) ? Set.of("test:read")
                    : "wildcard".equals(username) ? Set.of("*:*:*") : Set.of("document:file:manage");
                return new LoginUser(9L, 1L, user, permissions);
            };
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception
        {
            return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .httpBasic(basic -> { }).build();
        }

        @Bean("ss")
        PermissionService permissions()
        {
            return new PermissionService();
        }

        @Bean
        FilterRegistrationBean<jakarta.servlet.Filter> multipartParserProbe()
        {
            FilterRegistrationBean<jakarta.servlet.Filter> probe = new FilterRegistrationBean<>((request, response, chain) ->
                chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest) request) {
                    @Override
                    public Collection<Part> getParts() throws IOException, ServletException
                    {
                        multipartParses.incrementAndGet();
                        return super.getParts();
                    }
                }, response));
            probe.setOrder(Integer.MIN_VALUE);
            probe.addUrlPatterns("/*");
            return probe;
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
