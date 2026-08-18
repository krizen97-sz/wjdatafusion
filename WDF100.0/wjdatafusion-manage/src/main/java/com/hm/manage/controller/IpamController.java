package com.hm.manage.controller;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
import com.hm.manage.domain.IpamAddress;
import com.hm.manage.domain.IpamNetwork;
import com.hm.manage.domain.bo.IpamConfigCommitBo;
import com.hm.manage.domain.bo.IpamScenarioSettingBo;
import com.hm.manage.service.IIpamService;

@RestController
@RequestMapping("/ipam")
public class IpamController extends BaseController
{
    @Autowired
    private IIpamService ipamService;

    @PreAuthorize("@ss.hasPermi('ipam:network:list')")
    @GetMapping("/network/list")
    public TableDataInfo networkList(IpamNetwork network)
    {
        startPage();
        List<IpamNetwork> list = ipamService.selectNetworkList(network);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('ipam:network:list')")
    @GetMapping("/network/tree")
    public AjaxResult networkTree(IpamNetwork network)
    {
        return success(ipamService.selectNetworkList(network));
    }

    @PreAuthorize("@ss.hasPermi('ipam:network:list')")
    @GetMapping("/settings/scenario")
    public AjaxResult scenarioSetting()
    {
        AjaxResult result = success();
        result.put("scenarioType", ipamService.getScenarioType());
        return result;
    }

    @PreAuthorize("@ss.hasPermi('ipam:network:edit')")
    @Log(title = "IP分配管控-使用场景", businessType = BusinessType.UPDATE)
    @PutMapping("/settings/scenario")
    public AjaxResult updateScenarioSetting(@RequestBody IpamScenarioSettingBo setting)
    {
        return toAjax(ipamService.updateScenarioType(setting));
    }

    @PreAuthorize("@ss.hasPermi('ipam:network:add')")
    @Log(title = "IP分配管控-网段", businessType = BusinessType.INSERT)
    @PostMapping("/network")
    public AjaxResult addNetwork(@RequestBody IpamNetwork network)
    {
        return toAjax(ipamService.insertNetwork(network));
    }

    @PreAuthorize("@ss.hasPermi('ipam:network:edit')")
    @Log(title = "IP分配管控-网段", businessType = BusinessType.UPDATE)
    @PutMapping("/network")
    public AjaxResult editNetwork(@RequestBody IpamNetwork network)
    {
        return toAjax(ipamService.updateNetwork(network));
    }

    @PreAuthorize("@ss.hasPermi('ipam:network:remove')")
    @Log(title = "IP分配管控-网段", businessType = BusinessType.DELETE)
    @DeleteMapping("/network/{networkIds}")
    public AjaxResult removeNetwork(@PathVariable Long[] networkIds)
    {
        return toAjax(ipamService.deleteNetworkByIds(networkIds));
    }

    @PreAuthorize("@ss.hasPermi('ipam:address:list')")
    @GetMapping("/network/overview")
    public AjaxResult networkOverview(Long networkId, String keyword, String targetType, String manufacturer)
    {
        return success(ipamService.getNetworkOverview(networkId, keyword, targetType, manufacturer));
    }

    @PreAuthorize("@ss.hasPermi('ipam:address:list')")
    @GetMapping("/address/grid")
    public AjaxResult addressGrid(Long networkId, Integer pageNum, Integer pageSize)
    {
        return success(ipamService.getAddressGridByNetworkId(networkId, pageNum, pageSize));
    }

    @PreAuthorize("@ss.hasPermi('ipam:address:list')")
    @GetMapping("/address/list")
    public TableDataInfo addressList(IpamAddress address)
    {
        startPage();
        List<IpamAddress> list = ipamService.selectAddressList(address);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('ipam:address:allocate') and (#address.status != 'ISSUED' or @ss.hasPermi('ipam:address:issue'))")
    @Log(title = "IP分配管控-地址分配", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/address/allocate")
    public AjaxResult allocateAddress(@RequestBody IpamAddress address)
    {
        return toAjax(ipamService.allocateAddress(address));
    }

    @PreAuthorize("@ss.hasPermi('ipam:address:allocate') and (!#commitBo.containsIssued() or @ss.hasPermi('ipam:address:issue'))")
    @Log(title = "IP分配管控-可视化配置", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/config/commit")
    public AjaxResult commitConfig(@Valid @RequestBody IpamConfigCommitBo commitBo)
    {
        return toAjax(ipamService.commitConfigSheet(commitBo));
    }

    @PreAuthorize("@ss.hasPermi('ipam:address:edit') and (#address.status != 'ISSUED' or @ss.hasPermi('ipam:address:issue'))")
    @Log(title = "IP分配管控-地址编辑", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/address")
    public AjaxResult editAddress(@RequestBody IpamAddress address)
    {
        return toAjax(ipamService.updateAddress(address));
    }

    @PreAuthorize("@ss.hasPermi('ipam:credential:view')")
    @Log(title = "IP分配管控-查看设备密码", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    @GetMapping("/address/{addressId}/credential")
    public AjaxResult getAddressCredential(@PathVariable Long addressId, HttpServletResponse response)
    {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0L);
        AjaxResult result = success();
        result.put("password", ipamService.getAddressCredential(addressId));
        return result;
    }

    @PreAuthorize("@ss.hasPermi('ipam:address:release')")
    @Log(title = "IP分配管控-地址释放", businessType = BusinessType.UPDATE)
    @PutMapping("/address/release/{addressId}")
    public AjaxResult releaseAddress(@PathVariable Long addressId)
    {
        return toAjax(ipamService.releaseAddress(addressId));
    }

    @PreAuthorize("@ss.hasPermi('ipam:address:export')")
    @Log(title = "IP分配管控-地址台账", businessType = BusinessType.EXPORT,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/address/export")
    public void exportAddress(HttpServletResponse response, IpamAddress address)
    {
        List<IpamAddress> list = ipamService.selectAddressList(address);
        ExcelUtil<IpamAddress> util = new ExcelUtil<>(IpamAddress.class);
        util.exportExcel(response, list, "IP地址台账");
    }
}
