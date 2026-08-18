package com.hm.manage.service.document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.manage.domain.DocDocument;

public interface DocumentEditorProvider
{
    String getProviderName();

    Map<String, Object> buildEditorBootstrap(DocDocument document, LoginUser loginUser, boolean editable);

    void verifyFileToken(String token, DocDocument document);

    Map<String, Object> verifyCallback(String accessToken, String outboxToken, DocDocument document,
        Map<String, Object> payload);

    void downloadCallbackFile(String sourceUrl, Path target) throws IOException, InterruptedException;

    /**
     * Requests an asynchronous force-save of the current editor session.
     *
     * @return {@code true} when ONLYOFFICE accepted the command and a status-6 callback is expected;
     *         {@code false} when the editor reports that there are no new changes to persist
     */
    boolean forceSave(DocDocument document);

    void revokeEditingRights(DocDocument document, Collection<Long> userIds);

    void revokeAllEditingRights(DocDocument document);
}
