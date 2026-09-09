import 'core-js/actual/global-this.js'
import 'core-js/actual/array/at.js'
import 'core-js/actual/array/flat.js'
import 'core-js/actual/array/flat-map.js'
import 'core-js/actual/object/from-entries.js'
import 'core-js/actual/string/replace-all.js'

if (typeof globalThis.WeakRef === 'undefined') {
  class CompatibleWeakRef {
    constructor(target) {
      if ((typeof target !== 'object' && typeof target !== 'function') || target === null) {
        throw new TypeError('WeakRef target must be an object')
      }
      this.target = target
    }

    deref() {
      return this.target
    }
  }

  Object.defineProperty(globalThis, 'WeakRef', {
    configurable: true,
    writable: true,
    value: CompatibleWeakRef
  })
}
