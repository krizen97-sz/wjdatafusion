#!/usr/bin/env python3
"""Inventory a Kettle ZIP using only ZIP/XML/properties/JVM class-file parsing.

No plugin is loaded, no JVM is started, and KTR/KJB/configuration contents are
never read. Output is discovery evidence, not a runtime registry or form schema.
Only the Python standard library is required (Python >= 3.9).
"""
import argparse
from collections import Counter, defaultdict
from functools import lru_cache
import hashlib
from io import BytesIO
import json
from pathlib import Path, PurePosixPath
import re
import struct
import sys
import xml.etree.ElementTree as ET
from zipfile import BadZipFile, ZipFile


SCHEMA_VERSION = 1
ANNOTATIONS = {
    "Lorg/pentaho/di/core/annotations/Step;": "step",
    "Lorg/pentaho/di/core/annotations/JobEntry;": "job-entry",
}
DESCRIPTORS = {"plugin.xml", "kettle-steps.xml", "kettle-job-entries.xml"}
IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_.-]{1,100}$")
CLASS_NAME = re.compile(r"^[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)+$")
IPV4 = re.compile(r"(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])")
URL = re.compile(r"(?i)\b(?:https?|ftp|sftp|jdbc|ldap|smb)://[^\s<>\"']+")
# Reviewed translations for extension descriptors that ship without Chinese.
# Values are explicitly marked editorial_translation, never original UI text.
EXTENSION_NAMES_ZH = {
    "HBPFaceKafkaConsumer": "HBP 人脸 Kafka 输入",
    "HBPFaceKafkaProducer": "HBP 人脸 Kafka 输出",
    "HBPKafkaConsumer": "HBP Kafka 输入",
    "HBPKafkaProducer": "HBP Kafka 输出",
    "KafkaConsumer": "Kafka 输入",
    "KafkaConsumerByBroker": "按 Broker 连接的 Kafka 输入",
    "KafkaConsumer4Huawei": "华为 Kafka 输入",
    "KafkaProducer": "Kafka 输出",
    "NewKafkaProducer": "新版 Kafka 输出",
    "HbaseInput": "HBase 输入",
    "HBaseOutput": "HBase 输出",
    "RabbitmqConsumer": "RabbitMQ 输入",
    "RabbitmqProducer": "RabbitMQ 输出",
    "ActivemqConsumer": "ActiveMQ 输入",
    "ActivemqProducer": "ActiveMQ 输出",
    "MongoDbInput": "MongoDB 输入",
    "MongoDbOutput": "MongoDB 输出",
    "MongoDbDelete": "MongoDB 删除",
    "ImpalaBatchOut": "Impala 批量输出",
    "ElasticSearch4HumanInfo": "人员信息 Elasticsearch 批量输出",
    "JudgeTableCount": "表记录数判断",
    "MyPlugin": "海康自定义 MyPlugin",
    "CloudStore": "上传图片到海康云存储",
    "DATASOURCE_PUBLISH": "发布数据模型", "DTD_VALIDATOR": "DTD 校验",
    "DataRefineryBuildModel": "构建数据模型", "DummyJob": "示例作业",
    "HL7MLLPAcknowledge": "HL7 MLLP 确认", "HL7MLLPInput": "HL7 MLLP 输入",
    "HTTP": "HTTP 请求", "HadoopCopyFilesPlugin": "Hadoop 复制文件",
    "HadoopJobExecutorPlugin": "Hadoop 作业执行", "HadoopPigScriptExecutorPlugin": "Pig 脚本执行",
    "HadoopTransJobExecutorPlugin": "Pentaho MapReduce 执行", "MS_ACCESS_BULK_LOAD": "MS Access 批量加载",
    "OozieJobExecutor": "Oozie 作业执行", "PALO_CUBE_CREATE": "创建 Palo 立方体",
    "PALO_CUBE_DELETE": "删除 Palo 立方体", "SHELL": "Shell 脚本", "SQL": "SQL 脚本",
    "SqoopExport": "Sqoop 导出", "SqoopImport": "Sqoop 导入", "XML_WELL_FORMED": "XML 格式检查",
    "XSD_VALIDATOR": "XSD 校验", "XSLT": "XSL 转换", "AddXML": "添加 XML",
    "CallEndpointStep": "调用服务端点", "CassandraInput": "Cassandra 输入", "CassandraOutput": "Cassandra 输出",
    "ClosureGenerator": "生成闭包关系", "ConcatFields": "拼接字段", "CreateSharedDimensions": "共享维度",
    "DummyStep": "示例步骤", "ElasticSearchBulk": "Elasticsearch 批量写入", "FieldMetadataAnnotation": "标注数据流元数据",
    "GPBulkLoader": "Greenplum 批量加载", "GPLoad": "Greenplum 加载", "GetSessionVariableStep": "获取会话变量",
    "GetSlaveSequence": "从从属服务器获取序列 ID", "GisCoordinateTransformation": "GIS 坐标系转换",
    "GisFileInput": "GIS 文件输入", "GisFileOutput": "GIS 文件输出", "GisGeometryInfo": "GIS 几何信息",
    "GisGeoprocessing": "GIS 空间处理", "GisGroupBy": "GIS 分组", "GisRelate": "GIS 空间关系与邻近分析",
    "HBaseInput": "HBase 输入", "HBaseRowDecoder": "HBase 行解码", "HL7Input": "HL7 输入",
    "HTTPPOST": "HTTP POST 请求", "HadoopEnterPlugin": "MapReduce 输入", "HadoopExitPlugin": "MapReduce 输出",
    "HadoopFileInputPlugin": "Hadoop 文件输入", "HadoopFileOutputPlugin": "Hadoop 文件输出",
    "LucidDBStreamingLoader": "LucidDB 流式加载", "MonetDBAgileMart": "MonetDB 敏捷数据集市",
    "MultiwayMergeJoin": "多路归并连接", "OldTextFileInput": "旧版文本文件输入",
    "OpenERPObjectDelete": "OpenERP 对象删除", "OpenERPObjectInput": "OpenERP 对象输入",
    "OpenERPObjectOutputImport": "OpenERP 对象输出", "PGPDecryptStream": "PGP 数据流解密",
    "PGPEncryptStream": "PGP 数据流加密", "PaloCellInput": "Palo 单元格输入", "PaloCellOutput": "Palo 单元格输出",
    "PaloDimInput": "Palo 维度输入", "PaloDimOutput": "Palo 维度输出", "ParallelGzipCsvInput": "GZIP CSV 输入",
    "Rest": "REST 客户端", "RuleAccumulator": "规则累积", "RuleExecutor": "规则执行", "S3CSVINPUT": "S3 CSV 输入",
    "SFTPPut": "SFTP 上传", "SSTableOutput": "SSTable 输出", "SalesforceDelete": "Salesforce 删除",
    "SalesforceInput": "Salesforce 输入", "SalesforceInsert": "Salesforce 插入", "SalesforceUpdate": "Salesforce 更新",
    "SalesforceUpsert": "Salesforce 插入或更新", "Script": "脚本", "SetSessionVariableStep": "设置会话变量",
    "ShapeFileReader": "ESRI Shapefile 读取", "SimpleMapping": "简单映射（子转换）", "SingleThreader": "单线程执行",
    "SwitchCase": "多分支条件路由", "TableAgileMart": "表敏捷数据集市", "TeraDataBulkLoader": "Teradata TPT 批量加载",
    "TransExecutor": "转换执行器", "TypeExitEdi2XmlStep": "EDI 转 XML",
    "TypeExitGoogleAnalyticsInputStep": "Google Analytics 输入", "VerticaBulkLoader": "Vertica 批量加载",
    "XMLInputStream": "XML 流式输入（StAX）", "XMLJoin": "XML 连接", "XMLOutput": "XML 输出",
    "XSDValidator": "XSD 校验", "getXMLData": "从 XML 获取数据",
}
CATEGORY_ZH = {
    "Input": "输入", "Output": "输出", "Transform": "转换", "Utility": "工具",
    "Flow": "流程", "Scripting": "脚本", "Lookup": "查询", "Joins": "连接",
    "Statistics": "统计", "Mapping": "映射", "Inline": "内联", "Experimental": "实验",
    "Deprecated": "已弃用", "Job": "作业", "General": "通用", "Validation": "校验",
    "Bulk": "批量加载", "FileTransfer": "文件传输", "FileManagement": "文件管理",
    "Mail": "邮件", "Conditions": "条件", "XML": "XML", "Repository": "资源库",
    "Big Data": "大数据", "Data Warehouse": "数据仓库", "Business Intelligence": "商业智能",
    "SAP": "SAP", "Palo": "Palo", "OpenERP": "OpenERP", "Hadoop": "Hadoop",
    "Agile": "敏捷数据集市", "BAServer": "商业分析服务器", "BA Server": "商业分析服务器",
    "Geospatial": "地理空间", "Modeling": "数据建模",
}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def safe_text(value):
    """Resource labels only; strip addresses and credential assignments defensively."""
    value = URL.sub("[address omitted]", str(value))
    value = IPV4.sub("[address omitted]", value)
    value = re.sub(r"(?i)((?:password|passwd|secret|token)\s*[=:]\s*)\S+", r"\1[omitted]", value)
    return value


class Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def take(self, size):
        if self.pos + size > len(self.data):
            raise ValueError("truncated class file")
        value = self.data[self.pos:self.pos + size]
        self.pos += size
        return value

    def u1(self):
        return self.take(1)[0]

    def u2(self):
        return struct.unpack(">H", self.take(2))[0]

    def u4(self):
        return struct.unpack(">I", self.take(4))[0]


def bytecode_references(code, cp):
    """Decode instruction boundaries; operands are not searched as opcodes."""
    strings, new_classes, class_constants = [], [], []
    widths = {16: 1, 17: 2, 18: 1, 19: 2, 20: 2, 132: 2, 169: 1,
              185: 4, 186: 4, 187: 2, 188: 1, 189: 2, 192: 2, 193: 2,
              197: 3, 198: 2, 199: 2, 200: 4, 201: 4}
    widths.update({op: 1 for op in [*range(21, 26), *range(54, 59)]})
    widths.update({op: 2 for op in [*range(153, 169), *range(178, 185)]})
    pos = 0
    while pos < len(code):
        op, start = code[pos], pos
        pos += 1
        if op in (170, 171):
            pos += (-pos) % 4
            if op == 170:
                low, high = struct.unpack(">ii", code[pos + 4:pos + 12])
                pos += 12 + 4 * (high - low + 1)
            else:
                count = struct.unpack(">i", code[pos + 4:pos + 8])[0]
                pos += 8 + 8 * count
        elif op == 196:
            pos += 5 if code[pos] == 132 else 3
        else:
            width = widths.get(op, 0)
            if op in (18, 19, 187):
                index = code[pos] if op == 18 else struct.unpack(">H", code[pos:pos + 2])[0]
                item = cp[index]
                if item and op in (18, 19) and item[0] == 8:
                    strings.append(cp[item[1]][1])
                elif item and op in (18, 19) and item[0] == 7:
                    class_constants.append(cp[item[1]][1].replace("/", "."))
                elif item and op == 187 and item[0] == 7:
                    new_classes.append(cp[item[1]][1].replace("/", "."))
            pos += width
        if pos > len(code) or pos <= start:
            raise ValueError("invalid bytecode instruction boundary")
    return {"strings": sorted(set(strings)), "newClasses": sorted(set(new_classes)),
            "classConstants": sorted(set(class_constants))}


def parse_class(data):
    reader = Reader(data)
    if reader.u4() != 0xCAFEBABE:
        raise ValueError("invalid class magic")
    minor, major = reader.u2(), reader.u2()
    cp = [None] * reader.u2()
    index = 1
    while index < len(cp):
        tag = reader.u1()
        if tag == 1:
            cp[index] = (tag, reader.take(reader.u2()).replace(b"\xc0\x80", b"\x00").decode("utf-8", "replace"))
        elif tag in (3, 4):
            cp[index] = (tag, reader.u4())
        elif tag in (5, 6):
            cp[index] = (tag, reader.take(8).hex())
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            cp[index] = (tag, reader.u2())
        elif tag in (9, 10, 11, 12, 17, 18):
            cp[index] = (tag, reader.u2(), reader.u2())
        elif tag == 15:
            cp[index] = (tag, reader.u1(), reader.u2())
        else:
            raise ValueError("unsupported constant-pool tag %s" % tag)
        index += 1

    def utf(i):
        return cp[i][1] if i else ""

    def klass(i):
        return utf(cp[i][1]).replace("/", ".") if i else ""

    def annotation(r):
        result = {"type": utf(r.u2()), "values": {}}
        for _ in range(r.u2()):
            key = utf(r.u2())
            result["values"][key] = annotation_value(r)
        return result

    def annotation_value(r):
        tag = chr(r.u1())
        if tag == "e":
            return {"enumType": utf(r.u2()), "value": utf(r.u2())}
        if tag == "@":
            return annotation(r)
        if tag == "[":
            return [annotation_value(r) for _ in range(r.u2())]
        return utf(r.u2())

    def attributes(r):
        result = {}
        for _ in range(r.u2()):
            name = utf(r.u2())
            raw = r.take(r.u4())
            if name in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
                ar = Reader(raw)
                result.setdefault("annotations", []).extend(annotation(ar) for _ in range(ar.u2()))
            elif name == "Code":
                cr = Reader(raw)
                cr.take(4)
                result["codeReferences"] = bytecode_references(cr.take(cr.u4()), cp)
        return result

    access, this, parent = reader.u2(), klass(reader.u2()), klass(reader.u2())
    interfaces = [klass(reader.u2()) for _ in range(reader.u2())]
    members = []
    for member_type in ("fields", "methods"):
        collection = []
        for _ in range(reader.u2()):
            item = {"name": "", "descriptor": "", "access": reader.u2()}
            item["name"], item["descriptor"] = utf(reader.u2()), utf(reader.u2())
            item.update(attributes(reader))
            collection.append(item)
        members.append(collection)
    result = {"name": this, "superClass": parent, "interfaces": interfaces,
              "access": access, "classVersion": [major, minor], "fields": members[0], "methods": members[1]}
    result.update(attributes(reader))
    return result


def properties(data):
    try:
        content = data.decode("utf-8")
    except UnicodeDecodeError:
        content = data.decode("iso-8859-1")
    content = re.sub(r"\\\r?\n\s*", "", content)

    def unescape(text):
        text = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m[1], 16)), text)
        return re.sub(r"\\(.)", lambda m: {"n": "\n", "r": "\r", "t": "\t", "f": "\f"}.get(m[1], m[1]), text)

    result = {}
    for line in content.splitlines():
        line = line.lstrip()
        if not line or line.startswith(("#", "!")):
            continue
        match = re.match(r"((?:\\.|[^:=\s])+)\s*[:=]?\s*(.*)", line)
        if match:
            result[unescape(match[1])] = unescape(match[2])
    return result


def xml_registrations(data, source):
    # Only descriptors reach this function; ET does not resolve external entities.
    root = ET.fromstring(data)
    if root.tag == "steps":
        nodes, kind = root.findall("step"), "step"
    elif root.tag == "job-entries":
        nodes, kind = root.findall("job-entry"), "job-entry"
    elif root.tag == "plugin":
        nodes, kind = [root], "step" if "/steps/" in source else "unknown"
    else:
        return []
    results = []
    for node in nodes:
        values = {key: node.get(key) or node.findtext(key) or "" for key in
                  ("id", "classname", "description", "category", "tooltip", "iconfile")}
        if not values["id"] or not values["classname"]:
            continue
        results.append({"kind": kind, "registrationType": "descriptor", "source": source, **values,
                        "localized": {key: {n.get("locale", ""): n.text or "" for n in
                                             node.findall("localized_%s/%s" % (key, key))}
                                      for key in ("description", "category", "tooltip")},
                        "libraries": [n.get("name", "") for n in node.findall("libraries/library")]})
    return results


class Inventory:
    def __init__(self, path):
        self.path = path
        self.outer = ZipFile(path)
        self.archives, self.registrations, self.resources = [], [], []
        self.class_index, self.resource_index = defaultdict(list), defaultdict(list)
        self.classes, self.candidates, self.errors = {}, {}, []
        self.scan_cache = {}
        self.attribute_descriptors = defaultdict(list)
        self.stats = Counter()

    @lru_cache(maxsize=10)
    def jar_bytes(self, source):
        if "!/" not in source:
            return self.outer.read(source)
        container, entry = source.rsplit("!/", 1)
        with ZipFile(BytesIO(self.jar_bytes(container))) as jar:
            return jar.read(entry)

    def read_member(self, source):
        if "!/" not in source:
            return self.outer.read(source)
        container, entry = source.rsplit("!/", 1)
        with ZipFile(BytesIO(self.jar_bytes(container))) as jar:
            return jar.read(entry)

    def scan_jar(self, source, depth=0):
        data = self.jar_bytes(source)
        digest = sha256(data)
        archive = {"source": source, "sha256": digest, "bytes": len(data),
                   "archiveType": PurePosixPath(source).suffix.lstrip(".").lower(),
                   "scope": "osgi-cache" if "/system/karaf/" in source and "/cache/" in source else
                            "plugin" if "/plugins/" in source else "library",
                   "nestedDepth": depth}
        self.archives.append(archive)
        if digest in self.scan_cache:
            cached = self.scan_cache[digest]
        else:
            cached = {"classes": [], "registrations": [], "resources": [], "nested": [], "attributes": []}
            with ZipFile(BytesIO(data)) as jar:
                for name in sorted(jar.namelist()):
                    if name.endswith(".class"):
                        self.stats["uniqueJarClassEntriesInspected"] += 1
                        body = jar.read(name)
                        parsed = None
                        if any(marker.encode() in body for marker in ANNOTATIONS) or (
                            b"org/pentaho/di/trans/step/BaseStepMeta" in body or
                            b"org/pentaho/di/trans/step/StepMetaInterface" in body or
                            b"org/pentaho/di/job/entry/JobEntryBase" in body):
                            try:
                                parsed = parse_class(body)
                            except (ValueError, IndexError, struct.error) as exc:
                                self.errors.append({"source": source + "!/" + name, "error": type(exc).__name__})
                        cached["classes"].append((name, parsed, sha256(body) if parsed else None))
                    elif PurePosixPath(name).name in DESCRIPTORS:
                        try:
                            cached["registrations"].extend(xml_registrations(jar.read(name), name))
                        except ET.ParseError:
                            self.errors.append({"source": source + "!/" + name, "error": "descriptor_xml_invalid"})
                    elif re.search(r"(?:^|/)messages(?:_[\w]+)?\.properties$", name):
                        cached["resources"].append((name, properties(jar.read(name))))
                    elif name.endswith("/step-attributes.xml"):
                        try:
                            root = ET.fromstring(jar.read(name))
                            fields = [{"id": n.get("id", ""), **{key: safe_text(n.findtext(key) or "")
                                      for key in ("xmlcode", "repcode", "description", "tooltip", "valuetype", "parentid")}}
                                      for n in root.findall("attribute")]
                            cached["attributes"].append((name, fields))
                        except ET.ParseError:
                            self.errors.append({"source": source + "!/" + name, "error": "attribute_xml_invalid"})
                    elif name.lower().endswith((".jar", ".zip", ".war", ".kar")):
                        cached["nested"].append(name)
            self.scan_cache[digest] = cached
        for entry, parsed, class_hash in cached["classes"]:
            name = entry[:-6].replace("/", ".")
            member = source + "!/" + entry
            self.class_index[name].append(member)
            if parsed:
                self.classes[member] = (parsed, class_hash)
                if parsed["superClass"].endswith((".BaseStepMeta", ".JobEntryBase")) or any(
                        x.endswith(".StepMetaInterface") for x in parsed["interfaces"]):
                    self.candidates[name] = parsed
                for annotation in parsed.get("annotations", []):
                    if annotation["type"] in ANNOTATIONS:
                        values = annotation["values"]
                        ids = values.get("id", "")
                        if not isinstance(ids, list):
                            ids = str(ids).split(",")
                        for plugin_id in ids:
                            self.registrations.append({"kind": ANNOTATIONS[annotation["type"]],
                                "id": plugin_id.strip(), "classname": parsed["name"],
                                "description": values.get("name", ""), "tooltip": values.get("description", ""),
                                "category": values.get("categoryDescription", ""),
                                "i18nPackageName": values.get("i18nPackageName", ""),
                                "iconfile": values.get("image", ""), "registrationType": "annotation",
                                "source": member, "localized": {}, "libraries": []})
        for registration in cached["registrations"]:
            self.registrations.append({**registration, "source": source + "!/" + registration["source"]})
        for entry, values in cached["resources"]:
            resource = {"source": source + "!/" + entry, "entry": entry, "values": values}
            self.resources.append(resource)
            for key in values:
                self.resource_index[key].append(resource)
        for entry, fields in cached["attributes"]:
            self.attribute_descriptors[entry.rsplit("/", 1)[0]].append({"source": source + "!/" + entry, "fields": fields})
        for nested in cached["nested"]:
            if depth >= 4:
                self.errors.append({"source": source + "!/" + nested, "error": "nested_archive_depth_limit"})
            else:
                self.scan_jar(source + "!/" + nested, depth + 1)

    def scan(self):
        names = sorted(self.outer.namelist())
        self.stats["outerEntries"] = len(names)
        self.stats["skippedBusinessFlowFiles"] = sum(n.lower().endswith((".ktr", ".kjb")) for n in names)
        jars = [n for n in names if n.lower().endswith(".jar") or (
                "/data-integration-hikvision/" in n and n.lower().endswith((".zip", ".war", ".kar")))]
        self.stats["outerJarFiles"] = sum(n.lower().endswith(".jar") for n in names)
        self.stats["skippedNonRuntimeArchiveFiles"] = sum(n.lower().endswith((".zip", ".war", ".kar")) and n not in jars for n in names)
        for index, name in enumerate(jars):
            try:
                self.scan_jar(name)
            except (BadZipFile, ValueError, KeyError) as exc:
                self.errors.append({"source": name, "error": type(exc).__name__})
            if (index + 1) % 100 == 0:
                print("inspected %s/%s outer runtime archives" % (index + 1, len(jars)), file=sys.stderr)
        for name in names:
            if PurePosixPath(name).name in DESCRIPTORS:
                try:
                    self.registrations.extend(xml_registrations(self.outer.read(name), name))
                except ET.ParseError:
                    self.errors.append({"source": name, "error": "descriptor_xml_invalid"})
        for registration in self.registrations:
            if registration["kind"] == "unknown":
                klass = self.candidates.get(registration["classname"])
                if klass:
                    registration["kind"] = "job-entry" if klass["superClass"].endswith(".JobEntryBase") else "step"
        return self

    def class_info(self, source):
        if source not in self.classes:
            raw = self.read_member(source)
            self.classes[source] = (parse_class(raw), sha256(raw))
        return self.classes[source]

    def resolve(self, raw, registration, locale):
        package = registration.get("i18nPackageName") or registration["classname"].rsplit(".", 1)[0]
        key = raw
        if raw.startswith("i18n:"):
            _, package, key = raw.split(":", 2)
        resources = self.resource_index.get(key, [])
        expected = package.replace(".", "/") + "/messages/"
        preferred_container = registration["source"].split("!/")[0]
        suffix = "messages_%s.properties" % locale
        matches = [r for r in resources if r["entry"].endswith(suffix)]
        matches.sort(key=lambda r: (not r["entry"].startswith(expected),
                                    not r["source"].startswith(preferred_container + "!/"), r["source"]))
        if matches:
            best = matches[0]
            return {"text": safe_text(best["values"][key]), "source": best["source"], "key": key,
                    "evidence": "message_resource" if best["entry"].startswith(expected) else "message_key_fallback"}
        return {"text": safe_text(raw), "source": registration["source"], "evidence": "unresolved_key" if raw.startswith("i18n:") else "literal"}

    def translated(self, registration, key, locale):
        localized = registration.get("localized", {}).get(key, {}).get(locale)
        return self.resolve(localized or registration.get(key, ""), registration, locale)

    def class_variants(self, name, config=False):
        groups = {}
        for source in self.class_index.get(name, []):
            try:
                parsed, digest = self.class_info(source)
            except (ValueError, IndexError, struct.error) as exc:
                self.errors.append({"source": source, "error": type(exc).__name__})
                continue
            if digest in groups:
                groups[digest]["sources"].append(source)
                continue
            entry = {"className": name, "sha256": digest, "sources": [source],
                     "superClass": parsed["superClass"], "interfaces": parsed["interfaces"]}
            if config:
                methods = {m["name"]: m for m in parsed["methods"]}
                entry["declaredInstanceFields"] = [{"name": f["name"], "jvmType": f["descriptor"],
                    "accessors": [m for m in methods if m in {"get" + f["name"][:1].upper() + f["name"][1:],
                        "set" + f["name"][:1].upper() + f["name"][1:], "is" + f["name"][:1].upper() + f["name"][1:]}]}
                    for f in parsed["fields"] if not f["access"] & 0x0008 and not f["access"] & 0x1000]
                method_keys = {}
                structural_keys = set()
                for method in parsed["methods"]:
                    if method["name"] in {"getXML", "loadXML", "saveRep", "readRep", "loadXMLConfig", "loadXMLFile"}:
                        values = method.get("codeReferences", {}).get("strings", [])
                        keys = {v for v in values if IDENTIFIER.match(v) and v not in self.resource_index
                                and v not in {"true", "false", "UTF-8", "US-ASCII", "None", "Zip", "admin", "root", "guest"}}
                        tags = {tag for v in values for tag in re.findall(r"</?([A-Za-z_][\w.-]*)\s*>", v)}
                        structural_keys.update(tags)
                        keys.update(tags)
                        method_keys.setdefault(method["name"], set()).update(keys)
                field_names = {f["name"].lower().replace("_", "") for f in entry["declaredInstanceFields"]}
                entry["serializationIdentifierCandidates"] = [
                    {"identifier": key, "methods": sorted(m for m, keys in method_keys.items() if key in keys)}
                    for key in sorted(set().union(*method_keys.values()) if method_keys else set())
                    if key in structural_keys or key.lower().replace("_", "") in field_names
                    or sum(key in keys for keys in method_keys.values()) > 1]
                entry["configurationEvidence"] = "declared_fields_and_serialization_method_literals_not_a_complete_form_schema"
            groups[digest] = entry
        return list(groups.values())

    def related_classes(self, name, kind):
        found = defaultdict(set)
        for source in self.class_index.get(name, []):
            parsed, _ = self.class_info(source)
            for method in parsed["methods"]:
                refs = method.get("codeReferences", {})
                if method["name"] in {"getStep", "getDialog", "getDialogClassName"}:
                    for candidate in refs.get("newClasses", []):
                        role = "dialog" if method["name"] != "getStep" else "implementation"
                        if candidate in self.class_index:
                            found[(role, candidate)].add(method["name"] + ":new")
                    for candidate in refs.get("strings", []):
                        if CLASS_NAME.match(candidate) and "Dialog" in candidate and candidate in self.class_index:
                            found[("dialog", candidate)].add(method["name"] + ":class_name_literal")
                    for candidate in refs.get("classConstants", []):
                        if "Dialog" in candidate and candidate in self.class_index:
                            found[("dialog", candidate)].add(method["name"] + ":class_constant")
        if kind == "job-entry":
            found[("implementation", name)].add("job_entry_registration")
        candidates = {name[:-4] + "Dialog" if name.endswith("Meta") else name + "Dialog"}
        candidates |= {x.replace(".di.trans.", ".di.ui.trans.").replace(
            ".di.job.", ".di.ui.job.") for x in candidates}
        for candidate in sorted(candidates):
            if candidate in self.class_index:
                found[("dialog", candidate)].add("naming_convention_and_class_presence")
        return [{"role": role, "className": candidate, "linkEvidence": sorted(evidence),
                 "variants": self.class_variants(candidate)} for (role, candidate), evidence in sorted(found.items())]

    def dialog_labels(self, related, registration):
        labels = {}
        for relation in related:
            if relation["role"] != "dialog":
                continue
            for source in self.class_index[relation["className"]]:
                parsed, _ = self.class_info(source)
                for method in parsed["methods"]:
                    for key in method.get("codeReferences", {}).get("strings", []):
                        if IDENTIFIER.match(key) and re.search(r"(?:Label|ColumnInfo|Tooltip|Tab|Group)(?:\.|$)", key):
                            if key not in labels:
                                labels[key] = {"key": key, "referencedBy": relation["className"],
                                               "zhCN": self.resolve(key, registration, "zh_CN")}
        return [labels[k] for k in sorted(labels)]

    def catalog(self):
        grouped = defaultdict(list)
        other_registrations = []
        for registration in self.registrations:
            if registration["kind"] not in {"step", "job-entry"}:
                other_registrations.append(registration)
                continue
            grouped[(registration["kind"], registration["id"], registration["classname"])].append(registration)
        plugins = []
        for (kind, plugin_id, name), registrations in sorted(grouped.items()):
            registration = sorted(registrations, key=lambda r: (r["registrationType"] != "descriptor", r["source"]))[0]
            zh_name = self.translated(registration, "description", "zh_CN")
            en_name = self.translated(registration, "description", "en_US")
            zh_category = self.translated(registration, "category", "zh_CN")
            en_category = self.translated(registration, "category", "en_US")
            if not re.search(r"[\u3400-\u9fff]", zh_name["text"]) and plugin_id in EXTENSION_NAMES_ZH:
                zh_name = {"text": EXTENSION_NAMES_ZH[plugin_id], "evidence": "editorial_translation",
                           "sourceText": en_name["text"]}
            if not re.search(r"[\u3400-\u9fff]", zh_category["text"]):
                token = en_category["text"].rsplit(".", 1)[-1]
                if token in CATEGORY_ZH:
                    zh_category = {"text": CATEGORY_ZH[token], "evidence": "editorial_translation", "sourceText": en_category["text"]}
            variants = self.class_variants(name, config=True)
            related = self.related_classes(name, kind)
            private = name.startswith("com.hikvision.") or plugin_id.startswith("HBP") or plugin_id == "CloudStore"
            extension = private or name.startswith("com.ruckuswireless.")
            plugins.append({"catalogKey": "%s:%s:%s" % (kind, plugin_id, name), "kind": kind, "id": plugin_id,
                "name": {"zhCN": zh_name, "enUS": en_name}, "category": {"zhCN": zh_category, "enUS": en_category},
                "implementationFamily": "hikvision_named_extension" if private else "bundled_extension" if extension else "pentaho_or_other",
                "registeredClass": name, "registrations": registrations, "registeredClassVariants": variants,
                "relatedClasses": related, "dialogLabelCandidates": self.dialog_labels(related, registration),
                "stepAttributeDescriptors": self.attribute_descriptors.get(name.rsplit(".", 1)[0].replace(".", "/"), []),
                "evidenceStatus": {"discovered": True, "runtimeLoaded": False, "configurationValidated": False,
                                   "executionValidated": False, "browserAdapterAvailable": False},
                "notes": ["class_loading_precedence_unverified"] if len(variants) > 1 else []})
        by_id = defaultdict(list)
        by_archive_hash = defaultdict(list)
        for plugin in plugins:
            by_id[(plugin["kind"], plugin["id"])].append(plugin["catalogKey"])
        for archive in self.archives:
            by_archive_hash[archive["sha256"]].append(archive["source"])
        registered = {p["registeredClass"] for p in plugins}
        unregistered = [{"className": name, "superClass": parsed["superClass"],
                         "sources": self.class_index[name], "variants": self.class_variants(name, config=True),
                         "status": "class_candidate_without_registration"}
                        for name, parsed in sorted(self.candidates.items()) if name not in registered
                        and not parsed["access"] & 0x0400 and "$" not in name]
        summary = {**dict(self.stats), "archives": len(self.archives), "uniqueArchiveHashes": len(by_archive_hash),
            "archivesByType": dict(Counter(a["archiveType"] for a in self.archives)),
            "registrations": len(self.registrations), "catalogEntries": len(plugins), "logicalPluginIds": len(by_id),
            "byKind": dict(Counter(p["kind"] for p in plugins)),
            "categoriesZhCN": dict(sorted(Counter(p["category"]["zhCN"]["text"] for p in plugins).items())),
            "hikvisionNamedEntries": sum(p["implementationFamily"] == "hikvision_named_extension" for p in plugins),
            "bundledExtensionEntries": sum(p["implementationFamily"] == "bundled_extension" for p in plugins),
            "entriesWithMultipleClassVariants": sum(len(p["registeredClassVariants"]) > 1 for p in plugins),
            "entriesWithoutRegisteredClass": sum(not p["registeredClassVariants"] for p in plugins),
            "entriesWithDialogEvidence": sum(any(c["role"] == "dialog" for c in p["relatedClasses"]) for p in plugins),
            "entriesWithImplementationEvidence": sum(any(c["role"] == "implementation" for c in p["relatedClasses"]) for p in plugins),
            "entriesWithStepAttributeDescriptors": sum(bool(p["stepAttributeDescriptors"]) for p in plugins),
            "entriesWithChineseName": sum(bool(re.search(r"[\u3400-\u9fff]", p["name"]["zhCN"]["text"])) for p in plugins),
            "unregisteredClassCandidates": len(unregistered), "otherPluginDescriptors": len(other_registrations), "errors": len(self.errors)}
        return {"schemaVersion": SCHEMA_VERSION, "source": {"fileName": self.path.name,
                "sha256": self.file_hash(), "bytes": self.path.stat().st_size},
            "method": "offline_zip_xml_annotations_classfile_and_message_resources",
            "limitations": ["Not a loaded Kettle registry; classpath precedence, plugin activation and dependencies are unverified.",
                "Instance fields and serialization literals are candidates; nested models, defaults, constraints and behavior require adapter work.",
                "No KTR/KJB contents, runtime settings, credential values or service addresses are extracted.",
                "Labels with editorial_translation are curated translations; message_key_fallback requires UI confirmation.",
                "No plugin classes are executed. Runtime/browser flags deliberately remain false for every entry."],
            "summary": summary, "plugins": plugins, "archives": self.archives,
            "duplicateArchives": [{"sha256": h, "sources": sources} for h, sources in sorted(by_archive_hash.items()) if len(sources) > 1],
            "duplicateIdsWithDifferentRegisteredClasses": [{"kind": k[0], "id": k[1], "catalogKeys": v} for k, v in sorted(by_id.items()) if len(v) > 1],
            "unregisteredClassCandidates": unregistered, "otherPluginDescriptors": other_registrations, "errors": self.errors}

    def file_hash(self):
        digest = hashlib.sha256()
        with self.path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        return digest.hexdigest()


def self_test():
    import unittest

    class ParserTests(unittest.TestCase):
        def test_resource_encoding_and_continuation(self):
            self.assertEqual(properties(b"name=\\u8f93\\u5165\nkey=first\\\n second\n"), {"name": "输入", "key": "firstsecond"})

        def test_descriptors_and_locales(self):
            raw = b'<plugin id="A" classname="example.AMeta"><localized_description><description locale="zh_CN">A</description></localized_description></plugin>'
            entry = xml_registrations(raw, "plugins/steps/a/plugin.xml")[0]
            self.assertEqual((entry["id"], entry["kind"], entry["localized"]["description"]["zh_CN"]), ("A", "step", "A"))
            self.assertEqual(xml_registrations(b'<transformation><step/></transformation>', "sample.ktr"), [])

        def test_opcode_operands_are_not_opcodes(self):
            cp = [None, (1, "field_name"), (8, 1)]
            self.assertEqual(bytecode_references(bytes([17, 18, 2, 18, 2, 177]), cp)["strings"], ["field_name"])

        def test_class_annotation_and_field_fixture(self):
            # A synthetic class-file fixture; no compiler/JVM/plugins needed.
            strings = ["example/Meta", "java/lang/Object", "field", "Ljava/lang/String;", "RuntimeVisibleAnnotations",
                       "Lorg/pentaho/di/core/annotations/Step;", "id", "FixtureStep", "name", "Fixture.Name"]
            cp = b"".join(b"\x01" + struct.pack(">H", len(s)) + s.encode() for s in strings)
            cp += b"\x07\x00\x01\x07\x00\x02"
            annotation = struct.pack(">HHHHcHHcH", 1, 6, 2, 7, b"s", 8, 9, b"s", 10)
            raw = struct.pack(">IHHH", 0xCAFEBABE, 0, 52, 13) + cp
            raw += struct.pack(">HHHHHHHHHH", 1, 11, 12, 0, 1, 2, 3, 4, 0, 0)
            raw += struct.pack(">HHI", 1, 5, len(annotation)) + annotation
            parsed = parse_class(raw)
            self.assertEqual(parsed["name"], "example.Meta")
            self.assertEqual(parsed["fields"][0]["name"], "field")
            self.assertEqual(parsed["annotations"][0]["values"]["id"], "FixtureStep")

        def test_redaction(self):
            text = safe_text("server 192.0.2.7 https://example.invalid/path password=fixture")
            self.assertNotIn("192.0.2.7", text)
            self.assertNotIn("example.invalid", text)
            self.assertNotIn("fixture", text)

    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(ParserTests))
    return 0 if result.wasSuccessful() else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip", type=Path, help="original Kettle attachment; never extracted or executed")
    parser.add_argument("--output", type=Path, help="runtime evidence catalog.json; do not commit generated data")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if not args.zip or not args.output:
        parser.error("--zip and --output are required unless --self-test is used")
    inventory = Inventory(args.zip).scan()
    catalog = inventory.catalog()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.output), "source": catalog["source"], "summary": catalog["summary"]}, ensure_ascii=False, indent=2))
    return 1 if catalog["errors"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
