/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils;

import android.text.SpannableString;
import android.text.TextUtils;

import com.exteragram.messenger.components.PreformattedScrollView;

import org.telegram.utils.code.highlight.CodeHighlighting;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.tl.TL_iv;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public final class MarkdownUtils {

    private static final String THEME_EXTENSION = "attheme";

    private static final String[] MARKDOWN_TEXT_EXTENSIONS = {
            "txt",
            "text"
    };

    private static final String[] MARKDOWN_MIME_PREFIXES = {
            "text/plain",
            "text/x-diff",
            "text/x-patch",
            "text/csv",
            "text/xml",
            "text/yaml",
            "text/x-yaml",
            "text/css",
            "text/javascript",
            "application/json",
            "application/ld+json",
            "application/json5",
            "application/xml",
            "application/yaml",
            "application/x-yaml",
            "application/javascript",
            "application/x-javascript",
            "application/x-sh"
    };

    private static final HashMap<String, String> PREFORMATTED_EXTENSION_LANGUAGES = new HashMap<>();
    private static final HashMap<String, String> PREFORMATTED_FILENAMES = new HashMap<>();

    static {
        // extracted from codelng.gzip
        addLanguage("plain", "log");
        addLanguage("diff", "diff", "patch");
        addLanguage("json", "json", "webmanifest");
        addLanguage("json5", "json5");
        addLanguage("xml", "xml", "rss", "atom");
        addLanguage("svg", "svg");
        addLanguage("html", "html", "htm", "xhtml");
        addLanguage("css", "css");
        addLanguage("scss", "scss");
        addLanguage("sass", "sass");
        addLanguage("less", "less");
        addLanguage("javascript", "js", "mjs", "cjs");
        addLanguage("jsx", "jsx");
        addLanguage("typescript", "ts");
        addLanguage("tsx", "tsx");
        addLanguage("java", "java");
        addLanguage("kotlin", "kt", "kts");
        addLanguage("gradle", "gradle");
        addLanguage("groovy", "groovy");
        addLanguage("python", "py", "pyw", "plugin");
        addLanguage("bash", "sh", "bash", "zsh", "fish", "shell");
        addLanguage("powershell", "ps1", "psm1", "psd1");
        addLanguage("batch", "bat", "cmd");
        addLanguage("sql", "sql");
        addLanguage("yaml", "yaml", "yml");
        addLanguage("ini", "ini", "toml", "properties", "props", "conf", "cfg", "config", "env", "dotenv");
        addLanguage("csv", "csv", "tsv");
        addLanguage("docker", "dockerfile");
        addLanguage("makefile", "make", "mk", "mak");
        addLanguage("cmake", "cmake");
        addLanguage("go", "go");
        addLanguage("rust", "rs");
        addLanguage("swift", "swift");
        addLanguage("dart", "dart");
        addLanguage("php", "php", "phtml");
        addLanguage("ruby", "rb", "gemspec");
        addLanguage("c", "c");
        addLanguage("cpp", "h", "hh", "hpp", "hxx", "cpp", "cc", "cxx");
        addLanguage("csharp", "cs");
        addLanguage("fsharp", "fs", "fsx");
        addLanguage("visual-basic", "vb", "vba");
        addLanguage("lua", "lua");
        addLanguage("perl", "pl", "pm");
        addLanguage("r", "r");
        addLanguage("scala", "scala");
        addLanguage("haskell", "hs");
        addLanguage("elixir", "ex", "exs");
        addLanguage("erlang", "erl", "hrl");
        addLanguage("protobuf", "proto", "protobuf");
        addLanguage("graphql", "graphql", "gql");
        addLanguage("glsl", "glsl", "vert", "frag", "geom", "comp");
        addLanguage("http", "http");

        addFilename("docker", "Dockerfile");
        addFilename("makefile", "Makefile", "GNUmakefile");
        addFilename("cmake", "CMakeLists.txt");
        addFilename("git", ".gitignore", ".gitattributes", ".gitmodules");
        addFilename("docker", ".dockerignore");
        addFilename("ini", ".editorconfig", ".env");
    }

    private MarkdownUtils() {
    }

    public static boolean isTheme(MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        final String fileName = FileLoader.getDocumentFileName(messageObject.getDocument());
        return !TextUtils.isEmpty(fileName) && fileName.toLowerCase(Locale.ROOT).endsWith(THEME_EXTENSION);
    }

    public static boolean isExteraMarkdown(MessageObject messageObject) {
        if (messageObject == null || isTheme(messageObject)) {
            return false;
        }
        final String fileName = FileLoader.getDocumentFileName(messageObject.getDocument());
        return isExteraMarkdownExtension(messageObject.getExtension()) ||
                isExteraMarkdownMime(messageObject.getMimeType()) ||
                !TextUtils.isEmpty(getPreformattedLanguage(fileName, messageObject.getExtension(), messageObject.getMimeType()));
    }

    public static boolean isExteraMarkdownExtension(String ext) {
        if (isMarkdownTextExtension(ext)) {
            return true;
        }
        return !TextUtils.isEmpty(getPreformattedLanguage(null, ext, null));
    }

    public static boolean isExteraMarkdownMime(String mime) {
        if (TextUtils.isEmpty(mime)) {
            return false;
        }
        final String normalized = mime.toLowerCase(Locale.ROOT);
        for (String prefix : MARKDOWN_MIME_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static String getPreformattedLanguage(String fileName, String ext, String mime) {
        final String languageByFileName = getPreformattedLanguageByFileName(fileName);
        if (!TextUtils.isEmpty(languageByFileName)) {
            return languageByFileName;
        }

        String normalizedExt = normalizeExtension(ext);
        if (TextUtils.isEmpty(normalizedExt)) {
            normalizedExt = getExtensionFromFileName(fileName);
        }
        final String languageByExtension = PREFORMATTED_EXTENSION_LANGUAGES.get(normalizedExt);
        if (!TextUtils.isEmpty(languageByExtension)) {
            return languageByExtension;
        }

        if (!TextUtils.isEmpty(mime)) {
            final String normalizedMime = mime.toLowerCase(Locale.ROOT);
            if (normalizedMime.startsWith("text/x-diff") || normalizedMime.startsWith("text/x-patch")) {
                return "diff";
            } else if (normalizedMime.startsWith("text/csv")) {
                return "csv";
            } else if (normalizedMime.startsWith("text/xml") || normalizedMime.startsWith("application/xml")) {
                return "xml";
            } else if (normalizedMime.startsWith("application/json5")) {
                return "json5";
            } else if (normalizedMime.startsWith("application/json") || normalizedMime.startsWith("application/ld+json")) {
                return "json";
            } else if (normalizedMime.startsWith("text/yaml") || normalizedMime.startsWith("text/x-yaml") ||
                    normalizedMime.startsWith("application/yaml") || normalizedMime.startsWith("application/x-yaml")) {
                return "yaml";
            } else if (normalizedMime.startsWith("text/css")) {
                return "css";
            } else if (normalizedMime.startsWith("text/javascript") || normalizedMime.startsWith("application/javascript") ||
                    normalizedMime.startsWith("application/x-javascript")) {
                return "javascript";
            } else if (normalizedMime.startsWith("application/x-sh")) {
                return "bash";
            }
        }
        return "";
    }

    public static void appendPreformattedBlocks(List<TL_iv.PageBlock> blocks, String text, String language, int maxTextLength) {
        final int blockLength = Math.max(1, maxTextLength);
        if (TextUtils.isEmpty(text)) {
            final TL_iv.pageBlockPreformatted block = new TL_iv.pageBlockPreformatted();
            block.text = plain("");
            block.language = language == null ? "" : language;
            blocks.add(block);
            return;
        }
        final HighlightSource source = new HighlightSource(text, language == null ? "" : language);
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + blockLength);
            if (end < text.length()) {
                final int newline = text.lastIndexOf('\n', end - 1);
                if (newline > start) {
                    end = newline + 1;
                }
            }
            blocks.add(new PreformattedChunk(source, start, end));
            start = end;
        }
    }

    public static final class PreformattedChunk extends TL_iv.pageBlockPreformatted {

        public final boolean joinsPrevious;
        public final boolean joinsNext;
        public final PreformattedScrollView.Group scrollGroup;

        private final HighlightSource source;
        private final int start;
        private final int end;
        private final String plainText;
        private CharSequence highlightedText;

        private PreformattedChunk(HighlightSource source, int start, int end) {
            this.source = source;
            this.start = start;
            this.end = end;
            plainText = source.text.substring(start, end);
            joinsPrevious = start > 0;
            joinsNext = end < source.text.length();
            scrollGroup = source.scrollGroup;
            text = plain(plainText);
            language = source.language;
        }

        public CharSequence getHighlightedText() {
            if (highlightedText == null) {
                final SpannableString highlighted = source.getHighlighted();
                if (highlighted instanceof CodeHighlighting.LockedSpannableString && !((CodeHighlighting.LockedSpannableString) highlighted).ready) {
                    return plainText;
                }
                highlightedText = highlighted.subSequence(start, end);
            }
            return highlightedText;
        }
    }

    private static final class HighlightSource {

        final String text;
        final String language;
        final PreformattedScrollView.Group scrollGroup = new PreformattedScrollView.Group();
        private SpannableString highlighted;

        HighlightSource(String text, String language) {
            this.text = text;
            this.language = language;
        }

        SpannableString getHighlighted() {
            if (highlighted == null) {
                highlighted = CodeHighlighting.getHighlighted(text, language);
            }
            return highlighted;
        }
    }

    private static void addLanguage(String language, String... extensions) {
        for (String extension : extensions) {
            PREFORMATTED_EXTENSION_LANGUAGES.put(extension, language);
        }
    }

    private static void addFilename(String language, String... fileNames) {
        for (String fileName : fileNames) {
            PREFORMATTED_FILENAMES.put(fileName.toLowerCase(Locale.ROOT), language);
        }
    }

    private static boolean isMarkdownTextExtension(String ext) {
        final String normalized = normalizeExtension(ext);
        for (String textExtension : MARKDOWN_TEXT_EXTENSIONS) {
            if (textExtension.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String getPreformattedLanguageByFileName(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        final String normalized = getBaseName(fileName).toLowerCase(Locale.ROOT);
        final String exactLanguage = PREFORMATTED_FILENAMES.get(normalized);
        if (!TextUtils.isEmpty(exactLanguage)) {
            return exactLanguage;
        }
        if (normalized.startsWith("dockerfile.")) {
            return "docker";
        } else if (normalized.startsWith("makefile.")) {
            return "makefile";
        } else if (normalized.startsWith(".env.")) {
            return "ini";
        }
        return "";
    }

    private static String getExtensionFromFileName(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        final String baseName = getBaseName(fileName);
        final int dot = baseName.lastIndexOf('.');
        if (dot <= 0 || dot >= baseName.length() - 1) {
            return "";
        }
        return normalizeExtension(baseName.substring(dot + 1));
    }

    private static String getBaseName(String fileName) {
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return slash >= 0 ? fileName.substring(slash + 1) : fileName;
    }

    private static String normalizeExtension(String ext) {
        if (TextUtils.isEmpty(ext)) {
            return "";
        }
        String normalized = ext.trim();
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static TL_iv.RichText plain(String text) {
        final TL_iv.textPlain richText = new TL_iv.textPlain();
        richText.text = text == null ? "" : text;
        return richText;
    }
}