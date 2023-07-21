#!/bin/sh
echo $KEYSTORE_FILE | base64 --decode > key.jks
echo storePassword=$KEYSTORE_PASSWORD > keystore.properties
echo keyPassword=$KEY_PASSWORD >> keystore.properties
echo keyAlias=$KEYSTORE_ALIAS >> keystore.properties
echo storeFile=key.jks >> keystore.properties