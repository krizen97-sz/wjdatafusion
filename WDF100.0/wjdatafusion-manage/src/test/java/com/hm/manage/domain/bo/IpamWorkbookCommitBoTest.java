package com.hm.manage.domain.bo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IpamWorkbookCommitBoTest
{
    @Test
    void containsIssuedMustInspectEverySheet()
    {
        IpamConfigRowBo allocated = new IpamConfigRowBo();
        allocated.setStatus("ALLOCATED");
        IpamConfigCommitBo first = new IpamConfigCommitBo();
        first.setRows(List.of(allocated));

        IpamWorkbookCommitBo workbook = new IpamWorkbookCommitBo();
        workbook.setSheets(List.of(first));
        assertFalse(workbook.containsIssued());

        IpamConfigRowBo issued = new IpamConfigRowBo();
        issued.setStatus(" issued ");
        IpamConfigCommitBo second = new IpamConfigCommitBo();
        second.setRows(List.of(issued));
        workbook.setSheets(List.of(first, second));
        assertTrue(workbook.containsIssued());
    }
}
