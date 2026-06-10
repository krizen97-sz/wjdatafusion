package com.hm.manage.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.core.page.TableDataInfo;
import com.hm.common.enums.BusinessType;
import com.hm.common.utils.poi.ExcelUtil;
import com.hm.manage.domain.SupportContact;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportContactService;

@RestController
@RequestMapping("/support/contact")
public class SupportContactController extends BaseController
{
    @Autowired
    private ISupportContactService contactService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @PreAuthorize("@ss.hasPermi('support:org:list')")
    @GetMapping("/list")
    public TableDataInfo list(SupportContact contact)
    {
        startPage();
        List<SupportContact> list = contactService.selectSupportContactList(contact);
        changeLogService.recordQuery(null, "CONTACT", null, null, "查询人员列表");
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:org:export')")
    @Log(title = "联系人管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SupportContact contact)
    {
        List<SupportContact> list = contactService.selectSupportContactList(contact);
        ExcelUtil<SupportContact> util = new ExcelUtil<>(SupportContact.class);
        util.exportExcel(response, list, "联系人数据");
    }

    @PreAuthorize("@ss.hasPermi('support:org:query')")
    @GetMapping(value = "/{contactId}")
    public AjaxResult getInfo(@PathVariable("contactId") Long contactId)
    {
        SupportContact contact = contactService.selectSupportContactByContactId(contactId);
        changeLogService.recordQuery(null, "CONTACT", contactId, contact == null ? null : contact.getContactName(), "查询人员详情");
        return success(contact);
    }

    @PreAuthorize("@ss.hasPermi('support:org:add')")
    @Log(title = "联系人管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SupportContact contact)
    {
        return toAjax(contactService.insertSupportContact(contact));
    }

    @PreAuthorize("@ss.hasPermi('support:org:edit')")
    @Log(title = "联系人管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SupportContact contact)
    {
        return toAjax(contactService.updateSupportContact(contact));
    }

    @PreAuthorize("@ss.hasPermi('support:org:remove')")
    @Log(title = "联系人管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{contactIds}")
    public AjaxResult remove(@PathVariable Long[] contactIds)
    {
        return toAjax(contactService.deleteSupportContactByContactIds(contactIds));
    }
}
