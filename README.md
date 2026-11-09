

<p align="center">
  <img src="https://picgo.2006111.xyz/pigo/picgo/ic_launcher_round.webp" width="100" height="100" alt="素履 Logo" />
</p>

<h1 align="center">Mooic</h1>

<p align="center">
  一个基于 Material 3 设计的 Android 网易云音乐客户端
</p>

<p align="center">
  <a href="./LICENSE">
      <img src="https://img.shields.io/github/v/release/kid-depress/Mooic?include_prereleases" alt="License" /></a>
  <img src="https://img.shields.io/badge/License-MIT-red.svg" alt="Platform" />
  <img src="https://img.shields.io/github/downloads/kid-depress/Mooic/total" />
</p>

## 说明

- 本项目为本人个人项目，仅用于个人学习研究，请勿用于商业用途。
- 本项目大部分界面和功能直接参考了 [JetMelo](https://github.com/rcmiku/JetMelo), api接口来自[api-enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced)
- 本项目代码都是英文字母的随机组合,不代表个人观点

## 功能特性

- 播放网易云音乐歌曲
- 音乐信息与账号功能由 App 直连网易云接口，无需额外 Node.js API 服务
- 内置「音源」管理页：音源开关、拖拽排序、线路检测、从 URL 导入
- 支持下载歌曲（标准 / 较高 / 极高 / 无损 / 母带），后台下载、下载管理页
- 后台播放占用极低
- 搜索网易云音乐中的歌曲、专辑和歌单
- 支持登录
- 支持同步歌词
- 提供个性化推荐

## 音源与下载

### 音源

设置 → 音源。列表顺序就是播放/下载的优先级，第一个启用且拿到完整地址的音源生效。

| 音源 | 说明 |
| --- | --- |
| 网易云音乐（官方） | 直连官方 `/song/url/v1`，能拿到多高音质取决于登录账号的会员等级；试听片段会自动跳过 |
| 匹配音源（Unblock） | 官方拿不到完整地址时，按线路顺序请求 `UnblockNeteaseMusic-utils` 兼容服务的 `/match` 接口 |

- 长按卡片右侧拖拽把手可调整优先级，卡片开关控制启用状态。
- 点击卡片里的线路标签可以单独「线路检测」：绿色正常、红色异常、灰色未检测。
- 「从 URL 导入」支持两种输入：
  1. 直接用匹配服务根地址，例如 `https://your-server.com`；
  2. 指向一份 JSON 描述：`{ "name": "青听音源", "type": "unblock", "url": "https://your-server.com", "providers": ["unm", "byfuns"] }`。
- 匹配音源卡片菜单里可以「修改地址」，留空则使用内置默认服务。

### 下载

歌曲菜单 → 下载 → 选择音质，任务进入「设置 → 下载管理」。

- 下载走的是当前音源顺序，VIP 歌曲请先登录对应的网易云账号，或配置可用的匹配音源。
- Android 10+ 通过 MediaStore 保存到 `Music/Mooic`，无需存储权限；更低版本保存到应用私有音乐目录。
- 下载在前台服务中进行，通知栏显示当前任务进度。

## 截图

<table>
  <tr>
    <td><img src="https://picgo.2006111.xyz/pigo/picgo/01.jpg" width="200" /></td>
    <td><img src="https://picgo.2006111.xyz/pigo/picgo/02.jpg" width="200" /></td>
    <td><img src="https://picgo.2006111.xyz/pigo/picgo/03.jpg" width="200" /></td>
  </tr>
  <tr>
    <td><img src="https://picgo.2006111.xyz/pigo/picgo/04.jpg" width="200" /></td>
    <td><img src="https://pub-77ce2dc4c08f493ca6b677b267453c9e.r2.dev/pigo/picgo/05.jpg" width="200" /></td> 
    <td><img src="https://picgo.2006111.xyz/pigo/picgo/06.jpg" width="200" /></td>
  </tr>
</table>




## 致谢
- [JetMelo](https://github.com/rcmiku/JetMelo)
- [InnerTune](https://github.com/z-huang/InnerTune)
- [Protobuf](https://github.com/protocolbuffers/protobuf)
- [Reorderable](https://github.com/Calvin-LL/Reorderable)
- [Ktor](https://github.com/ktorio/ktor)
- [Coil](https://github.com/coil-kt/coil)
- [Ksp](https://github.com/google/ksp)

## 免责声明

本软件与网易云音乐、杭州网易云音乐科技有限公司及其任何关联公司不存在隶属、资助、授权或认可关系。

本软件仅供学习与交流使用，不得用于商业用途。

