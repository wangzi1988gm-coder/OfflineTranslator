# 语音对讲模式说明

## 1. 设计目标

让两位使用不同语言的人（面对面或远程视频时）通过手机轮流说话：
1. A 方按住麦克风 → 识别为 A 语言 → 翻译成 B 语言 → 朗读 B 语言。
2. 松手后自动切换说话方。
3. 整个流程在本地完成，断网可用。

## 2. 状态机

```
IDLE ──按下按钮──► RECORDING ──松开──► PROCESSING ──► SPEAKING ──► IDLE
                       │                  │                │
                       └─ 取消/失败 ──► IDLE              └─ 用户打断
```

## 3. ASR 流式识别

`sherpa-onnx` 的 `OnlineRecognizer` 支持流式：
- 每 ~250 ms 喂一段音频（16 kHz mono float）。
- 调用 `recognizer.decode(stream)` 取得增量结果。
- 端点检测：`recognizer.isEndpoint(stream)`（默认基于 CTC 后验）。
- 松开时填充尾部静音 → `recognizer.getResult(stream).text`。

实现位置：`ConversationViewModel.kt#startRecording()`。

## 4. 自动切换说话方

`toggleSpeaker()` 在一条消息完成后自动调用。也可手动按"切换说话方"按钮：
- A 方 → B 方 → A 方 → …

## 5. 离线翻译的级联

若当前说话方翻译目标是塔吉克语，而 `zh↔tg` 模型未安装：
- `zh→ru→tg` 或 `en→ru→tg` 级联自动启用。
- 译文区显示 "级联: zh→ru→tg"。

## 6. 麦克风权限

- 第一次按按钮时请求。
- 用户拒绝后，可在系统设置 → 应用 → 离线翻译 → 权限 → 麦克风，手动开启。

## 7. VAD 与电平

- 流式 ASR 自带端点检测；
- `AudioWaveform` 实时显示音量（取最近 24 个采样块的能量峰值）。
- 静音 800 ms 自动停止当前轮（端点阈值可在 sherpa-onnx 配置中调整）。

## 8. 后台与锁屏

- 录音在前台 Service 之外运行；锁屏后会暂停。
- 如需后台运行，可在 Manifest 加 `FOREGROUND_SERVICE` 权限并启动 Foreground Service。
  本工程目前不实现（避免增加复杂度）。

## 9. 性能（荣耀 500 Pro 实测参考）

- ASR 端到端延迟：~700 ms（中文 5 秒语音）。
- 翻译：~500 ms。
- TTS：~1 s。
- 单轮对话总耗时：~2.5 s。
