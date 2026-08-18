package com.hm.manage.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.hm.common.constant.CacheConstants;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.core.redis.RedisCache;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.common.utils.DateUtils;
import com.hm.manage.domain.DocAcl;
import com.hm.manage.domain.DocDocument;
import com.hm.manage.domain.DocFolder;
import com.hm.manage.domain.DocOperationLog;
import com.hm.manage.domain.DocUserQuota;
import com.hm.manage.domain.DocVersion;
import com.hm.manage.domain.bo.DocAclSaveBo;
import com.hm.manage.domain.bo.DocCreateBo;
import com.hm.manage.domain.bo.DocFolderReorderBo;
import com.hm.manage.domain.bo.DocFolderSaveBo;
import com.hm.manage.domain.bo.DocQuotaUpdateBo;
import com.hm.manage.domain.bo.DocUpdateBo;
import com.hm.manage.domain.vo.DocUserStorageVo;
import com.hm.manage.domain.vo.DocUserVo;
import com.hm.manage.domain.vo.DocWorkspaceSummaryVo;
import com.hm.manage.config.DocumentManagementProperties;
import com.hm.manage.mapper.DocumentWorkspaceMapper;
import com.hm.manage.service.IDocumentWorkspaceService;
import com.hm.manage.service.document.DocFileResource;
import com.hm.manage.service.document.DocumentEditorProvider;
import com.hm.manage.service.document.DocumentStorageService;
import com.hm.system.service.ISysUserService;

@Service
public class DocumentWorkspaceServiceImpl implements IDocumentWorkspaceService
{
    private static final Logger log = LoggerFactory.getLogger(DocumentWorkspaceServiceImpl.class);
    private static final Set<String> SCOPES = Set.of("MY", "SHARED", "RECENT", "ARCHIVED", "TRASH", "ADMIN_ALL");
    private static final Set<String> FILE_TYPES = Set.of("doc", "docx", "xls", "xlsx", "pdf", "zip", "rar");
    private static final Set<String> CREATABLE_FILE_TYPES = Set.of("docx", "xlsx");
    private static final Set<String> ARCHIVE_FILE_TYPES = Set.of("zip", "rar");
    private static final Set<String> LIFECYCLE_STATUSES = Set.of("ACTIVE", "ARCHIVED", "TRASH");
    private static final Set<String> ACL_PERMISSIONS = Set.of("VIEW", "EDIT");
    private static final Set<String> FOLDER_COLORS = Set.of(
        "#4F7CCF", "#2F8F6B", "#A06A2B", "#8A63B8", "#C45D6A", "#5C718A");
    private static final String DEFAULT_FOLDER_COLOR = "#4F7CCF";
    private static final int MAX_FOLDER_DEPTH = 32;
    private static final int MAX_SIBLING_FOLDERS = 500;
    private static final int MAX_COLLABORATORS = 100;
    private static final int EXPIRY_BATCH_SIZE = 100;
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;
    private static final long DEFAULT_QUOTA_BYTES = 100L * BYTES_PER_MEGABYTE;
    private static final long DEFAULT_MAX_UPLOAD_BYTES = 100L * BYTES_PER_MEGABYTE;
    private static final long HARD_MAX_UPLOAD_BYTES = 100L * BYTES_PER_MEGABYTE;
    private static final long MAX_CONFIGURABLE_QUOTA_MB = 102400L;
    private static final Pattern COPY_SUFFIX_PATTERN = Pattern.compile("\\(附件[一二三四五六七八九十百]+\\)$");
    private static final String[] CHINESE_DIGITS = { "零", "一", "二", "三", "四", "五", "六", "七", "八", "九" };

    @Autowired
    private DocumentWorkspaceMapper mapper;

    @Autowired
    private DocumentStorageService storageService;

    @Autowired
    private DocumentManagementProperties documentProperties;

    @Autowired
    private DocumentEditorProvider editorProvider;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ISysUserService sysUserService;

    @Override
    public List<DocFolder> listFolders()
    {
        List<DocFolder> folders = mapper.selectFoldersByOwner(SecurityUtils.getUserId());
        Map<Long, DocFolder> byId = new LinkedHashMap<>();
        Map<Long, Long> directCounts = new LinkedHashMap<>();
        Map<Long, Long> directSizes = new LinkedHashMap<>();
        for (DocFolder folder : folders)
        {
            byId.put(folder.getFolderId(), folder);
            directCounts.put(folder.getFolderId(), valueOrZero(folder.getDocumentCount()));
            directSizes.put(folder.getFolderId(), valueOrZero(folder.getTotalSize()));
            folder.setDocumentCount(valueOrZero(folder.getDocumentCount()));
            folder.setTotalSize(valueOrZero(folder.getTotalSize()));
        }
        for (DocFolder folder : folders)
        {
            long documentCount = directCounts.get(folder.getFolderId());
            long totalSize = directSizes.get(folder.getFolderId());
            Long parentId = folder.getParentId();
            Set<Long> visited = new HashSet<>();
            visited.add(folder.getFolderId());
            for (int depth = 0; parentId != null && parentId > 0 && depth < MAX_FOLDER_DEPTH; depth++)
            {
                if (!visited.add(parentId))
                {
                    break;
                }
                DocFolder parent = byId.get(parentId);
                if (parent == null)
                {
                    break;
                }
                parent.setDocumentCount(valueOrZero(parent.getDocumentCount()) + documentCount);
                parent.setTotalSize(valueOrZero(parent.getTotalSize()) + totalSize);
                parentId = parent.getParentId();
            }
        }
        return folders;
    }

    @Override
    public DocWorkspaceSummaryVo getWorkspaceSummary()
    {
        DocWorkspaceSummaryVo summary = mapper.selectWorkspaceSummary(SecurityUtils.getUserId());
        if (summary == null)
        {
            summary = new DocWorkspaceSummaryVo();
        }
        summary.setFileCount(valueOrZero(summary.getFileCount()));
        summary.setTotalSize(valueOrZero(summary.getTotalSize()));
        summary.setUnfiledCount(valueOrZero(summary.getUnfiledCount()));
        DocUserQuota quota = currentQuota(SecurityUtils.getUserId());
        long usedSize = valueOrZero(mapper.selectOwnedStorageBytes(SecurityUtils.getUserId()));
        summary.setUsedSize(usedSize);
        summary.setQuotaSize(quota.getQuotaBytes());
        summary.setRemainingSize(Math.max(0L, quota.getQuotaBytes() - usedSize));
        summary.setMaxUploadSize(quota.getMaxUploadBytes());
        summary.setUsagePercent(usagePercent(usedSize, quota.getQuotaBytes()));
        summary.setDocumentAdmin(isDocumentAdmin());
        return summary;
    }

    @Override
    public List<DocUserStorageVo> listDocumentStorageUsers()
    {
        requireDocumentAdmin();
        List<DocUserStorageVo> users = mapper.selectDocumentStorageUsers();
        users.forEach(this::normalizeStorageUser);
        return users;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocUserStorageVo updateDocumentStoragePolicy(Long userId, DocQuotaUpdateBo input)
    {
        requireDocumentAdmin();
        if (userId == null || userId <= 0L || mapper.countDocumentWorkspaceAccess(userId) <= 0)
        {
            throw new ServiceException("用户不存在或未获得文档管理权限");
        }
        if (mapper.countDocumentAdminRole(userId) > 0)
        {
            throw new ServiceException("admin 总权限账号不适用普通用户容量配置");
        }
        if (input == null || input.getQuotaMb() == null || input.getMaxUploadMb() == null)
        {
            throw new ServiceException("可用空间和单文件上传上限不能为空");
        }
        long quotaMb = input.getQuotaMb();
        long maxUploadMb = input.getMaxUploadMb();
        if (quotaMb < 1L || quotaMb > MAX_CONFIGURABLE_QUOTA_MB)
        {
            throw new ServiceException("可用空间必须在1MB到102400MB之间");
        }
        if (maxUploadMb < 1L || maxUploadMb > 100L)
        {
            throw new ServiceException("单个文件上传上限必须在1MB到100MB之间");
        }
        long quotaBytes = quotaMb * BYTES_PER_MEGABYTE;
        long maxUploadBytes = maxUploadMb * BYTES_PER_MEGABYTE;
        mapper.insertDefaultQuota(userId, SecurityUtils.getUsername());
        if (mapper.selectQuotaForUpdate(userId) == null)
        {
            throw new ServiceException("用户容量记录初始化失败");
        }
        long usedSize = valueOrZero(mapper.selectOwnedStorageBytes(userId));
        if (quotaBytes < usedSize)
        {
            throw new ServiceException("可用空间不能低于该用户当前已使用的" + readableMegabytes(usedSize) + "MB");
        }
        if (mapper.updateQuota(userId, quotaBytes, maxUploadBytes, SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("用户容量配置更新失败");
        }
        DocUserStorageVo result = mapper.selectDocumentStorageUsers().stream()
            .filter(item -> userId.equals(item.getUserId())).findFirst().orElseGet(DocUserStorageVo::new);
        result.setUserId(userId);
        result.setUsedSize(usedSize);
        result.setQuotaSize(quotaBytes);
        result.setMaxUploadSize(maxUploadBytes);
        normalizeStorageUser(result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocFolder createFolder(DocFolderSaveBo input)
    {
        Long ownerId = SecurityUtils.getUserId();
        DocFolder folder = normalizeFolder(input, ownerId, null, null);
        folder.setCreateBy(SecurityUtils.getUsername());
        mapper.insertFolder(folder);
        return folder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFolder(Long folderId, DocFolderSaveBo input)
    {
        Long ownerId = SecurityUtils.getUserId();
        DocFolder current = requireOwnedFolder(folderId, ownerId);
        DocFolder folder = normalizeFolder(input, ownerId, folderId, current);
        if (folder.getParentId().equals(folderId))
        {
            throw new ServiceException("目录不能移动到自身");
        }
        requireNoFolderCycle(folderId, folder.getParentId(), ownerId);
        folder.setFolderId(folderId);
        folder.setUpdateBy(SecurityUtils.getUsername());
        if (mapper.updateFolder(folder) != 1)
        {
            throw new ServiceException("目录更新失败");
        }
        logOperation(null, "FOLDER_UPDATE", ownerId, SecurityUtils.getUsername(),
            current.getFolderName() + " -> " + folder.getFolderName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderFolders(DocFolderReorderBo input)
    {
        if (input == null || input.getFolderIds() == null || input.getFolderIds().isEmpty())
        {
            throw new ServiceException("目录排序参数不能为空");
        }
        Long parentId = input.getParentId() == null ? 0L : input.getParentId();
        if (parentId < 0)
        {
            throw new ServiceException("上级目录无效");
        }
        if (input.getFolderIds().size() > MAX_SIBLING_FOLDERS)
        {
            throw new ServiceException("单级目录数量不能超过" + MAX_SIBLING_FOLDERS + "个");
        }

        Long ownerId = SecurityUtils.getUserId();
        if (parentId > 0)
        {
            requireOwnedFolder(parentId, ownerId);
        }
        LinkedHashSet<Long> requestedIds = new LinkedHashSet<>();
        for (Long folderId : input.getFolderIds())
        {
            if (folderId == null || folderId <= 0 || !requestedIds.add(folderId))
            {
                throw new ServiceException("目录排序列表包含无效或重复目录");
            }
        }

        List<DocFolder> siblings = mapper.selectFoldersByOwnerAndParentForUpdate(ownerId, parentId);
        LinkedHashSet<Long> currentIds = new LinkedHashSet<>();
        for (DocFolder sibling : siblings)
        {
            currentIds.add(sibling.getFolderId());
        }
        if (requestedIds.size() != currentIds.size() || !requestedIds.containsAll(currentIds))
        {
            throw new ServiceException("目录结构已发生变化，请刷新后重试");
        }
        if (new ArrayList<>(requestedIds).equals(new ArrayList<>(currentIds)))
        {
            return;
        }

        int sortOrder = 10;
        String operator = SecurityUtils.getUsername();
        for (Long folderId : requestedIds)
        {
            if (mapper.updateFolderSortOrder(folderId, ownerId, parentId, sortOrder, operator) != 1)
            {
                throw new ServiceException("目录排序保存失败，请刷新后重试");
            }
            sortOrder += 10;
        }
        logOperation(null, "FOLDER_REORDER", ownerId, operator,
            "parentId=" + parentId + ";folderIds=" + requestedIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFolder(Long folderId)
    {
        Long ownerId = SecurityUtils.getUserId();
        requireOwnedFolder(folderId, ownerId);
        if (mapper.countChildFolders(folderId) > 0 || mapper.countDocumentsByFolder(folderId) > 0)
        {
            throw new ServiceException("目录中仍有子目录或文档，无法删除");
        }
        if (mapper.deleteFolder(folderId) != 1)
        {
            throw new ServiceException("目录删除失败");
        }
    }

    @Override
    public List<DocDocument> listDocuments(String scope, Long folderId, String keyword, String fileType,
        String accessPermission)
    {
        String normalizedScope = StringUtils.trimToEmpty(scope).toUpperCase(Locale.ROOT);
        if (StringUtils.isBlank(normalizedScope))
        {
            normalizedScope = "MY";
        }
        if (!SCOPES.contains(normalizedScope))
        {
            throw new ServiceException("文档范围无效");
        }
        if ("ADMIN_ALL".equals(normalizedScope))
        {
            requireDocumentAdmin();
        }
        String normalizedFileType = normalizeOptionalFileType(fileType);
        Long normalizedFolderId = folderId;
        if (normalizedFolderId != null && ("MY".equals(normalizedScope) || "ARCHIVED".equals(normalizedScope)))
        {
            if (normalizedFolderId == 0)
            {
                // 根目录是全部文件的汇总入口，不对应可挂载文档的真实目录。
                normalizedFolderId = null;
            }
            else if (normalizedFolderId > 0)
            {
                requireOwnedFolder(normalizedFolderId, SecurityUtils.getUserId());
            }
        }
        else if (!("MY".equals(normalizedScope) || "ARCHIVED".equals(normalizedScope)))
        {
            normalizedFolderId = null;
        }
        String normalizedKeyword = StringUtils.trimToEmpty(keyword);
        if (normalizedKeyword.length() > 100)
        {
            throw new ServiceException("搜索内容不能超过100个字符");
        }
        String normalizedAccessPermission = "";
        if ("SHARED".equals(normalizedScope) && StringUtils.isNotBlank(accessPermission))
        {
            normalizedAccessPermission = StringUtils.trimToEmpty(accessPermission).toUpperCase(Locale.ROOT);
            if (!ACL_PERMISSIONS.contains(normalizedAccessPermission))
            {
                throw new ServiceException("共享权限筛选无效");
            }
        }
        List<DocDocument> documents = mapper.selectDocumentList(SecurityUtils.getUserId(), normalizedScope,
            normalizedFolderId, normalizedKeyword, normalizedFileType, normalizedAccessPermission);
        if ("ADMIN_ALL".equals(normalizedScope))
        {
            Long currentUserId = SecurityUtils.getUserId();
            documents.forEach(document -> document.setAccessPermission(
                currentUserId.equals(document.getOwnerId()) ? "OWNER" : "ADMIN"));
        }
        return documents;
    }

    @Override
    public DocDocument getDocument(Long documentId)
    {
        return requireReadable(documentId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocDocument createDocument(DocCreateBo input)
    {
        if (input == null)
        {
            throw new ServiceException("文档参数不能为空");
        }
        Long ownerId = SecurityUtils.getUserId();
        Long folderId = normalizeFolderId(input.getFolderId(), ownerId);
        String fileType = normalizeCreatableFileType(input.getFileType());
        String title = normalizeTitle(input.getTitle(), fileType);
        String username = SecurityUtils.getUsername();
        DocUserQuota quota = lockQuota(ownerId);
        long usedSize = valueOrZero(mapper.selectOwnedStorageBytes(ownerId));
        String pendingKey = "pending/" + UUID.randomUUID() + "." + fileType;

        DocDocument document = new DocDocument();
        document.setFolderId(folderId);
        document.setOwnerId(ownerId);
        document.setTitle(title);
        document.setFileType(fileType);
        document.setDocumentType(isWordType(fileType) ? "word" : "cell");
        document.setStorageKey(pendingKey);
        document.setFileSize(0L);
        document.setContentVersion(1);
        document.setEditorKey("pending-" + UUID.randomUUID());
        document.setChecksum("");
        document.setLifecycleStatus("ACTIVE");
        document.setCreateBy(username);
        document.setUpdateBy(username);
        mapper.insertDocument(document);

        String storageKey = versionStorageKey(document.getDocumentId(), 1, fileType, "initial");
        Path target = storageService.resolve(storageKey);
        try
        {
            storageService.createBlank(storageKey, fileType);
            document.setStorageKey(storageKey);
            document.setFileSize(Files.size(target));
            ensureHardFileLimit(document.getFileSize());
            ensureQuotaCapacity(quota, usedSize, document.getFileSize());
            document.setChecksum(storageService.checksum(target));
            document.setEditorKey(editorKey(document.getDocumentId(), 1));
            mapper.updateDocumentContent(document);
            insertVersion(document, "CREATE", ownerId, displayName(SecurityUtils.getLoginUser().getUser()));
            logOperation(document.getDocumentId(), "CREATE", ownerId, username, title);
            document.setAccessPermission("OWNER");
            return document;
        }
        catch (IOException | RuntimeException exception)
        {
            storageService.deleteQuietly(target);
            if (exception instanceof ServiceException serviceException)
            {
                throw serviceException;
            }
            throw new ServiceException("创建空白文档失败").setDetailMessage(exception.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocDocument copyDocument(Long documentId)
    {
        DocDocument source = requireOwner(documentId);
        if ("TRASH".equals(source.getLifecycleStatus()))
        {
            throw new ServiceException("请先恢复文档后再复制");
        }
        Long ownerId = SecurityUtils.getUserId();
        Long folderId = normalizeFolderId(source.getFolderId(), ownerId);
        Path sourceFile = requireStoredFile(source);
        String username = SecurityUtils.getUsername();
        String title = nextCopyTitle(source, ownerId, folderId);
        DocUserQuota quota = lockQuota(ownerId);
        long usedSize = valueOrZero(mapper.selectOwnedStorageBytes(ownerId));
        Path stored = null;
        try
        {
            long sourceSize = Files.size(sourceFile);
            ensureHardFileLimit(sourceSize);
            ensureQuotaCapacity(quota, usedSize, sourceSize);
            String checksum = StringUtils.defaultIfBlank(source.getChecksum(), storageService.checksum(sourceFile));
            DocDocument copy = new DocDocument();
            copy.setFolderId(folderId);
            copy.setOwnerId(ownerId);
            copy.setTitle(title);
            copy.setFileType(source.getFileType());
            copy.setDocumentType(StringUtils.defaultIfBlank(source.getDocumentType(), documentTypeFor(source.getFileType())));
            copy.setStorageKey("pending/" + UUID.randomUUID() + "." + source.getFileType());
            copy.setFileSize(0L);
            copy.setContentVersion(1);
            copy.setEditorKey("pending-" + UUID.randomUUID());
            copy.setChecksum("");
            copy.setLifecycleStatus("ACTIVE");
            copy.setCreateBy(username);
            copy.setUpdateBy(username);
            mapper.insertDocument(copy);

            String storageKey = versionStorageKey(copy.getDocumentId(), 1, copy.getFileType(), checksum);
            stored = storageService.copyIntoStorage(sourceFile, storageKey);
            scheduleStorageTransition(null, stored);
            copy.setStorageKey(storageKey);
            copy.setFileSize(Files.size(stored));
            copy.setEditorKey(editorKey(copy.getDocumentId(), 1));
            copy.setChecksum(checksum);
            mapper.updateDocumentContent(copy);
            insertVersion(copy, "COPY", ownerId, displayName(SecurityUtils.getLoginUser().getUser()));
            logOperation(copy.getDocumentId(), "COPY", ownerId, username,
                "复制自文档 " + source.getDocumentId() + "：" + source.getTitle());
            copy.setAccessPermission("OWNER");
            return copy;
        }
        catch (IOException | RuntimeException exception)
        {
            storageService.deleteQuietly(stored);
            if (exception instanceof ServiceException serviceException)
            {
                throw serviceException;
            }
            throw new ServiceException("复制文档失败").setDetailMessage(exception.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadDocument(MultipartFile upload, Long requestedFolderId)
    {
        String originalFilename = uploadFilename(upload);
        String fileType = normalizeFileType(extension(originalFilename));
        String title = normalizeTitle(originalFilename, fileType);
        Long ownerId = SecurityUtils.getUserId();
        Long folderId = normalizeFolderId(requestedFolderId, ownerId);
        String username = SecurityUtils.getUsername();
        DocUserQuota quota = lockQuota(ownerId);
        long usedSize = valueOrZero(mapper.selectOwnedStorageBytes(ownerId));
        long uploadLimit = Math.min(HARD_MAX_UPLOAD_BYTES, quota.getMaxUploadBytes());
        Path temporary = null;
        Path stored = null;
        try
        {
            temporary = storageService.copyUploadToTemp(upload, uploadLimit);
            DocumentStorageService.UploadValidationResult validation = isArchiveType(fileType)
                ? storageService.validateUploadedArchiveFile(temporary, fileType)
                : (isPdfType(fileType) ? storageService.validateUploadedPdfFile(temporary)
                    : storageService.validateUploadedOfficeFile(temporary, fileType));
            long uploadedSize = Files.size(temporary);
            ensureHardFileLimit(uploadedSize);
            ensureQuotaCapacity(quota, usedSize, uploadedSize);
            String checksum = storageService.checksum(temporary);

            DocDocument document = new DocDocument();
            document.setFolderId(folderId);
            document.setOwnerId(ownerId);
            document.setTitle(title);
            document.setFileType(fileType);
            document.setDocumentType(documentTypeFor(fileType));
            document.setStorageKey("pending/" + UUID.randomUUID() + "." + fileType);
            document.setFileSize(0L);
            document.setContentVersion(1);
            document.setEditorKey("pending-" + UUID.randomUUID());
            document.setChecksum("");
            document.setLifecycleStatus("ACTIVE");
            document.setCreateBy(username);
            document.setUpdateBy(username);
            mapper.insertDocument(document);

            String storageKey = versionStorageKey(document.getDocumentId(), 1, fileType, checksum);
            storageService.moveIntoStorage(temporary, storageKey);
            temporary = null;
            stored = storageService.resolve(storageKey);
            scheduleStorageTransition(null, stored);

            document.setStorageKey(storageKey);
            document.setFileSize(Files.size(stored));
            document.setContentVersion(1);
            document.setEditorKey(editorKey(document.getDocumentId(), 1));
            document.setChecksum(checksum);
            mapper.updateDocumentContent(document);
            String creatorName = displayName(SecurityUtils.getLoginUser().getUser());
            insertVersion(document, "UPLOAD", ownerId, creatorName);
            logOperation(document.getDocumentId(), "CREATE", ownerId, username, "上传 " + title);
            document.setAccessPermission("OWNER");
            return Map.of("document", document, "warnings", validation.warnings());
        }
        catch (IOException | RuntimeException exception)
        {
            storageService.deleteQuietly(temporary);
            storageService.deleteQuietly(stored);
            if (exception instanceof ServiceException serviceException)
            {
                throw serviceException;
            }
            throw new ServiceException("上传文件失败").setDetailMessage(exception.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDocument(Long documentId, DocUpdateBo input)
    {
        if (input == null)
        {
            throw new ServiceException("文档参数不能为空");
        }
        DocDocument document = requireOwner(documentId);
        String oldTitle = document.getTitle();
        Long oldFolderId = document.getFolderId();
        String oldStatus = document.getLifecycleStatus();
        if (input.getTitle() != null)
        {
            document.setTitle(normalizeTitle(input.getTitle(), document.getFileType()));
        }
        if (input.getFolderId() != null)
        {
            document.setFolderId(normalizeFolderId(input.getFolderId(), document.getOwnerId()));
        }
        if (input.getLifecycleStatus() != null)
        {
            String status = StringUtils.trimToEmpty(input.getLifecycleStatus()).toUpperCase(Locale.ROOT);
            if (!LIFECYCLE_STATUSES.contains(status))
            {
                throw new ServiceException("文档状态无效");
            }
            document.setLifecycleStatus(status);
        }
        document.setUpdateBy(SecurityUtils.getUsername());
        mapper.updateDocumentMetadata(document);
        String action = !oldStatus.equals(document.getLifecycleStatus())
            ? document.getLifecycleStatus()
            : !oldFolderId.equals(document.getFolderId()) ? "MOVE" : "RENAME";
        logOperation(documentId, action, SecurityUtils.getUserId(), SecurityUtils.getUsername(),
            oldTitle + " -> " + document.getTitle());
    }

    @Override
    public List<DocAcl> listCollaborators(Long documentId)
    {
        requireOwner(documentId);
        Set<Long> onlineIds = onlineUserIds();
        Date now = new Date();
        List<DocAcl> list = mapper.selectAclList(documentId);
        list.forEach(item -> {
            item.setOnline(onlineIds.contains(item.getUserId()));
            item.setExpired(item.getExpiresAt() != null && !item.getExpiresAt().after(now));
        });
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCollaborators(Long documentId, DocAclSaveBo input)
    {
        DocDocument document = requireOwner(documentId);
        List<DocAclSaveBo.Entry> entries = input == null || input.getEntries() == null
            ? List.of() : input.getEntries();
        if (entries.size() > MAX_COLLABORATORS)
        {
            throw new ServiceException("单个文档最多配置" + MAX_COLLABORATORS + "名协作者");
        }
        Set<Long> uniqueUsers = new LinkedHashSet<>();
        Map<Long, DocAcl> desired = new LinkedHashMap<>();
        Map<Long, SysUser> collaborators = new LinkedHashMap<>();
        for (DocAclSaveBo.Entry entry : entries)
        {
            if (entry == null || entry.getUserId() == null || entry.getUserId().equals(document.getOwnerId()))
            {
                throw new ServiceException("协作者用户无效");
            }
            if (!uniqueUsers.add(entry.getUserId()))
            {
                throw new ServiceException("协作者不能重复");
            }
            String permission = StringUtils.trimToEmpty(entry.getPermission()).toUpperCase(Locale.ROOT);
            if (!ACL_PERMISSIONS.contains(permission))
            {
                throw new ServiceException("协作者权限必须是查看或编辑");
            }
            if (isViewOnlyType(document.getFileType()) && !"VIEW".equals(permission))
            {
                throw new ServiceException(isPdfType(document.getFileType())
                    ? "PDF 仅支持共享查看权限，不能授予在线编辑权限"
                    : "压缩包仅支持共享下载权限，不能授予在线编辑权限");
            }
            SysUser user = sysUserService.selectUserById(entry.getUserId());
            if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag()))
            {
                throw new ServiceException("协作者不存在或已停用");
            }
            if (mapper.countDocumentWorkspaceAccess(entry.getUserId()) <= 0)
            {
                throw new ServiceException("协作者尚未获得文档管理权限");
            }
            // 文档共享使用独立的文档权限边界。document角色的数据范围保持“仅本人”，
            // 不能再使用若依通用用户数据范围阻断跨部门、跨用户的文档协作。
            DocAcl acl = new DocAcl();
            acl.setDocumentId(documentId);
            acl.setUserId(entry.getUserId());
            acl.setPermission(permission);
            acl.setGrantedBy(document.getOwnerId());
            acl.setExpiresAt(normalizeExpiration(entry.getExpiresAt()));
            desired.put(entry.getUserId(), acl);
            collaborators.put(entry.getUserId(), user);
        }

        Map<Long, DocAcl> current = new LinkedHashMap<>();
        mapper.selectAclRecords(documentId).forEach(acl -> current.put(acl.getUserId(), acl));
        List<Long> revokeUserIds = new ArrayList<>();
        Long operatorId = SecurityUtils.getUserId();
        String operatorName = SecurityUtils.getUsername();

        for (Map.Entry<Long, DocAcl> entry : desired.entrySet())
        {
            Long userId = entry.getKey();
            DocAcl wanted = entry.getValue();
            DocAcl existing = current.remove(userId);
            SysUser user = collaborators.get(userId);
            String targetName = displayName(user);
            if (existing == null)
            {
                mapper.insertAcl(wanted);
                logOperation(documentId, "ACL_GRANT", operatorId, operatorName, userId, targetName,
                    null, permissionSnapshot(wanted), "已授予文档权限");
            }
            else if (!wanted.getPermission().equals(existing.getPermission())
                || !sameExpiration(wanted.getExpiresAt(), existing.getExpiresAt()))
            {
                mapper.updateAcl(wanted);
                logOperation(documentId, "ACL_CHANGE", operatorId, operatorName, userId, targetName,
                    permissionSnapshot(existing), permissionSnapshot(wanted), "已调整文档权限或有效期");
                if ("EDIT".equals(existing.getPermission()) && "VIEW".equals(wanted.getPermission()))
                {
                    revokeUserIds.add(userId);
                }
            }
        }
        for (DocAcl removed : current.values())
        {
            mapper.deleteAcl(documentId, removed.getUserId());
            SysUser user = sysUserService.selectUserById(removed.getUserId());
            String targetName = user == null ? "用户#" + removed.getUserId() : displayName(user);
            logOperation(documentId, "ACL_REVOKE", operatorId, operatorName, removed.getUserId(), targetName,
                permissionSnapshot(removed), null, "已移除文档权限");
            if ("EDIT".equals(removed.getPermission()))
            {
                revokeUserIds.add(removed.getUserId());
            }
        }
        editorProvider.revokeEditingRights(document, revokeUserIds);
    }

    @Override
    public List<DocUserVo> listCollaboratorCandidates(Long documentId, String keyword)
    {
        DocDocument document = requireOwner(documentId);
        String normalizedKeyword = StringUtils.trimToEmpty(keyword).toLowerCase(Locale.ROOT);
        if (normalizedKeyword.length() > 100)
        {
            throw new ServiceException("搜索内容不能超过100个字符");
        }
        Long currentUserId = SecurityUtils.getUserId();
        Set<Long> onlineIds = onlineUserIds();
        List<DocUserVo> result = mapper.selectCollaboratorCandidates(
            currentUserId, document.getOwnerId(), normalizedKeyword, MAX_COLLABORATORS);
        result.forEach(item -> item.setOnline(onlineIds.contains(item.getUserId())));
        return result;
    }

    @Override
    public List<DocVersion> listVersions(Long documentId)
    {
        DocDocument document = requireReadable(documentId, true);
        List<DocVersion> versions = mapper.selectVersions(documentId);
        versions.forEach(version -> version.setCurrent(Objects.equals(version.getVersionNo(), document.getContentVersion())));
        return versions;
    }

    @Override
    public List<DocOperationLog> listOperations(Long documentId)
    {
        requireOwner(documentId);
        return mapper.selectOperationLogs(documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> getEditorBootstrap(Long documentId)
    {
        DocDocument document = requireReadable(documentId, false);
        if (isArchiveType(document.getFileType()))
        {
            throw new ServiceException("压缩包不支持在线预览或编辑，请下载后使用");
        }
        if (isPdfType(document.getFileType()))
        {
            throw new ServiceException("PDF 仅支持只读在线预览，不进入文档编辑器");
        }
        boolean editable = "ACTIVE".equals(document.getLifecycleStatus())
            && ("OWNER".equals(document.getAccessPermission()) || "EDIT".equals(document.getAccessPermission()));
        logOperation(documentId, "OPEN", SecurityUtils.getUserId(), SecurityUtils.getUsername(),
            editable ? "编辑" : "查看");
        return editorProvider.buildEditorBootstrap(document, SecurityUtils.getLoginUser(), editable);
    }

    @Override
    public Map<String, Object> forceSaveDocument(Long documentId)
    {
        DocDocument document = requireReadable(documentId, false);
        if (isArchiveType(document.getFileType()))
        {
            throw new ServiceException("压缩包不支持在线编辑");
        }
        if (isPdfType(document.getFileType()))
        {
            throw new ServiceException("PDF 仅支持只读在线预览，不能强制保存");
        }
        boolean editable = "ACTIVE".equals(document.getLifecycleStatus())
            && ("OWNER".equals(document.getAccessPermission()) || "EDIT".equals(document.getAccessPermission()));
        if (!editable)
        {
            throw new ServiceException("当前用户没有文档编辑权限");
        }
        boolean queued = editorProvider.forceSave(document);
        return Map.of(
            "queued", queued,
            "contentVersion", document.getContentVersion());
    }

    @Override
    public DocFileResource getDownloadFile(Long documentId)
    {
        DocDocument document = requireReadable(documentId, false);
        Path path = requireStoredFile(document);
        return new DocFileResource(path, document.getTitle(), document.getFileType());
    }

    @Override
    public DocFileResource getPreviewFile(Long documentId)
    {
        DocDocument document = requireReadable(documentId, false);
        if (!isPdfType(document.getFileType()))
        {
            throw new ServiceException("当前文件不是可在线预览的 PDF");
        }
        return new DocFileResource(requireStoredFile(document), document.getTitle(), document.getFileType());
    }

    @Override
    public List<DocFileResource> getBatchDownloadFiles(List<Long> documentIds)
    {
        if (documentIds == null || documentIds.isEmpty())
        {
            throw new ServiceException("请至少选择一份文档");
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long documentId : documentIds)
        {
            if (documentId == null || documentId <= 0L)
            {
                throw new ServiceException("批量下载包含无效文档ID");
            }
            uniqueIds.add(documentId);
        }
        if (uniqueIds.size() > documentProperties.getMaxBatchDownloadFiles())
        {
            throw new ServiceException("单次最多批量下载" + documentProperties.getMaxBatchDownloadFiles() + "份文档");
        }
        List<DocFileResource> resources = new ArrayList<>();
        long totalSize = 0L;
        for (Long documentId : uniqueIds)
        {
            DocDocument document = requireReadable(documentId, false);
            Path path = requireStoredFile(document);
            try
            {
                long size = Files.size(path);
                if (size > documentProperties.getMaxBatchDownloadSize() - totalSize)
                {
                    throw new ServiceException("所选文档总大小超过"
                        + (documentProperties.getMaxBatchDownloadSize() / (1024L * 1024L)) + "MB限制");
                }
                totalSize += size;
            }
            catch (IOException exception)
            {
                throw new ServiceException("读取批量下载文件失败").setDetailMessage(exception.getMessage());
            }
            resources.add(new DocFileResource(path, document.getTitle(), document.getFileType()));
        }
        return List.copyOf(resources);
    }

    @Override
    public DocFileResource getEditorFile(Long documentId, String accessToken)
    {
        DocDocument document = mapper.selectDocumentRecord(documentId);
        if (document == null || "TRASH".equals(document.getLifecycleStatus()))
        {
            throw new ServiceException("文档不存在");
        }
        editorProvider.verifyFileToken(accessToken, document);
        return new DocFileResource(requireStoredFile(document), document.getTitle(), document.getFileType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireCollaboratorPermissions()
    {
        int expiredCount = 0;
        for (DocAcl acl : mapper.selectExpiredAclRecords(EXPIRY_BATCH_SIZE))
        {
            if ("EDIT".equals(acl.getPermission()))
            {
                DocDocument document = mapper.selectDocumentRecord(acl.getDocumentId());
                if (document != null)
                {
                    try
                    {
                        editorProvider.revokeEditingRights(document, List.of(acl.getUserId()));
                    }
                    catch (RuntimeException exception)
                    {
                        log.warn("撤销到期文档编辑会话失败，aclId={}，documentId={}，userId={}：{}",
                            acl.getAclId(), acl.getDocumentId(), acl.getUserId(), exception.getMessage());
                        continue;
                    }
                }
            }
            if (mapper.deleteExpiredAcl(acl.getAclId()) == 1)
            {
                String targetName = StringUtils.defaultIfBlank(acl.getNickName(), acl.getUserName());
                if (StringUtils.isBlank(targetName))
                {
                    targetName = "用户#" + acl.getUserId();
                }
                logOperation(acl.getDocumentId(), "ACL_EXPIRE", null, "系统", acl.getUserId(), targetName,
                    permissionSnapshot(acl), null, "文档权限已按设定时间自动到期");
                expiredCount++;
            }
        }
        return expiredCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleEditorCallback(Long documentId, String accessToken, String outboxToken,
        Map<String, Object> payload)
    {
        if (payload == null)
        {
            throw new ServiceException("编辑器回调内容为空");
        }
        DocDocument document = mapper.selectDocumentRecordForUpdate(documentId);
        if (document == null)
        {
            throw new ServiceException("回调文档不存在");
        }
        if (isViewOnlyType(document.getFileType()))
        {
            throw new ServiceException(isPdfType(document.getFileType())
                ? "PDF 不接受编辑器回调" : "压缩包不接受编辑器回调");
        }
        Map<String, Object> verifiedPayload = editorProvider.verifyCallback(accessToken, outboxToken, document, payload);
        int status = callbackStatus(verifiedPayload.get("status"));
        if (status != 2 && status != 6)
        {
            return Map.of("error", 0);
        }
        CallbackActor actor = callbackActor(verifiedPayload);
        String sourceUrl = String.valueOf(verifiedPayload.get("url"));
        if (StringUtils.isBlank(sourceUrl) || "null".equals(sourceUrl))
        {
            throw new ServiceException("编辑器回调缺少文件地址");
        }

        Path temporary = null;
        Path stored = null;
        Path previousStored = storageService.resolve(document.getStorageKey());
        try
        {
            temporary = storageService.createTempFile();
            editorProvider.downloadCallbackFile(sourceUrl, temporary);
            String previousFileType = document.getFileType();
            String persistedFileType = storageService.validateEditorOfficeFile(temporary, previousFileType);
            long nextFileSize = Files.size(temporary);
            ensureHardFileLimit(nextFileSize);
            String checksum = storageService.checksum(temporary);
            if (checksum.equals(document.getChecksum()))
            {
                storageService.deleteQuietly(temporary);
                if (status == 2)
                {
                    String nextEditorKey = editorKey(documentId, document.getContentVersion());
                    if (!nextEditorKey.equals(document.getEditorKey()))
                    {
                        document.setEditorKey(nextEditorKey);
                        document.setUpdateBy(actor.name());
                        mapper.updateDocumentContent(document);
                    }
                }
                return Map.of("error", 0);
            }
            DocUserQuota quota = lockQuota(document.getOwnerId(), actor.name());
            long usedSize = valueOrZero(mapper.selectOwnedStorageBytes(document.getOwnerId()));
            ensureQuotaCapacity(quota, Math.max(0L, usedSize - valueOrZero(document.getFileSize())), nextFileSize);
            int nextVersion = document.getContentVersion() + 1;
            String storageKey = versionStorageKey(documentId, nextVersion, persistedFileType, checksum);
            stored = storageService.resolve(storageKey);
            storageService.moveIntoStorage(temporary, storageKey);
            temporary = null;

            if (!persistedFileType.equals(previousFileType))
            {
                document.setFileType(persistedFileType);
                document.setTitle(replaceFileExtension(document.getTitle(), persistedFileType));
            }
            document.setStorageKey(storageKey);
            document.setFileSize(Files.size(stored));
            document.setContentVersion(nextVersion);
            document.setChecksum(checksum);
            if (status == 2)
            {
                document.setEditorKey(editorKey(documentId, nextVersion));
            }
            document.setUpdateBy(actor.name());
            mapper.updateDocumentContent(document);
            String sourceType = status == 2 ? "FINAL_SAVE" : "FORCE_SAVE";
            insertVersion(document, sourceType, actor.userId(), actor.name());
            scheduleStorageTransition(previousStored, stored);
            return Map.of("error", 0);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            storageService.deleteQuietly(temporary);
            storageService.deleteQuietly(stored);
            throw new ServiceException("编辑器回调下载被中断");
        }
        catch (IOException | RuntimeException exception)
        {
            storageService.deleteQuietly(temporary);
            if (exception instanceof ServiceException serviceException)
            {
                throw serviceException;
            }
            storageService.deleteQuietly(stored);
            throw new ServiceException("保存编辑器回调文件失败").setDetailMessage(exception.getMessage());
        }
    }

    private String replaceFileExtension(String title, String fileType)
    {
        String normalizedTitle = StringUtils.trimToEmpty(title);
        int separator = normalizedTitle.lastIndexOf('.');
        String baseName = separator > 0 ? normalizedTitle.substring(0, separator) : normalizedTitle;
        if (StringUtils.isBlank(baseName))
        {
            baseName = "未命名文档";
        }
        return baseName + "." + fileType;
    }

    private DocFolder normalizeFolder(DocFolderSaveBo input, Long ownerId, Long excludeFolderId,
        DocFolder current)
    {
        if (input == null)
        {
            throw new ServiceException("目录参数不能为空");
        }
        String name = StringUtils.trimToEmpty(input.getFolderName());
        if (StringUtils.isBlank(name) || name.length() > 100 || containsPathSeparator(name))
        {
            throw new ServiceException("目录名称不能为空、不能包含路径符号且最多100个字符");
        }
        Long parentId = input.getParentId() == null ? 0L : input.getParentId();
        if (parentId < 0)
        {
            throw new ServiceException("上级目录无效");
        }
        if (parentId > 0)
        {
            requireOwnedFolder(parentId, ownerId);
        }
        if (mapper.countSiblingFolder(ownerId, parentId, name, excludeFolderId) > 0)
        {
            throw new ServiceException("同级目录下已存在相同名称");
        }
        DocFolder folder = new DocFolder();
        folder.setParentId(parentId);
        folder.setOwnerId(ownerId);
        folder.setFolderName(name);
        folder.setFolderColor(normalizeFolderColor(input.getFolderColor(), current));
        if (current != null && Objects.equals(parentId, current.getParentId()))
        {
            folder.setSortOrder(current.getSortOrder() == null ? 0 : current.getSortOrder());
        }
        else
        {
            Integer nextSortOrder = mapper.selectNextFolderSortOrder(ownerId, parentId);
            folder.setSortOrder(nextSortOrder == null ? 10 : nextSortOrder);
        }
        return folder;
    }

    private String normalizeFolderColor(String color, DocFolder current)
    {
        if (StringUtils.isBlank(color))
        {
            String existing = current == null ? null : StringUtils.trimToEmpty(current.getFolderColor()).toUpperCase(Locale.ROOT);
            return FOLDER_COLORS.contains(existing) ? existing : DEFAULT_FOLDER_COLOR;
        }
        String normalized = StringUtils.trimToEmpty(color).toUpperCase(Locale.ROOT);
        if (!FOLDER_COLORS.contains(normalized))
        {
            throw new ServiceException("目录颜色无效，请使用系统提供的颜色");
        }
        return normalized;
    }

    private void requireNoFolderCycle(Long folderId, Long parentId, Long ownerId)
    {
        Long cursor = parentId;
        for (int depth = 0; cursor != null && cursor > 0 && depth < MAX_FOLDER_DEPTH; depth++)
        {
            if (cursor.equals(folderId))
            {
                throw new ServiceException("目录不能移动到自己的子目录");
            }
            DocFolder parent = requireOwnedFolder(cursor, ownerId);
            cursor = parent.getParentId();
        }
        if (cursor != null && cursor > 0)
        {
            throw new ServiceException("目录层级不能超过" + MAX_FOLDER_DEPTH + "层");
        }
    }

    private DocFolder requireOwnedFolder(Long folderId, Long ownerId)
    {
        if (folderId == null || folderId <= 0)
        {
            throw new ServiceException("目录不存在");
        }
        DocFolder folder = mapper.selectFolderById(folderId);
        if (folder == null || !ownerId.equals(folder.getOwnerId()))
        {
            throw new ServiceException("目录不存在或无权访问");
        }
        return folder;
    }

    private Long normalizeFolderId(Long folderId, Long ownerId)
    {
        Long normalized = folderId == null ? 0L : folderId;
        if (normalized <= 0)
        {
            throw new ServiceException("根目录只用于管理目录，请先新建并选择一个目录");
        }
        requireOwnedFolder(normalized, ownerId);
        return normalized;
    }

    private String nextCopyTitle(DocDocument source, Long ownerId, Long folderId)
    {
        String fileType = normalizeFileType(source.getFileType());
        String title = StringUtils.trimToEmpty(source.getTitle());
        String extension = "." + fileType;
        String baseName = title.toLowerCase(Locale.ROOT).endsWith(extension)
            ? title.substring(0, title.length() - extension.length()) : title;
        baseName = COPY_SUFFIX_PATTERN.matcher(baseName).replaceFirst("");
        if (StringUtils.isBlank(baseName))
        {
            baseName = defaultTitleFor(fileType);
        }
        for (int index = 1; index <= 99; index++)
        {
            String suffix = "(附件" + chineseNumber(index) + ")";
            int maximumBaseLength = 160 - suffix.length() - extension.length();
            if (maximumBaseLength <= 0)
            {
                throw new ServiceException("文档名称过长，无法生成副本名称");
            }
            String candidateBase = baseName.length() > maximumBaseLength
                ? baseName.substring(0, maximumBaseLength) : baseName;
            String candidate = candidateBase + suffix + extension;
            if (mapper.countDocumentTitle(ownerId, folderId, candidate) == 0)
            {
                return candidate;
            }
        }
        throw new ServiceException("同一目录中的文档副本过多，请先重命名后再复制");
    }

    private String chineseNumber(int value)
    {
        if (value < 10)
        {
            return CHINESE_DIGITS[value];
        }
        int tens = value / 10;
        int ones = value % 10;
        return (tens == 1 ? "" : CHINESE_DIGITS[tens]) + "十" + (ones == 0 ? "" : CHINESE_DIGITS[ones]);
    }

    private long valueOrZero(Long value)
    {
        return value == null ? 0L : value;
    }

    private DocUserQuota currentQuota(Long userId)
    {
        return normalizeQuota(userId, mapper.selectQuota(userId));
    }

    private DocUserQuota lockQuota(Long userId)
    {
        return lockQuota(userId, SecurityUtils.getUsername());
    }

    private DocUserQuota lockQuota(Long userId, String operator)
    {
        mapper.insertDefaultQuota(userId, StringUtils.defaultIfBlank(operator, "system"));
        return normalizeQuota(userId, mapper.selectQuotaForUpdate(userId));
    }

    private DocUserQuota normalizeQuota(Long userId, DocUserQuota quota)
    {
        DocUserQuota normalized = quota == null ? new DocUserQuota() : quota;
        normalized.setUserId(userId);
        if (normalized.getQuotaBytes() == null || normalized.getQuotaBytes() < 1L)
        {
            normalized.setQuotaBytes(DEFAULT_QUOTA_BYTES);
        }
        if (normalized.getMaxUploadBytes() == null || normalized.getMaxUploadBytes() < 1L)
        {
            normalized.setMaxUploadBytes(DEFAULT_MAX_UPLOAD_BYTES);
        }
        normalized.setMaxUploadBytes(Math.min(HARD_MAX_UPLOAD_BYTES, normalized.getMaxUploadBytes()));
        return normalized;
    }

    private void ensureQuotaCapacity(DocUserQuota quota, long usedSize, long additionalSize)
    {
        long safeUsed = Math.max(0L, usedSize);
        long safeAdditional = Math.max(0L, additionalSize);
        long quotaBytes = quota.getQuotaBytes();
        if (safeUsed > quotaBytes || safeAdditional > quotaBytes - safeUsed)
        {
            long remaining = Math.max(0L, quotaBytes - safeUsed);
            throw new ServiceException("可用空间不足，当前剩余" + readableMegabytes(remaining)
                + "MB，本次文件需要" + readableMegabytes(safeAdditional) + "MB");
        }
    }

    private void ensureHardFileLimit(long fileSize)
    {
        if (fileSize > HARD_MAX_UPLOAD_BYTES)
        {
            throw new ServiceException("单个文件不能超过100MB");
        }
    }

    private void normalizeStorageUser(DocUserStorageVo user)
    {
        user.setFileCount(valueOrZero(user.getFileCount()));
        user.setUsedSize(valueOrZero(user.getUsedSize()));
        user.setQuotaSize(user.getQuotaSize() == null || user.getQuotaSize() < 1L
            ? DEFAULT_QUOTA_BYTES : user.getQuotaSize());
        user.setMaxUploadSize(user.getMaxUploadSize() == null || user.getMaxUploadSize() < 1L
            ? DEFAULT_MAX_UPLOAD_BYTES : Math.min(HARD_MAX_UPLOAD_BYTES, user.getMaxUploadSize()));
        user.setUsagePercent(usagePercent(user.getUsedSize(), user.getQuotaSize()));
    }

    private double usagePercent(long usedSize, long quotaSize)
    {
        if (quotaSize <= 0L)
        {
            return 0D;
        }
        double percentage = Math.min(100D, usedSize * 100D / quotaSize);
        return Math.round(percentage * 100D) / 100D;
    }

    private long readableMegabytes(long bytes)
    {
        return bytes <= 0L ? 0L : Math.max(1L, (bytes + BYTES_PER_MEGABYTE - 1L) / BYTES_PER_MEGABYTE);
    }

    private boolean isDocumentAdmin()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return loginUser.getUser() != null && loginUser.getUser().getRoles() != null
            && loginUser.getUser().getRoles().stream()
                .anyMatch(role -> role != null && "admin".equals(role.getRoleKey()));
    }

    private void requireDocumentAdmin()
    {
        if (!isDocumentAdmin())
        {
            throw new ServiceException("仅 admin 角色可以管理全部用户文档与容量");
        }
    }

    private DocDocument requireReadable(Long documentId, boolean allowTrash)
    {
        if (documentId == null)
        {
            throw new ServiceException("文档ID不能为空");
        }
        DocDocument document = mapper.selectAccessibleDocument(documentId, SecurityUtils.getUserId());
        if (document == null && isDocumentAdmin())
        {
            document = mapper.selectDocumentRecord(documentId);
            if (document != null)
            {
                document.setAccessPermission(SecurityUtils.getUserId().equals(document.getOwnerId()) ? "OWNER" : "ADMIN");
            }
        }
        if (document == null || (!allowTrash && "TRASH".equals(document.getLifecycleStatus())))
        {
            throw new ServiceException("文档不存在或无权访问");
        }
        return document;
    }

    private DocDocument requireOwner(Long documentId)
    {
        DocDocument document = requireReadable(documentId, true);
        if (!"OWNER".equals(document.getAccessPermission()))
        {
            throw new ServiceException("仅文档所有者可以执行此操作");
        }
        return document;
    }

    private Path requireStoredFile(DocDocument document)
    {
        Path path = storageService.resolve(document.getStorageKey());
        if (!Files.isRegularFile(path))
        {
            throw new ServiceException("文档文件不存在，请联系管理员检查共享存储");
        }
        return path;
    }

    private Date normalizeExpiration(Date expiresAt)
    {
        if (expiresAt == null)
        {
            return null;
        }
        Date normalized = new Date((expiresAt.getTime() / 1000L) * 1000L);
        if (!normalized.after(new Date()))
        {
            throw new ServiceException("权限到期时间必须晚于当前时间");
        }
        return normalized;
    }

    private boolean sameExpiration(Date left, Date right)
    {
        if (left == null || right == null)
        {
            return left == null && right == null;
        }
        return left.getTime() / 1000L == right.getTime() / 1000L;
    }

    private String permissionSnapshot(DocAcl acl)
    {
        String expiry = acl.getExpiresAt() == null ? "PERMANENT"
            : DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, acl.getExpiresAt());
        return StringUtils.defaultString(acl.getPermission()) + "|" + expiry;
    }

    private String normalizeOptionalFileType(String fileType)
    {
        if (StringUtils.isBlank(fileType))
        {
            return "";
        }
        return normalizeFileType(fileType);
    }

    private String normalizeFileType(String fileType)
    {
        String normalized = StringUtils.trimToEmpty(fileType).toLowerCase(Locale.ROOT);
        if (!FILE_TYPES.contains(normalized))
        {
            throw new ServiceException("仅支持 DOC、DOCX、XLS、XLSX、PDF、ZIP 和 RAR 文件");
        }
        return normalized;
    }

    private String normalizeCreatableFileType(String fileType)
    {
        String normalized = StringUtils.trimToEmpty(fileType).toLowerCase(Locale.ROOT);
        if (!CREATABLE_FILE_TYPES.contains(normalized))
        {
            throw new ServiceException("新建文档仅支持 DOCX 和 XLSX 格式");
        }
        return normalized;
    }

    private String normalizeTitle(String title, String fileType)
    {
        String normalized = StringUtils.trimToEmpty(title);
        if (StringUtils.isBlank(normalized))
        {
            normalized = defaultTitleFor(fileType);
        }
        if (containsPathSeparator(normalized) || normalized.indexOf('\0') >= 0)
        {
            throw new ServiceException("文档名称不能包含路径符号");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String supported : FILE_TYPES)
        {
            if (lower.endsWith("." + supported))
            {
                normalized = normalized.substring(0, normalized.length() - supported.length() - 1);
                break;
            }
        }
        normalized = StringUtils.trimToEmpty(normalized) + "." + fileType;
        if (normalized.length() > 160)
        {
            throw new ServiceException("文档名称不能超过160个字符");
        }
        return normalized;
    }

    private boolean containsPathSeparator(String value)
    {
        return value.contains("/") || value.contains("\\");
    }

    private boolean isWordType(String fileType)
    {
        return "doc".equals(fileType) || "docx".equals(fileType);
    }

    private boolean isArchiveType(String fileType)
    {
        return ARCHIVE_FILE_TYPES.contains(StringUtils.trimToEmpty(fileType).toLowerCase(Locale.ROOT));
    }

    private boolean isPdfType(String fileType)
    {
        return "pdf".equals(StringUtils.trimToEmpty(fileType).toLowerCase(Locale.ROOT));
    }

    private boolean isViewOnlyType(String fileType)
    {
        return isArchiveType(fileType) || isPdfType(fileType);
    }

    private String documentTypeFor(String fileType)
    {
        if (isArchiveType(fileType))
        {
            return "archive";
        }
        if (isPdfType(fileType))
        {
            return "pdf";
        }
        return isWordType(fileType) ? "word" : "cell";
    }

    private String defaultTitleFor(String fileType)
    {
        if (isArchiveType(fileType))
        {
            return "未命名压缩包";
        }
        if (isPdfType(fileType))
        {
            return "未命名PDF文件";
        }
        return isWordType(fileType) ? "未命名文档" : "未命名表格";
    }

    private String uploadFilename(MultipartFile upload)
    {
        if (upload == null)
        {
            throw new ServiceException("请选择需要上传的文件");
        }
        String filename = StringUtils.trimToEmpty(upload.getOriginalFilename()).replace('\\', '/');
        int separator = filename.lastIndexOf('/');
        if (separator >= 0)
        {
            filename = filename.substring(separator + 1);
        }
        if (StringUtils.isBlank(filename) || filename.indexOf('\0') >= 0 || filename.length() > 160)
        {
            throw new ServiceException("上传文件名称无效或超过160个字符");
        }
        return filename;
    }

    private String extension(String filename)
    {
        int separator = filename.lastIndexOf('.');
        if (separator <= 0 || separator == filename.length() - 1)
        {
            throw new ServiceException("文件缺少有效扩展名，仅支持 DOC、DOCX、XLS、XLSX、PDF、ZIP 和 RAR");
        }
        return filename.substring(separator + 1);
    }

    private String versionStorageKey(Long documentId, int version, String fileType, String checksum)
    {
        String suffix = StringUtils.defaultIfBlank(checksum, "file");
        suffix = suffix.substring(0, Math.min(12, suffix.length())).replaceAll("[^a-zA-Z0-9]", "");
        return "documents/" + documentId + "/v" + version + "-" + suffix + "." + fileType;
    }

    private String editorKey(Long documentId, int version)
    {
        return "doc-" + documentId + "-v" + version;
    }

    private DocVersion insertVersion(DocDocument document, String sourceType, Long creatorId, String creatorName)
    {
        DocVersion version = new DocVersion();
        version.setDocumentId(document.getDocumentId());
        version.setVersionNo(document.getContentVersion());
        version.setSourceType(sourceType);
        version.setCreatorId(creatorId);
        version.setCreatorName(creatorName);
        mapper.insertVersion(version);
        return version;
    }

    private void scheduleStorageTransition(Path previousStored, Path currentStored)
    {
        if (currentStored == null || (previousStored != null
            && previousStored.toAbsolutePath().normalize().equals(currentStored.toAbsolutePath().normalize())))
        {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            if (previousStored != null)
            {
                storageService.deleteQuietly(previousStored);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                if (previousStored != null)
                {
                    storageService.deleteQuietly(previousStored);
                }
            }

            @Override
            public void afterCompletion(int status)
            {
                if (status != TransactionSynchronization.STATUS_COMMITTED)
                {
                    storageService.deleteQuietly(currentStored);
                }
            }
        });
    }

    private void logOperation(Long documentId, String action, Long operatorId, String operatorName, String detail)
    {
        logOperation(documentId, action, operatorId, operatorName, null, null, null, null, detail);
    }

    private void logOperation(Long documentId, String action, Long operatorId, String operatorName,
        Long targetUserId, String targetUserName, String previousValue, String currentValue, String detail)
    {
        DocOperationLog operationLog = new DocOperationLog();
        operationLog.setDocumentId(documentId);
        operationLog.setActionType(action);
        operationLog.setOperatorId(operatorId);
        operationLog.setOperatorName(operatorName);
        operationLog.setTargetUserId(targetUserId);
        operationLog.setTargetUserName(StringUtils.abbreviate(targetUserName, 64));
        operationLog.setPreviousValue(StringUtils.abbreviate(previousValue, 128));
        operationLog.setCurrentValue(StringUtils.abbreviate(currentValue, 128));
        operationLog.setDetailContent(StringUtils.abbreviate(StringUtils.defaultString(detail), 500));
        mapper.insertOperationLog(operationLog);
    }

    private CallbackActor callbackActor(Map<String, Object> payload)
    {
        Object rawUsers = payload.get("users");
        if (rawUsers instanceof Collection<?> users)
        {
            List<?> values = new ArrayList<>(users);
            for (int index = values.size() - 1; index >= 0; index--)
            {
                try
                {
                    Long userId = Long.valueOf(String.valueOf(values.get(index)));
                    SysUser user = sysUserService.selectUserById(userId);
                    if (user != null)
                    {
                        return new CallbackActor(userId, displayName(user));
                    }
                    return new CallbackActor(userId, "用户#" + userId);
                }
                catch (NumberFormatException ignored)
                {
                    // Ignore unknown external editor identifiers and retain the provider fallback.
                }
            }
        }
        return new CallbackActor(null, editorProvider.getProviderName());
    }

    private record CallbackActor(Long userId, String name) { }

    private Set<Long> onlineUserIds()
    {
        Set<Long> result = new HashSet<>();
        try
        {
            Collection<String> keys = redisCache.keys(CacheConstants.LOGIN_TOKEN_KEY + "*");
            if (keys == null)
            {
                return result;
            }
            for (String key : keys)
            {
                LoginUser loginUser = redisCache.getCacheObject(key);
                if (loginUser != null && loginUser.getUserId() != null && loginUser.getUser() != null)
                {
                    result.add(loginUser.getUserId());
                }
            }
        }
        catch (Exception exception)
        {
            log.warn("读取文档协作者在线状态失败: {}", exception.getMessage());
        }
        return result;
    }

    private String displayName(SysUser user)
    {
        return StringUtils.defaultIfBlank(user.getNickName(), user.getUserName());
    }

    private int callbackStatus(Object value)
    {
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        try
        {
            return Integer.parseInt(String.valueOf(value));
        }
        catch (Exception exception)
        {
            throw new ServiceException("编辑器回调状态无效");
        }
    }
}
