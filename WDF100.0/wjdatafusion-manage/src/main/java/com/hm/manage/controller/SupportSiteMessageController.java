package com.hm.manage.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.core.page.TableDataInfo;
import com.hm.common.enums.BusinessType;
import com.hm.manage.domain.SupportSiteMessage;
import com.hm.manage.service.ISupportSiteMessageService;

@RestController
@RequestMapping("/support/siteMessage")
public class SupportSiteMessageController extends BaseController
{
    @Autowired
    private ISupportSiteMessageService messageService;

    @PreAuthorize("@ss.hasPermi('support:message:list')")
    @GetMapping("/list")
    public TableDataInfo list(SupportSiteMessage message)
    {
        startPage();
        List<SupportSiteMessage> list = messageService.selectSupportSiteMessageList(message);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:message:list')")
    @GetMapping("/latest")
    public AjaxResult latest(@RequestParam(required = false) Long siteId,
                             @RequestParam(required = false) Long afterMessageId,
                             @RequestParam(required = false) Integer limit)
    {
        List<SupportSiteMessage> rows = messageService.selectLatestSupportSiteMessages(siteId, afterMessageId, limit);
        Long latestMessageId = afterMessageId;
        for (SupportSiteMessage row : rows)
        {
            if (row.getMessageId() != null && (latestMessageId == null || row.getMessageId() > latestMessageId))
            {
                latestMessageId = row.getMessageId();
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", rows);
        data.put("latestMessageId", latestMessageId);
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('support:message:add')")
    @Log(title = "现场留言板", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SupportSiteMessage message)
    {
        return toAjax(messageService.insertSupportSiteMessage(message));
    }
}
