package com.hm.manage.service.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.StringUtils;
import com.hm.manage.config.DocumentManagementProperties;
import com.hm.manage.domain.DocDocument;

@Component
public class OnlyOfficeDocumentEditorProvider implements DocumentEditorProvider
{
    private static final String PURPOSE_FILE = "DOCUMENT_FILE";
    private static final String PURPOSE_CALLBACK = "DOCUMENT_CALLBACK";

    @Autowired
    private DocumentManagementProperties properties;

    @Autowired
    private DocumentJwtService jwtService;

    @Override
    public String getProviderName()
    {
        return "ONLYOFFICE";
    }

    @Override
    public Map<String, Object> buildEditorBootstrap(DocDocument document, LoginUser loginUser, boolean editable)
    {
        DocumentManagementProperties.OnlyOffice config = requireReady();
        Duration fileLifetime = Duration.ofMinutes(config.getFileTokenMinutes());
        Duration callbackLifetime = Duration.ofHours(config.getCallbackTokenHours());

        Map<String, Object> fileClaims = Map.of(
            "purpose", PURPOSE_FILE,
            "documentId", document.getDocumentId(),
            "editorKey", document.getEditorKey());
        Map<String, Object> callbackClaims = Map.of(
            "purpose", PURPOSE_CALLBACK,
            "documentId", document.getDocumentId(),
            "editorKey", document.getEditorKey());
        String fileToken = jwtService.sign(fileClaims, fileLifetime);
        String callbackToken = jwtService.sign(callbackClaims, callbackLifetime);
        String baseUrl = stripTrailingSlash(config.getPlatformBaseUrl());

        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("edit", editable);
        permissions.put("review", false);
        permissions.put("comment", editable);
        permissions.put("download", false);
        permissions.put("print", false);
        permissions.put("copy", true);

        Map<String, Object> documentConfig = new LinkedHashMap<>();
        documentConfig.put("fileType", document.getFileType());
        documentConfig.put("key", document.getEditorKey());
        documentConfig.put("title", document.getTitle());
        documentConfig.put("url", baseUrl + "/document/workspace/editor/file/" + document.getDocumentId()
            + "?access_token=" + encode(fileToken));
        documentConfig.put("permissions", permissions);

        SysUser user = loginUser.getUser();
        String displayName = StringUtils.isBlank(user.getNickName()) ? user.getUserName() : user.getNickName();
        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("callbackUrl", baseUrl + "/document/workspace/editor/callback/" + document.getDocumentId()
            + "?access_token=" + encode(callbackToken));
        editorConfig.put("lang", StringUtils.defaultIfBlank(config.getLanguage(), "zh-CN"));
        editorConfig.put("region", StringUtils.defaultIfBlank(config.getRegion(), "zh-CN"));
        editorConfig.put("mode", editable ? "edit" : "view");
        editorConfig.put("user", Map.of("id", String.valueOf(loginUser.getUserId()), "name", displayName));
        editorConfig.put("coEditing", Map.of("mode", "fast", "change", true));
        editorConfig.put("customization", Map.of(
            "autosave", true,
            "forcesave", true,
            "compactHeader", false,
            "help", false,
            "about", false,
            "feedback", false));

        Map<String, Object> editor = new LinkedHashMap<>();
        editor.put("documentType", document.getDocumentType());
        editor.put("document", documentConfig);
        editor.put("editorConfig", editorConfig);
        editor.put("height", "100%");
        editor.put("width", "100%");
        editor.put("type", "desktop");
        editor.put("token", jwtService.sign(editor, callbackLifetime));

        Map<String, Object> bootstrap = new LinkedHashMap<>();
        bootstrap.put("provider", getProviderName());
        bootstrap.put("apiJsUrl", stripTrailingSlash(config.getServerUrl())
            + "/web-apps/apps/api/documents/api.js");
        bootstrap.put("permission", editable ? "EDIT" : "VIEW");
        bootstrap.put("config", editor);
        return bootstrap;
    }

    @Override
    public void verifyFileToken(String token, DocDocument document)
    {
        Map<String, Object> claims = jwtService.verify(token);
        requireClaim(claims, "purpose", PURPOSE_FILE);
        requireNumber(claims, "documentId", document.getDocumentId());
        requireClaim(claims, "editorKey", document.getEditorKey());
    }

    @Override
    public Map<String, Object> verifyCallback(String accessToken, String outboxToken, DocDocument document,
        Map<String, Object> payload)
    {
        Map<String, Object> accessClaims = jwtService.verify(accessToken);
        requireClaim(accessClaims, "purpose", PURPOSE_CALLBACK);
        requireNumber(accessClaims, "documentId", document.getDocumentId());
        requireClaim(accessClaims, "editorKey", document.getEditorKey());

        Object bodyToken = payload.get("token");
        String signedToken = bodyToken == null ? stripTokenPrefix(outboxToken) : String.valueOf(bodyToken);
        Map<String, Object> signedPayload = callbackPayload(jwtService.verify(signedToken));
        requireClaim(signedPayload, "key", document.getEditorKey());
        long status = jwtService.numberValue(signedPayload.get("status"));
        if (status < 1 || status > 7)
        {
            throw new ServiceException("编辑器回调状态无效");
        }
        requireSameOpenValue(payload, signedPayload, "key");
        requireSameOpenNumber(payload, signedPayload, "status");
        if (status == 2 || status == 6)
        {
            requireClaimPresent(signedPayload, "url");
            requireSameOpenValue(payload, signedPayload, "url");
        }
        return signedPayload;
    }

    @Override
    public void downloadCallbackFile(String sourceUrl, Path target) throws IOException, InterruptedException
    {
        DocumentManagementProperties.OnlyOffice config = requireReady();
        URI source;
        try
        {
            source = URI.create(sourceUrl);
        }
        catch (Exception exception)
        {
            throw new ServiceException("编辑器回调下载地址无效");
        }
        if (!("http".equalsIgnoreCase(source.getScheme()) || "https".equalsIgnoreCase(source.getScheme()))
            || StringUtils.isBlank(source.getHost()) || !trustedHosts(config).contains(source.getHost().toLowerCase(Locale.ROOT)))
        {
            throw new ServiceException("编辑器回调下载地址不在内网白名单中");
        }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.getConnectTimeoutSeconds()))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        HttpRequest request = HttpRequest.newBuilder(source)
            .timeout(Duration.ofSeconds(config.getReadTimeoutSeconds()))
            .header("User-Agent", "RuoYi-Document-Callback/1.0")
            .GET()
            .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            response.body().close();
            throw new ServiceException("编辑器回调文件下载失败，状态码：" + response.statusCode());
        }
        long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (declaredLength > properties.getMaxFileSize())
        {
            response.body().close();
            throw new ServiceException("编辑器回调文件超过大小限制");
        }
        try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(target))
        {
            byte[] buffer = new byte[16 * 1024];
            long total = 0L;
            int length;
            while ((length = input.read(buffer)) >= 0)
            {
                if (length == 0)
                {
                    continue;
                }
                total += length;
                if (total > properties.getMaxFileSize())
                {
                    throw new ServiceException("编辑器回调文件超过大小限制");
                }
                output.write(buffer, 0, length);
            }
        }
    }

    @Override
    public boolean forceSave(DocDocument document)
    {
        if (document == null)
        {
            throw new ServiceException("强制保存文档不能为空");
        }
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("c", "forcesave");
        command.put("key", document.getEditorKey());
        int error = sendCommand(document, command, "强制保存", "RuoYi-Document-AutoSave/1.0");
        if (error == 0)
        {
            return true;
        }
        if (error == 4)
        {
            return false;
        }
        throw new ServiceException("强制保存在线文档失败，编辑服务错误码：" + error);
    }

    @Override
    public void revokeEditingRights(DocDocument document, Collection<Long> userIds)
    {
        if (document == null || userIds == null || userIds.isEmpty())
        {
            return;
        }
        List<String> users = userIds.stream().filter(id -> id != null).map(String::valueOf).distinct().toList();
        if (users.isEmpty())
        {
            return;
        }
        sendDropCommand(document, users);
    }

    @Override
    public void revokeAllEditingRights(DocDocument document)
    {
        if (document != null)
        {
            sendDropCommand(document, null);
        }
    }

    private void sendDropCommand(DocDocument document, List<String> users)
    {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("c", "drop");
        command.put("key", document.getEditorKey());
        if (users != null)
        {
            command.put("users", users);
        }
        int error = sendCommand(document, command, "撤销会话", "RuoYi-Document-Permission/1.0");
        if (error != 0 && error != 1)
        {
            throw new ServiceException("撤销在线编辑会话失败，编辑服务错误码：" + error);
        }
    }

    private int sendCommand(DocDocument document, Map<String, Object> command, String action, String userAgent)
    {
        DocumentManagementProperties.OnlyOffice config = requireReady();
        Map<String, Object> requestBody = new LinkedHashMap<>(command);
        requestBody.put("token", jwtService.sign(command, Duration.ofMinutes(config.getFileTokenMinutes())));

        URI commandUri = URI.create(stripTrailingSlash(config.getServerUrl()) + "/command?shardkey="
            + encode(document.getEditorKey()));
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.getConnectTimeoutSeconds()))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        HttpRequest request = HttpRequest.newBuilder(commandUri)
            .timeout(Duration.ofSeconds(config.getReadTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent)
            .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(requestBody), StandardCharsets.UTF_8))
            .build();
        try
        {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300)
            {
                throw new ServiceException(action + "在线文档失败，状态码：" + response.statusCode());
            }
            JSONObject result = JSON.parseObject(response.body());
            int error = result == null ? -1 : result.getIntValue("error");
            return error;
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new ServiceException(action + "在线文档被中断");
        }
        catch (IOException exception)
        {
            throw new ServiceException("无法连接在线编辑服务" + action).setDetailMessage(exception.getMessage());
        }
    }

    private DocumentManagementProperties.OnlyOffice requireReady()
    {
        DocumentManagementProperties.OnlyOffice config = properties.getOnlyOffice();
        if (!config.isEnabled())
        {
            throw new ServiceException("在线编辑服务尚未启用");
        }
        if (StringUtils.isBlank(config.getServerUrl()) || StringUtils.isBlank(config.getPlatformBaseUrl()))
        {
            throw new ServiceException("ONLYOFFICE 内网地址配置不完整");
        }
        return config;
    }

    private List<String> trustedHosts(DocumentManagementProperties.OnlyOffice config)
    {
        List<String> hosts = new ArrayList<>();
        for (String host : config.getTrustedDownloadHosts())
        {
            if (StringUtils.isNotBlank(host))
            {
                hosts.add(host.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (hosts.isEmpty())
        {
            try
            {
                hosts.add(URI.create(config.getServerUrl()).getHost().toLowerCase(Locale.ROOT));
            }
            catch (Exception exception)
            {
                throw new ServiceException("ONLYOFFICE 服务地址配置无效");
            }
        }
        return hosts;
    }

    private void requireClaim(Map<String, Object> claims, String key, String expected)
    {
        if (!expected.equals(String.valueOf(claims.get(key))))
        {
            throw new ServiceException("编辑器签名用途不匹配");
        }
    }

    private void requireNumber(Map<String, Object> claims, String key, long expected)
    {
        if (jwtService.numberValue(claims.get(key)) != expected)
        {
            throw new ServiceException("编辑器签名文档版本不匹配");
        }
    }

    private Map<String, Object> callbackPayload(Map<String, Object> claims)
    {
        Object nested = claims.get("payload");
        if (nested instanceof Map<?, ?> nestedMap)
        {
            Map<String, Object> result = new LinkedHashMap<>();
            nestedMap.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>(claims);
        result.remove("iat");
        result.remove("exp");
        return result;
    }

    private void requireClaimPresent(Map<String, Object> claims, String key)
    {
        Object value = claims.get(key);
        if (value == null || StringUtils.isBlank(String.valueOf(value)))
        {
            throw new ServiceException("编辑器回调签名缺少" + key);
        }
    }

    private void requireSameOpenValue(Map<String, Object> opened, Map<String, Object> signed, String key)
    {
        Object openedValue = opened.get(key);
        if (openedValue != null && !String.valueOf(openedValue).equals(String.valueOf(signed.get(key))))
        {
            throw new ServiceException("编辑器回调内容与签名不一致");
        }
    }

    private void requireSameOpenNumber(Map<String, Object> opened, Map<String, Object> signed, String key)
    {
        Object openedValue = opened.get(key);
        if (openedValue != null && jwtService.numberValue(openedValue) != jwtService.numberValue(signed.get(key)))
        {
            throw new ServiceException("编辑器回调内容与签名不一致");
        }
    }

    private String stripTokenPrefix(String value)
    {
        String token = StringUtils.trimToEmpty(value);
        if (token.regionMatches(true, 0, "Bearer ", 0, 7))
        {
            return token.substring(7).trim();
        }
        if (token.regionMatches(true, 0, "JWT ", 0, 4))
        {
            return token.substring(4).trim();
        }
        return token;
    }

    private String stripTrailingSlash(String value)
    {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
