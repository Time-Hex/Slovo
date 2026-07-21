package com.timehex.slovo;

import android.content.Context;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.widget.TextView;

import java.util.ArrayList;

/** A lightweight text view that lays a book out as screen-sized pages. */
final class PagedTextView extends TextView {
    interface PageListener {
        void onPageChanged(int page, int count, int progress);
    }

    private final ArrayList<Integer> starts = new ArrayList<>();
    private final ArrayList<Integer> ends = new ArrayList<>();
    private String bookText = "";
    private int pageIndex;
    private int requestedProgress;
    private PageListener listener;

    PagedTextView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
    }

    void setPageListener(PageListener value) { listener = value; }

    void setBookText(String value, int progress) {
        bookText = value == null ? "" : value;
        requestedProgress = Math.max(0, Math.min(100, progress));
        post(this::paginate);
    }

    boolean nextPage() {
        if (pageIndex + 1 >= starts.size()) return false;
        pageIndex++;
        showPage();
        return true;
    }

    boolean previousPage() {
        if (pageIndex <= 0) return false;
        pageIndex--;
        showPage();
        return true;
    }

    int pageCount() { return starts.size(); }

    void repaginate() {
        requestedProgress = progress();
        post(this::paginate);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w != oldW || h != oldH) post(this::paginate);
    }

    private void paginate() {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0 || bookText.isEmpty()) {
            if (bookText.isEmpty()) super.setText("В книге не найден текст");
            return;
        }

        int keepProgress = starts.isEmpty() ? requestedProgress : progress();
        starts.clear();
        ends.clear();
        TextPaint paint = getPaint();
        int start = 0;
        while (start < bookText.length()) {
            while (start < bookText.length() && Character.isWhitespace(bookText.charAt(start))) start++;
            if (start >= bookText.length()) break;
            int chunkEnd = Math.min(bookText.length(), start + 12000);
            CharSequence chunk = bookText.subSequence(start, chunkEnd);
            StaticLayout layout = StaticLayout.Builder.obtain(chunk, 0, chunk.length(), paint, width)
                    .setIncludePad(false)
                    .setLineSpacing(dp(5), 1f)
                    .build();
            int lastLine = -1;
            for (int line = 0; line < layout.getLineCount(); line++) {
                if (layout.getLineBottom(line) <= height) lastLine = line;
                else break;
            }
            int end = lastLine >= 0 ? start + layout.getLineEnd(lastLine) : Math.min(bookText.length(), start + 1);
            if (end <= start) end = Math.min(bookText.length(), start + 1);
            starts.add(start);
            ends.add(end);
            start = end;
        }
        pageIndex = starts.size() <= 1 ? 0 : Math.min(starts.size() - 1, keepProgress * (starts.size() - 1) / 100);
        showPage();
    }

    private void showPage() {
        if (starts.isEmpty()) return;
        int start = starts.get(pageIndex);
        int end = ends.get(pageIndex);
        super.setText(bookText.substring(start, end).trim());
        if (listener != null) listener.onPageChanged(pageIndex + 1, starts.size(), progress());
    }

    private int progress() {
        if (starts.size() <= 1) return starts.isEmpty() ? 0 : 100;
        return pageIndex * 100 / (starts.size() - 1);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
