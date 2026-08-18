package com.hm.manage.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.IpamAddress;
import com.hm.manage.domain.IpamNetwork;
import com.hm.manage.domain.bo.IpamConfigCommitBo;
import com.hm.manage.domain.bo.IpamWorkbookCommitBo;
import com.hm.manage.domain.vo.IpamWorkbookCatalogVo;
import com.hm.manage.mapper.IpamWorkbookMapper;
import com.hm.manage.service.IIpamService;
import com.hm.manage.service.IIpamWorkbookService;

@Service
public class IpamWorkbookServiceImpl implements IIpamWorkbookService
{
    private static final int MAX_COMMUNITY_NAME_LENGTH = 120;
    private static final int MAX_WORKBOOK_ROWS = 4096;

    @Autowired
    private IpamWorkbookMapper ipamWorkbookMapper;

    @Autowired
    private IIpamService ipamService;

    @Override
    public IpamWorkbookCatalogVo getCatalog()
    {
        IpamWorkbookCatalogVo catalog = new IpamWorkbookCatalogVo();
        catalog.setScenarioType(ipamService.getScenarioType());
        catalog.setNetworks(ipamService.selectNetworkList(new IpamNetwork()));
        catalog.setCommunities(ipamWorkbookMapper.selectCommunityCatalog());
        return catalog;
    }

    @Override
    public List<IpamAddress> selectCommunityAddressList(String communityName)
    {
        String normalizedName = StringUtils.trim(communityName);
        if (StringUtils.isEmpty(normalizedName))
        {
            throw new ServiceException("小区名称或项目名称不能为空");
        }
        if (normalizedName.length() > MAX_COMMUNITY_NAME_LENGTH)
        {
            throw new ServiceException("小区名称或项目名称不能超过" + MAX_COMMUNITY_NAME_LENGTH + "个字符");
        }
        return ipamWorkbookMapper.selectCommunityAddressList(normalizedName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int commitWorkbook(IpamWorkbookCommitBo workbook)
    {
        if (workbook == null || workbook.getSheets() == null || workbook.getSheets().isEmpty())
        {
            throw new ServiceException("没有需要保存的工作表数据");
        }

        int totalRows = workbook.getSheets().stream()
            .filter(sheet -> sheet != null && sheet.getRows() != null)
            .mapToInt(sheet -> sheet.getRows().size())
            .sum();
        if (totalRows > MAX_WORKBOOK_ROWS)
        {
            throw new ServiceException("单次最多保存" + MAX_WORKBOOK_ROWS + "个IP，请缩小编辑范围后重试");
        }

        int affected = 0;
        for (IpamConfigCommitBo sheet : workbook.getSheets())
        {
            if (sheet == null)
            {
                throw new ServiceException("工作表中存在空配置批次");
            }
            affected += ipamService.commitConfigSheet(sheet);
        }
        return affected;
    }
}
