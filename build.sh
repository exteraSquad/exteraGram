#!/bin/bash

token="5192489829:AAEp_-6uGF4jhHpi-YTexmAPeADA4CwAUiQ"
doc="https://api.telegram.org/bot$token/sendDocument?chat_id=-1001559501352"
doc_fail="https://api.telegram.org/bot$token/sendDocument?chat_id=963080346"

send_build() { curl -F document=@"$1" "$doc" -F "parse_mode=html" -F caption="$text"; }
build_failed() { curl -F document=@"$1" "$doc_fail" -F "parse_mode=html" -F caption="$text_failed"; }

start=$(date +"%s")
./gradlew assembleBetaDebug 2>&1 | tee -a log.txt
end=$(date +"%s")
bt=$(($end - $start))
apk=$(find TMessagesProj/build/outputs/apk -name '*.apk')
# zip -q9 apk.zip $apk

text_failed="
<b>Build failed ✓</b>
 
<b>$commit</b>
 
<b>Author:</b> <code>$commit_author</code>
<b>SHA:</b> <code>$commit_sha</code>
<b>MD5:</b> <code>$(md5sum $apk | cut -d' ' -f1)</code>
<b>Build Time:</b> <code>$(($bt / 60)):$(($bt % 60))</code>
"

text="
<b>$commit</b>
 
<b>Author:</b> <code>$commit_author</code>
<b>SHA:</b> <code>$commit_sha</code>
<b>MD5:</b> <code>$(md5sum $apk | cut -d' ' -f1)</code>
<b>Build Time:</b> <code>$(($bt / 60)):$(($bt % 60))</code>
"

if [[ -f $apk ]]; then
    send_build "$apk"
else
    build_failed log.txt
    exit 1
fi