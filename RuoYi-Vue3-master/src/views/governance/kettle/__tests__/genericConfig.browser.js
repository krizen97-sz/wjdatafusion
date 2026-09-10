import { describe, fieldValue, writeField, addRow, removeRow, addGroup, rowField, fieldType, parseTemplate, children, nativeLabel } from '../genericConfig.js'

// Run in an actual browser DOM. Every fixture is synthetic and no network/engine calls are made.
export function runGenericConfigTests() {
  const results = []
  const xml = text => new DOMParser().parseFromString(text, 'application/xml').documentElement
  const text = node => new XMLSerializer().serializeToString(node)
  const assert = (condition, message) => { if (!condition) throw new Error(message) }
  const throws = action => { let failed = false; try { action() } catch { failed = true } assert(failed, 'Expected operation to be rejected') }
  const test = (name, action) => { try { action(); results.push({ name, passed: true }) } catch (error) { results.push({ name, passed: false, error: error.message }) } }
  test('describing native configuration does not mutate unknown XML', () => {
    const node = xml('<step future="keep"><type>Custom</type><sql><![CDATA[a < b && c > d]]><!--note--></sql><future><leaf mode="new">value</leaf></future></step>')
    const before = text(node); describe(node, '<fields><field><name/></field></fields>'); assert(before === text(node), 'Description changed XML')
  })
  test('plugin fragments and native or step wrappers resolve to the same configuration', () => {
    const node = xml('<step><fields/></step>'), body = '<fields><field><name/></field></fields>'
    for (const template of [body, `<step>${body}</step>`, `<native>${body}</native>`, `<native><step>${body}</step></native>`]) {
      const groups = describe(node, template).groups
      assert(groups.length === 1 && groups[0].name === 'fields' && groups[0].element === node.firstElementChild, 'Wrapper was inserted as a plugin field')
    }
  })
  test('editing scalar preserves CDATA comments and attributes', () => {
    const node = xml('<step><sql extension="keep"><![CDATA[old < expression]]><!--comment--></sql><future unchanged="yes"/></step>')
    writeField(describe(node).fields.find(f => f.name === 'sql'), 'new < expression && true')
    assert(text(node).includes('<![CDATA[new < expression && true]]>'), 'CDATA type lost')
    assert(text(node).includes('<!--comment-->') && text(node).includes('extension="keep"') && text(node).includes('unchanged="yes"'), 'Unknown data lost')
  })
  test('heterogeneous repeated row columns can be added only to the selected row', () => {
    const node = xml('<records><record><name>a</name><future flavor="v2">first</future></record><record><name>b</name></record></records>')
    const group = describe(node).groups[0], column = group.columns.find(c => c.key === 'future')
    writeField(rowField(group.rows[1], column, group.template), 'second')
    assert(group.rows[0].querySelector('future').textContent === 'first', 'Other row changed')
    assert(group.rows[1].querySelector('future').getAttribute('flavor') === 'v2', 'Native extension structure lost')
  })
  test('deleting a selected row preserves siblings and interleaved extensions', () => {
    const node = xml('<records><record id="first">a</record><!--keep--><future key="x"/><record id="second">b</record></records>')
    const group = describe(node).groups.find(g => g.name === 'record'); removeRow(group, group.rows[1])
    assert(node.querySelectorAll('record').length === 1 && node.querySelector('record').getAttribute('id') === 'first', 'Wrong row deleted')
    assert(text(node).includes('<!--keep-->') && node.querySelector('future').getAttribute('key') === 'x', 'Unknown siblings lost')
  })
  test('empty collections use native allocated rows without normalizing boolean literals', () => {
    const node = xml('<step><fields/></step>'), template = '<fields><field native="yes"><ascending>N</ascending><date_format_lenient>false</date_format_lenient></field></fields>'
    const section = describe(node, template).groups[0], group = describe(section.element, section.template).groups[0]
    const row = addRow(group), fields = describe(row).fields
    assert(row.querySelector('ascending').textContent === 'N' && row.querySelector('date_format_lenient').textContent === 'false', 'Boolean encoding changed')
    assert(fields.find(f => f.name === 'ascending').type === 'boolean' && fields.find(f => f.name === 'date_format_lenient').type === 'truefalse', 'Boolean controls use incorrect encoding')
  })
  test('readonly rejects scalar add and delete mutations', () => {
    const node = xml('<fields><field><name>a</name></field><field><name>b</name></field></fields>'), before = text(node), group = describe(node).groups[0]
    throws(() => writeField(describe(group.rows[0]).fields[0], 'new', { readonly: true }))
    throws(() => addRow(group, { readonly: true })); throws(() => removeRow(group, group.rows[0], { readonly: true }))
    assert(before === text(node), 'Readonly XML changed')
  })
  test('optional native group is only created on an explicit action', () => {
    const node = xml('<step><untouched value="x"/></step>'), group = describe(node, '<options schema="native"><timeout>30</timeout></options>').groups[0]
    assert(!node.querySelector('options'), 'Optional group appeared while reading'); addGroup(group)
    assert(node.querySelector('options').getAttribute('schema') === 'native' && node.querySelector('untouched').getAttribute('value') === 'x', 'Wrong group created')
  })
  test('mixed text edits retain nested elements and comments', () => {
    const node = xml('<mixed>before<item value="keep"/><!--note-->after</mixed>'), child = node.querySelector('item')
    writeField(describe(node).fields.find(f => f.ownText), 'replacement')
    assert(node.querySelector('item') === child && text(node).includes('<!--note-->'), 'Mixed content children were rebuilt')
    assert(node.textContent === 'replacement', 'Direct text update failed')
  })
  test('new rows remove secret identities and values but preserve semantic binding attributes', () => {
    const node = xml('<records><record data-rynew-id="old" data-rynew-definition-id="bound"><password data-rynew-secret-id="old-secret">synthetic-secret</password><future keep="yes"/></record><record><password/></record></records>')
    const group = describe(node).groups[0], row = addRow(group)
    assert(!row.hasAttribute('data-rynew-id') && !row.querySelector('password').hasAttribute('data-rynew-secret-id'), 'Identity copied')
    assert(row.querySelector('password').textContent === '' && row.getAttribute('data-rynew-definition-id') === 'bound', 'Secret or binding clone incorrect')
    assert(row.querySelector('future').getAttribute('keep') === 'yes', 'Unknown clone structure lost')
  })
  test('row removal rejects foreign-parent rows', () => {
    const node = xml('<fields><field><name>a</name></field></fields>'), other = xml('<fields><field/></fields>'), group = describe(node).groups[0]
    throws(() => removeRow(group, other.firstElementChild)); assert(children(node).length === 1, 'Foreign removal changed current group')
  })
  test('unknown namespace attributes retain namespace and neighboring fields', () => {
    const node = xml('<settings xmlns:x="urn:synthetic" x:future="before"><other>keep</other></settings>')
    const field = describe(node).fields.find(f => f.key === '@x:future'); writeField(field, 'after')
    assert(node.getAttributeNode('x:future').namespaceURI === 'urn:synthetic' && node.getAttribute('x:future') === 'after', 'Namespace attribute changed shape')
    assert(node.querySelector('other').textContent === 'keep', 'Sibling changed')
  })
  test('malformed or external-entity templates are rejected', () => {
    const node = xml('<step/>'); throws(() => parseTemplate('<!DOCTYPE a [<!ENTITY x SYSTEM "file:///synthetic">]><field/>', node)); throws(() => parseTemplate('<field>', node))
  })
  test('repeated scalar values remain individually editable', () => {
    const node = xml('<values><value>first</value><value>second</value></values>'), group = describe(node).groups[0]
    writeField(rowField(group.rows[1], group.columns[0], group.template), 'changed')
    assert(group.rows[0].textContent === 'first' && group.rows[1].textContent === 'changed', 'Scalar rows were conflated')
  })
  test('identifiers and integers never undergo implicit number coercion', () => {
    const node = xml('<settings><id>00012</id><limit>9223372036854775807</limit></settings>'), fields = describe(node).fields
    assert(fieldType('id', '00012') === 'text' && fieldValue(fields[1]) === '9223372036854775807', 'Native string or integer precision changed')
  })
  test('unknown native names do not resolve JavaScript prototype properties', () => {
    for (const name of ['constructor', '__proto__', 'toString']) assert(nativeLabel(name) === name, 'Native name was replaced by a prototype property')
  })
  return { total: results.length, passed: results.filter(r => r.passed).length, results }
}
