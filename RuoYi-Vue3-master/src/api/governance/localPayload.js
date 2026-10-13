/** Carry graph content losslessly through the platform's ordinary JSON/XSS boundary. */
export function localDefinitionPayload({ name, revision, graph }) {
  if (!graph || typeof graph !== 'object' || Array.isArray(graph)) throw new Error('缺少有效的流程定义')
  const bytes = new TextEncoder().encode(JSON.stringify(graph))
  let binary = ''
  for (const value of bytes) binary += String.fromCharCode(value)
  return { name, ...(revision === undefined ? {} : { revision }), graphBase64: btoa(binary) }
}
