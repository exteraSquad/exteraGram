
  
<div align="center">

  <img src="https://i.imgur.com/5EmxevP.png" alt="exteraGram logo" width="150px" />
  
  # exteraGram

  
  Experimental **third-party** Telegram client based on [official sources](https://github.com/DrKLO/Telegram).
  
  ---
  [![Channel](https://img.shields.io/badge/Channel-Telegram-red.svg)](https://t.me/exteraGram)
  [![Chat](https://img.shields.io/badge/Chat-Telegram-red.svg)](https://t.me/exteraChat)
  [![Downloads](https://img.shields.io/badge/Release%20at%20-%20Telegram-red.svg)](https://t.me/exteraReleases)
  [![Beta Builds](https://img.shields.io/badge/Beta%20at%20-%20Telegram-red.svg)](https://t.me/exteraGramCI)
</div>

## Features

- Reworked Music Player
- Dynamic theme (Android 12+)
- Modern Interface using Telegram design system, Google material 3 and custom icons.
- Download/Upload speed boost
- CameraX
- Large interface customization
- Integrated ChatGPT (key required)
- Custom app icons
- Zalgo Filters


## Installation
---
### Importing API hash and keys

- You should get **YOUR OWN API KEY AND HASH** here: https://core.telegram.org/api/obtaining_api_id and create a file called `API_KEYS` in the source root directory.

- Also you should get **YOUR OWN MAPS API KEY** here: https://console.cloud.google.com/google/maps-apis/credentials and add it to this file.

- And you need to generate **SIGNING KEY**: https://developer.android.com/studio/publish/app-signing#generate-key

  

The file content should look like this:

```
APP_ID = 123456
APP_HASH = abcdef0123456789 (32 chars)
MAPS_V2_API = abcdef01234567-abcdef012345678910111213
SIGNING_KEY_PASSWORD = A1BcDEFHJ2KLMn3oP
SIGNING_KEY_ALIAS = abcdefghjklm
SIGNING_KEY_STORE_PASSWORD = Z9yXDEFHJ6KRqn7oP
```

## Build
---

1. Clone exteraGram's source code using `git clone https://github.com/exteraSquad/exteraGram.git`

2. Fill out values in `API_KEYS` like [here](https://github.com/exteraSquad/exteraGram#importing-api-hash-and-keys)

3. Open the project in Android Studio. It should be opened, **not imported**

4. You are ready to compile `exteraGram`

  

-  **exteraGram** can be built with **Android Studio** or from the command line with **Gradle**:

```
./gradlew assembleAfatRelease
```

## Contributing

This is an open-source project, you can do so without any programming.

Here are a few things you can do:

- [Test and report issues](https://github.com/exteraSquad/exteraGram/issues/new/choose)
- [Translate the extera strings app into your language](https://crowdin.com/project/exteralocales) -
**exteraGram** is a fork of **Telegram for Android** and most of the localizations follow the translations of **Telegram for Android**, check it out [here](https://translations.telegram.org/en/android/). As for specialized strings for **exteraGram**, we use **Crowdin** to translate **exteraGram**.


 ## Thanks to:
- [Catogram](https://github.com/Catogram/Catogram)
- [Nekogram](https://gitlab.com/Nekogram/Nekogram)
- [OwlGram](https://github.com/OwlGramDev/OwlGram)

License
---
Licensed under the GNU General Public License v2.0

[![License: GPLv2](https://img.shields.io/badge/License-GPL%20v2-red.svg?style=for-the-badge)](https://github.com/exteraSquad/exteraGram/blob/main/LICENSE)
