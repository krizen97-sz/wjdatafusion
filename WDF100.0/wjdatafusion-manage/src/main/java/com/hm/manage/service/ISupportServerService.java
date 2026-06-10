package com.hm.manage.service;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import com.hm.manage.domain.SupportServer;

public interface ISupportServerService
{
    SupportServer selectSupportServerByServerId(Long serverId);

    List<SupportServer> selectSupportServerList(SupportServer server);

    int insertSupportServer(SupportServer server);

    int updateSupportServer(SupportServer server);

    int deleteSupportServerByServerIds(Long[] serverIds);

    String getServerPasswordPlain(Long serverId);

    void exportImportTemplate(HttpServletResponse response) throws Exception;

    List<SupportServer> parseImportFile(MultipartFile file) throws Exception;
}
