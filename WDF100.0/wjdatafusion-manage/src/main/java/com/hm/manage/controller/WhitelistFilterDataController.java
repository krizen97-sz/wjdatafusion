package com.hm.manage.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.core.page.TableDataInfo;
import com.hm.common.enums.BusinessType;
import com.hm.common.utils.poi.ExcelUtil;
import com.hm.manage.domain.bo.WhitelistKafkaPublishBo;
import com.hm.manage.domain.WhitelistFilterData;
import com.hm.manage.domain.vo.WhitelistKafkaPullResultVo;
import com.hm.manage.service.IWhitelistFilterDataService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/whitelist/filterData")
public class WhitelistFilterDataController extends BaseController
{
    @Autowired
    private IWhitelistFilterDataService whitelistFilterDataService;

    @PreAuthorize("@ss.hasPermi('whitelist:filterData:list')")
    @GetMapping("/list")
    public TableDataInfo list(WhitelistFilterData whitelistFilterData)
    {
        startPage();
        List<WhitelistFilterData> list = whitelistFilterDataService.selectWhitelistFilterDataList(whitelistFilterData);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('whitelist:filterData:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(whitelistFilterDataService.selectWhitelistFilterDataById(id));
    }

    @GetMapping("/dashboardSummary")
    public AjaxResult dashboardSummary()
    {
        return success(whitelistFilterDataService.getDashboardSummary());
    }

    @PreAuthorize("@ss.hasPermi('whitelist:filterData:export')")
    @Log(title = "过滤数据", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WhitelistFilterData whitelistFilterData)
    {
        List<WhitelistFilterData> list = whitelistFilterDataService.selectWhitelistFilterDataList(whitelistFilterData);
        ExcelUtil<WhitelistFilterData> util = new ExcelUtil<>(WhitelistFilterData.class);
        util.exportExcel(response, list, "过滤数据");
    }

    @PreAuthorize("@ss.hasPermi('whitelist:filterData:pull')")
    @Log(title = "过滤数据", businessType = BusinessType.OTHER)
    @PostMapping("/pullOnce")
    public AjaxResult pullOnce()
    {
        WhitelistKafkaPullResultVo result = whitelistFilterDataService.pullKafkaData();
        return AjaxResult.success("本次消费 " + result.getPolledMessages() + " 条，解析 " + result.getParsedMessages()
                + " 条，入库 " + result.getInsertedRows() + " 条，跳过 " + result.getSkippedMessages() + " 条", result);
    }

    @PreAuthorize("@ss.hasPermi('whitelist:filterData:pull')")
    @Log(title = "过滤数据", businessType = BusinessType.INSERT)
    @PostMapping("/publish")
    public AjaxResult publish(@Valid @RequestBody WhitelistKafkaPublishBo publishBo)
    {
        whitelistFilterDataService.publishKafkaData(publishBo.getMessage());
        return success("消息已写入 Topic");
    }

    @PreAuthorize("@ss.hasPermi('whitelist:filterData:remove')")
    @Log(title = "过滤数据", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(whitelistFilterDataService.deleteWhitelistFilterDataByIds(ids));
    }
}
