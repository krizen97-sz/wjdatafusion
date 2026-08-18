package com.hm.quartz.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.hm.manage.service.IDocumentWorkspaceService;

@Component("documentPermissionExpiryTask")
public class DocumentPermissionExpiryTask
{
    private static final Logger log = LoggerFactory.getLogger(DocumentPermissionExpiryTask.class);

    @Autowired
    private IDocumentWorkspaceService workspaceService;

    public void expire()
    {
        int count = workspaceService.expireCollaboratorPermissions();
        if (count > 0)
        {
            log.info("文档协作权限到期处理完成，本次移除{}条授权", count);
        }
    }
}
