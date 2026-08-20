package com.hm.manage.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class AutoInspectionLabelMapperContractTest
{
    @Test
    void labelsAndRecordIdsMustParticipateInReadWriteAndFiltering() throws Exception
    {
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("mapper/support/SupportAutoInspectionMapper.xml"))
        {
            assertNotNull(input);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(input);
            var xpath = XPathFactory.newInstance().newXPath();

            String templateList = xpath.evaluate("/mapper/select[@id='selectTemplateList']", document);
            String templateDetail = xpath.evaluate("/mapper/select[@id='selectTemplateById']", document);
            String templateInsert = xpath.evaluate("/mapper/insert[@id='insertTemplate']", document);
            String templateUpdate = xpath.evaluate("/mapper/update[@id='updateTemplate']", document);
            String planList = xpath.evaluate("/mapper/select[@id='selectPlanList']", document);
            String planDetail = xpath.evaluate("/mapper/select[@id='selectPlanById']", document);
            String planInsert = xpath.evaluate("/mapper/insert[@id='insertPlan']", document);
            String planUpdate = xpath.evaluate("/mapper/update[@id='updatePlan']", document);
            String recordList = xpath.evaluate("/mapper/select[@id='selectRecordList']", document);

            assertTrue(templateList.contains("t.label_name as labelName"));
            assertTrue(templateList.contains("t.label_name = #{labelName}"));
            assertTrue(templateDetail.contains("label_name as labelName"));
            assertTrue(templateInsert.contains("label_name"));
            assertTrue(templateInsert.contains("#{labelName}"));
            assertTrue(templateUpdate.contains("label_name = #{labelName}"));
            assertTrue(planList.contains("p.label_name as labelName"));
            assertTrue(planList.contains("p.label_name = #{labelName}"));
            assertTrue(planDetail.contains("p.label_name as labelName"));
            assertTrue(planInsert.contains("label_name"));
            assertTrue(planInsert.contains("#{labelName}"));
            assertTrue(planUpdate.contains("label_name = #{labelName}"));
            assertTrue(recordList.contains("template_id = #{templateId}"));
            assertTrue(recordList.contains("plan_id = #{planId}"));
        }
    }
}
