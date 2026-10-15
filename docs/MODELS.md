# 模型获取与放置说明

> 本应用"真正离线"——所有模型都在本地运行。下面列出了每种模型的推荐来源、放置位置和预期大小。

## 1. 总览

| 类别 | 模型 | 推荐来源 | 推荐大小 | 备注 |
| --- | --- | --- | --- | --- |
| 翻译 | zh↔en | Helsinki-NLP/OPUS-MT | 300 MB/方向 | INT8 量化 |
| 翻译 | zh↔ru | Helsinki-NLP/OPUS-MT | 300 MB/方向 | INT8 |
| 翻译 | ru↔tg | Helsinki-NLP/OPUS-MT | 150 MB/方向 | 小型数据集 |
| ASR | zh | sherpa-onnx Paraformer | 250 MB | 流式 |
| ASR | en | sherpa-onnx Whisper Tiny | 75 MB | 多语言通用 |
| ASR | ru | sherpa-onnx 俄文 Paraformer | 250 MB | 塔吉克兜底 |
| TTS | zh | sherpa-onnx VITS-zh | 100 MB | |
| TTS | en | sherpa-onnx VITS-en | 100 MB | |
| TTS | ru | sherpa-onnx VITS-ru | 100 MB | 塔吉克兜底 |
| OCR | 多脚本 | Google ML Kit Text Recognition | 已内置 | APK 自带，无需手动放 |

完整安装后总占用约 2.5 GB；荣耀 500 Pro 512 GB 存储空间足够。

## 2. 翻译模型（OPUS-MT / MarianMT）

### 2.1 转换流程

Helsinki-NLP 在 HuggingFace 上的 OPUS-MT 模型为 PyTorch（`pytorch_model.bin` / `model.safetensors`），需要导出为 ONNX：

1. 安装导出工具：
   ```bash
   pip install transformers torch onnx onnxruntime
   ```
2. 一键导出脚本（节选）：
   ```python
   from transformers import MarianMTModel, MarianTokenizer
   import torch

   model = MarianMTModel.from_pretrained("Helsinki-NLP/opus-mt-zh-en")
   tok = MarianTokenizer.from_pretrained("Helsinki-NLP/opus-mt-zh-en")

   enc = model.get_encoder()
   dec = model.get_decoder()

   enc.eval(); dec.eval()
   dummy_ids = torch.ones(1, 8, dtype=torch.long)
   dummy_mask = torch.ones(1, 8, dtype=torch.long)

   torch.onnx.export(enc, (dummy_ids, dummy_mask),
       "encoder.onnx", input_names=["input_ids","attention_mask"],
       output_names=["last_hidden_state"],
       dynamic_axes={"input_ids":{1:"seq"}, "attention_mask":{1:"seq"},
                     "last_hidden_state":{1:"seq"}},
       opset_version=14)

   # decoder 导出需要额外参数；建议参考 HuggingFace 的 optimum/onnxruntime 工具
   ```

3. INT8 量化（强烈推荐）：
   ```bash
   python -m onnxruntime.quantization.quantize \
       --input encoder.onnx --output encoder.int8.onnx \
       --input decoder.onnx --output decoder.int8.onnx
   ```

### 2.2 必需文件

```
translator/zh-en/
├── encoder.onnx       或 encoder.int8.onnx
├── decoder.onnx       或 decoder.int8.onnx
├── source.spm         源语言 SPM 模型（huggingface 上同目录的 source.spm）
└── target.spm         目标语言 SPM 模型
```

> 备注：本工程的 `SentencePieceLite` 可读取 `.spm` 二进制（含 protobuf 头部）；如果转换出问题，可退化为 `vocab.src` / `vocab.tgt`（每行 `piece<TAB>id` 格式）。

### 2.3 塔吉克语（ru↔tg）

塔吉克语 OPUS-MT 模型在 HuggingFace 上以 Helsinki-NLP/OPUS-MT 仓库提供：
- `Helsinki-NLP/opus-mt-ru-tg`（俄→塔吉克）
- `Helsinki-NLP/opus-mt-tg-ru`（塔吉克→俄）

按相同流程导出 ONNX。

### 2.4 zh↔tg / en↔tg（级联）

直接 NMT 模型少。本工程走 `zh→ru→tg` / `en→ru→tg` 级联：
- 需要 `zh↔ru` 和 `ru↔tg` 两套模型。
- `TranslatorRouter` 自动组合，无需用户配置。

## 3. ASR 模型（sherpa-onnx）

### 3.1 推荐模型

| 语言 | 模型 | 大小 |
| --- | --- | --- |
| zh | `sherpa-onnx-streaming-paraformer-zh` | ~250 MB |
| en | `sherpa-onnx-whisper-tiny.en` 或 `sherpa-onnx-streaming-zipformer-en-2023-06-26` | ~75 MB / ~250 MB |
| ru | `sherpa-onnx-streaming-zipformer-ru` 或 Paraformer 俄文 | ~250 MB |

> 中文也可考虑：`csukuangfj/sherpa-onnx-streaming-paraformer-large-zh-cantonese-2024-03-11`（更准）。

### 3.2 必需文件

```
asr/zh/
├── encoder.onnx
├── decoder.onnx
├── joiner.onnx
└── tokens.txt
```

或单文件 Paraformer：
```
asr/zh/
├── model.onnx        (文件名不限，但扩展名 .onnx)
└── tokens.txt
```

### 3.3 下载来源

- GitHub Release：https://github.com/k2fsa/sherpa-onnx/releases/tag/asr-models
- HuggingFace：搜索 `sherpa-onnx` 组织

## 4. TTS 模型（sherpa-onnx VITS）

### 4.1 推荐模型

| 语言 | 模型 | 大小 |
| --- | --- | --- |
| zh | `vits-zh-hf-ayan` 或 `vits-mandarin` | ~100 MB |
| en | `vits-ljs` | ~100 MB |
| ru | `vits-russian` 或多语言 VITS | ~100 MB |

### 4.2 必需文件

```
tts/zh/
├── model.onnx
├── tokens.txt
├── espeak-ng-data/        （可选；部分 Piper 模型需要）
└── lexicon.txt            （可选）
```

### 4.3 塔吉克语 TTS

塔吉克语 TTS 模型暂缺开源版本。本工程使用 **俄文 VITS 兜底**（塔吉克语采用西里尔字母，俄文模型基本可读）。
- 效果：发音基本正确，但语调偏俄语。

## 5. OCR

OCR 使用 **Google ML Kit Text Recognition**，模型随 APK 自带，**无需手动下载**。
- 拉丁字母识别器：`com.google.mlkit:text-recognition`（默认拉丁）
- 中文识别器：`com.google.mlkit:text-recognition-chinese`
- 塔吉克语（西里尔）由拉丁识别器兜底识别（西里尔是 ML Kit 拉丁识别器的子支持集）。

## 6. 模型导入方式

### 6.1 运行时导入（推荐）

1. 把模型放到手机的 `Download/` 或任何目录，保持上文的目录结构。
2. 打开 App → 「模型」页 → 「导入模型目录」。
3. 通过系统的"文档选择器"指向根目录即可；App 会自动识别并拷贝。

最终位置：
```
/Android/data/com.example.offlinetranslator/files/models/
├── translator/zh-en/encoder.onnx ...
├── asr/zh/encoder.onnx ...
├── tts/zh/model.onnx ...
└── manifest.json
```

### 6.2 APK 内置

把模型放在 `app/src/main/assets/models/<category>/<key>/` 下。注意：
- assets 中模型**压缩会拖慢加载**；建议在 `app/build.gradle.kts` 添加：
  ```kotlin
  android {
      androidResources {
          additionalParameters("--no-compress", "onnx", "spm", "bin")
      }
  }
  ```

### 6.3 adb push（开发者）

```bash
adb push translator_zh-en /sdcard/Download/translator_zh-en
adb shell run-as com.example.offlinetranslator.debug \
    cp -r /sdcard/Download/translator_zh-en \
    /data/data/com.example.offlinetranslator.debug/files/models/translator/zh-en
```

## 7. 完整性校验（可选）

为避免拷贝损坏，建议下载时记录 `sha256`，在 manifest.json 中填写：
```json
{
  "checksumSha256": "sha256:..."
}
```
`ModelManager` 当前未做强制校验，但 `ModelPack` 字段已预留。

## 8. 性能预期（荣耀 500 Pro / 12GB RAM）

| 任务 | 延迟 | 内存占用 |
| --- | --- | --- |
| 文本翻译（zh↔en） | 300–800 ms / 50 字 | ~400 MB |
| 文本翻译（zh↔tg 级联） | 800–2000 ms | ~700 MB（两个模型同时驻留） |
| 流式 ASR（中文） | 实时 | ~300 MB |
| ASR 一次性识别（俄文 5 秒） | 1.5 s | ~250 MB |
| TTS（中文 30 字） | 1.2 s | ~100 MB |
| OCR（1080p 图片） | 800 ms | ~50 MB |

合计驻留：~600 MB；加载全部模型后峰值 ~1.5 GB，仍远低于 12 GB 内存。
