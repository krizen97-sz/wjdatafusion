package com.hm.manage.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.manage.domain.Novel;
import com.hm.manage.service.INovelService;
import com.hm.common.utils.poi.ExcelUtil;
import com.hm.common.core.page.TableDataInfo;

/**
 * novelController
 * 
 * @author hm
 * @date 2026-03-24
 */
@RestController
@RequestMapping("/manage/novel")
public class NovelController extends BaseController
{
    @Autowired
    private INovelService novelService;

    /**
     * 查询novel列表
     */
    @PreAuthorize("@ss.hasPermi('manage:novel:list')")
    @GetMapping("/list")
    public TableDataInfo list(Novel novel)
    {
        startPage();
        List<Novel> list = novelService.selectNovelList(novel);
        return getDataTable(list);
    }

    /**
     * 导出novel列表
     */
    @PreAuthorize("@ss.hasPermi('manage:novel:export')")
    @Log(title = "novel", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Novel novel)
    {
        List<Novel> list = novelService.selectNovelList(novel);
        ExcelUtil<Novel> util = new ExcelUtil<Novel>(Novel.class);
        util.exportExcel(response, list, "novel数据");
    }

    /**
     * 获取novel详细信息
     */
    @PreAuthorize("@ss.hasPermi('manage:novel:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(novelService.selectNovelById(id));
    }

    /**
     * 新增novel
     */
    @PreAuthorize("@ss.hasPermi('manage:novel:add')")
    @Log(title = "novel", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Novel novel)
    {
        return toAjax(novelService.insertNovel(novel));
    }

    /**
     * 修改novel
     */
    @PreAuthorize("@ss.hasPermi('manage:novel:edit')")
    @Log(title = "novel", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Novel novel)
    {
        return toAjax(novelService.updateNovel(novel));
    }

    /**
     * 删除novel
     */
    @PreAuthorize("@ss.hasPermi('manage:novel:remove')")
    @Log(title = "novel", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(novelService.deleteNovelByIds(ids));
    }
}
