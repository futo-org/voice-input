# Voice Input

The initial goal is to build a polished application for doing speech to text, integrating with third party keyboards that use generic speech-to-text APIs.

To download the latest successful build, go to [CI/CD > Pipelines](https://gitlab.futo.org/alex/voiceinput/-/pipelines), find the top-most passing pipeline, click the download button, and select build-job:archive. The apk is contained within the downloaded zip archive.


Supported keyboard list:
* AOSP Keyboard - IME
* OpenBoard - IME
* AnySoftKeyboard - Intent with IME fallback

Tested working proprietary keyboards:
* Grammarly Keyboard - IME
* Microsoft SwiftKey - Intent

Incompatible keyboards:
* Gboard - has its own thing
* Simple Keyboard by Raimondas Rimkus - no voice button
* Simple Keyboard by Simple Mobile Tools - no voice button
* FlorisBoard - no voice button
* Unexpected Keyboard - no voice button
* TypeWise - no voice button [but suggestion filed in 2019](https://suggestions.typewise.app/suggestions/65517/voice-to-text-dictation)

----------

Open-source projects used:
* MIT - Copyright (c) 2022 OpenAI [OpenAI Whisper](https://github.com/openai/whisper/)
* Apache 2.0 - Copyright (c) 2023 TensorFlow Authors [TensorFlow Lite](https://mvnrepository.com/artifact/org.tensorflow/tensorflow-lite)
* BSD-3-Clause - Copyright (C) 2010-2019 Max-Planck-Society [PocketFFT](https://gitlab.mpcdf.mpg.de/mtr/pocketfft/-/blob/master/LICENSE.md)
* BSD-3-Clause - Copyright (c) 2011, The WebRTC project authors [WebRTC VAD](https://github.com/abb128/android-vad/blob/main/vad/src/main/jni/webrtc_vad/LICENSE)
* MIT - Copyright (c) 2023 Georgiy Konovalov [android-vad](https://github.com/abb128/android-vad)
* Other app dependencies, listed in app/build.gradle