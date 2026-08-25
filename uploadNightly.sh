#!/bin/sh
aws configure set aws_access_key_id $VOICEINPUT_R2_ACCESS_KEY_ID
aws configure set aws_secret_access_key $VOICEINPUT_R2_SECRET_ACCESS_KEY
aws configure set region $VOICEINPUT_R2_DEFAULT_REGION

aws s3 cp "$1" s3://$VOICEINPUT_R2_BUCKET_NAME/VoiceInput/dev.apk --endpoint=$VOICEINPUT_R2_ENDPOINT_URL

echo "$2" > nightly_version
echo "https://dl.voiceinput.futo.org/VoiceInput/dev.apk?v=$2" >> nightly_version
echo "$3" >> nightly_version

aws s3 cp nightly_version s3://$VOICEINPUT_R2_BUCKET_NAME/VoiceInput/voice_input_version_dev --endpoint=$VOICEINPUT_R2_ENDPOINT_URL
