package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.KbPage;
import com.hm.manage.domain.KbPageVersion;
import com.hm.manage.domain.KbSpace;
import com.hm.manage.domain.vo.KbPageSearchVo;
import com.hm.manage.domain.vo.KbPageTreeVo;

public interface KnowledgeCenterMapper
{
    List<KbSpace> selectSpaceList();

    KbSpace selectSpaceById(@Param("spaceId") Long spaceId);

    int countSpaceName(@Param("spaceName") String spaceName, @Param("excludeSpaceId") Long excludeSpaceId);

    Integer selectNextSpaceSortOrder();

    int insertSpace(KbSpace space);

    int updateSpace(KbSpace space);

    List<KbPageTreeVo> selectPageTree(@Param("spaceId") Long spaceId,
        @Param("lifecycleStatus") String lifecycleStatus);

    List<KbPageSearchVo> searchPages(@Param("spaceId") Long spaceId, @Param("keyword") String keyword,
        @Param("limit") int limit);

    KbPage selectPageById(@Param("pageId") Long pageId);

    KbPage selectPageForUpdate(@Param("pageId") Long pageId);

    int countPageChildren(@Param("pageId") Long pageId);

    int countSiblingTitle(@Param("spaceId") Long spaceId, @Param("parentId") Long parentId,
        @Param("title") String title, @Param("excludePageId") Long excludePageId);

    Integer selectNextPageSortOrder(@Param("spaceId") Long spaceId, @Param("parentId") Long parentId);

    int insertPage(KbPage page);

    int updatePage(@Param("page") KbPage page, @Param("expectedVersion") Integer expectedVersion);

    int updateFolder(KbPage page);

    int deleteFolder(@Param("pageId") Long pageId);

    List<String> selectPageTags(@Param("pageId") Long pageId);

    int deletePageTags(@Param("pageId") Long pageId);

    int insertTag(@Param("tagName") String tagName, @Param("operator") String operator);

    Long selectTagId(@Param("tagName") String tagName);

    int insertPageTag(@Param("pageId") Long pageId, @Param("tagId") Long tagId);

    List<Long> selectPageDocumentIds(@Param("pageId") Long pageId);

    int deletePageDocuments(@Param("pageId") Long pageId);

    int insertPageDocument(@Param("pageId") Long pageId, @Param("documentId") Long documentId,
        @Param("sortOrder") int sortOrder, @Param("operator") String operator);

    int insertPageVersion(KbPageVersion version);

    List<KbPageVersion> selectPageVersions(@Param("pageId") Long pageId);

    KbPageVersion selectPageVersion(@Param("pageId") Long pageId, @Param("versionNo") Integer versionNo);
}
