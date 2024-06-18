# v1.3.4
* Fixed a compatibility issue with some devices causing a crash when used
* Improvements for TalkBack
* Updated the payment page design
* Updated license

# v1.3.3
* Fixed a bug causing infinite processing

# v1.3.2
* Replaced tflite with whisper.cpp (tflite still works for a transition period, but is unsupported and will later be removed)
* Now using fine-tuned models that make use of whisper.cpp audio_ctx parameter for speedup (thanks to whisper.cpp)
* Added personal dictionary option that make use of prompting (thanks to whisper.cpp)
* Beam search is now used instead of greedy search (thanks to whisper.cpp)
* Added English-244
* General UI improvements and theming
* You can now tap and hold the mic to prevent automatic stopping
* Added background model migration, and foreground model migration screen to download updated models
* Fixed a few minor bugs

Note: some version numbers were skipped

# v1.2.7
* Fixed issue with sampling invalid tokens, causing decoding to terminate unexpectedly
* Added option to disable animated circle (for e-ink devices, etc)

# v1.2.6
* Added screen-lock to keep screen awake during voice input
* Reduced number of memory-related crashes

# v1.2.5
* Added a setting to turn off VAD auto-stop 
* Added an option to configure model-switching behavior:
* * By default, as in previous versions, having multiple languages enabled will run the multilingual Whisper model at first, and if English is detected, it switches to the English Whisper model, which takes time
* * You can now disable this switch to continue decoding English with the multilingual Whisper model, which is technically capable of decoding English. This can be significantly faster, but may reduce accuracy.
* Fixed the issue preventing you from turning off the English language
* Added feedback for when recording is about to stop due to silence or 30 second limit
* Added option to try less-trained languages
* Added option to select language manually instead of auto-detecting

# v1.2.4
* Fixed a bug where the text would disappear after finishing

# v1.2.3
* Fixed support for Chromium based browsers (Google Chrome, Brave, etc)
* Fixed issues with screen rotation changes
* Fixed issues with space insertion logic always inserting a space at the start
* Fixed a few crashes and other miscellaneous bugs

# v1.2.2
* Recording now stops background media playback

# v1.2.1
* Added a link to the issue tracker
* Made it more difficult to accidentally misclick the already paid button
* Fixed text color issue with standalone payment dialogue

# v1.2.0
* Initial public release with standalone payment support