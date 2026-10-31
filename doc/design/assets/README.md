# 设计配图（`doc/design/assets`）

| 文件 | 说明 |
|------|------|
| [readme-runtime-topology.svg](./readme-runtime-topology.svg) | 根 README 运行时拓扑图（由 `scripts/check-runtime-topology.py` 校验端口与真源一致） |
| [bone-hero-banner.webp](./bone-hero-banner.webp) | README 顶部 Hero 主视觉（横版 1536×1024） |
| [bone-poster.webp](./bone-poster.webp) | 宣传海报（竖版·深色 1024×1536） |
| [bone-poster-light.webp](./bone-poster-light.webp) | 宣传海报（竖版·浅色 1024×1536） |

无 CI 脚本写死路径（`readme-runtime-topology.svg` 除外，见上表）；新增示意图优先放本目录。

## 位图格式选择

栅格图统一用 **WebP（q88）**，不要提交等尺寸 PNG：

- 这三张图 PNG 合计 5.5 MB，同分辨率 WebP 合计 492 KB（降 91%），
  实测 PSNR 38–39.5 dB，肉眼无差异、文字边缘锐利；
- 仓库历史是只增不减的，二进制一旦提交就永久留在 packfile 里，
  事后转格式只能让**工作区**变轻，无法回收历史体积——所以格式要在**首次提交前**定好；
- GitHub / Gitee / 现代浏览器均原生支持 WebP 渲染与下载。

新增位图请先转换再提交（macOS 自带 `cwebp`）：

```bash
cwebp -q 88 -m 6 <name>.png -o <name>.webp && rm <name>.png
```
