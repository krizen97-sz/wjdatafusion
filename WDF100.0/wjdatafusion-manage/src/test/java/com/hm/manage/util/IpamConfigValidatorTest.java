package com.hm.manage.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.bo.IpamConfigCommitBo;
import com.hm.manage.domain.bo.IpamConfigRowBo;
import com.hm.manage.util.IpamAddressUtils.CidrRange;

class IpamConfigValidatorTest
{
    private final CidrRange range = IpamAddressUtils.parseCidr("2.57.1.0/24");

    @Test
    void shouldAcceptIssuedStatusAndOnlyRequireCommunityName()
    {
        IpamConfigRowBo row = row("2.57.1.10", "第一小区", "ISSUED");
        IpamConfigCommitBo commit = commit(List.of(row));

        assertDoesNotThrow(() -> IpamConfigValidator.validate(commit, range, "2.57.1.1"));
    }

    @Test
    void shouldRejectMoreThan256Rows()
    {
        List<IpamConfigRowBo> rows = new ArrayList<>();
        for (int index = 0; index < 257; index++)
        {
            rows.add(row("2.57.1." + (index % 250 + 2), "第一小区", "ALLOCATED"));
        }

        assertThrows(ServiceException.class,
            () -> IpamConfigValidator.validate(commit(rows), range, "2.57.1.1"));
    }

    @Test
    void shouldRejectDuplicateIp()
    {
        IpamConfigCommitBo commit = commit(List.of(
            row("2.57.1.10", "第一小区", "ALLOCATED"),
            row("2.57.1.10", "第二小区", "ALLOCATED")));

        assertThrows(ServiceException.class,
            () -> IpamConfigValidator.validate(commit, range, "2.57.1.1"));
    }

    @Test
    void shouldRejectBoundaryGatewayAndOutOfRangeAddresses()
    {
        assertThrows(ServiceException.class,
            () -> IpamConfigValidator.validate(commit(List.of(row("2.57.1.0", "第一小区", "ALLOCATED"))), range, "2.57.1.1"));
        assertThrows(ServiceException.class,
            () -> IpamConfigValidator.validate(commit(List.of(row("2.57.1.1", "第一小区", "ALLOCATED"))), range, "2.57.1.1"));
        assertThrows(ServiceException.class,
            () -> IpamConfigValidator.validate(commit(List.of(row("2.57.2.10", "第一小区", "ALLOCATED"))), range, "2.57.1.1"));
    }

    private IpamConfigCommitBo commit(List<IpamConfigRowBo> rows)
    {
        IpamConfigCommitBo commit = new IpamConfigCommitBo();
        commit.setNetworkId(1L);
        commit.setRows(rows);
        return commit;
    }

    private IpamConfigRowBo row(String ipAddress, String communityName, String status)
    {
        IpamConfigRowBo row = new IpamConfigRowBo();
        row.setIpAddress(ipAddress);
        row.setCommunityName(communityName);
        row.setStatus(status);
        return row;
    }
}
