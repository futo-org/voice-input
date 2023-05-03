# WhisperVoiceInput

Android app to perform voice input using tflite Whisper model locally. This is under development and may not be stable.

Some usage notes:
* Android apps have several methods of performing voice input, this app currently implements only the intent method (`android.speech.action.RECOGNIZE_SPEECH`)
* The intent method isn't supported by AOSP keyboard or OpenBoard, so you need to use AnySoftKeyboard for keyboard integration
* Some apps like Firefox, Chrome, Organic Maps will also gain a voice input button after the app is installed, allowing you to do voice input that way
* The APK size is currently huge and not optimized
* Interaction with other installed voice input providers has not been tested