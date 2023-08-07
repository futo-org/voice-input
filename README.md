# FUTO Voice Input

FUTO Voice Input is an application that lets you do speech-to-text on Android, integrating with third party keyboards or apps that use the generic speech-to-text APIs.

To download the application as a user, visit the [FUTO Voice Input page](https://voiceinput.futo.org/).

## API support

The following APIs are supported:
* `android.speech.action.RECOGNIZE_SPEECH` implicit intent, for apps and some keyboards
* IME with `voice` subtype mode, for keyboards

Currently this does not support the SpeechRecognizer API, which few apps seem to use. Support for this is planned in the future.

## Keyboard support

Keyboard support is touched on in the Help section of the app. In short, the following keyboards are supported:
* **AOSP Keyboard**, which uses the IME
* **OpenBoard**, which uses the IME
* **AnySoftKeyboard**, which uses the implicit intent. This keyboard has some voice input usability issues but this is being worked on

If you're okay with using proprietary keyboards, the following are supported:
* **Grammarly Keyboard**, which uses the IME
* **Microsoft SwiftKey**, which uses the implicit intent

Incompatible keyboards:
* **Gboard** - has its own thing
* **Samsung Keyboard** - hardcoded to only allow either Samsung Voice Input, or Google Voice Input
* **Simple Keyboard** by Raimondas Rimkus - [no voice button](https://github.com/rkkr/simple-keyboard/issues/133)
* **Simple Keyboard** by Simple Mobile Tools - [no voice button](https://github.com/SimpleMobileTools/Simple-Keyboard/issues/201)
* **Unexpected Keyboard** - no voice button
* **TypeWise** - no voice button [but suggestion filed in 2019](https://suggestions.typewise.app/suggestions/65517/voice-to-text-dictation)

## Development

You can develop this app by opening it in Android Studio. Otherwise, you can use Gradle to build the app like so:
```bash
./gradlew assembleStandaloneRelease
```

There are four build flavors:
* `dev` - for development, includes Play Store billing and all payment methods, auto-update, etc
* `playStore` - Play Store build, does not include auto-update and only includes Play Store billing
* `standalone` - does not include Play Store billing library, includes auto-update
* `fDroid` - does not include Play Store billing nor auto-update

## Credits

Thanks to the following projects for making this possible:
* OpenAI - [OpenAI Whisper](https://github.com/openai/whisper/)
* TensorFlow Authors - [TensorFlow Lite](https://mvnrepository.com/artifact/org.tensorflow/tensorflow-lite)
* Max-Planck-Society - [PocketFFT](https://gitlab.mpcdf.mpg.de/mtr/pocketfft/-/blob/master/LICENSE.md)
* The WebRTC project authors - [WebRTC VAD](https://github.com/abb128/android-vad/blob/main/vad/src/main/jni/webrtc_vad/LICENSE)
* Georgiy Konovalov - [android-vad](https://github.com/abb128/android-vad)
* Other app dependencies, listed in app/build.gradle