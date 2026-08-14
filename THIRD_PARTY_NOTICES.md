# Third-Party Notices

本项目分发的构建产物中打包了以下第三方前端库（位于 `src/main/resources/cdn/`）。
部分库的压缩构建剥离了原始许可声明，此处统一列出以符合各许可证的署名要求。

| 库 | 版本 | 许可证 | 上游 |
|----|------|--------|------|
| Vue.js | 3.3.4 | MIT | https://github.com/vuejs/core |
| axios | 1.4.0 | MIT | https://github.com/axios/axios |
| three.js | r128（2021 构建，修订号未内嵌） | MIT | https://github.com/mrdoob/three.js |
| three.js examples（viewer3d_libs/：EffectComposer、RenderPass、ShaderPass、UnrealBloomPass、SMAAPass、CopyShader、FXAAShader、SMAAShader、LuminosityHighPassShader、Pass） | r128 同期 | MIT | https://github.com/mrdoob/three.js/tree/dev/examples/jsm |
| ECharts | 5.5.0 | Apache-2.0 | https://github.com/apache/echarts |
| fzstd | 未内嵌版本号 | MIT | https://github.com/101arrowz/fzstd |

各许可证全文见上游仓库。MIT 与 Apache-2.0 许可证允许随 AGPL-3.0 项目打包分发，此文件作为署名声明。

构建还依赖以下库（Gradle 依赖，随插件打包）：

| 库 | 许可证 |
|----|--------|
| kotlinx-serialization (Apache-2.0) | https://github.com/Kotlin/kotlinx.serialization |
| zstd-jni (BSD-2-Clause) | https://github.com/luben/zstd-jni |
| Kotlin Stdlib (Apache-2.0) | https://kotlinlang.org |
| TabooLib (MIT) | https://github.com/TabooLib/taboolib |
