# 前端浏览器兼容基线

## 支持范围

生产包最低支持Chrome 64。该版本是Vite 6原生ES Module、动态import和
`import.meta`构建方式的官方下限。开发服务器仍面向现代浏览器，不作为旧
Chrome验收入口。

Chrome 63及以下需要额外的SystemJS/legacy双包，不在当前支持范围内。不得
仅凭首页偶然显示正常，把更低版本描述为整个平台已经兼容。

## 根因与处理

v4.4.1沿用Vite 6默认`modules`目标，相当于Chrome 87。对应生产包117个
JavaScript文件中有20个仍含原生空值合并运算符，共200处；主入口与登录页
均受影响，Chrome 80以前可能直接报`Unexpected token ?`。

v4.4.2采用两层兼容策略：

1. `vite.config.js`将`build.target`固定为`chrome64`，由esbuild降低浏览器
   无法解析的新语法。
2. `src/polyfills/browserCompatibility.js`在Vue初始化前加载，只补齐代码实际
   使用且Chrome 64缺少的标准能力：`globalThis`、`Array.at/flat/flatMap`、
   `Object.fromEntries`和`String.replaceAll`。

`WeakRef`无法完全模拟垃圾回收语义。旧内核中的后备实现只提供RevoGrid使用
的构造和`deref()`契约，并在页面生命周期内持有强引用；支持原生`WeakRef`
的浏览器不会被覆盖。

巡检指标的精确数字分组使用字符串算法，不再依赖Chrome 67才提供的BigInt，
因此不会降低Chrome 64最低基线。

## 交付校验

`npm run verify:frontend`按顺序执行源码契约测试、生产构建和产物兼容扫描。
`scripts/check-browser-compat.mjs`会解析`dist`下全部JavaScript，发现以下语法
即返回失败：

- 可选链与空值合并；
- 逻辑赋值运算符；
- BigInt字面量；
- 私有类字段、私有方法和静态初始化块。

发布前还要在浏览器中临时移除上述标准方法，再确认兼容入口能够恢复它们并
正常渲染登录页。该测试只作用于隔离的本地页面，不修改用户浏览器配置。

## 维护约束

- 新增较新的JavaScript内置方法时，先检查Chrome 64支持状态；确需使用时，
  在统一兼容入口增加精确的`core-js`模块和测试，不在业务页面分散打补丁。
- 不导入完整`core-js`，避免无关全局改写和不必要的首屏体积。
- 不用正则替换生产代码；语法降级必须由Vite/esbuild完成。
- 若未来要支持Chrome 63及以下，单独评估官方`@vitejs/plugin-legacy`、额外包
  体积、CSP内联脚本和全业务回归，再调整本文件的支持范围。

## v4.4.2验证记录

- 修改前生产包共117个JavaScript文件，其中20个文件含200处空值合并语法；
  修改后同样117个文件的受限语法计数全部为0。
- 兼容入口测试主动移除6组目标方法后，所有方法均由启动入口恢复，登录页
  正常挂载，浏览器控制台无警告和错误。
- `npm run verify:frontend`通过：UI Guard为0错误、0警告、0例外，135项
  前端测试通过，2,800个模块生产构建和产物兼容扫描成功。
- `npm audit --omit=dev`未发现`core-js`漏洞；仓库原有Axios、ECharts和
  js-cookie告警不在本次兼容修复范围内，未执行破坏性的自动升级。
