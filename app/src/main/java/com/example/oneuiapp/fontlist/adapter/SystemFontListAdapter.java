package com.example.oneuiapp.fontlist.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SectionIndexer;

import androidx.annotation.NonNull;
import androidx.appcompat.util.SeslRoundedCorner;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;

import com.example.oneuiapp.R;
import com.example.oneuiapp.fontlist.FontFileInfo;
import com.example.oneuiapp.fontlist.search.FontTextHighlighter;
import com.example.oneuiapp.fontlist.viewholder.SystemFontViewHolder;
import com.example.oneuiapp.fontlist.viewholder.SortHeaderViewHolder;
import com.example.oneuiapp.fontlist.systemfont.SystemFontCache;
import com.example.oneuiapp.fontlist.systemfont.SystemFontInfo;
import com.example.oneuiapp.fontlist.systemfont.SystemFontPreferenceManager;
import com.example.oneuiapp.metadata.FontWeightWidthExtractor;
import com.example.oneuiapp.utils.FileUtils;
import com.example.oneuiapp.utils.SettingsHelper;
import com.example.oneuiapp.ui.widget.SortByItemLayout;

import dev.oneuiproject.oneui.widget.RoundLinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * SystemFontListAdapter — مبني على SortedList لأنيميشن الفرز الانسيابي.
 * ★ يحتوي على recyclerView مع onAttachedToRecyclerView / onDetachedFromRecyclerView ★
 * ★ PAYLOAD_UPDATE_HIGHLIGHT يحدّث تظليل نص البحث بصمت عبر bind الجزئي ★
 * ★ isTransparentTheme يُعطّل حسابات الزوايا والفواصل غير الضرورية لتوفير المعالجة ★
 *
 * ★ التعديل: تمرير weightWidthLabel من SystemFontInfo إلى SystemFontViewHolder ★
 */
public class SystemFontListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements SectionIndexer {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_FONT   = 1;
    private static final int VIEW_TYPE_SPACE  = 2;

    private static final String PAYLOAD_UPDATE_CORNERS   = "UPDATE_CORNERS";
    private static final String PAYLOAD_UPDATE_HIGHLIGHT = "UPDATE_HIGHLIGHT";

    private final Context context;
    private final SystemFontPreferenceManager preferenceManager;
    private final FontTextHighlighter highlighter;
    private final Handler mainHandler;
    private final ExecutorService executor;

    // ★ مرجع الـ RecyclerView لاستخدام post() في تأجيل الإشعارات ★
    private RecyclerView recyclerView;

    // ★ حالة الثيم الشفاف — تُقرأ مرة واحدة عند إنشاء الـ Adapter
    //   تُحدّد أي Layout يُستخدم وتُعطّل حسابات الزوايا والفواصل غير المطلوبة ★
    private final boolean isTransparentTheme;

    // ★ يمنع تشغيل نقرتين متزامنتين قبل اكتمال الانتقال الأول ★
    private boolean mClickGuard = false;

    private final SortedList<FontFileInfo> mSortedList;

    private List<SystemFontInfo> allFontsInfo;
    private String currentSearchQuery;

    private List<String> sections;
    private List<Integer> sectionPositions;
    private List<Integer> positionSections;

    private SortByItemLayout.SortType currentSortType;
    private boolean currentSortAscending;

    private OnFontClickListener fontClickListener;
    private SortByItemLayout.OnSortChangeListener sortChangeListener;

    // ─────────────────────────────────────────────────────────
    // SortedList.Callback — الإزاحة +1 بسبب الـ Header في position=0
    // ─────────────────────────────────────────────────────────
    private class FontSortedListCallback extends SortedList.Callback<FontFileInfo> {

        @Override
        public int compare(FontFileInfo a, FontFileInfo b) {
            return compareItems(a, b);
        }

        @Override
        public boolean areItemsTheSame(FontFileInfo a, FontFileInfo b) {
            return a.getPath().equals(b.getPath());
        }

        @Override
        public boolean areContentsTheSame(FontFileInfo a, FontFileInfo b) {
            return a.getName().equals(b.getName()) && a.getSize() == b.getSize();
        }

        @Override public void onInserted(int position, int count) { notifyItemRangeInserted(position + 1, count); }
        @Override public void onRemoved(int position, int count)  { notifyItemRangeRemoved(position + 1, count); }
        @Override public void onMoved(int from, int to)           { notifyItemMoved(from + 1, to + 1); }
        @Override public void onChanged(int position, int count)  { notifyItemRangeChanged(position + 1, count); }
    }

    private int compareItems(FontFileInfo a, FontFileInfo b) {
        if (a == null) return 1;
        if (b == null) return -1;
        int result;
        switch (currentSortType) {
            case DATE:  result = Long.compare(a.getLastModified(), b.getLastModified()); break;
            case SIZE:  result = Long.compare(a.getSize(), b.getSize()); break;
            case NAME:
            default:
                String nameA = a.getName() != null ? a.getName() : "";
                String nameB = b.getName() != null ? b.getName() : "";
                result = nameA.compareToIgnoreCase(nameB);
        }
        return currentSortAscending ? result : -result;
    }

    // ─────────────────────────────────────────────────────────
    // ViewHolder للفراغ السفلي
    // ─────────────────────────────────────────────────────────
    public static class SpaceViewHolder extends RecyclerView.ViewHolder {
        public SpaceViewHolder(@NonNull View itemView) {
            super(itemView);
            itemView.setFocusable(false);
            itemView.setClickable(false);
        }
    }

    public interface OnFontClickListener {
        void onFontClick(String fontPath, String realName, String fileName, int ttcIndex);
    }

    public SystemFontListAdapter(Context context, ExecutorService executor) {
        this.context           = context;
        this.preferenceManager = new SystemFontPreferenceManager(context);
        this.highlighter       = new FontTextHighlighter(context);
        this.mainHandler       = new Handler(Looper.getMainLooper());
        this.executor          = executor;

        this.allFontsInfo       = new ArrayList<>();
        this.currentSearchQuery = "";

        this.sections         = new ArrayList<>();
        this.sectionPositions = new ArrayList<>();
        this.positionSections = new ArrayList<>();

        this.currentSortType      = SortByItemLayout.SortType.NAME;
        this.currentSortAscending = true;

        // ★ قراءة حالة الثيم الشفاف مرة واحدة عند الإنشاء
        //   إذا كان مفعّلاً سيتم استخدام layouts الـ MaterialCardView
        //   وتعطيل حسابات الزوايا الدائرية لتوفير معالجة الـ CPU ★
        this.isTransparentTheme = SettingsHelper.isTransparentThemeEnabled(context);

        this.mSortedList = new SortedList<>(
            FontFileInfo.class,
            new FontSortedListCallback()
        );

        setHasStableIds(true);

        // ★ المراقب: يصحح زوايا OneUI مع تأجيل لتجنب قطع الأنيميشن ★
        // ★ في الثيم الشفاف: لا حاجة لهذه الحسابات فيُتجاهل التنفيذ تماماً ★
        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onItemRangeInserted(int p, int c) { updateListEdges(); }
            @Override public void onItemRangeRemoved(int p, int c)  { updateListEdges(); }
            @Override public void onItemRangeMoved(int f, int t, int c) { updateListEdges(); }

            private void updateListEdges() {
                // ★ توفير معالجة: في الثيم الشفاف لا زوايا دائرية للحساب ★
                if (isTransparentTheme || recyclerView == null) return;
                recyclerView.post(() -> {
                    if (recyclerView == null || recyclerView.isComputingLayout()) return;
                    int total = getItemCount();
                    if (total > 0) notifyItemChanged(0, PAYLOAD_UPDATE_CORNERS);
                    if (total > 2) {
                        int last       = total - 2;
                        int secondLast = total - 3;
                        notifyItemChanged(last, PAYLOAD_UPDATE_CORNERS);
                        if (secondLast > 0) notifyItemChanged(secondLast, PAYLOAD_UPDATE_CORNERS);
                    }
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────
    // ★ ربط / فك ربط الـ RecyclerView ★
    // ─────────────────────────────────────────────────────────
    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        this.recyclerView = null;
    }

    // ─────────────────────────────────────────────────────────
    // Setters
    // ─────────────────────────────────────────────────────────
    public void setFontClickListener(OnFontClickListener l)                    { this.fontClickListener = l; }
    public void setSortChangeListener(SortByItemLayout.OnSortChangeListener l) { this.sortChangeListener = l; }

    // ★ يُستدعى من saveLastOpenedAndUpdate (انتقال مؤكد) أو من unblockTouch (إلغاء) ★
    public void resetClickGuard() { mClickGuard = false; }

    public void saveLastOpenedAndUpdate(String path) {
        mClickGuard = false;
        preferenceManager.saveLastOpenedFont(path);
        smartUpdate();
    }

    public void setAllFontsInfo(List<SystemFontInfo> fontsInfo) {
        this.allFontsInfo = fontsInfo != null ? new ArrayList<>(fontsInfo) : new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────
    // تحديث البيانات
    // ─────────────────────────────────────────────────────────
    public void updateFilteredFonts(List<FontFileInfo> fonts, String searchQuery) {
        String oldQuery = this.currentSearchQuery;
        this.currentSearchQuery = searchQuery != null ? searchQuery : "";
        List<FontFileInfo> newList = fonts != null ? fonts : new ArrayList<>();

        mSortedList.replaceAll(newList);
        buildSections();

        // ★ تحديث تظليل النص للعناصر المتبقية بصمت عبر Payload ★
        if (!this.currentSearchQuery.equals(oldQuery) && recyclerView != null) {
            recyclerView.post(() -> {
                if (recyclerView != null && !recyclerView.isComputingLayout()) {
                    int size = mSortedList.size();
                    if (size > 0) notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_HIGHLIGHT);
                }
            });
        }
    }

    /**
     * ★ أنيميشن الفرز: snapshot → تحديث معيار الفرز → replaceAll → onMoved ★
     */
    public void setSortOptions(SortByItemLayout.SortType sortType, boolean ascending) {
        this.currentSortType      = sortType;
        this.currentSortAscending = ascending;

        final int size = mSortedList.size();
        List<FontFileInfo> snapshot = new ArrayList<>(size);
        for (int i = 0; i < size; i++) snapshot.add(mSortedList.get(i));

        mSortedList.replaceAll(snapshot);
        buildSections();

        // ★ تحديث الهيدر بعد انتهاء أنيميشن العناصر ★
        if (recyclerView != null) {
            recyclerView.post(() -> {
                if (recyclerView != null && !recyclerView.isComputingLayout()) {
                    notifyItemChanged(0);
                }
            });
        }
    }

    public void updateSortOptionsOnly(SortByItemLayout.SortType sortType, boolean ascending) {
        this.currentSortType      = sortType;
        this.currentSortAscending = ascending;
        notifyItemChanged(0);
    }

    public void smartUpdate() {
        buildSections();
        int size = mSortedList.size();
        if (size > 0) notifyItemRangeChanged(1, size);
        else notifyDataSetChanged();
    }

    // ─────────────────────────────────────────────────────────
    // بناء الـ Sections
    // ─────────────────────────────────────────────────────────
    private void buildSections() {
        sections.clear();
        sectionPositions.clear();
        positionSections.clear();

        for (int i = 0; i < mSortedList.size(); i++) {
            String name   = mSortedList.get(i).getName();
            String letter = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase() : "#";
            if (!Character.isLetter(letter.charAt(0))) letter = "#";

            if (sections.isEmpty() || !sections.get(sections.size() - 1).equals(letter)) {
                sections.add(letter);
                sectionPositions.add(i + 1);
            }
            positionSections.add(sections.size() - 1);
        }
    }

    private SystemFontInfo getFontInfoForPath(String path) {
        for (SystemFontInfo font : allFontsInfo)
            if (font.getPath().equals(path)) return font;
        return null;
    }

    // ─────────────────────────────────────────────────────────
    // RecyclerView.Adapter
    // ─────────────────────────────────────────────────────────
    @Override public int getItemCount() { return mSortedList.size() + 2; }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return VIEW_TYPE_HEADER;
        if (position == getItemCount() - 1) return VIEW_TYPE_SPACE;
        return VIEW_TYPE_FONT;
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) return "HEADER".hashCode();
        if (position == getItemCount() - 1) return "SPACE".hashCode();
        return mSortedList.get(position - 1).getPath().hashCode();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(context);

        if (viewType == VIEW_TYPE_HEADER) {
            // ★ اختيار Layout الهيدر بناءً على الثيم:
            //   - الثيم الشفاف: LinearLayout بدون خلفية (لا رسم لزوايا OneUI)
            //   - الثيم الافتراضي: RoundLinearLayout ★
            int headerLayout = isTransparentTheme
                    ? R.layout.sort_header_item_transparent
                    : R.layout.sort_header_item;
            return new SortHeaderViewHolder(inf.inflate(headerLayout, parent, false));
        }

        if (viewType == VIEW_TYPE_SPACE) {
            return new SpaceViewHolder(inf.inflate(R.layout.item_bottom_space, parent, false));
        }

        // ★ اختيار Layout العنصر بناءً على الثيم:
        //   - الثيم الشفاف: MaterialCardView بزوايا دائرية كاملة وخلفية شفافة
        //   - الثيم الافتراضي: RoundLinearLayout مع حسابات زوايا OneUI ★
        int itemLayout = isTransparentTheme
                ? R.layout.font_list_item_transparent
                : R.layout.font_list_item;
        return new SystemFontViewHolder(inf.inflate(itemLayout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SortHeaderViewHolder) {
            ((SortHeaderViewHolder) holder).bind(currentSortType, currentSortAscending, sortChangeListener);
        } else if (holder instanceof SystemFontViewHolder) {
            bindFontViewHolder((SystemFontViewHolder) holder, mSortedList.get(position - 1));
        }
        updateItemAppearance(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            if (payloads.contains(PAYLOAD_UPDATE_CORNERS)) {
                updateItemAppearance(holder, position);
            }
            // ★ تحديث تظليل نص البحث بصمت عبر bind الجزئي دون إعادة رسم العنصر ★
            // وصف الوزن/العرض لا يتغير عند تغيير البحث، فنمرره أيضاً للحفاظ على القيمة
            if (payloads.contains(PAYLOAD_UPDATE_HIGHLIGHT) && holder instanceof SystemFontViewHolder) {
                FontFileInfo fontInfo      = mSortedList.get(position - 1);
                String displayName         = FileUtils.removeExtension(fontInfo.getName());
                boolean isSearchActive     = currentSearchQuery != null && !currentSearchQuery.isEmpty();
                boolean isLastOpened       = preferenceManager.isLastOpenedFont(fontInfo.getPath());
                SystemFontInfo sfi         = getFontInfoForPath(fontInfo.getPath());
                String weightWidthLabel    = (sfi != null) ? sfi.getWeightWidthLabel() : null;
                ((SystemFontViewHolder) holder).bind(
                    displayName, fontInfo.getPath(), isSearchActive,
                    currentSearchQuery, isLastOpened, highlighter, weightWidthLabel
                );
            }
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    // ─────────────────────────────────────────────────────────
    // ★ هندسة الزوايا الدائرية (OneUI) ★
    // في الثيم الشفاف: نعود فوراً لأن MaterialCardView يتولى الزوايا
    // بشكل تلقائي دون أي حساب إضافي، مما يوفر معالجة الـ CPU أثناء التمرير
    // ─────────────────────────────────────────────────────────
    private void updateItemAppearance(RecyclerView.ViewHolder holder, int position) {
        // ★ توفير معالجة: في الثيم الشفاف، MaterialCardView يدير الزوايا تلقائياً ★
        if (isTransparentTheme) return;

        if (holder instanceof SortHeaderViewHolder) {
            RoundLinearLayout root = (RoundLinearLayout) holder.itemView;
            View divider           = holder.itemView.findViewById(R.id.divider);

            if (mSortedList.size() == 0) {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_ALL);
                if (divider != null) divider.setVisibility(View.GONE);
            } else {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT
                                     | SeslRoundedCorner.ROUNDED_CORNER_TOP_RIGHT);
                if (divider != null) divider.setVisibility(View.VISIBLE);
            }

        } else if (holder instanceof SystemFontViewHolder) {
            SystemFontViewHolder sfh = (SystemFontViewHolder) holder;
            RoundLinearLayout root   = (RoundLinearLayout) sfh.itemView;
            boolean isLast           = (position == getItemCount() - 2);

            if (isLast) {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_LEFT
                                     | SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_RIGHT);
                if (sfh.dividerView != null) sfh.dividerView.setVisibility(View.INVISIBLE);
            } else {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_NONE);
                if (sfh.dividerView != null) sfh.dividerView.setVisibility(View.VISIBLE);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // ربط بيانات عنصر الخط
    // ─────────────────────────────────────────────────────────
    private void bindFontViewHolder(SystemFontViewHolder holder, FontFileInfo fontInfo) {
        String fileName    = fontInfo.getName();
        String path        = fontInfo.getPath();
        String displayName = FileUtils.removeExtension(fileName);

        SystemFontInfo sfi = getFontInfoForPath(path);
        String realName    = (sfi != null) ? sfi.getRealName() : null;
        int ttcIndex       = (sfi != null) ? sfi.getTtcIndex() : 0;

        if (realName == null || realName.isEmpty()) realName = context.getString(R.string.unknown_font);

        // ★ استخراج وصف الوزن/العرض من SystemFontInfo ★
        String weightWidthLabel = (sfi != null) ? sfi.getWeightWidthLabel() : null;

        boolean isSearchActive = currentSearchQuery != null && !currentSearchQuery.isEmpty();
        boolean isLastOpened   = preferenceManager.isLastOpenedFont(path);

        // ★ تمرير weightWidthLabel إلى bind() ★
        holder.bind(displayName, path, isSearchActive, currentSearchQuery,
                    isLastOpened, highlighter, weightWidthLabel);

        if (SettingsHelper.isFontPreviewEnabled(context)) loadFontPreview(holder, path);
        else holder.setDefaultTypeface(SettingsHelper.getTypeface(context));

        final String finalRealName = realName;
        final int    finalTtcIndex = ttcIndex;

        holder.setOnClickListener(v -> {
            // ★ الحارس: يمنع تشغيل نقرتين متزامنتين ★
            if (mClickGuard) return;
            mClickGuard = true;
            if (fontClickListener != null)
                fontClickListener.onFontClick(path, finalRealName, fileName, finalTtcIndex);
        });
    }

    private void loadFontPreview(SystemFontViewHolder holder, String path) {
        SystemFontCache cache = SystemFontCache.getInstance();
        Typeface cached       = cache.getIfCached(path);

        if (cached != null) {
            holder.setTypeface(cached);
        } else {
            holder.setDefaultTypeface(SettingsHelper.getTypeface(context));
            if (executor != null && !executor.isShutdown()) {
                executor.execute(() -> {
                    Typeface loaded = cache.getTypeface(path);
                    if (loaded != null) {
                        mainHandler.post(() -> {
                            if (path.equals(holder.getTag())) holder.setTypeface(loaded);
                        });
                    }
                });
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // SectionIndexer
    // ─────────────────────────────────────────────────────────
    @Override public Object[] getSections() { return sections.toArray(); }

    @Override
    public int getPositionForSection(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= sectionPositions.size()) return 0;
        return sectionPositions.get(sectionIndex);
    }

    @Override
    public int getSectionForPosition(int position) {
        if (position <= 0) return 0;
        if (position >= getItemCount() - 1)
            return positionSections.isEmpty() ? 0 : positionSections.get(positionSections.size() - 1);
        int adj = position - 1;
        if (adj < 0 || adj >= positionSections.size()) return 0;
        return positionSections.get(adj);
    }
            }
