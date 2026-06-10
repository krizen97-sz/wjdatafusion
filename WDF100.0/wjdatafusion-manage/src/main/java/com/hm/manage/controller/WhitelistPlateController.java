package com.hm.manage.controller;

import java.util.List;
import java.util.Collections;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
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
import com.hm.common.core.page.PageDomain;
import com.hm.common.core.page.TableDataInfo;
import com.hm.common.core.page.TableSupport;
import com.hm.common.enums.BusinessType;
import com.hm.common.utils.poi.ExcelUtil;
import com.hm.manage.domain.WhitelistPlate;
import com.hm.manage.service.IWhitelistPlateService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@RestController
@RequestMapping("/whitelist/plate")
public class WhitelistPlateController extends BaseController
{
    @Autowired
    private IWhitelistPlateService whitelistPlateService;

    @PreAuthorize("@ss.hasPermi('whitelist:plate:list')")
    @GetMapping("/list")
    public TableDataInfo list(WhitelistPlate whitelistPlate)
    {
        List<WhitelistPlate> list = whitelistPlateService.selectWhitelistPlateList(whitelistPlate);
        PageDomain pageDomain = TableSupport.buildPageRequest();
        int pageNum = pageDomain.getPageNum() == null || pageDomain.getPageNum() < 1 ? 1 : pageDomain.getPageNum();
        int pageSize = pageDomain.getPageSize() == null || pageDomain.getPageSize() < 1 ? 10 : pageDomain.getPageSize();
        int fromIndex = Math.max((pageNum - 1) * pageSize, 0);
        int toIndex = Math.min(fromIndex + pageSize, list.size());
        List<WhitelistPlate> pageList = fromIndex >= list.size() ? Collections.emptyList() : list.subList(fromIndex, toIndex);
        TableDataInfo rspData = new TableDataInfo(pageList, list.size());
        rspData.setCode(200);
        rspData.setMsg("查询成功");
        return rspData;
    }

    @PreAuthorize("@ss.hasPermi('whitelist:plate:export')")
    @Log(title = "车牌管控", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WhitelistPlate whitelistPlate)
    {
        List<WhitelistPlate> list = whitelistPlateService.selectWhitelistPlateList(whitelistPlate);
        ExcelUtil<WhitelistPlate> util = new ExcelUtil<>(WhitelistPlate.class);
        util.exportExcel(response, list, "车牌管控数据");
    }

    @Log(title = "车牌管控", businessType = BusinessType.IMPORT)
    @PreAuthorize("@ss.hasPermi('whitelist:plate:import')")
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception
    {
        ExcelUtil<WhitelistPlate> util = new ExcelUtil<>(WhitelistPlate.class);
        List<WhitelistPlate> whitelistPlateList = util.importExcel(file.getInputStream());
        String message = whitelistPlateService.importWhitelistPlate(whitelistPlateList, updateSupport, getUsername());
        return success(message);
    }

    @PreAuthorize("@ss.hasPermi('whitelist:plate:import')")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) throws Exception
    {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.setHeader("Content-Disposition", "attachment;filename=whitelist_plate_template.xlsx");
        try (Workbook workbook = new XSSFWorkbook())
        {
            Sheet sheet = workbook.createSheet("车牌管控数据");
            Font headFont = workbook.createFont();
            headFont.setBold(true);
            headFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle headStyle = workbook.createCellStyle();
            headStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headStyle.setAlignment(HorizontalAlignment.CENTER);
            headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headStyle.setFont(headFont);

            CellStyle tipStyle = workbook.createCellStyle();
            tipStyle.setWrapText(true);
            tipStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            tipStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            Cell plateHead = headerRow.createCell(0);
            plateHead.setCellValue("车牌号码");
            plateHead.setCellStyle(headStyle);
            Cell statusHead = headerRow.createCell(1);
            statusHead.setCellValue("状态");
            statusHead.setCellStyle(headStyle);
            Cell remarkHead = headerRow.createCell(2);
            remarkHead.setCellValue("备注");
            remarkHead.setCellStyle(headStyle);

            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("苏D54321");
            exampleRow.createCell(1).setCellValue("启用");
            exampleRow.createCell(2).setCellValue("夜间重点关注");

            Row tipRow = sheet.createRow(2);
            tipRow.createCell(0).setCellValue("填写说明：支持现场常见车牌写法，系统会自动转成大写后导入。");
            tipRow.createCell(1).setCellValue("状态可填：启用 / 停用；留空时默认按启用处理。");
            tipRow.createCell(2).setCellValue("备注可选填；可用于说明管控原因或使用场景。");
            tipRow.getCell(0).setCellStyle(tipStyle);
            tipRow.getCell(1).setCellStyle(tipStyle);
            tipRow.getCell(2).setCellStyle(tipStyle);

            DataValidationHelper helper = sheet.getDataValidationHelper();
            DataValidationConstraint constraint = helper.createExplicitListConstraint(new String[] { "启用", "停用" });
            CellRangeAddressList addressList = new CellRangeAddressList(1, 500, 1, 1);
            DataValidation validation = helper.createValidation(constraint, addressList);
            validation.setSuppressDropDownArrow(true);
            validation.setShowErrorBox(true);
            sheet.addValidationData(validation);

            sheet.setColumnWidth(0, 22 * 256);
            sheet.setColumnWidth(1, 16 * 256);
            sheet.setColumnWidth(2, 28 * 256);
            workbook.write(response.getOutputStream());
        }
    }

    @PreAuthorize("@ss.hasPermi('whitelist:plate:query')")
    @GetMapping(value = "/{vehiclePlate}")
    public AjaxResult getInfo(@PathVariable("vehiclePlate") String vehiclePlate)
    {
        return success(whitelistPlateService.selectWhitelistPlateByVehiclePlate(vehiclePlate));
    }

    @PreAuthorize("@ss.hasPermi('whitelist:plate:add')")
    @Log(title = "车牌管控", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WhitelistPlate whitelistPlate)
    {
        whitelistPlate.setCreateBy(getUsername());
        return toAjax(whitelistPlateService.insertWhitelistPlate(whitelistPlate));
    }

    @PreAuthorize("@ss.hasPermi('whitelist:plate:edit')")
    @Log(title = "车牌管控", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WhitelistPlate whitelistPlate)
    {
        whitelistPlate.setUpdateBy(getUsername());
        return toAjax(whitelistPlateService.updateWhitelistPlate(whitelistPlate));
    }

    @PreAuthorize("@ss.hasPermi('whitelist:plate:edit')")
    @Log(title = "车牌管控", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody WhitelistPlate whitelistPlate)
    {
        whitelistPlate.setUpdateBy(getUsername());
        return toAjax(whitelistPlateService.changeStatus(whitelistPlate));
    }

    @PreAuthorize("@ss.hasPermi('whitelist:plate:remove')")
    @Log(title = "车牌管控", businessType = BusinessType.DELETE)
    @DeleteMapping("/{vehiclePlates}")
    public AjaxResult remove(@PathVariable String[] vehiclePlates)
    {
        return toAjax(whitelistPlateService.deleteWhitelistPlateByVehiclePlates(vehiclePlates));
    }
}
