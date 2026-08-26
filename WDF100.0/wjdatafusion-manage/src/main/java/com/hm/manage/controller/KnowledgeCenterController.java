package com.hm.manage.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.manage.domain.bo.KbFolderSaveBo;
import com.hm.manage.domain.bo.KbPageSaveBo;
import com.hm.manage.domain.bo.KbPageStatusBo;
import com.hm.manage.domain.bo.KbSpaceSaveBo;
import com.hm.manage.domain.bo.KbVersionRestoreBo;
import com.hm.manage.service.IKnowledgeCenterService;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeCenterController extends BaseController
{
    @Autowired
    private IKnowledgeCenterService knowledgeCenterService;

    @PreAuthorize("@ss.hasPermi('knowledge:page:list')")
    @GetMapping("/spaces")
    public AjaxResult spaces()
    {
        return success(knowledgeCenterService.listSpaces());
    }

    @PreAuthorize("@ss.hasPermi('knowledge:space:manage')")
    @Log(title = "知识中心-空间", businessType = BusinessType.INSERT)
    @PostMapping("/spaces")
    public AjaxResult createSpace(@Valid @RequestBody KbSpaceSaveBo input)
    {
        return success(knowledgeCenterService.createSpace(input));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:space:manage')")
    @Log(title = "知识中心-空间", businessType = BusinessType.UPDATE)
    @PutMapping("/spaces/{spaceId}")
    public AjaxResult updateSpace(@PathVariable Long spaceId, @Valid @RequestBody KbSpaceSaveBo input)
    {
        return success(knowledgeCenterService.updateSpace(spaceId, input));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:page:list')")
    @GetMapping("/pages/tree")
    public AjaxResult pageTree(@RequestParam Long spaceId,
        @RequestParam(required = false) String lifecycleStatus)
    {
        return success(knowledgeCenterService.listPageTree(spaceId, lifecycleStatus));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:page:list')")
    @GetMapping("/pages/search")
    public AjaxResult searchPages(@RequestParam(required = false) Long spaceId,
        @RequestParam String keyword)
    {
        return success(knowledgeCenterService.searchPages(spaceId, keyword));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:space:manage')")
    @Log(title = "知识中心-目录", businessType = BusinessType.INSERT)
    @PostMapping("/folders")
    public AjaxResult createFolder(@Valid @RequestBody KbFolderSaveBo input)
    {
        return success(knowledgeCenterService.createFolder(input));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:space:manage')")
    @Log(title = "知识中心-目录", businessType = BusinessType.UPDATE)
    @PutMapping("/folders/{folderId}")
    public AjaxResult updateFolder(@PathVariable Long folderId, @Valid @RequestBody KbFolderSaveBo input)
    {
        return success(knowledgeCenterService.updateFolder(folderId, input));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:space:manage')")
    @Log(title = "知识中心-目录", businessType = BusinessType.DELETE)
    @DeleteMapping("/folders/{folderId}")
    public AjaxResult removeFolder(@PathVariable Long folderId)
    {
        knowledgeCenterService.removeFolder(folderId);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('knowledge:page:list')")
    @GetMapping("/pages/{pageId}")
    public AjaxResult page(@PathVariable Long pageId)
    {
        return success(knowledgeCenterService.getPage(pageId));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:page:write')")
    @Log(title = "知识中心-文章", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/pages")
    public AjaxResult createPage(@Valid @RequestBody KbPageSaveBo input)
    {
        return success(knowledgeCenterService.createPage(input));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:page:write')")
    @Log(title = "知识中心-文章", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/pages/{pageId}")
    public AjaxResult updatePage(@PathVariable Long pageId, @Valid @RequestBody KbPageSaveBo input)
    {
        return success(knowledgeCenterService.updatePage(pageId, input));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:page:write')")
    @Log(title = "知识中心-归档", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/pages/{pageId}/archive")
    public AjaxResult archivePage(@PathVariable Long pageId, @Valid @RequestBody KbVersionRestoreBo input)
    {
        return success(knowledgeCenterService.updatePageStatus(pageId, statusInput("ARCHIVED", input)));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:page:remove')")
    @Log(title = "知识中心-回收站", businessType = BusinessType.DELETE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/pages/{pageId}/trash")
    public AjaxResult trashPage(@PathVariable Long pageId, @Valid @RequestBody KbVersionRestoreBo input)
    {
        return success(knowledgeCenterService.updatePageStatus(pageId, statusInput("TRASH", input)));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:page:remove')")
    @Log(title = "知识中心-恢复", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/pages/{pageId}/restore")
    public AjaxResult restorePage(@PathVariable Long pageId, @Valid @RequestBody KbVersionRestoreBo input)
    {
        return success(knowledgeCenterService.updatePageStatus(pageId, statusInput("ACTIVE", input)));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:page:list')")
    @GetMapping("/pages/{pageId}/versions")
    public AjaxResult versions(@PathVariable Long pageId)
    {
        return success(knowledgeCenterService.listVersions(pageId));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:page:list')")
    @GetMapping("/pages/{pageId}/versions/{versionNo}")
    public AjaxResult version(@PathVariable Long pageId, @PathVariable Integer versionNo)
    {
        return success(knowledgeCenterService.getVersion(pageId, versionNo));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:page:write')")
    @Log(title = "知识中心-版本恢复", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/pages/{pageId}/versions/{versionNo}/restore")
    public AjaxResult restoreVersion(@PathVariable Long pageId, @PathVariable Integer versionNo,
        @Valid @RequestBody KbVersionRestoreBo input)
    {
        return success(knowledgeCenterService.restoreVersion(pageId, versionNo, input));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:page:write') and @ss.hasPermi('document:file:manage')")
    @GetMapping("/document-candidates")
    public AjaxResult documentCandidates(@RequestParam(required = false) String keyword)
    {
        return success(knowledgeCenterService.listDocumentCandidates(keyword));
    }

    private KbPageStatusBo statusInput(String status, KbVersionRestoreBo source)
    {
        KbPageStatusBo input = new KbPageStatusBo();
        input.setLifecycleStatus(status);
        input.setExpectedVersion(source.getExpectedVersion());
        input.setChangeNote(source.getChangeNote());
        return input;
    }
}
