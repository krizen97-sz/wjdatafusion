package com.hm.manage.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.page.TableDataInfo;
import com.hm.manage.domain.SupportChangeLog;
import com.hm.manage.service.ISupportChangeLogService;

@RestController
@RequestMapping("/support/changeLog")
public class SupportChangeLogController extends BaseController
{
    @Autowired
    private ISupportChangeLogService changeLogService;

    @PreAuthorize("@ss.hasPermi('support:site:query')")
    @GetMapping("/list")
    public TableDataInfo list(SupportChangeLog changeLog)
    {
        startPage();
        List<SupportChangeLog> list = changeLogService.selectSupportChangeLogList(changeLog);
        return getDataTable(list);
    }
}
