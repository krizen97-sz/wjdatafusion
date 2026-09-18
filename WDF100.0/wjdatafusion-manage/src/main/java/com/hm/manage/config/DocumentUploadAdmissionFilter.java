package com.hm.manage.config;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.alibaba.fastjson2.JSON;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.SecurityUtils;
import com.hm.manage.domain.vo.DocWorkspaceSummaryVo;
import com.hm.manage.service.IDocumentWorkspaceService;

/** Checks headers, permissions and quota before the servlet writes multipart data to disk. */
final class DocumentUploadAdmissionFilter extends OncePerRequestFilter
{
    // The upload has one file, a folder id and short multipart headers. Bound the
    // framing allowance so restricted users cannot stage arbitrarily large bodies.
    static final long MULTIPART_OVERHEAD_BYTES = 1024 * 1024;

    private final ObjectProvider<IDocumentWorkspaceService> workspaceService;

    DocumentUploadAdmissionFilter(ObjectProvider<IDocumentWorkspaceService> workspaceService)
    {
        this.workspaceService = workspaceService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException
    {
        if (!StringUtils.startsWithIgnoreCase(request.getContentType(), MediaType.MULTIPART_FORM_DATA_VALUE))
        {
            reject(response, 415, "文档上传只支持 multipart/form-data 请求");
            return;
        }
        if (!"POST".equals(request.getMethod()))
        {
            reject(response, 405, "文档上传只支持 POST 请求");
            return;
        }

        LoginUser loginUser;
        try
        {
            loginUser = SecurityUtils.getLoginUser();
        }
        catch (ServiceException exception)
        {
            reject(response, 401, "请先登录后上传文件");
            return;
        }
        if (loginUser == null || loginUser.getUserId() == null)
        {
            reject(response, 401, "请先登录后上传文件");
            return;
        }
        if (loginUser.getPermissions() == null
            || !SecurityUtils.hasPermi(loginUser.getPermissions(), "document:file:manage"))
        {
            reject(response, 403, "没有文档管理权限，无法上传文件");
            return;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength < 0)
        {
            reject(response, 411, "上传请求必须提供文件总长度，请使用文档管理页面重新上传");
            return;
        }

        DocWorkspaceSummaryVo summary;
        try
        {
            summary = workspaceService.getObject().getWorkspaceSummary();
        }
        catch (RuntimeException exception)
        {
            logger.warn("Document upload quota could not be checked", exception);
            reject(response, 503, "暂时无法核验上传额度，请稍后重试");
            return;
        }
        if (summary == null || summary.getRemainingSize() == null || summary.getMaxUploadSize() == null
            || summary.getMaxUploadSize() < 0)
        {
            reject(response, 503, "上传额度配置不可用，请联系管理员");
            return;
        }
        long remaining = Math.max(0, summary.getRemainingSize());
        long singleFileLimit = summary.getMaxUploadSize();
        long admittedFileBytes = singleFileLimit > 0 ? Math.min(remaining, singleFileLimit) : remaining;
        if (admittedFileBytes == 0 || contentLength - MULTIPART_OVERHEAD_BYTES > admittedFileBytes)
        {
            reject(response, 413, singleFileLimit > 0 && singleFileLimit < remaining
                ? "文件超过当前账号的单个文件上传上限，请联系管理员调整"
                : "文件超过当前账号的剩余可用空间，请清理文件或联系管理员调整");
            return;
        }
        // The service performs exact file-size checks and atomic quota checks after
        // parsing; this coarse header check only bounds pre-validation disk usage.
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException
    {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(AjaxResult.error(status, message)));
    }
}
