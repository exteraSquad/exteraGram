#!/bin/bash

chat_id="963080346"
token="5192489829:AAEp_-6uGF4jhHpi-YTexmAPeADA4CwAUiQ"
msg="https://api.telegram.org/bot$token/sendMessage?chat_id=$chat_id"
doc="https://api.telegram.org/bot$token/sendDocument?chat_id=$chat_id"

send_msg() { curl -s -X POST "$msg" -d "parse_mode=html" -d text="$1"; }
send_build() { curl -F document=@"$1" "$doc" -F "parse_mode=html" -F caption="$text"; }
build_failed() { curl -F document=@"$1" "$doc" -F "parse_mode=html" -F caption="$text_failed"; }

build() {
    start=$(date +"%s")
    ./gradlew assembleBetaDebug 2>&1 | tee -a log.txt
    end=$(date +"%s")
    bt=$(($end - $start))
}

build
apk=$(find TMessagesProj/build/outputs/apk -name '*.apk')
zip -q9 apk.zip $apk

text_failed="
<b>Build failed ✓</b>
<b> </b>
<b>Commit:</b> <code>$commit</code>
<b>Author:</b> <code>$commit_author</code>
<b>SHA:</b> <code>$commit_sha</code>

<b>Run Number:</b> <code>$run_num</code>
<b>Build Time:</b> <code>$(($bt / 60)):$(($bt % 60))</code>
"

text="
<b>Commit:</b> <code>$commit</code>
<b>Author:</b> <code>$commit_author</code>
<b>SHA:</b> <code>$commit_sha</code>

<b>Run Number:</b> <code>$run_num</code>
<b>Build Time:</b> <code>$(($bt / 60)):$(($bt % 60))</code>
<b>MD5</b>: <code>$(md5sum $apk | cut -d' ' -f1)</code>
"

if [[ -f $apk ]]; then
    until [[ $(send_build "apk.zip" | grep -o '"ok":true') = '"ok":true' ]]; do sleep 5; done
else
    build_failed log.txt
    exit 1
fi