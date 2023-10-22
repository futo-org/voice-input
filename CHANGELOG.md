# v1.2.5
* Added a setting to turn off VAD auto-stop 
* Added an option to configure model-switching behavior:
* * By default, as in previous versions, having multiple languages enabled will run the multilingual Whisper model at first, and if English is detected, it switches to the English Whisper model, which takes time
* * You can now disable this switch to continue decoding English with the multilingual Whisper model, which is technically capable of decoding English. This can be significantly faster, but may reduce accuracy.
* Fixed the issue preventing you from turning off the English language

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