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