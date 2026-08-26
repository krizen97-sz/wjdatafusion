package com.hm.manage.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KnowledgeCenterMapperContractTest
{
    private final String mapper = read("src/main/resources/mapper/knowledge/KnowledgeCenterMapper.xml");

    @Test
    void articleUpdatesShouldUseOptimisticVersionGuard()
    {
        assertTrue(mapper.contains("content_version = #{page.contentVersion}"));
        assertTrue(mapper.contains("content_version = #{expectedVersion}"));
        assertTrue(mapper.contains("for update"));
    }

    @Test
    void documentLinksShouldStoreOnlyExistingDocumentIdsWithoutCopyingDocumentAclRules()
    {
        assertTrue(mapper.contains("insert into kb_page_document(page_id, document_id"));
        assertTrue(mapper.contains("select document_id from kb_page_document"));
        assertFalse(mapper.contains("doc_acl"));
        assertFalse(mapper.contains("storage_key"));
    }

    @Test
    void versionsShouldKeepFullKnowledgeAndDocumentRelationshipSnapshots()
    {
        assertTrue(mapper.contains("snapshot_content"));
        assertTrue(mapper.contains("snapshot_tags"));
        assertTrue(mapper.contains("snapshot_document_ids"));
        assertTrue(mapper.contains("content_checksum"));
    }

    @Test
    void lifecycleTreesShouldKeepActiveFoldersAndFolderDeletionShouldProtectEveryChild()
    {
        assertTrue(mapper.contains("p.page_type = 'FOLDER' and p.lifecycle_status = 'ACTIVE'"));
        assertTrue(mapper.contains("p.page_type = 'ARTICLE' and p.lifecycle_status = #{lifecycleStatus}"));
        assertTrue(mapper.contains("select count(1) from kb_page where parent_id = #{pageId}"));
        assertFalse(mapper.contains("parent_id = #{pageId} and lifecycle_status != 'TRASH'"));
    }

    private String read(String path)
    {
        try
        {
            return Files.readString(Path.of(path));
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(exception);
        }
    }
}
