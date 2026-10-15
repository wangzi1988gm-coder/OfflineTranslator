# 离线翻译助手 — 架构与实现说明

> 目标：荣耀 500 Pro（MEP-AN00，Android 16 / MagicOS 10，12GB RAM + 512GB ROM）上的完全离线翻译、语音对讲与图片翻译应用。

## 1. 设计原则

1. **完全离线**：翻译、语音识别、OCR、朗读在手机本地执行；除首次安装与一次性模型导入外，断网后所有功能可用。
2. **窄而深**：四个语种（中 / 英 / 俄 / 塔吉克）双向 6 个方向，不做无关语种。
3. **可降级**：每条流水线都设计回退实现（系统 → 本地引擎；本地引擎缺失 → 词典/规则兜底），保证功能始终可用。
4. **模块化**：每个引擎有清晰接口，未来可替换实现。

## 2. 模块划分

```
com.example.offlinetranslator
├── App.kt                          Application，全局容器
├── MainActivity.kt                 Compose host
├── di/
│   └── AppContainer.kt             简易依赖注入容器
├── data/
│   ├── Language.kt                 语言枚举与 BCP-47 信息
│   ├── ModelPack.kt                模型包元数据（已下载、版本、大小）
│   ├── ModelManifest.kt            引擎模型清单（翻译/ASR/TTS）
│   ├── Settings.kt                 DataStore 设置（默认方向、TTS 引擎、麦克风增益等）
│   └── AppDatabase.kt              Room 历史记录
├── engine/
│   ├── translator/
│   │   ├── TranslationEngine.kt    接口
│   │   ├── MarianMtEngine.kt       ONNX Runtime Mobile 加载 OPUS-MT
│   │   ├── CascadeTranslator.kt    zh↔tg 通过 ru 做级联
│   │   ├── TranslatorRouter.kt     按语言对挑选最佳引擎
│   │   └── tokenize/
│   │       └── SentencePieceLite.kt  极简 SPM 解码（针对 Marian 分词）
│   ├── asr/
│   │   ├── AsrEngine.kt
│   │   ├── SherpaOnnxAsrEngine.kt  离线 ASR（zh/en/ru/tg）
│   │   ├── WhisperAsrEngine.kt     Whisper.cpp 兜底（更通用、稍慢）
│   │   └── AudioRecorder.kt        16 kHz / 16-bit PCM 录音
│   ├── ocr/
│   │   ├── OcrEngine.kt
│   │   └── MlKitOcrEngine.kt       中英俄 + 拉丁 / 西里尔 / 汉字
│   └── tts/
│       ├── TtsEngine.kt
│       ├── SystemTtsEngine.kt      Android 系统 TTS（首选）
│       └── SherpaOnnxTtsEngine.kt  VITS 离线 TTS（兜底）
├── model/
│   ├── ModelManager.kt             模型加载、卸载、占用统计
│   └── ModelStorage.kt             内部存储 / Android/data 路径
├── ui/
│   ├── theme/                      主题、字体、颜色
│   ├── component/                  通用组件
│   ├── screen/
│   │   ├── translate/TranslateScreen.kt
│   │   ├── translate/TranslateViewModel.kt
│   │   ├── conversation/ConversationScreen.kt
│   │   ├── conversation/ConversationViewModel.kt
│   │   ├── image/ImageTranslateScreen.kt
│   │   ├── image/ImageTranslateViewModel.kt
│   │   ├── models/ModelManageScreen.kt
│   │   └── settings/SettingsScreen.kt
│   └── nav/AppNav.kt
└── util/
    ├── Permission.kt
    ├── LocaleHelper.kt             BCP-47、脚本检测、塔吉克键盘辅助
    └── Logger.kt
```

## 3. 引擎选型

| 任务 | 首选实现 | 兜底实现 | 模型来源 |
| --- | --- | --- | --- |
| 翻译 zh↔en/zh↔ru | MarianMT（ONNX） | 系统 NMT / 词典 | Helsinki-NLP/OPUS-MT（CC BY）|
| 翻译 ru↔tg | OPUS-MT ru↔tg | — | Helsinki-NLP/OPUS-MT |
| 翻译 zh↔tg | 级联（zh→ru→tg） | 词典+规则 | 由上两个组合 |
| 翻译 en↔tg | 级联（en→ru→tg） | 词典+规则 | 由上两个组合 |
| 翻译 zh↔en/zh↔ru | 备用：NLLB-200 distilled（若空间允许） | — | Meta（CC BY-NC，本项目仅用于离线推理）|
| ASR | sherpa-onnx（CTC / Transducer） | Whisper.cpp tiny | sherpa-onnx 模型仓库 |
| OCR | ML Kit 拉丁 + 中文识别器 | Tesseract（可选） | Google ML Kit |
| TTS | Android 系统 TTS（用户已装语音） | sherpa-onnx VITS | sherpa-onnx 模型仓库 |

> 塔吉克语现状：高质量 ASR / TTS 模型较少。`tg` 优先复用俄语模型（俄塔互通度高），系统 TTS 选择俄语朗读西里尔塔吉克文时，多数设备可正常播报。若设备无塔吉克语音，本应用按 `tg → ru` 朗读策略兜底。

## 4. 模型放置与加载

```
/data/user/0/com.example.offlinetranslator/files/models/
├── translator/
│   ├── zh-en/        opus-mt-zh-en.int8.onnx + vocab
│   ├── en-zh/
│   ├── zh-ru/
│   ├── ru-zh/
│   ├── ru-tg/
│   └── tg-ru/
├── asr/
│   ├── zh/           sherpa-onnx streaming paraformer/zh
│   ├── en/
│   ├── ru/
│   └── tg/
└── tts/
    └── vits-zh/      sherpa-onnx vits 模型
```

模型管理界面会：
- 列出本地已安装模型
- 检查 `assets/` 中的内置默认模型
- 让用户从 SD 卡 / 应用下载目录导入
- 显示占用空间

## 5. 关键流水线

### 文本翻译
```
Text → 检测脚本 → 选引擎 → 分词 → ONNX 推理 → SPM 解码 → 译文
                       ↘ CascadeTranslator（zh↔tg, en↔tg）
```

### 语音对讲
```
按住麦克风 → ASR 引擎流式识别 → 翻译 → TTS 朗读
                                ↘ 对方语言再回译（可视化）
```

### 图片翻译
```
图片 → ML Kit OCR（多脚本） → 行切分 → 翻译 → 覆盖到原图
                              ↘ 多语言合成：原文 + 译文 双语图
```

## 6. 性能与内存

- 12GB RAM：ONNX 推理使用 NNAPI / XNNPACK 后端，单模型 < 500 MB 驻留。
- 模型 INT8 量化优先；FP16 备选。
- ASR / TTS 按需加载/卸载，避免长期驻留。
- Compose UI 使用 LazyColumn，避免 OOM。

## 7. 安全

- 应用不申请 INTERNET 权限（强制离线）。
- 不读取通讯录、相册以外的应用数据。
- 录音 / 相机权限按需申请。
