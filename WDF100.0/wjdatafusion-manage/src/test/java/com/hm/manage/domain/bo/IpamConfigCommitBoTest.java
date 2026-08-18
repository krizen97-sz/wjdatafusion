package com.hm.manage.domain.bo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IpamConfigCommitBoTest
{
    @Test
    void issuedPermissionCheckMustUseNormalizedStatus()
    {
        IpamConfigRowBo row = new IpamConfigRowBo();
        row.setStatus(" issued ");
        IpamConfigCommitBo commit = new IpamConfigCommitBo();
        commit.setRows(List.of(row));

        assertTrue(commit.containsIssued());
    }
}
