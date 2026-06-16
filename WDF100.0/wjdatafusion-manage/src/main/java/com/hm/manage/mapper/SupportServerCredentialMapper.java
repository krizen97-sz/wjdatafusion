package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportServerCredential;

public interface SupportServerCredentialMapper
{
    SupportServerCredential selectCredentialById(Long credentialId);

    List<SupportServerCredential> selectCredentialsByServerId(Long serverId);

    int insertCredential(SupportServerCredential credential);

    int updateCredential(SupportServerCredential credential);

    int clearDefaultByServerId(@Param("serverId") Long serverId, @Param("excludeCredentialId") Long excludeCredentialId);

    int deleteCredentialById(Long credentialId);

    int deleteCredentialsByServerId(Long serverId);
}
