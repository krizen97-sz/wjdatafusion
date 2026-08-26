package com.hm.manage.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class KnowledgeCenterControllerTest
{
    @Test
    void everyEndpointShouldHaveDedicatedKnowledgePermission()
    {
        for (Method method : KnowledgeCenterController.class.getDeclaredMethods())
        {
            boolean mapped = method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
            if (!mapped)
            {
                continue;
            }
            PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
            assertNotNull(permission, method.getName() + " 必须声明知识中心权限");
            assertTrue(permission.value().contains("knowledge:"), method.getName() + " 必须使用知识中心权限字符");
        }
    }

    @Test
    void documentCandidatesShouldRequireBothWriteAndExistingDocumentPermission() throws Exception
    {
        Method method = KnowledgeCenterController.class.getDeclaredMethod("documentCandidates", String.class);
        String expression = method.getAnnotation(PreAuthorize.class).value();

        assertTrue(expression.contains("knowledge:page:write"));
        assertTrue(expression.contains("document:file:manage"));
    }
}
