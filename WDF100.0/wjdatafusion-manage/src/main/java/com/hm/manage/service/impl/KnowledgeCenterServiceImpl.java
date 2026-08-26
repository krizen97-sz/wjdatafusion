package com.hm.manage.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.DocDocument;
import com.hm.manage.domain.KbPage;
import com.hm.manage.domain.KbPageVersion;
import com.hm.manage.domain.KbSpace;
import com.hm.manage.domain.bo.KbFolderSaveBo;
import com.hm.manage.domain.bo.KbPageSaveBo;
import com.hm.manage.domain.bo.KbPageStatusBo;
import com.hm.manage.domain.bo.KbSpaceSaveBo;
import com.hm.manage.domain.bo.KbVersionRestoreBo;
import com.hm.manage.domain.vo.KbDocumentCandidateVo;
import com.hm.manage.domain.vo.KbDocumentLinkVo;
import com.hm.manage.domain.vo.KbPageDetailVo;
import com.hm.manage.domain.vo.KbPageSearchVo;
import com.hm.manage.domain.vo.KbPageTreeVo;
import com.hm.manage.domain.vo.KbVersionDetailVo;
import com.hm.manage.mapper.KnowledgeCenterMapper;
import com.hm.manage.service.IDocumentWorkspaceService;
import com.hm.manage.service.IKnowledgeCenterService;
import com.hm.manage.service.knowledge.KnowledgeHtmlSanitizer;

@Service
public class KnowledgeCenterServiceImpl implements IKnowledgeCenterService
{
    private static final String PAGE_TYPE_FOLDER = "FOLDER";
    private static final String PAGE_TYPE_ARTICLE = "ARTICLE";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String STATUS_TRASH = "TRASH";
    private static final Set<String> PAGE_STATUSES = Set.of(STATUS_ACTIVE, STATUS_ARCHIVED, STATUS_TRASH);
    private static final String DOCUMENT_PERMISSION = "document:file:manage";
    private static final String REMOVE_PERMISSION = "knowledge:page:remove";
    private static final int SEARCH_LIMIT = 100;
    private static final int MAX_TAGS = 8;
    private static final int MAX_DOCUMENTS = 20;

    @Autowired
    private KnowledgeCenterMapper mapper;

    @Autowired
    private IDocumentWorkspaceService documentWorkspaceService;

    @Autowired
    private KnowledgeHtmlSanitizer htmlSanitizer;

    @Override
    public List<KbSpace> listSpaces()
    {
        return mapper.selectSpaceList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbSpace createSpace(KbSpaceSaveBo input)
    {
        requireInput(input, "知识空间参数不能为空");
        String name = normalizeRequired(input.getSpaceName(), 100, "知识空间名称");
        if (mapper.countSpaceName(name, null) > 0)
        {
            throw new ServiceException("知识空间名称已存在");
        }
        String operator = SecurityUtils.getUsername();
        KbSpace space = new KbSpace();
        space.setSpaceName(name);
        space.setDescription(normalizeOptional(input.getDescription(), 500, "知识空间说明"));
        space.setSortOrder(input.getSortOrder() == null
            ? valueOrDefault(mapper.selectNextSpaceSortOrder(), 10) : input.getSortOrder());
        space.setStatus("0");
        space.setCreateBy(operator);
        space.setUpdateBy(operator);
        if (mapper.insertSpace(space) != 1)
        {
            throw new ServiceException("知识空间创建失败");
        }
        return mapper.selectSpaceById(space.getSpaceId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbSpace updateSpace(Long spaceId, KbSpaceSaveBo input)
    {
        requireInput(input, "知识空间参数不能为空");
        KbSpace current = requireSpace(spaceId);
        String name = normalizeRequired(input.getSpaceName(), 100, "知识空间名称");
        if (mapper.countSpaceName(name, spaceId) > 0)
        {
            throw new ServiceException("知识空间名称已存在");
        }
        current.setSpaceName(name);
        current.setDescription(normalizeOptional(input.getDescription(), 500, "知识空间说明"));
        current.setSortOrder(input.getSortOrder() == null ? current.getSortOrder() : input.getSortOrder());
        current.setUpdateBy(SecurityUtils.getUsername());
        if (mapper.updateSpace(current) != 1)
        {
            throw new ServiceException("知识空间保存失败");
        }
        return mapper.selectSpaceById(spaceId);
    }

    @Override
    public List<KbPageTreeVo> listPageTree(Long spaceId, String lifecycleStatus)
    {
        requireSpace(spaceId);
        String normalizedStatus = normalizeLifecycle(lifecycleStatus, STATUS_ACTIVE);
        if (STATUS_TRASH.equals(normalizedStatus) && !SecurityUtils.hasPermi(REMOVE_PERMISSION))
        {
            throw new ServiceException("当前用户没有知识回收站访问权限");
        }
        return mapper.selectPageTree(spaceId, normalizedStatus);
    }

    @Override
    public List<KbPageSearchVo> searchPages(Long spaceId, String keyword)
    {
        if (spaceId != null)
        {
            requireSpace(spaceId);
        }
        String normalized = normalizeOptional(keyword, 100, "搜索内容");
        if (StringUtils.isBlank(normalized))
        {
            return List.of();
        }
        return mapper.searchPages(spaceId, normalized, SEARCH_LIMIT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbPage createFolder(KbFolderSaveBo input)
    {
        requireInput(input, "知识目录参数不能为空");
        requireSpace(input.getSpaceId());
        Long parentId = normalizeParent(input.getSpaceId(), input.getParentId(), null);
        String title = normalizeRequired(input.getTitle(), 100, "目录名称");
        ensureUniqueSibling(input.getSpaceId(), parentId, title, null);
        String operator = SecurityUtils.getUsername();
        KbPage folder = new KbPage();
        folder.setSpaceId(input.getSpaceId());
        folder.setParentId(parentId);
        folder.setPageType(PAGE_TYPE_FOLDER);
        folder.setTitle(title);
        folder.setSummary("");
        folder.setContent("");
        folder.setSortOrder(input.getSortOrder() == null
            ? valueOrDefault(mapper.selectNextPageSortOrder(input.getSpaceId(), parentId), 10)
            : input.getSortOrder());
        folder.setContentVersion(0);
        folder.setLifecycleStatus(STATUS_ACTIVE);
        folder.setCreateUserId(SecurityUtils.getUserId());
        folder.setUpdateUserId(SecurityUtils.getUserId());
        folder.setCreateBy(operator);
        folder.setUpdateBy(operator);
        if (mapper.insertPage(folder) != 1)
        {
            throw new ServiceException("知识目录创建失败");
        }
        return mapper.selectPageById(folder.getPageId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbPage updateFolder(Long folderId, KbFolderSaveBo input)
    {
        requireInput(input, "知识目录参数不能为空");
        KbPage current = requireFolder(folderId);
        requireSpace(input.getSpaceId());
        if (!current.getSpaceId().equals(input.getSpaceId()))
        {
            throw new ServiceException("知识目录不能跨空间移动");
        }
        Long parentId = normalizeParent(input.getSpaceId(), input.getParentId(), folderId);
        String title = normalizeRequired(input.getTitle(), 100, "目录名称");
        ensureUniqueSibling(input.getSpaceId(), parentId, title, folderId);
        current.setSpaceId(input.getSpaceId());
        current.setParentId(parentId);
        current.setTitle(title);
        current.setSortOrder(input.getSortOrder() == null ? current.getSortOrder() : input.getSortOrder());
        current.setUpdateUserId(SecurityUtils.getUserId());
        current.setUpdateBy(SecurityUtils.getUsername());
        if (mapper.updateFolder(current) != 1)
        {
            throw new ServiceException("知识目录保存失败");
        }
        return mapper.selectPageById(folderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFolder(Long folderId)
    {
        requireFolder(folderId);
        if (mapper.countPageChildren(folderId) > 0)
        {
            throw new ServiceException("知识目录中仍有内容，无法删除");
        }
        if (mapper.deleteFolder(folderId) != 1)
        {
            throw new ServiceException("知识目录删除失败");
        }
    }

    @Override
    public KbPageDetailVo getPage(Long pageId)
    {
        return buildDetail(requireArticle(pageId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbPageDetailVo createPage(KbPageSaveBo input)
    {
        requireInput(input, "知识参数不能为空");
        requireSpace(input.getSpaceId());
        Long parentId = normalizeParent(input.getSpaceId(), input.getParentId(), null);
        String title = normalizeRequired(input.getTitle(), 160, "知识标题");
        ensureUniqueSibling(input.getSpaceId(), parentId, title, null);
        List<String> tags = normalizeTags(input.getTagNames());
        List<Long> documentIds = validateDocumentIds(input.getDocumentIds(), null);
        String content = normalizeAndSanitizeContent(input.getContent());
        String operator = SecurityUtils.getUsername();
        Long userId = SecurityUtils.getUserId();
        KbPage page = new KbPage();
        page.setSpaceId(input.getSpaceId());
        page.setParentId(parentId);
        page.setPageType(PAGE_TYPE_ARTICLE);
        page.setTitle(title);
        page.setSummary(normalizeOptional(input.getSummary(), 500, "知识摘要"));
        page.setContent(content);
        page.setSortOrder(valueOrDefault(mapper.selectNextPageSortOrder(input.getSpaceId(), parentId), 10));
        page.setContentVersion(1);
        page.setLifecycleStatus(STATUS_ACTIVE);
        page.setCreateUserId(userId);
        page.setUpdateUserId(userId);
        page.setCreateBy(operator);
        page.setUpdateBy(operator);
        if (mapper.insertPage(page) != 1)
        {
            throw new ServiceException("知识创建失败");
        }
        replaceTags(page.getPageId(), tags, operator);
        replaceDocuments(page.getPageId(), documentIds, operator);
        insertVersion(page, tags, documentIds, "CREATE", List.of("CREATE"),
            defaultNote(input.getChangeNote(), "创建知识"), null);
        return buildDetail(mapper.selectPageById(page.getPageId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbPageDetailVo updatePage(Long pageId, KbPageSaveBo input)
    {
        requireInput(input, "知识参数不能为空");
        KbPage current = requireArticleForUpdate(pageId);
        requireWritableLifecycle(current);
        assertExpectedVersion(current, input.getExpectedVersion());
        requireSpace(input.getSpaceId());
        Long parentId = normalizeParent(input.getSpaceId(), input.getParentId(), pageId);
        String title = normalizeRequired(input.getTitle(), 160, "知识标题");
        ensureUniqueSibling(input.getSpaceId(), parentId, title, pageId);
        List<String> previousTags = mapper.selectPageTags(pageId);
        List<Long> previousDocuments = mapper.selectPageDocumentIds(pageId);
        List<String> tags = normalizeTags(input.getTagNames());
        List<Long> documentIds = validateDocumentIds(input.getDocumentIds(), previousDocuments);
        String summary = normalizeOptional(input.getSummary(), 500, "知识摘要");
        String content = normalizeAndSanitizeContent(input.getContent());
        List<String> changes = detectChanges(current, input.getSpaceId(), parentId, title, summary, content,
            tags, documentIds, previousTags, previousDocuments);
        current.setSpaceId(input.getSpaceId());
        current.setParentId(parentId);
        current.setTitle(title);
        current.setSummary(summary);
        current.setContent(content);
        current.setContentVersion(current.getContentVersion() + 1);
        current.setUpdateUserId(SecurityUtils.getUserId());
        current.setUpdateBy(SecurityUtils.getUsername());
        if (mapper.updatePage(current, input.getExpectedVersion()) != 1)
        {
            throw versionConflict();
        }
        replaceTags(pageId, tags, current.getUpdateBy());
        replaceDocuments(pageId, documentIds, current.getUpdateBy());
        insertVersion(current, tags, documentIds, "UPDATE", changes.isEmpty() ? List.of("SAVE") : changes,
            defaultNote(input.getChangeNote(), changes.isEmpty() ? "保存当前内容" : "更新知识内容"), null);
        return buildDetail(mapper.selectPageById(pageId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbPageDetailVo updatePageStatus(Long pageId, KbPageStatusBo input)
    {
        requireInput(input, "知识状态参数不能为空");
        KbPage current = requireArticleForUpdate(pageId);
        assertExpectedVersion(current, input.getExpectedVersion());
        String target = normalizeLifecycle(input.getLifecycleStatus(), null);
        if (STATUS_TRASH.equals(current.getLifecycleStatus()) && !STATUS_ACTIVE.equals(target))
        {
            throw new ServiceException("回收站知识必须先恢复后才能执行其他操作");
        }
        if (target.equals(current.getLifecycleStatus()))
        {
            return buildDetail(current);
        }
        current.setLifecycleStatus(target);
        current.setContentVersion(current.getContentVersion() + 1);
        current.setUpdateUserId(SecurityUtils.getUserId());
        current.setUpdateBy(SecurityUtils.getUsername());
        if (mapper.updatePage(current, input.getExpectedVersion()) != 1)
        {
            throw versionConflict();
        }
        List<String> tags = mapper.selectPageTags(pageId);
        List<Long> documentIds = mapper.selectPageDocumentIds(pageId);
        String operation = STATUS_ARCHIVED.equals(target) ? "ARCHIVE"
            : STATUS_TRASH.equals(target) ? "TRASH" : "RESTORE_STATUS";
        insertVersion(current, tags, documentIds, operation, List.of("STATUS"),
            defaultNote(input.getChangeNote(), statusNote(target)), null);
        return buildDetail(mapper.selectPageById(pageId));
    }

    @Override
    public List<KbPageVersion> listVersions(Long pageId)
    {
        requireArticle(pageId);
        return mapper.selectPageVersions(pageId);
    }

    @Override
    public KbVersionDetailVo getVersion(Long pageId, Integer versionNo)
    {
        requireArticle(pageId);
        KbPageVersion version = mapper.selectPageVersion(pageId, versionNo);
        if (version == null)
        {
            throw new ServiceException("知识版本不存在");
        }
        KbVersionDetailVo result = new KbVersionDetailVo();
        result.setVersion(version);
        result.setTags(parseStringList(version.getSnapshotTags()));
        result.setDocumentIds(parseLongList(version.getSnapshotDocumentIds()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbPageDetailVo restoreVersion(Long pageId, Integer versionNo, KbVersionRestoreBo input)
    {
        requireInput(input, "知识版本恢复参数不能为空");
        KbPage current = requireArticleForUpdate(pageId);
        requireWritableLifecycle(current);
        assertExpectedVersion(current, input.getExpectedVersion());
        KbPageVersion snapshot = mapper.selectPageVersion(pageId, versionNo);
        if (snapshot == null)
        {
            throw new ServiceException("知识版本不存在");
        }
        requireSpace(snapshot.getSnapshotSpaceId());
        Long parentId = normalizeParent(snapshot.getSnapshotSpaceId(), snapshot.getSnapshotParentId(), pageId);
        ensureUniqueSibling(snapshot.getSnapshotSpaceId(), parentId,
            normalizeRequired(snapshot.getSnapshotTitle(), 160, "知识标题"), pageId);
        List<String> previousTags = mapper.selectPageTags(pageId);
        List<Long> previousDocuments = mapper.selectPageDocumentIds(pageId);
        List<String> tags = normalizeTags(parseStringList(snapshot.getSnapshotTags()));
        List<Long> documentIds = validateDocumentIds(parseLongList(snapshot.getSnapshotDocumentIds()),
            previousDocuments);
        String title = normalizeRequired(snapshot.getSnapshotTitle(), 160, "知识标题");
        String summary = normalizeOptional(snapshot.getSnapshotSummary(), 500, "知识摘要");
        String content = normalizeAndSanitizeContent(snapshot.getSnapshotContent());
        List<String> changes = detectChanges(current, snapshot.getSnapshotSpaceId(), parentId, title,
            summary, content, tags, documentIds, previousTags, previousDocuments);
        current.setSpaceId(snapshot.getSnapshotSpaceId());
        current.setParentId(parentId);
        current.setTitle(title);
        current.setSummary(summary);
        current.setContent(content);
        current.setContentVersion(current.getContentVersion() + 1);
        current.setUpdateUserId(SecurityUtils.getUserId());
        current.setUpdateBy(SecurityUtils.getUsername());
        if (mapper.updatePage(current, input.getExpectedVersion()) != 1)
        {
            throw versionConflict();
        }
        replaceTags(pageId, tags, current.getUpdateBy());
        replaceDocuments(pageId, documentIds, current.getUpdateBy());
        insertVersion(current, tags, documentIds, "RESTORE_VERSION",
            changes.isEmpty() ? List.of("SAVE") : changes,
            defaultNote(input.getChangeNote(), "从V" + versionNo + "恢复并创建新版本"), versionNo);
        return buildDetail(mapper.selectPageById(pageId));
    }

    @Override
    public List<KbDocumentCandidateVo> listDocumentCandidates(String keyword)
    {
        requireDocumentModulePermission();
        String normalized = normalizeOptional(keyword, 100, "文档搜索内容");
        Map<Long, DocDocument> documents = new LinkedHashMap<>();
        documentWorkspaceService.listDocuments("MY", null, normalized, null, null)
            .forEach(document -> documents.put(document.getDocumentId(), document));
        documentWorkspaceService.listDocuments("SHARED", null, normalized, null, null)
            .forEach(document -> documents.putIfAbsent(document.getDocumentId(), document));
        return documents.values().stream()
            .filter(document -> !STATUS_TRASH.equals(document.getLifecycleStatus()))
            .sorted(Comparator.comparing(DocDocument::getUpdateTime,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(SEARCH_LIMIT)
            .map(this::toCandidate)
            .toList();
    }

    private KbPageDetailVo buildDetail(KbPage page)
    {
        if (page == null)
        {
            throw new ServiceException("知识不存在");
        }
        KbPageDetailVo detail = new KbPageDetailVo();
        detail.setPage(page);
        detail.setTags(List.copyOf(mapper.selectPageTags(page.getPageId())));
        detail.setDocuments(resolveDocumentLinks(page.getPageId()));
        return detail;
    }

    private List<KbDocumentLinkVo> resolveDocumentLinks(Long pageId)
    {
        List<Long> documentIds = mapper.selectPageDocumentIds(pageId);
        List<KbDocumentLinkVo> result = new ArrayList<>();
        boolean moduleAllowed = SecurityUtils.hasPermi(DOCUMENT_PERMISSION);
        for (int index = 0; index < documentIds.size(); index++)
        {
            Long documentId = documentIds.get(index);
            KbDocumentLinkVo link = new KbDocumentLinkVo();
            link.setDocumentId(documentId);
            link.setSortOrder(index * 10);
            if (!moduleAllowed)
            {
                link.setTitle("无文档访问权限");
                link.setAccessStatus("NO_MODULE_PERMISSION");
                result.add(link);
                continue;
            }
            try
            {
                DocDocument document = documentWorkspaceService.getDocument(documentId);
                populateDocumentLink(link, document);
                link.setAccessStatus(STATUS_TRASH.equals(document.getLifecycleStatus()) ? "TRASH"
                    : STATUS_ARCHIVED.equals(document.getLifecycleStatus()) ? "ARCHIVED" : "AVAILABLE");
            }
            catch (ServiceException exception)
            {
                link.setTitle("文档不存在或无权访问");
                link.setAccessStatus("NO_ACCESS");
            }
            result.add(link);
        }
        return List.copyOf(result);
    }

    private void populateDocumentLink(KbDocumentLinkVo link, DocDocument document)
    {
        link.setTitle(document.getTitle());
        link.setFileType(document.getFileType());
        link.setDocumentType(document.getDocumentType());
        link.setFileSize(document.getFileSize());
        link.setContentVersion(document.getContentVersion());
        link.setLifecycleStatus(document.getLifecycleStatus());
        link.setAccessPermission(document.getAccessPermission());
    }

    private KbDocumentCandidateVo toCandidate(DocDocument document)
    {
        KbDocumentCandidateVo candidate = new KbDocumentCandidateVo();
        candidate.setDocumentId(document.getDocumentId());
        candidate.setTitle(document.getTitle());
        candidate.setFileType(document.getFileType());
        candidate.setFileSize(document.getFileSize());
        candidate.setLifecycleStatus(document.getLifecycleStatus());
        candidate.setAccessPermission(document.getAccessPermission());
        candidate.setOwnerName(document.getOwnerName());
        candidate.setFolderName(document.getFolderName());
        candidate.setUpdateTime(document.getUpdateTime());
        return candidate;
    }

    private KbSpace requireSpace(Long spaceId)
    {
        if (spaceId == null || spaceId <= 0L)
        {
            throw new ServiceException("知识空间不能为空");
        }
        KbSpace space = mapper.selectSpaceById(spaceId);
        if (space == null || !"0".equals(space.getStatus()))
        {
            throw new ServiceException("知识空间不存在或已停用");
        }
        return space;
    }

    private KbPage requirePage(Long pageId)
    {
        if (pageId == null || pageId <= 0L)
        {
            throw new ServiceException("知识ID不能为空");
        }
        KbPage page = mapper.selectPageById(pageId);
        if (page == null)
        {
            throw new ServiceException("知识不存在");
        }
        return page;
    }

    private KbPage requireArticle(Long pageId)
    {
        KbPage page = requirePage(pageId);
        if (!PAGE_TYPE_ARTICLE.equals(page.getPageType()))
        {
            throw new ServiceException("当前节点不是知识文章");
        }
        if (STATUS_TRASH.equals(page.getLifecycleStatus()) && !SecurityUtils.hasPermi(REMOVE_PERMISSION))
        {
            throw new ServiceException("知识不存在或当前用户无权访问");
        }
        return page;
    }

    private void requireWritableLifecycle(KbPage page)
    {
        if (STATUS_TRASH.equals(page.getLifecycleStatus()))
        {
            throw new ServiceException("回收站知识必须先恢复后才能修改");
        }
    }

    private KbPage requireArticleForUpdate(Long pageId)
    {
        KbPage page = mapper.selectPageForUpdate(pageId);
        if (page == null || !PAGE_TYPE_ARTICLE.equals(page.getPageType()))
        {
            throw new ServiceException("知识不存在");
        }
        return page;
    }

    private KbPage requireFolder(Long folderId)
    {
        KbPage page = requirePage(folderId);
        if (!PAGE_TYPE_FOLDER.equals(page.getPageType()))
        {
            throw new ServiceException("当前节点不是知识目录");
        }
        return page;
    }

    private Long normalizeParent(Long spaceId, Long requestedParentId, Long currentPageId)
    {
        Long parentId = requestedParentId == null ? 0L : requestedParentId;
        if (parentId <= 0L)
        {
            return 0L;
        }
        if (currentPageId != null && parentId.equals(currentPageId))
        {
            throw new ServiceException("知识节点不能作为自己的上级目录");
        }
        KbPage parent = requireFolder(parentId);
        if (!spaceId.equals(parent.getSpaceId()) || !STATUS_ACTIVE.equals(parent.getLifecycleStatus()))
        {
            throw new ServiceException("上级知识目录无效");
        }
        if (currentPageId != null && isDescendant(parentId, currentPageId))
        {
            throw new ServiceException("不能将知识目录移动到自己的下级目录");
        }
        return parentId;
    }

    private boolean isDescendant(Long candidateParentId, Long currentPageId)
    {
        Long cursor = candidateParentId;
        Set<Long> visited = new LinkedHashSet<>();
        for (int depth = 0; cursor != null && cursor > 0L && depth < 64; depth++)
        {
            if (!visited.add(cursor))
            {
                throw new ServiceException("知识目录层级存在循环，请先修复目录结构");
            }
            if (cursor.equals(currentPageId))
            {
                return true;
            }
            KbPage node = mapper.selectPageById(cursor);
            cursor = node == null ? 0L : node.getParentId();
        }
        if (cursor != null && cursor > 0L)
        {
            throw new ServiceException("知识目录层级超过64级，无法继续移动");
        }
        return false;
    }

    private void ensureUniqueSibling(Long spaceId, Long parentId, String title, Long excludePageId)
    {
        if (mapper.countSiblingTitle(spaceId, parentId, title, excludePageId) > 0)
        {
            throw new ServiceException("同一目录下已存在同名知识节点");
        }
    }

    private List<String> normalizeTags(List<String> input)
    {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (input != null)
        {
            for (String value : input)
            {
                String tag = normalizeOptional(value, 40, "标签名称");
                if (StringUtils.isNotBlank(tag))
                {
                    tags.add(tag);
                }
            }
        }
        if (tags.size() > MAX_TAGS)
        {
            throw new ServiceException("单篇知识最多设置" + MAX_TAGS + "个标签");
        }
        return List.copyOf(tags);
    }

    private List<Long> validateDocumentIds(List<Long> input, List<Long> unchangedIds)
    {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (input != null)
        {
            for (Long documentId : input)
            {
                if (documentId == null || documentId <= 0L)
                {
                    throw new ServiceException("关联文档包含无效ID");
                }
                ids.add(documentId);
            }
        }
        if (ids.size() > MAX_DOCUMENTS)
        {
            throw new ServiceException("单篇知识最多关联" + MAX_DOCUMENTS + "份文档");
        }
        List<Long> normalizedIds = List.copyOf(ids);
        if (unchangedIds != null && normalizedIds.equals(unchangedIds))
        {
            return normalizedIds;
        }
        if (!ids.isEmpty())
        {
            requireDocumentModulePermission();
            for (Long documentId : ids)
            {
                DocDocument document = documentWorkspaceService.getDocument(documentId);
                if (STATUS_TRASH.equals(document.getLifecycleStatus()))
                {
                    throw new ServiceException("回收站文档不能关联到知识：" + document.getTitle());
                }
            }
        }
        return normalizedIds;
    }

    private void requireDocumentModulePermission()
    {
        if (!SecurityUtils.hasPermi(DOCUMENT_PERMISSION))
        {
            throw new ServiceException("当前用户没有文档管理访问权限");
        }
    }

    private void replaceTags(Long pageId, List<String> tags, String operator)
    {
        mapper.deletePageTags(pageId);
        for (String tag : tags)
        {
            mapper.insertTag(tag, operator);
            Long tagId = mapper.selectTagId(tag);
            if (tagId == null || mapper.insertPageTag(pageId, tagId) != 1)
            {
                throw new ServiceException("知识标签保存失败");
            }
        }
    }

    private void replaceDocuments(Long pageId, List<Long> documentIds, String operator)
    {
        mapper.deletePageDocuments(pageId);
        for (int index = 0; index < documentIds.size(); index++)
        {
            if (mapper.insertPageDocument(pageId, documentIds.get(index), index * 10, operator) != 1)
            {
                throw new ServiceException("关联文档保存失败");
            }
        }
    }

    private void insertVersion(KbPage page, List<String> tags, List<Long> documentIds,
        String operationType, List<String> changeFields, String changeNote, Integer restoredFromVersion)
    {
        KbPageVersion version = new KbPageVersion();
        version.setPageId(page.getPageId());
        version.setVersionNo(page.getContentVersion());
        version.setSnapshotSpaceId(page.getSpaceId());
        version.setSnapshotParentId(page.getParentId());
        version.setSnapshotTitle(page.getTitle());
        version.setSnapshotSummary(page.getSummary());
        version.setSnapshotContent(page.getContent());
        version.setSnapshotLifecycleStatus(page.getLifecycleStatus());
        version.setSnapshotTags(JSON.toJSONString(tags));
        version.setSnapshotDocumentIds(JSON.toJSONString(documentIds));
        version.setOperationType(operationType);
        version.setChangeFields(String.join(",", changeFields));
        version.setChangeNote(changeNote);
        version.setContentChecksum(checksum(page, tags, documentIds));
        version.setOperatorId(SecurityUtils.getUserId());
        version.setOperatorName(currentDisplayName());
        version.setRestoredFromVersion(restoredFromVersion);
        if (mapper.insertPageVersion(version) != 1)
        {
            throw new ServiceException("知识修改记录保存失败");
        }
    }

    private List<String> detectChanges(KbPage current, Long spaceId, Long parentId, String title,
        String summary, String content, List<String> tags, List<Long> documentIds,
        List<String> previousTags, List<Long> previousDocuments)
    {
        List<String> changes = new ArrayList<>();
        if (!title.equals(current.getTitle())) changes.add("TITLE");
        if (!summary.equals(StringUtils.defaultString(current.getSummary()))) changes.add("SUMMARY");
        if (!content.equals(StringUtils.defaultString(current.getContent()))) changes.add("CONTENT");
        if (!spaceId.equals(current.getSpaceId()) || !parentId.equals(current.getParentId())) changes.add("DIRECTORY");
        if (!tags.equals(previousTags)) changes.add("TAGS");
        if (!documentIds.equals(previousDocuments)) changes.add("DOCUMENTS");
        return changes;
    }

    private void assertExpectedVersion(KbPage current, Integer expectedVersion)
    {
        if (expectedVersion == null || !expectedVersion.equals(current.getContentVersion()))
        {
            throw versionConflict();
        }
    }

    private ServiceException versionConflict()
    {
        return new ServiceException("知识已被其他用户更新，请重新加载最新版本后再保存");
    }

    private String normalizeAndSanitizeContent(String content)
    {
        String normalized = normalizeRequired(content, 2_000_000, "知识正文");
        String sanitized = htmlSanitizer.sanitize(normalized);
        if (StringUtils.isBlank(sanitized))
        {
            throw new ServiceException("知识正文清洗后为空，请检查内容");
        }
        return sanitized;
    }

    private String normalizeLifecycle(String value, String fallback)
    {
        String normalized = StringUtils.trimToEmpty(value).toUpperCase(Locale.ROOT);
        if (StringUtils.isBlank(normalized))
        {
            normalized = fallback;
        }
        if (!PAGE_STATUSES.contains(normalized))
        {
            throw new ServiceException("知识状态无效");
        }
        return normalized;
    }

    private String normalizeRequired(String value, int maxLength, String label)
    {
        String normalized = StringUtils.trimToEmpty(value);
        if (StringUtils.isBlank(normalized))
        {
            throw new ServiceException(label + "不能为空");
        }
        if (normalized.length() > maxLength)
        {
            throw new ServiceException(label + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String label)
    {
        String normalized = StringUtils.trimToEmpty(value);
        if (normalized.length() > maxLength)
        {
            throw new ServiceException(label + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String defaultNote(String requested, String fallback)
    {
        String normalized = normalizeOptional(requested, 500, "修改说明");
        return StringUtils.isBlank(normalized) ? fallback : normalized;
    }

    private String statusNote(String status)
    {
        return STATUS_ARCHIVED.equals(status) ? "归档知识"
            : STATUS_TRASH.equals(status) ? "移入回收站" : "恢复知识";
    }

    private String currentDisplayName()
    {
        var loginUser = SecurityUtils.getLoginUser();
        if (loginUser.getUser() != null && StringUtils.isNotBlank(loginUser.getUser().getNickName()))
        {
            return loginUser.getUser().getNickName();
        }
        return SecurityUtils.getUsername();
    }

    private String checksum(KbPage page, List<String> tags, List<Long> documentIds)
    {
        String payload = String.join("\n",
            StringUtils.defaultString(page.getTitle()),
            StringUtils.defaultString(page.getSummary()),
            StringUtils.defaultString(page.getContent()),
            StringUtils.defaultString(page.getLifecycleStatus()),
            JSON.toJSONString(tags), JSON.toJSONString(documentIds));
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private List<String> parseStringList(String json)
    {
        return StringUtils.isBlank(json) ? List.of() : List.copyOf(JSON.parseArray(json, String.class));
    }

    private List<Long> parseLongList(String json)
    {
        return StringUtils.isBlank(json) ? List.of() : List.copyOf(JSON.parseArray(json, Long.class));
    }

    private int valueOrDefault(Integer value, int fallback)
    {
        return value == null ? fallback : value;
    }

    private void requireInput(Object input, String message)
    {
        if (input == null)
        {
            throw new ServiceException(message);
        }
    }
}
