package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.KbPage;
import com.hm.manage.domain.KbPageVersion;
import com.hm.manage.domain.KbSpace;
import com.hm.manage.domain.bo.KbFolderSaveBo;
import com.hm.manage.domain.bo.KbPageSaveBo;
import com.hm.manage.domain.bo.KbPageStatusBo;
import com.hm.manage.domain.bo.KbSpaceSaveBo;
import com.hm.manage.domain.bo.KbVersionRestoreBo;
import com.hm.manage.domain.vo.KbDocumentCandidateVo;
import com.hm.manage.domain.vo.KbPageDetailVo;
import com.hm.manage.domain.vo.KbPageSearchVo;
import com.hm.manage.domain.vo.KbPageTreeVo;
import com.hm.manage.domain.vo.KbVersionDetailVo;

public interface IKnowledgeCenterService
{
    List<KbSpace> listSpaces();

    KbSpace createSpace(KbSpaceSaveBo input);

    KbSpace updateSpace(Long spaceId, KbSpaceSaveBo input);

    List<KbPageTreeVo> listPageTree(Long spaceId, String lifecycleStatus);

    List<KbPageSearchVo> searchPages(Long spaceId, String keyword);

    KbPage createFolder(KbFolderSaveBo input);

    KbPage updateFolder(Long folderId, KbFolderSaveBo input);

    void removeFolder(Long folderId);

    KbPageDetailVo getPage(Long pageId);

    KbPageDetailVo createPage(KbPageSaveBo input);

    KbPageDetailVo updatePage(Long pageId, KbPageSaveBo input);

    KbPageDetailVo updatePageStatus(Long pageId, KbPageStatusBo input);

    List<KbPageVersion> listVersions(Long pageId);

    KbVersionDetailVo getVersion(Long pageId, Integer versionNo);

    KbPageDetailVo restoreVersion(Long pageId, Integer versionNo, KbVersionRestoreBo input);

    List<KbDocumentCandidateVo> listDocumentCandidates(String keyword);
}
