# App icons

Source of truth for every launcher icon. Everything under `src/main/res-appicons`,
`src/main/AndroidManifest-appicons.xml` and `src/main/java/com/exteragram/messenger/appicons/GeneratedAppIcons.java`
is produced from here by

```
./gradlew :TMessagesProj:generateAppIcons
```

The task needs `cwebp`, `dwebp` and `rsvg-convert` on the machine that runs it (`brew install webp librsvg`,
`apt install webp librsvg2-bin`). Regular builds never run it, so nobody else needs those tools — commit
what the task produces.

## Layout

```
appicons/
  icons.json
  <author>/<icon id>/ic_launcher_foreground.(svg|png|webp)
  <author>/<icon id>/ic_launcher_background.(svg|png|webp)
  <author>/<icon id>/ic_launcher_monochrome.(svg|png|webp)   optional
```

`<author>` is the Telegram username of the contest author, or `exteragram` for the fork's own icons.
Raster masters must be square and at least 432x432; SVG must use the 108x108 adaptive icon canvas.

## icons.json

One entry per icon, in the order they appear on the icon screen.

| field | meaning |
|---|---|
| `id` | required, `[a-z0-9_]`; drives `ic_launcher_<id>` resources and the source folder |
| `author` | folder name and `@username` shown under the icon; omit for fork icons |
| `authorName` | display name shown instead of `@username` |
| `title` / `titleRes` | icon name, literal or an existing string resource |
| `component` | activity-alias name, defaults to `<PascalCaseId>Icon`; **never change it for a shipped icon** |
| `resource` | resource base name, defaults to `ic_launcher_<id>` |
| `background`, `foreground`, `monochrome` | `#RRGGBB`, `@color/x`, `@drawable/x`, `@mipmap/x`, a file name, or `none`; omitted means "look for a file in the folder" |
| `availability` | `always`, `winter` or `monet`; anything else is rejected |
| `round` | also emit `<resource>_round`; only the default icon needs it |
| `legacy` | emit the pre-26 bitmap, `false` when the layers cannot be rasterized |
| `description`, `descriptionRes` | one line under the name on the icon screen. A `descriptionRes` needs the string in both `values/extera.xml` and `values-ru/extera.xml`; a literal `description` is not translated. Icons without one show `@author` instead |
| `color` | `#RRGGBB` behind the preview on the icon screen; taken from the artwork when missing |
| `lossless` | encode the background and the pre-26 bitmap without loss; foregrounds are always lossless |
| `assetsOnly` | build the resources but no alias and no registry entry |

Adding a contest icon is: drop the folder in, add an entry, run the task, commit.
