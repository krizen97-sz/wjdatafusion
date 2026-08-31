package com.hm.manage.service;

import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ISupportEquipmentTopologyWorkbookService
{
    void exportWorkbook(HttpServletResponse response, Long siteId) throws Exception;

    Map<String, Object> importWorkbook(Long siteId, MultipartFile file) throws Exception;
}
