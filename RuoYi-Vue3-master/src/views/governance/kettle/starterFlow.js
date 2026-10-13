// Synthetic, local-only native Kettle example. INPUT_DIR and WORK_DIR remain separate.
export const STARTER_ID = 'csv-clean-sort-v1'
export const STARTER_FILENAME = 'sample.csv'
export const STARTER_CSV = 'name\nbob\nalice\n张三\n'
export const STARTER_PREVIEW_NODE = '按姓名排序'
const escapeXml = text => String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;')

export function starterFlowXml(name) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<transformation data-rynew-timezone="Asia/Shanghai" data-rynew-example="${STARTER_ID}">
  <info><name>${escapeXml(name)}</name><directory>/</directory><trans_type>Normal</trans_type><size_rowset>100</size_rowset><parameters/></info>
  <order>
    <hop><from>读取示例CSV</from><to>整理姓名字段</to><enabled>Y</enabled></hop>
    <hop><from>整理姓名字段</from><to>按姓名排序</to><enabled>Y</enabled></hop>
    <hop><from>按姓名排序</from><to>输出结果文件</to><enabled>Y</enabled></hop>
  </order>
  <step><name>读取示例CSV</name><type>CsvInput</type><copies>1</copies><distribute>Y</distribute>
    <filename>\${INPUT_DIR}/sample.csv</filename><separator>,</separator><enclosure>"</enclosure><header>Y</header>
    <encoding>UTF-8</encoding><buffer_size>50000</buffer_size><lazy_conversion>N</lazy_conversion><newline_possible>N</newline_possible>
    <fields><field><name>name</name><type>String</type><length>-1</length><precision>-1</precision><trim_type>none</trim_type></field></fields>
    <GUI><xloc>80</xloc><yloc>120</yloc><draw>Y</draw></GUI>
  </step>
  <step><name>整理姓名字段</name><type>ScriptValueMod</type><copies>1</copies><distribute>Y</distribute>
    <compatible>N</compatible><optimizationLevel>0</optimizationLevel>
    <jsScripts><jsScript><jsScript_type>0</jsScript_type><jsScript_name>整理姓名</jsScript_name><jsScript_script>var cleaned_name = String(name).replace(/^\\s+|\\s+$/g, "").toUpperCase();</jsScript_script></jsScript></jsScripts>
    <fields><field><name>cleaned_name</name><type>String</type><length>-1</length><precision>-1</precision><replace>N</replace></field></fields>
    <GUI><xloc>320</xloc><yloc>120</yloc><draw>Y</draw></GUI>
  </step>
  <step><name>按姓名排序</name><type>SortRows</type><copies>1</copies><distribute>Y</distribute>
    <directory>\${WORK_DIR}</directory><prefix>sort</prefix><sort_size>1000</sort_size><free_memory>25</free_memory><compress>N</compress><unique_rows>N</unique_rows>
    <fields><field><name>cleaned_name</name><ascending>Y</ascending><case_sensitive>Y</case_sensitive><presorted>N</presorted></field></fields>
    <GUI><xloc>560</xloc><yloc>120</yloc><draw>Y</draw></GUI>
  </step>
  <step><name>输出结果文件</name><type>TextFileOutput</type><copies>1</copies><distribute>Y</distribute>
    <separator>,</separator><enclosure/><header>Y</header><footer>N</footer><format>UNIX</format><compression>None</compression><encoding>UTF-8</encoding><create_parent_folder>Y</create_parent_folder>
    <file><name>\${WORK_DIR}/result</name><extention>csv</extention><is_command>N</is_command><servlet_output>N</servlet_output><append>N</append><split>N</split><haspartno>N</haspartno><add_date>N</add_date><add_time>N</add_time><splitevery>0</splitevery><max_wait_time_ms>0</max_wait_time_ms><rename_file_name>N</rename_file_name></file>
    <fields><field><name>name</name><type>String</type><length>-1</length><precision>-1</precision><trim_type>none</trim_type></field><field><name>cleaned_name</name><type>String</type><length>-1</length><precision>-1</precision><trim_type>none</trim_type></field></fields>
    <GUI><xloc>800</xloc><yloc>120</yloc><draw>Y</draw></GUI>
  </step>
</transformation>`
}

export function starterInputForm() {
  const form = new FormData()
  form.append('file', new Blob([STARTER_CSV], { type: 'text/csv;charset=utf-8' }), STARTER_FILENAME)
  return form
}
