package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.IpamAddress;
import com.hm.manage.domain.bo.IpamWorkbookCommitBo;
import com.hm.manage.domain.vo.IpamWorkbookCatalogVo;

public interface IIpamWorkbookService
{
    IpamWorkbookCatalogVo getCatalog();

    List<IpamAddress> selectCommunityAddressList(String communityName);

    int commitWorkbook(IpamWorkbookCommitBo workbook);
}
