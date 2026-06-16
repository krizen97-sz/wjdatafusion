package com.hm.manage.service;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportServerCredential;

public interface ISupportServerService
{
    SupportServer selectSupportServerByServerId(Long serverId);

    List<SupportServer> selectSupportServerList(SupportServer server);

    int insertSupportServer(SupportServer server);

    int updateSupportServer(SupportServer server);

    int deleteSupportServerByServerIds(Long[] serverIds);

    String getServerPasswordPlain(Long serverId);

    List<SupportServerCredential> selectServerCredentialList(Long serverId);

    int insertServerCredential(SupportServerCredential credential);

    int updateServerCredential(SupportServerCredential credential);

    int deleteServerCredentialById(Long credentialId);

    String getServerCredentialPasswordPlain(Long credentialId);

    List<Map<String, Object>> selectServerCredentialPlainSummaries(Long[] serverIds);

    void exportImportTemplate(HttpServletResponse response) throws Exception;

    List<SupportServer> parseImportFile(MultipartFile file) throws Exception;
}
