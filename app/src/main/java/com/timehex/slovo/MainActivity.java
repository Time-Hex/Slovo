package com.timehex.slovo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.pdf.PdfRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_FILES = 101;
    private static final int PICK_FOLDER = 102;
    private static final int INK = Color.rgb(9, 11, 24);
    private static final int PANEL = Color.rgb(17, 20, 43);
    private static final int VIOLET = Color.rgb(138, 114, 248);
    private static final int IVORY = Color.rgb(244, 240, 232);
    private static final int MUTED = Color.rgb(170, 168, 186);
    private static final int LINE = Color.rgb(41, 45, 74);

    private LibraryStore store;
    private FrameLayout content;
    private LinearLayout nav;
    private TextView screenTitle;
    private String screen = "library";
    private MediaPlayer player;
    private LibraryStore.Item playingItem;
    private int playingChapter;
    private SeekBar audioSeek;
    private TextView audioTime;
    private Button playButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean userSeeking;
    private String readerTitle = "";
    private float readerBrightness = 0.65f;
    private final Runnable restoreReaderTitle = () -> {
        if ("reader".equals(screen)) screenTitle.setText(readerTitle);
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(INK);
        getWindow().setNavigationBarColor(INK);
        store = new LibraryStore(this);
        buildShell();
        showLibrary("");
    }

    private void buildShell() {
        LinearLayout root = column();
        root.setBackgroundColor(INK);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(14), dp(14), dp(10));
        TextView mark = text("S", 24, IVORY);
        mark.setTypeface(Typeface.SERIF, Typeface.BOLD_ITALIC);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(VIOLET, 15));
        header.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));
        screenTitle = text("Библиотека", 24, IVORY);
        screenTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.leftMargin = dp(12);
        header.addView(screenTitle, titleParams);
        root.addView(header);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        nav = row();
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(7), dp(8), dp(8));
        nav.setBackgroundColor(PANEL);
        addNav("Библиотека", () -> showLibrary(""));
        addNav("Полки", this::showShelves);
        addNav("Настройки", this::showSettings);
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        setContentView(root);
    }

    private void addNav(String label, Runnable action) {
        Button b = button(label, false);
        b.setTextSize(12);
        b.setOnClickListener(v -> action.run());
        nav.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private void showLibrary(String query) {
        screen = "library";
        screenTitle.setText("Библиотека");
        nav.setVisibility(View.VISIBLE);
        content.removeAllViews();
        LinearLayout body = column();
        body.setPadding(dp(16), dp(4), dp(16), dp(18));

        EditText search = new EditText(this);
        search.setHint("Найти книгу");
        search.setHintTextColor(MUTED);
        search.setTextColor(IVORY);
        search.setSingleLine(true);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(round(PANEL, 16));
        search.setText(query);
        body.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        LinearLayout actions = row();
        actions.setPadding(0, dp(10), 0, dp(4));
        Button files = button("+ Книги и аудио", true);
        files.setOnClickListener(v -> pickFiles());
        actions.addView(files, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button folder = button("+ Папка аудио", false);
        folder.setOnClickListener(v -> pickFolder());
        LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        folderParams.leftMargin = dp(8);
        actions.addView(folder, folderParams);
        body.addView(actions);

        TextView count = text("В библиотеке: " + store.items().size(), 13, MUTED);
        count.setPadding(dp(2), dp(8), 0, dp(8));
        body.addView(count);

        LinearLayout list = column();
        body.addView(list);
        renderItems(list, query);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            public void onTextChanged(CharSequence s, int st, int b, int c) { renderItems(list, s.toString()); }
            public void afterTextChanged(Editable s) { }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        content.addView(scroll);
    }

    private void renderItems(LinearLayout target, String query) {
        target.removeAllViews();
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        ArrayList<LibraryStore.Item> visible = new ArrayList<>();
        for (LibraryStore.Item item : store.items()) if (q.isEmpty() || item.title.toLowerCase(Locale.ROOT).contains(q)) visible.add(item);
        Collections.sort(visible, (a, b) -> Long.compare(b.progress, a.progress));
        if (visible.isEmpty()) {
            TextView empty = text(store.items().isEmpty() ? "Добавьте первую книгу или аудиокнигу" : "Ничего не найдено", 16, MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(64), dp(20), dp(64));
            target.addView(empty);
            return;
        }
        for (LibraryStore.Item item : visible) target.addView(bookCard(item));
    }

    private View bookCard(LibraryStore.Item item) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(PANEL, 18));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(10);
        card.setLayoutParams(cp);

        TextView title = text(item.title, 18, IVORY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title);
        String kind = item.kind == LibraryStore.Kind.AUDIO ? "Аудиокнига" : item.kind == LibraryStore.Kind.PDF ? "PDF" : "Книга";
        String shelfText = item.shelves.isEmpty() ? "Без полки" : join(item.shelves);
        TextView meta = text(kind + "  •  " + shelfText, 13, MUTED);
        meta.setPadding(0, dp(5), 0, dp(7));
        card.addView(meta);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(item.progress);
        progress.setProgressTintList(ColorStateList.valueOf(VIOLET));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        card.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5)));
        TextView percent = text(item.progress + "%", 12, MUTED);
        percent.setGravity(Gravity.END);
        card.addView(percent);

        LinearLayout buttons = row();
        buttons.setPadding(0, dp(7), 0, 0);
        Button open = button(item.progress > 0 ? "Продолжить" : "Открыть", true);
        open.setOnClickListener(v -> openItem(item));
        buttons.addView(open, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button shelves = button("Полки", false);
        shelves.setOnClickListener(v -> chooseShelves(item));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(42), 1);
        sp.leftMargin = dp(7);
        buttons.addView(shelves, sp);
        Button more = button("⋮", false);
        more.setOnClickListener(v -> confirmDelete(item));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(48), dp(42));
        mp.leftMargin = dp(7);
        buttons.addView(more, mp);
        card.addView(buttons);
        return card;
    }

    private void showShelves() {
        screen = "shelves";
        screenTitle.setText("Полки");
        nav.setVisibility(View.VISIBLE);
        content.removeAllViews();
        LinearLayout body = column();
        body.setPadding(dp(16), dp(4), dp(16), dp(20));
        Button add = button("+ Создать полку", true);
        add.setOnClickListener(v -> newShelfDialog());
        body.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        for (String shelf : new ArrayList<>(store.shelves())) {
            int count = 0;
            for (LibraryStore.Item item : store.items()) if (item.shelves.contains(shelf)) count++;
            LinearLayout card = row();
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(12), dp(8), dp(12));
            card.setBackground(round(PANEL, 17));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72));
            p.topMargin = dp(10);
            TextView name = text(shelf + "\n" + count + " книг", 16, IVORY);
            card.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button open = button("Открыть", false);
            String selected = shelf;
            open.setOnClickListener(v -> showShelf(selected));
            card.addView(open, new LinearLayout.LayoutParams(dp(86), dp(42)));
            card.setOnLongClickListener(v -> { deleteShelfDialog(selected); return true; });
            body.addView(card, p);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        content.addView(scroll);
    }

    private void showShelf(String shelf) {
        screen = "shelf";
        screenTitle.setText(shelf);
        nav.setVisibility(View.GONE);
        content.removeAllViews();
        LinearLayout body = column();
        body.setPadding(dp(16), dp(4), dp(16), dp(20));
        Button back = button("‹ Все полки", false);
        back.setOnClickListener(v -> showShelves());
        body.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45)));
        for (LibraryStore.Item item : store.items()) if (item.shelves.contains(shelf)) body.addView(bookCard(item));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        content.addView(scroll);
    }

    private void showSettings() {
        screen = "settings";
        screenTitle.setText("Настройки");
        nav.setVisibility(View.VISIBLE);
        content.removeAllViews();
        LinearLayout body = column();
        body.setPadding(dp(18), dp(10), dp(18), dp(18));
        TextView about = text("Slovo 0.3.0\n\nКниги остаются на вашем телефоне. Приложение работает без аккаунта, сервера и рекламы. Дополнительное озвучивание текста отсутствует.\n\nПоддержка: TXT, FB2, EPUB, PDF, MP3, M4B, M4A, AAC, OGG и WAV.", 16, IVORY);
        about.setLineSpacing(dp(4), 1f);
        about.setPadding(dp(18), dp(18), dp(18), dp(18));
        about.setBackground(round(PANEL, 18));
        body.addView(about);
        content.addView(body);
    }

    private void openItem(LibraryStore.Item item) {
        if (item.uris.isEmpty()) { toast("Файл больше недоступен"); return; }
        if (item.kind == LibraryStore.Kind.AUDIO) showAudio(item);
        else if (item.kind == LibraryStore.Kind.PDF) showPdf(item);
        else showTextReader(item);
    }

    private void showTextReader(LibraryStore.Item item) {
        enterReader(item.title);
        LinearLayout page = column();
        TextView loading = text("Открываю книгу…", 16, MUTED);
        loading.setGravity(Gravity.CENTER);
        page.addView(loading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        content.addView(page);
        new Thread(() -> {
            try {
                String value = BookContentLoader.load(getContentResolver(), Uri.parse(item.uris.get(0)));
                runOnUiThread(() -> renderText(item, value));
            } catch (Exception e) {
                runOnUiThread(() -> { toast("Не удалось открыть книгу"); showLibrary(""); });
            }
        }).start();
    }

    private void renderText(LibraryStore.Item item, String value) {
        content.removeAllViews();
        applyReaderBrightness();
        LinearLayout page = column();
        PagedTextView book = new PagedTextView(this);
        book.setTextSize(18);
        book.setTextColor(IVORY);
        book.setTypeface(Typeface.SERIF);
        book.setLineSpacing(dp(5), 1f);
        book.setGravity(Gravity.TOP);
        book.setPadding(dp(22), dp(18), dp(22), dp(18));
        page.addView(book, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(item.progress);
        progress.setProgressTintList(ColorStateList.valueOf(VIOLET));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        page.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));

        LinearLayout footer = row();
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(12), dp(6), dp(12), dp(8));
        Button previous = button("‹", false);
        footer.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(40)));
        TextView counter = text("Подготовка страниц…", 13, MUTED);
        counter.setGravity(Gravity.CENTER);
        footer.addView(counter, new LinearLayout.LayoutParams(0, dp(40), 1));
        Button next = button("›", false);
        footer.addView(next, new LinearLayout.LayoutParams(dp(48), dp(40)));
        page.addView(footer);

        TextView hint = text("← → страницы     ↑ ↓ яркость", 12, MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, dp(7));
        page.addView(hint);

        book.setPageListener((current, count, valueProgress) -> {
            counter.setText("Страница " + current + " из " + count);
            progress.setProgress(valueProgress);
            item.progress = valueProgress;
            store.save();
        });
        previous.setOnClickListener(v -> book.previousPage());
        next.setOnClickListener(v -> book.nextPage());
        attachReaderGestures(book, book::nextPage, book::previousPage);
        content.addView(page);
        book.setBookText(value, item.progress);
    }

    private void showPdf(LibraryStore.Item item) {
        enterReader(item.title);
        applyReaderBrightness();
        try {
            ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(Uri.parse(item.uris.get(0)), "r");
            if (fd == null) throw new Exception("missing file");
            PdfRenderer renderer = new PdfRenderer(fd);
            LinearLayout page = column();
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            page.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            TextView counter = text("", 13, MUTED);
            counter.setGravity(Gravity.CENTER);
            page.addView(counter);
            SeekBar seek = new SeekBar(this);
            seek.setMax(Math.max(0, renderer.getPageCount() - 1));
            int start = Math.min(seek.getMax(), item.progress * Math.max(0, renderer.getPageCount() - 1) / 100);
            seek.setProgress(start);
            page.addView(seek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            final PdfRenderer.Page[] current = new PdfRenderer.Page[1];
            Runnable draw = () -> {
                if (current[0] != null) current[0].close();
                current[0] = renderer.openPage(seek.getProgress());
                int width = Math.max(dp(320), content.getWidth());
                int height = width * current[0].getHeight() / Math.max(1, current[0].getWidth());
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                current[0].render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                image.setImageBitmap(bitmap);
                counter.setText((seek.getProgress() + 1) + " / " + renderer.getPageCount());
                item.progress = renderer.getPageCount() <= 1 ? 100 : seek.getProgress() * 100 / (renderer.getPageCount() - 1);
                store.save();
            };
            seek.setOnSeekBarChangeListener(new SimpleSeek() { public void onStopTrackingTouch(SeekBar bar) { draw.run(); } });
            Runnable nextPage = () -> {
                if (seek.getProgress() < seek.getMax()) { seek.setProgress(seek.getProgress() + 1); draw.run(); }
            };
            Runnable previousPage = () -> {
                if (seek.getProgress() > 0) { seek.setProgress(seek.getProgress() - 1); draw.run(); }
            };
            attachReaderGestures(image, nextPage, previousPage);
            TextView hint = text("← → страницы     ↑ ↓ яркость", 12, MUTED);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, 0, 0, dp(7));
            page.addView(hint);
            content.addView(page);
            image.post(draw);
        } catch (Exception e) {
            toast("Не удалось открыть PDF");
            showLibrary("");
        }
    }

    private void showAudio(LibraryStore.Item item) {
        enterReader(item.title);
        playingItem = item;
        LinearLayout page = column();
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(22), dp(36), dp(22), dp(26));
        TextView cover = text("S", 72, IVORY);
        cover.setGravity(Gravity.CENTER);
        cover.setTypeface(Typeface.SERIF, Typeface.BOLD_ITALIC);
        cover.setBackground(round(PANEL, 28));
        page.addView(cover, new LinearLayout.LayoutParams(dp(230), dp(230)));
        TextView title = text(item.title, 23, IVORY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(24), 0, dp(4));
        page.addView(title);
        TextView chapter = text(item.uris.size() > 1 ? "Глава 1 из " + item.uris.size() : "Аудиокнига", 14, MUTED);
        chapter.setGravity(Gravity.CENTER);
        page.addView(chapter);
        audioSeek = new SeekBar(this);
        audioSeek.setMax(1000);
        audioSeek.setProgressTintList(ColorStateList.valueOf(VIOLET));
        page.addView(audioSeek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        audioTime = text("00:00  •  00:00", 13, MUTED);
        audioTime.setGravity(Gravity.CENTER);
        page.addView(audioTime);
        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(20), 0, 0);
        Button back = button("−15", false);
        back.setTextSize(18);
        back.setOnClickListener(v -> seekAudio(-15000));
        controls.addView(back, new LinearLayout.LayoutParams(dp(72), dp(58)));
        playButton = button("▶", true);
        playButton.setTextSize(22);
        playButton.setOnClickListener(v -> toggleAudio());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(76), dp(64));
        pp.leftMargin = pp.rightMargin = dp(16);
        controls.addView(playButton, pp);
        Button next = button("+15", false);
        next.setTextSize(18);
        next.setOnClickListener(v -> seekAudio(15000));
        controls.addView(next, new LinearLayout.LayoutParams(dp(72), dp(58)));
        page.addView(controls);
        content.addView(page);
        audioSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            public void onStartTrackingTouch(SeekBar s) { userSeeking = true; }
            public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                if (player != null) player.seekTo(player.getDuration() * s.getProgress() / 1000);
            }
        });
        startAudio(item, 0, item.positionMs, false, chapter);
    }

    private void startAudio(LibraryStore.Item item, int chapterIndex, long position, boolean autoplay, TextView chapterLabel) {
        releasePlayer();
        playingChapter = Math.max(0, Math.min(chapterIndex, item.uris.size() - 1));
        try {
            player = new MediaPlayer();
            player.setDataSource(this, Uri.parse(item.uris.get(playingChapter)));
            player.setOnPreparedListener(mp -> {
                if (position > 0 && position < mp.getDuration()) mp.seekTo((int) position);
                item.durationMs = mp.getDuration();
                if (autoplay) mp.start();
                updatePlayButton();
                handler.post(updateAudio);
            });
            player.setOnCompletionListener(mp -> {
                if (playingChapter + 1 < item.uris.size()) startAudio(item, playingChapter + 1, 0, true, chapterLabel);
                else { item.progress = 100; item.positionMs = 0; store.save(); updatePlayButton(); }
            });
            player.prepareAsync();
            if (item.uris.size() > 1) chapterLabel.setText("Глава " + (playingChapter + 1) + " из " + item.uris.size());
        } catch (Exception e) { toast("Не удалось открыть аудиофайл"); }
    }

    private final Runnable updateAudio = new Runnable() {
        @Override public void run() {
            if (player != null && playingItem != null) {
                try {
                    int duration = Math.max(1, player.getDuration());
                    int position = player.getCurrentPosition();
                    if (!userSeeking && audioSeek != null) audioSeek.setProgress(position * 1000 / duration);
                    if (audioTime != null) audioTime.setText(time(position) + "  •  " + time(duration));
                    float total = playingItem.uris.size();
                    playingItem.progress = Math.min(100, Math.round(((playingChapter + position / (float) duration) / total) * 100));
                    playingItem.positionMs = position;
                    playingItem.durationMs = duration;
                    store.save();
                } catch (Exception ignored) { }
                handler.postDelayed(this, 700);
            }
        }
    };

    private void toggleAudio() {
        if (player == null) return;
        if (player.isPlaying()) player.pause(); else player.start();
        updatePlayButton();
    }

    private void seekAudio(int delta) {
        if (player == null) return;
        int target = Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + delta));
        player.seekTo(target);
    }

    private void updatePlayButton() { if (playButton != null && player != null) playButton.setText(player.isPlaying() ? "Ⅱ" : "▶"); }

    private void enterReader(String title) {
        screen = "reader";
        readerTitle = title;
        screenTitle.setText(title);
        nav.setVisibility(View.GONE);
        content.removeAllViews();
        screenTitle.setOnClickListener(v -> leaveReader());
        toast("Нажмите на название сверху, чтобы вернуться");
    }

    private void leaveReader() {
        releasePlayer();
        resetReaderBrightness();
        handler.removeCallbacks(restoreReaderTitle);
        screenTitle.setOnClickListener(null);
        showLibrary("");
    }

    private void attachReaderGestures(View target, Runnable nextPage, Runnable previousPage) {
        target.setClickable(true);
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final float[] startBrightness = new float[1];
        final boolean[] vertical = new boolean[1];
        target.setOnTouchListener((view, event) -> {
            float x = event.getRawX();
            float y = event.getRawY();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX[0] = x;
                downY[0] = y;
                startBrightness[0] = readerBrightness;
                vertical[0] = false;
                return true;
            }
            float dx = x - downX[0];
            float dy = y - downY[0];
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (!vertical[0] && Math.abs(dy) > dp(12) && Math.abs(dy) > Math.abs(dx) * 1.2f) vertical[0] = true;
                if (vertical[0]) {
                    float height = Math.max(dp(240), view.getHeight());
                    readerBrightness = clamp(startBrightness[0] - dy / height, 0.05f, 1f);
                    setWindowBrightness(readerBrightness);
                    showBrightnessHint();
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (vertical[0]) {
                    getSharedPreferences("slovo_reader", MODE_PRIVATE).edit().putFloat("brightness", readerBrightness).apply();
                    showBrightnessHint();
                } else if (Math.abs(dx) > dp(55) && Math.abs(dx) > Math.abs(dy)) {
                    if (dx < 0) nextPage.run(); else previousPage.run();
                } else view.performClick();
                return true;
            }
            return event.getActionMasked() != MotionEvent.ACTION_CANCEL;
        });
    }

    private void applyReaderBrightness() {
        readerBrightness = getSharedPreferences("slovo_reader", MODE_PRIVATE).getFloat("brightness", 0.65f);
        readerBrightness = clamp(readerBrightness, 0.05f, 1f);
        setWindowBrightness(readerBrightness);
    }

    private void setWindowBrightness(float value) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = value;
        getWindow().setAttributes(params);
    }

    private void resetReaderBrightness() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(params);
    }

    private void showBrightnessHint() {
        handler.removeCallbacks(restoreReaderTitle);
        screenTitle.setText("Яркость " + Math.round(readerBrightness * 100) + "%");
        handler.postDelayed(restoreReaderTitle, 700);
    }

    private void pickFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "application/pdf", "application/epub+zip", "application/xml", "audio/*", "application/octet-stream"});
        startActivityForResult(intent, PICK_FILES);
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_FILES) {
            ClipData clip = data.getClipData();
            if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) importFile(clip.getItemAt(i).getUri());
            else if (data.getData() != null) importFile(data.getData());
            showLibrary("");
        } else if (requestCode == PICK_FOLDER && data.getData() != null) {
            importAudioFolder(data.getData());
            showLibrary("");
        }
    }

    private void importFile(Uri uri) {
        persist(uri);
        String name = displayName(uri);
        LibraryStore.Item item = new LibraryStore.Item();
        item.title = stripExtension(name);
        item.kind = LibraryStore.kindFor(name);
        item.uris.add(uri.toString());
        store.add(item);
    }

    private void importAudioFolder(Uri tree) {
        persist(tree);
        ArrayList<NamedUri> files = new ArrayList<>();
        try {
            String treeId = DocumentsContract.getTreeDocumentId(tree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
            try (Cursor cursor = getContentResolver().query(children,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                if (cursor != null) while (cursor.moveToNext()) {
                    String id = cursor.getString(0), name = cursor.getString(1), mime = cursor.getString(2);
                    if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(mime) && LibraryStore.kindFor(name) == LibraryStore.Kind.AUDIO) {
                        files.add(new NamedUri(name, DocumentsContract.buildDocumentUriUsingTree(tree, id)));
                    }
                }
            }
        } catch (Exception e) { toast("Не удалось прочитать папку"); }
        files.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
        if (files.isEmpty()) { toast("В папке не найдено аудиофайлов"); return; }
        LibraryStore.Item item = new LibraryStore.Item();
        item.kind = LibraryStore.Kind.AUDIO;
        item.title = stripExtension(files.get(0).name);
        for (NamedUri file : files) item.uris.add(file.uri.toString());
        store.add(item);
    }

    private void chooseShelves(LibraryStore.Item item) {
        String[] names = store.shelves().toArray(new String[0]);
        boolean[] checked = new boolean[names.length];
        for (int i = 0; i < names.length; i++) checked[i] = item.shelves.contains(names[i]);
        new AlertDialog.Builder(this).setTitle("Разместить на полках")
                .setMultiChoiceItems(names, checked, (d, which, value) -> checked[which] = value)
                .setPositiveButton("Сохранить", (d, w) -> {
                    item.shelves.clear();
                    for (int i = 0; i < names.length; i++) if (checked[i]) item.shelves.add(names[i]);
                    store.save(); showLibrary("");
                }).setNegativeButton("Отмена", null).show();
    }

    private void newShelfDialog() {
        EditText input = new EditText(this);
        input.setHint("Название полки");
        new AlertDialog.Builder(this).setTitle("Новая полка").setView(input)
                .setPositiveButton("Создать", (d, w) -> { store.addShelf(input.getText().toString()); showShelves(); })
                .setNegativeButton("Отмена", null).show();
    }

    private void deleteShelfDialog(String shelf) {
        new AlertDialog.Builder(this).setTitle("Удалить полку «" + shelf + "»?")
                .setMessage("Книги останутся в библиотеке.")
                .setPositiveButton("Удалить", (d, w) -> { store.removeShelf(shelf); showShelves(); })
                .setNegativeButton("Отмена", null).show();
    }

    private void confirmDelete(LibraryStore.Item item) {
        new AlertDialog.Builder(this).setTitle("Убрать из библиотеки?")
                .setMessage("Сам файл останется на телефоне.")
                .setPositiveButton("Убрать", (d, w) -> { store.remove(item); showLibrary(""); })
                .setNegativeButton("Отмена", null).show();
    }

    private void persist(Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) { }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return LibraryStore.titleFor(uri);
    }

    private void releasePlayer() {
        handler.removeCallbacks(updateAudio);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    @Override public void onBackPressed() {
        if ("reader".equals(screen)) leaveReader();
        else if ("shelf".equals(screen)) showShelves();
        else if (!"library".equals(screen)) showLibrary("");
        else super.onBackPressed();
    }

    @Override protected void onDestroy() { releasePlayer(); super.onDestroy(); }

    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private TextView text(String value, int sp, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); return v; }
    private Button button(String value, boolean primary) {
        Button b = new Button(this);
        b.setText(value); b.setTextSize(13); b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : IVORY);
        b.setBackground(round(primary ? VIOLET : LINE, 14));
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private String stripExtension(String name) { int p = name.lastIndexOf('.'); return p > 0 ? name.substring(0, p) : name; }
    private String join(Iterable<String> values) { StringBuilder s = new StringBuilder(); for (String value : values) { if (s.length() > 0) s.append(", "); s.append(value); } return s.toString(); }
    private String time(long ms) { long sec = Math.max(0, ms / 1000); return String.format(Locale.ROOT, "%02d:%02d", sec / 60, sec % 60); }
    private float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private static class NamedUri { final String name; final Uri uri; NamedUri(String name, Uri uri) { this.name = name; this.uri = uri; } }
    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        public void onProgressChanged(SeekBar s, int p, boolean fromUser) { }
        public void onStartTrackingTouch(SeekBar s) { }
        public void onStopTrackingTouch(SeekBar s) { }
    }
}
