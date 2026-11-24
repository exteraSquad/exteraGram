/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.lyrics;

import android.text.TextUtils;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

final class TtmlLyricsReader {

    private static final int MAX_DEPTH = 64;

    private TtmlLyricsReader() {
    }

    private static final class Element {
        final String name;
        final HashMap<String, String> attributes = new HashMap<>();
        final ArrayList<Object> children = new ArrayList<>();

        Element(String name) {
            this.name = name;
        }

        String attr(String name) {
            return attributes.get(name);
        }

        boolean hasElements() {
            for (Object child : children) {
                if (child instanceof Element) {
                    return true;
                }
            }
            return false;
        }

        void collect(String name, ArrayList<Element> out) {
            for (Object child : children) {
                if (child instanceof Element element) {
                    if (name.equals(element.name)) {
                        out.add(element);
                    } else {
                        element.collect(name, out);
                    }
                }
            }
        }

        Element first(String name) {
            final ArrayList<Element> found = new ArrayList<>(1);
            collect(name, found);
            return found.isEmpty() ? null : found.get(0);
        }

        String flatten() {
            final StringBuilder sb = new StringBuilder();
            flatten(sb);
            return sb.toString();
        }

        private void flatten(StringBuilder sb) {
            for (Object child : children) {
                if (child instanceof String) {
                    sb.append((String) child);
                } else {
                    final Element element = (Element) child;
                    if ("br".equals(element.name)) {
                        sb.append(' ');
                    } else {
                        element.flatten(sb);
                    }
                }
            }
        }
    }

    private static final class Subtitles {
        String language;
        final HashMap<String, String> byKey = new HashMap<>();
    }

    static Lyrics read(String xml, int source, String provider, Locale preferred) throws XmlPullParserException, IOException {
        if (TextUtils.isEmpty(xml)) {
            return null;
        }
        final Element root = tree(xml);
        final Element body = root != null ? root.first("body") : null;
        if (body == null) {
            return null;
        }
        final Element head = root.first("head");
        final ArrayList<String> singers = new ArrayList<>();
        final ArrayList<String> groups = new ArrayList<>();
        Subtitles translation = null;
        Subtitles romanization = null;
        if (head != null) {
            final ArrayList<Element> agents = new ArrayList<>();
            head.collect("agent", agents);
            for (Element agent : agents) {
                final String id = agent.attr("id");
                if (id != null) {
                    (("person".equals(agent.attr("type")) || agent.attr("type") == null) ? singers : groups).add(id);
                }
            }
            translation = subtitles(head, "translation", preferred);
            romanization = subtitles(head, "transliteration", null);
        }
        final ArrayList<Element> paragraphs = new ArrayList<>();
        body.collect("p", paragraphs);
        final ArrayList<Lyrics.Line> lines = new ArrayList<>(paragraphs.size());
        for (Element p : paragraphs) {
            final Lyrics.Line line = line(p, singers, groups, translation, romanization);
            if (line != null) {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? null : Lyrics.fromTimedLines(lines, source, provider, translation != null ? translation.language : null);
    }

    private static Element tree(String xml) throws XmlPullParserException, IOException {
        final XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new StringReader(xml));
        final ArrayList<Element> stack = new ArrayList<>();
        Element root = null;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.START_TAG) {
                if (stack.size() >= MAX_DEPTH) {
                    throw new XmlPullParserException("ttml too deep");
                }
                final Element element = new Element(parser.getName());
                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    element.attributes.put(parser.getAttributeName(i), parser.getAttributeValue(i));
                }
                if (stack.isEmpty()) {
                    root = element;
                } else {
                    stack.get(stack.size() - 1).children.add(element);
                }
                stack.add(element);
            } else if (event == XmlPullParser.END_TAG) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
            } else if (event == XmlPullParser.TEXT && !stack.isEmpty()) {
                stack.get(stack.size() - 1).children.add(parser.getText());
            }
        }
        return root;
    }

    private static Subtitles subtitles(Element head, String kind, Locale preferred) {
        final ArrayList<Element> sets = new ArrayList<>();
        head.collect(kind, sets);
        Element chosen = null;
        for (Element set : sets) {
            if (chosen == null) {
                chosen = set;
            }
            final String lang = set.attr("lang");
            if (preferred != null && lang != null && preferred.getLanguage().equalsIgnoreCase(Locale.forLanguageTag(lang).getLanguage())) {
                chosen = set;
                break;
            }
        }
        if (chosen == null) {
            return null;
        }
        final Subtitles out = new Subtitles();
        out.language = chosen.attr("lang");
        final ArrayList<Element> texts = new ArrayList<>();
        chosen.collect("text", texts);
        for (Element text : texts) {
            final String key = text.attr("for");
            final String value = clean(text.flatten());
            if (key != null && value != null) {
                out.byKey.put(key, value);
            }
        }
        return out.byKey.isEmpty() ? null : out;
    }

    private static Lyrics.Line line(Element p, ArrayList<String> singers, ArrayList<String> groups, Subtitles translation, Subtitles romanization) {
        final Lyrics.LineBuilder main = new Lyrics.LineBuilder();
        final Lyrics.LineBuilder background = new Lyrics.LineBuilder();
        final StringBuilder inlineTranslation = new StringBuilder();
        final StringBuilder inlineRomanization = new StringBuilder();
        walk(p, main, background, inlineTranslation, inlineRomanization);
        long time = parseTime(p.attr("begin"));
        if (time < 0) {
            time = main.firstStart() >= 0 ? main.firstStart() : background.firstStart();
        }
        if (time < 0) {
            return null;
        }
        final Lyrics.Line line = main.build(time);
        Lyrics.Line backing = null;
        if (background.hasText()) {
            backing = background.build(background.firstStart() >= 0 ? background.firstStart() : time);
        }
        if (line.isBlank() && backing == null) {
            return null;
        }
        final String key = p.attr("key");
        String tr = clean(inlineTranslation.toString());
        if (tr == null && translation != null && key != null) {
            tr = translation.byKey.get(key);
        }
        String ro = clean(inlineRomanization.toString());
        if (ro == null && romanization != null && key != null) {
            ro = romanization.byKey.get(key);
        }
        return line.withExtras(backing, voice(p.attr("agent"), singers, groups), tr, ro);
    }

    private static void walk(Element parent, Lyrics.LineBuilder target, Lyrics.LineBuilder background, StringBuilder translation, StringBuilder romanization) {
        for (Object child : parent.children) {
            if (child instanceof String) {
                target.appendText((String) child);
                continue;
            }
            final Element element = (Element) child;
            final String role = element.attr("role");
            if ("x-bg".equals(role)) {
                walk(element, background, background, translation, romanization);
            } else if ("x-translation".equals(role)) {
                translation.append(element.flatten());
            } else if ("x-roman".equals(role)) {
                romanization.append(element.flatten());
            } else if ("br".equals(element.name)) {
                target.space(" ");
            } else {
                final long begin = "span".equals(element.name) ? parseTime(element.attr("begin")) : -1;
                if (begin >= 0 && !element.hasElements()) {
                    target.add(element.flatten(), begin, parseTime(element.attr("end")));
                } else {
                    walk(element, target, background, translation, romanization);
                }
            }
        }
    }

    private static int voice(String agent, ArrayList<String> singers, ArrayList<String> groups) {
        if (agent == null || groups.contains(agent)) {
            return Lyrics.VOICE_MAIN;
        }
        int index = singers.indexOf(agent);
        if (index < 0) {
            singers.add(agent);
            index = singers.size() - 1;
        }
        return index % 2 == 1 ? Lyrics.VOICE_SECOND : Lyrics.VOICE_MAIN;
    }

    private static String clean(String text) {
        if (text == null) {
            return null;
        }
        final String value = text.replaceAll("\\s+", " ").trim();
        return value.isEmpty() ? null : value;
    }

    static long parseTime(String value) {
        if (TextUtils.isEmpty(value)) {
            return -1;
        }
        String s = value.trim();
        double unit = 1000;
        if (s.endsWith("ms")) {
            s = s.substring(0, s.length() - 2);
            unit = 1;
        } else if (s.endsWith("s")) {
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            s = s.substring(0, s.length() - 1);
            unit = 60_000;
        }
        try {
            double total = 0;
            for (String part : s.split(":")) {
                total = total * 60 + Double.parseDouble(part);
            }
            return total < 0 || Double.isNaN(total) || Double.isInfinite(total) ? -1 : Math.round(total * unit);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
