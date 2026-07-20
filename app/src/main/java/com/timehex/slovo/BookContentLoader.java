package com.timehex.slovo;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class BookContentLoader {
    private BookContentLoader() { }

    static String load(ContentResolver resolver, Uri uri) throws Exception {
        String name = uri.toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".fb2")) return loadFb2(resolver, uri);
        if (name.endsWith(".epub")) return loadEpub(resolver, uri);
        return loadText(resolver, uri);
    }

    private static String loadText(ContentResolver resolver, Uri uri) throws Exception {
        byte[] bytes;
        try (InputStream in = resolver.openInputStream(uri)) { bytes = readAll(in); }
        String utf = new String(bytes, StandardCharsets.UTF_8);
        int bad = count(utf, '\uFFFD');
        if (bad > Math.max(2, utf.length() / 500)) return new String(bytes, Charset.forName("windows-1251"));
        return utf;
    }

    private static String loadFb2(ContentResolver resolver, Uri uri) throws Exception {
        StringBuilder out = new StringBuilder();
        try (InputStream in = resolver.openInputStream(uri)) {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(in, null);
            boolean body = false;
            int event;
            while ((event = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = p.getName();
                    if ("body".equals(tag)) body = true;
                    if (body && ("p".equals(tag) || "title".equals(tag) || "subtitle".equals(tag))) {
                        String text = p.nextText().trim();
                        if (!text.isEmpty()) out.append(text).append("\n\n");
                    }
                }
            }
        }
        return out.toString();
    }

    private static String loadEpub(ContentResolver resolver, Uri uri) throws Exception {
        List<Part> parts = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(resolver.openInputStream(uri)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String n = entry.getName().toLowerCase(Locale.ROOT);
                if (!entry.isDirectory() && (n.endsWith(".html") || n.endsWith(".xhtml") || n.endsWith(".htm"))) {
                    String html = new String(readAll(zip), StandardCharsets.UTF_8);
                    parts.add(new Part(entry.getName(), htmlToText(html)));
                }
                zip.closeEntry();
            }
        }
        Collections.sort(parts, (a, b) -> a.name.compareToIgnoreCase(b.name));
        StringBuilder out = new StringBuilder();
        for (Part part : parts) if (!part.text.trim().isEmpty()) out.append(part.text.trim()).append("\n\n");
        return out.toString();
    }

    private static String htmlToText(String html) {
        return html
                .replaceAll("(?is)<(script|style).*?>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li)>", "\n\n")
                .replaceAll("(?s)<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n");
    }

    private static byte[] readAll(InputStream in) throws Exception {
        if (in == null) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static int count(String value, char c) {
        int result = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == c) result++;
        return result;
    }

    private static final class Part {
        final String name;
        final String text;
        Part(String name, String text) { this.name = name; this.text = text; }
    }
}

