package com.hm.manage.mapper;

import java.io.InputStream;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import com.hm.manage.domain.DocDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentFavoriteMapperTest
{
    @Test
    void personalBookmarksRemainSubjectToCurrentPermissionsAndLifecycle() throws Exception
    {
        var ds = new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        var config = new Configuration(new Environment("favorites-test", new JdbcTransactionFactory(), ds));
        String resource = "mapper/document/DocumentWorkspaceMapper.xml";
        try (InputStream xml = getClass().getClassLoader().getResourceAsStream(resource))
        {
            new XMLMapperBuilder(xml, config, resource, config.getSqlFragments()).parse();
        }
        try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession(true);
             Statement sql = session.getConnection().createStatement())
        {
            createFixture(sql);
            DocumentWorkspaceMapper mapper = session.getMapper(DocumentWorkspaceMapper.class);
            mapper.insertFavorite(8L, 42L);
            mapper.insertFavorite(8L, 42L);
            mapper.insertFavorite(9L, 42L);
            mapper.insertFavorite(9L, 43L);
            assertEquals("VIEW", favorites(mapper, 8L, false).get(0).getAccessPermission());
            assertEquals(1, favorites(mapper, 8L, false).size());
            assertEquals(1, favorites(mapper, 9L, false).size()); // 43 is already in trash
            assertTrue(mapper.selectAccessibleDocument(42L, 8L).isFavorite());
            assertFalse(mapper.selectAccessibleDocument(42L, 7L).isFavorite());
            var shared = mapper.selectDocumentList(8L, "SHARED", null, "", "", "", false);
            assertTrue(shared.get(0).isFavorite());
            mapper.deleteFavorite(8L, 42L);
            mapper.deleteFavorite(8L, 42L);
            assertTrue(favorites(mapper, 8L, false).isEmpty());
            assertTrue(mapper.isFavorite(9L, 42L));

            mapper.insertFavorite(8L, 42L);
            sql.execute("UPDATE doc_acl SET expires_at = '2000-01-01' WHERE user_id = 8");
            session.clearCache();
            assertTrue(favorites(mapper, 8L, false).isEmpty());
            assertTrue(favorites(mapper, 7L, true).isEmpty()); // admin cannot see another user's bookmark
            assertEquals(1, favorites(mapper, 8L, true).size()); // explicit admin access to own bookmark
            sql.execute("DELETE FROM doc_acl WHERE user_id = 9");
            session.clearCache();
            assertTrue(favorites(mapper, 9L, false).isEmpty());
            sql.execute("UPDATE doc_document SET lifecycle_status = 'TRASH' WHERE document_id = 42");
            session.clearCache();
            assertTrue(favorites(mapper, 8L, true).isEmpty());
        }
    }

    private List<DocDocument> favorites(DocumentWorkspaceMapper mapper, Long userId, boolean admin)
    {
        return mapper.selectDocumentList(userId, "FAVORITES", null, "", "", "", admin);
    }

    private void createFixture(Statement sql) throws Exception
    {
        sql.execute("CREATE TABLE doc_document (document_id BIGINT PRIMARY KEY, folder_id BIGINT, owner_id BIGINT, "
            + "title VARCHAR(100), file_type VARCHAR(10), document_type VARCHAR(16), storage_key VARCHAR(100), "
            + "file_size BIGINT, content_version INT, editor_key VARCHAR(20), checksum VARCHAR(64), "
            + "lifecycle_status VARCHAR(16), create_by VARCHAR(20), create_time DATETIME, "
            + "update_by VARCHAR(20), update_time DATETIME)");
        sql.execute("CREATE TABLE doc_acl (document_id BIGINT, user_id BIGINT, permission VARCHAR(16), expires_at DATETIME)");
        sql.execute("CREATE TABLE sys_user (user_id BIGINT PRIMARY KEY, user_name VARCHAR(20), nick_name VARCHAR(20), "
            + "avatar VARCHAR(20), status CHAR(1), del_flag CHAR(1))");
        sql.execute("CREATE TABLE doc_folder (folder_id BIGINT, folder_name VARCHAR(20), deleted CHAR(1))");
        sql.execute("CREATE TABLE doc_operation_log (document_id BIGINT, operator_id BIGINT, create_time DATETIME, action_type VARCHAR(16))");
        sql.execute("CREATE TABLE doc_favorite (user_id BIGINT, document_id BIGINT, create_time DATETIME DEFAULT CURRENT_TIMESTAMP, "
            + "PRIMARY KEY(user_id, document_id))");
        sql.execute("INSERT INTO sys_user(user_id,user_name,status,del_flag) VALUES (7,'owner','0','0'), (8,'reader','0','0'), (9,'editor','0','0')");
        sql.execute("INSERT INTO doc_document(document_id, owner_id, lifecycle_status, file_type) VALUES (42,7,'ACTIVE','zip'), (43,9,'TRASH','docx')");
        sql.execute("INSERT INTO doc_acl VALUES (42,8,'VIEW',NULL), (42,9,'EDIT',NULL)");
    }
}
