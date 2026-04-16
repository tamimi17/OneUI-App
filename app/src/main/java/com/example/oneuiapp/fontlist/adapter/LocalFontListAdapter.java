package com.example.oneuiapp.fontlist.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseBooleanArray;
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
import com.example.oneuiapp.fontlist.localfont.LocalFontCache;
import com.example.oneuiapp.fontlist.localfont.LocalFontPreferenceManager;
import com.example.oneuiapp.fontlist.search.FontTextHighlighter;
import com.example.oneuiapp.fontlist.viewholder.LocalFontViewHolder;
import com.example.oneuiapp.fontlist.viewholder.SortHeaderViewHolder;
import com.example.oneuiapp.metadata.FontWeightWidthExtractor;
import com.example.oneuiapp.utils.FileUtils;
import com.example.oneuiapp.utils.SettingsHelper;
import com.example.oneuiapp.viewmodel.LocalFontListViewModel;
import com.example.oneuiapp.ui.widget.SortByItemLayout;

import dev.oneuiproject.oneui.widget.RoundLinearLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * LocalFontListAdapter — مبني على SortedList لأنيميشن الفرز الانسيابي
 * ★ SortedList يتولى الترتيب وتوليد onMoved/onInserted/onRemoved تلقائياً ★
 * ★ AdapterDataObserver يصحح زوايا OneUI عند كل تغيير ★
 * ★ isTransparentTheme يُعطّل حسابات الزوايا والفواصل غير الضرورية لتوفير المعالجة ★
 *
 * ★ التعديل: تمرير weightWidthLabel من FontFileInfoWithMetadata إلى LocalFontViewHolder ★
 */
public class LocalFontListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements SectionIndexer {

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_FONT   = 1;
    public static final int VIEW_TYPE_SPACE  = 2;

    private static final String PAYLOAD_UPDATE_CORNERS   = "UPDATE_CORNERS";
    private static final String PAYLOAD_UPDATE_HIGHLIGHT = "UPDATE_HIGHLIGHT";

    private final Context context;
    private final LocalFontPreferenceManager preferenceManager;
    private final FontTextHighlighter highlighter;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private RecyclerView recyclerView;

    // ★ حالة الثيم الشفاف — تُقرأ مرة واحدة عند إنشاء الـ Adapter
    //   تُحدّد أي Layout يُستخدم وتُعطّل حسابات الزوايا والفواصل غير المطلوبة ★
    private final boolean isTransparentTheme;

    // ★ يمنع تشغيل نقرتين متزامنتين قبل اكتمال الانتقال الأول ★
    private boolean mClickGuard = false;

    // ★ SortedList يحل محل List العادية — يرتب ويُنيم تلقائياً ★
    private final SortedList<FontFileInfo> mSortedList;

    private HashMap<String, LocalFontListViewModel.FontFileInfoWithMetadata> fontsMetadataMap;
    private String currentSearchQuery;

    private List<String> sections;
    private List<Integer> sectionPositions;
    private List<Integer> positionSections;

    private SortByItemLayout.SortType currentSortType;
    private boolean currentSortAscending;

    private boolean isSelectionMode = false;
    private SparseBooleanArray selectedItems = new SparseBooleanArray();

    private OnFontClickListener fontClickListener;
    private SortByItemLayout.OnSortChangeListener sortChangeListener;
    private OnSelectionListener selectionListener;

    // ─────────────────────────────────────────────────────────
    // ★ SortedList.Callback — يُترجم أحداث SortedList إلى إشعارات الـ Adapter ★
    // الإزاحة +1 ضرورية لأن position=0 محجوزة للـ Header
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

        @Override
        public void onInserted(int position, int count) {
            notifyItemRangeInserted(position + 1, count);
        }

        @Override
        public void onRemoved(int position, int count) {
            notifyItemRangeRemoved(position + 1, count);
        }

        @Override
        public void onMoved(int fromPosition, int toPosition) {
            // ★ هذا هو قلب أنيميشن الفرز — يُحرّك العنصر من موقعه إلى موقعه الجديد ★
            notifyItemMoved(fromPosition + 1, toPosition + 1);
        }

        @Override
        public void onChanged(int position, int count) {
            notifyItemRangeChanged(position + 1, count);
        }
    }

    // ─────────────────────────────────────────────────────────
    // دالة المقارنة — تقرأ currentSortType و currentSortAscending مباشرة
    // ─────────────────────────────────────────────────────────
    private int compareItems(FontFileInfo a, FontFileInfo b) {
        if (a == null) return 1;
        if (b == null) return -1;
        int result;
        switch (currentSortType) {
            case DATE:
                result = Long.compare(a.getLastModified(), b.getLastModified());
                break;
            case SIZE:
                result = Long.compare(a.getSize(), b.getSize());
                break;
            case NAME:
            default:
                String nameA = a.getName() != null ? a.getName() : "";
                String nameB = b.getName() != null ? b.getName() : "";
                result = nameA.compareToIgnoreCase(nameB);
        }
        return currentSortAscending ? result : -result;
    }

    // ─────────────────────────────────────────────────────────
    // ★ ViewHolder للفراغ السفلي الوهمي ★
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

    public interface OnSelectionListener {
        void onStartSelection(int position);
        void onToggleSelection(int position);
    }

    public LocalFontListAdapter(Context context, ExecutorService executor) {
        this.context = context;
        this.preferenceManager = new LocalFontPreferenceManager(context);
        this.highlighter = new FontTextHighlighter(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = executor;

        this.fontsMetadataMap = new HashMap<>();
        this.currentSearchQuery = "";

        this.sections = new ArrayList<>();
        this.sectionPositions = new ArrayList<>();
        this.positionSections = new ArrayList<>();

        this.currentSortType = SortByItemLayout.SortType.NAME;
        this.currentSortAscending = true;

        // ★ قراءة حالة الثيم الشفاف مرة واحدة عند الإنشاء
        //   إذا كان مفعّلاً سيتم استخدام layouts الـ MaterialCardView
        //   وتعطيل حسابات الزوايا الدائرية لتوفير معالجة الـ CPU ★
        this.isTransparentTheme = SettingsHelper.isTransparentThemeEnabled(context);

        this.mSortedList = new SortedList<>(
            FontFileInfo.class,
            new FontSortedListCallback()
        );

        setHasStableIds(true); // ★ يضمن تتبع هوية العناصر لمنع إعادة رسمها عشوائياً ★

        // ★★★ المراقب: يراقب كل تغيير ويصحح الزوايا فوراً ★★★
        // ★ في الثيم الشفاف: لا حاجة لهذه الحسابات فيُتجاهل التنفيذ تماماً ★
        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                updateListEdges();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                updateListEdges();
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                updateListEdges();
            }

            private void updateListEdges() {
                // ★ توفير معالجة: في الثيم الشفاف لا زوايا دائرية للحساب ★
                if (isTransparentTheme || recyclerView == null) return;
                // ★ تأجيل التحديث حتى ينتهي RecyclerView من حسابات الأنيميشن ★
                recyclerView.post(() -> {
                    if (recyclerView == null || recyclerView.isComputingLayout()) return;
                    int total = getItemCount();
                    if (total > 0) {
                        notifyItemChanged(0, PAYLOAD_UPDATE_CORNERS);
                    }
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

    public void setFontClickListener(OnFontClickListener listener)            { this.fontClickListener = listener; }
    public void setSortChangeListener(SortByItemLayout.OnSortChangeListener l) { this.sortChangeListener = l; }
    public void setSelectionListener(OnSelectionListener listener)             { this.selectionListener = listener; }

    // ★ يُستدعى من saveLastOpenedAndUpdate (انتقال مؤكد) أو من unblockTouch (إلغاء) ★
    public void resetClickGuard() { mClickGuard = false; }

    public void saveLastOpenedAndUpdate(String path) {
        mClickGuard = false;
        preferenceManager.saveLastOpenedFont(path);
        smartUpdate();
    }

    // ─────────────────────────────────────────────────────────
    // دوال التحديد المتعدد
    // ─────────────────────────────────────────────────────────

    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) selectedItems.clear();
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setItemSelected(int position, boolean selected) {
        if (selected) selectedItems.put(position, true);
        else selectedItems.delete(position);
        notifyItemChanged(position);
    }

    public void clearSelection()                         { selectedItems.clear(); }
    public boolean isItemSelected(int position)          { return selectedItems.get(position, false); }
    public boolean isSelectionMode()                     { return isSelectionMode; }

    public String getFilePath(int position) {
        if (position > 0 && position <= mSortedList.size())
            return mSortedList.get(position - 1).getPath();
        return null;
    }

    public int findPositionByPath(String path) {
        if (path == null) return -1;
        for (int i = 0; i < mSortedList.size(); i++)
            if (path.equals(mSortedList.get(i).getPath())) return i + 1;
        return -1;
    }

    // ─────────────────────────────────────────────────────────
    // ★ تحديث البيانات — SortedList.replaceAll يحسب الفرق ويولد الأنيميشن ★
    // ─────────────────────────────────────────────────────────
    public void updateFilteredFonts(List<FontFileInfo> fonts, String searchQuery) {
        String oldQuery = this.currentSearchQuery;
        this.currentSearchQuery = searchQuery != null ? searchQuery : "";
        List<FontFileInfo> newList = fonts != null ? fonts : new ArrayList<>();

        // 1. تطبيق الفلتر: SortedList يُخفي/يُحرّك العناصر تلقائياً بأنيميشن
        mSortedList.replaceAll(newList);
        buildSections();

        // 2. ★ تحديث تظليل النص للعناصر المتبقية بصمت عبر Payload دون إعادة رسمها ★
        if (!this.currentSearchQuery.equals(oldQuery) && recyclerView != null) {
            recyclerView.post(() -> {
                if (recyclerView != null && !recyclerView.isComputingLayout()) {
                    int size = mSortedList.size();
                    if (size > 0) {
                        notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_HIGHLIGHT);
                    }
                }
            });
        }
    }

    /**
     * ★ الدالة الجوهرية لأنيميشن الفرز ★
     * عند تغيير معيار الفرز: تلتقط snapshot من العناصر الحالية، تحدّث حقول الفرز،
     * ثم تُعيد الإدراج. SortedList يستخدم areItemsTheSame() لاكتشاف أن العناصر
     * ذاتها تحركت فيولّد استدعاءات onMoved() → أنيميشن انزلاق حقيقي.
     */
    public void setSortOptions(SortByItemLayout.SortType sortType, boolean ascending) {
        this.currentSortType = sortType;
        this.currentSortAscending = ascending;

        // التقاط snapshot قبل إعادة الفرز
        final int size = mSortedList.size();
        List<FontFileInfo> snapshot = new ArrayList<>(size);
        for (int i = 0; i < size; i++) snapshot.add(mSortedList.get(i));

        // replaceAll تستخدم الـ Comparator الجديد (الذي يقرأ الحقول المحدّثة)
        // وتولد onMoved عند اكتشاف تغير الموضع
        mSortedList.replaceAll(snapshot);
        buildSections();

        // ★ تحديث الهيدر بشكل غير متزامن لتجنب قطع أنيميشن العناصر ★
        if (recyclerView != null) {
            recyclerView.post(() -> {
                if (recyclerView != null && !recyclerView.isComputingLayout()) {
                    notifyItemChanged(0);
                }
            });
        }
    }

    /**
     * تحديث خيارات العرض في الهيدر فقط — بدون إعادة فرز (للتهيئة الأولية)
     */
    public void updateSortOptionsOnly(SortByItemLayout.SortType sortType, boolean ascending) {
        this.currentSortType = sortType;
        this.currentSortAscending = ascending;
        notifyItemChanged(0);
    }

    public void setAllFontsMetadata(List<LocalFontListViewModel.FontFileInfoWithMetadata> metadata) {
        fontsMetadataMap.clear();
        if (metadata != null)
            for (LocalFontListViewModel.FontFileInfoWithMetadata item : metadata)
                fontsMetadataMap.put(item.getPath(), item);
    }

    /**
     * تفويض إلى updateFilteredFonts — SortedList يتولى الأنيميشن بدلاً من DiffUtil
     */
    public void updateListWithAnimation(List<FontFileInfo> newFonts) {
        updateFilteredFonts(newFonts, currentSearchQuery);
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

    private LocalFontListViewModel.FontFileInfoWithMetadata getFontMetadataForPath(String path) {
        return fontsMetadataMap.get(path);
    }

    // ─────────────────────────────────────────────────────────
    // RecyclerView.Adapter
    // ─────────────────────────────────────────────────────────

    @Override
    public int getItemCount() { return mSortedList.size() + 2; }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return VIEW_TYPE_HEADER;
        if (position == getItemCount() - 1) return VIEW_TYPE_SPACE;
        return VIEW_TYPE_FONT;
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
        return new LocalFontViewHolder(inf.inflate(itemLayout, parent, false));
    }

    // ★ الربط الكامل
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SortHeaderViewHolder) {
            SortHeaderViewHolder h = (SortHeaderViewHolder) holder;
            h.bind(currentSortType, currentSortAscending, sortChangeListener);
            h.setSortEnabled(!isSelectionMode);
        } else if (holder instanceof LocalFontViewHolder) {
            bindLocalFontViewHolder((LocalFontViewHolder) holder, mSortedList.get(position - 1), position);
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
            // ★ تحديث تظليل نص البحث بصمت دون إعادة رسم العنصر كاملاً ★
            // وصف الوزن/العرض لا يتغير عند تغيير نص البحث، فلا داعي لتحديثه هنا
            if (payloads.contains(PAYLOAD_UPDATE_HIGHLIGHT) && holder instanceof LocalFontViewHolder) {
                FontFileInfo fontInfo = mSortedList.get(position - 1);
                String displayName = FileUtils.removeExtension(fontInfo.getName());
                boolean isSearchActive = currentSearchQuery != null && !currentSearchQuery.isEmpty();
                android.text.Spannable highlighted = highlighter.highlightText(displayName, currentSearchQuery);
                ((LocalFontViewHolder) holder).fontNameTextView.setText(isSearchActive ? highlighted : displayName);
            }
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    // ─────────────────────────────────────────────────────────
    // ★ دالة هندسة الزوايا المستقلة ★
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

        } else if (holder instanceof LocalFontViewHolder) {
            LocalFontViewHolder fh = (LocalFontViewHolder) holder;
            RoundLinearLayout root = (RoundLinearLayout) fh.itemView;
            boolean isLast         = (position == getItemCount() - 2);

            if (isLast) {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_LEFT
                                     | SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_RIGHT);
                if (fh.dividerView != null) fh.dividerView.setVisibility(View.INVISIBLE);
            } else {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_NONE);
                if (fh.dividerView != null) fh.dividerView.setVisibility(View.VISIBLE);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // ربط بيانات عنصر الخط
    // ─────────────────────────────────────────────────────────
    private void bindLocalFontViewHolder(LocalFontViewHolder holder, FontFileInfo fontInfo, int position) {
        String fileName    = fontInfo.getName();
        String path        = fontInfo.getPath();
        String displayName = FileUtils.removeExtension(fileName);

        LocalFontListViewModel.FontFileInfoWithMetadata metadata = getFontMetadataForPath(path);
        String realName = (metadata != null) ? metadata.getRealName() : null;
        if (realName == null || realName.isEmpty()) realName = context.getString(R.string.unknown_font);

        // ★ استخراج وصف الوزن/العرض من البيانات الوصفية ★
        // إذا لم يُستخرج بعد يُعرض "غير معروف" تلقائياً من الـ ViewHolder
        String weightWidthLabel = (metadata != null) ? metadata.getWeightWidthLabel() : null;

        boolean isSearchActive = currentSearchQuery != null && !currentSearchQuery.isEmpty();
        boolean isLastOpened   = preferenceManager.isLastOpenedFont(path);

        // ★ تمرير weightWidthLabel إلى bind() ★
        holder.bind(displayName, path, isSearchActive, currentSearchQuery,
                    isLastOpened, highlighter, isSelectionMode, isItemSelected(position),
                    weightWidthLabel);

        if (SettingsHelper.isFontPreviewEnabled(context)) loadFontPreview(holder, path);
        else holder.setDefaultTypeface(SettingsHelper.getTypeface(context));

        final String finalRealName = realName;

        holder.itemView.setOnClickListener(v -> {
            // ★ الحارس: يمنع تشغيل نقرتين متزامنتين ★
            if (mClickGuard) return;
            mClickGuard = true;
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) { mClickGuard = false; return; }
            if (isSelectionMode) {
                mClickGuard = false;
                if (selectionListener != null) selectionListener.onToggleSelection(pos);
            } else {
                if (fontClickListener != null)
                    fontClickListener.onFontClick(path, finalRealName, fileName, 0);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;
            if (!isSelectionMode && selectionListener != null) selectionListener.onStartSelection(pos);
            if (recyclerView != null) recyclerView.seslStartLongPressMultiSelection();
            return true;
        });
    }

    private void loadFontPreview(LocalFontViewHolder holder, String path) {
        LocalFontCache cache = LocalFontCache.getInstance();
        Typeface cached = cache.getIfCached(path);

        if (cached != null) {
            holder.setTypeface(cached);
        } else {
            holder.setDefaultTypeface(SettingsHelper.getTypeface(context));
            if (executor != null && !executor.isShutdown()) {
                executor.execute(() -> {
                    Typeface loaded = cache.getTypeface(path);
                    if (loaded != null) {
                        mainHandler.post(() -> {
                            if (!SettingsHelper.isFontPreviewEnabled(context)) return;
                            if (path.equals(holder.getTag())) holder.setTypeface(loaded);
                        });
                    }
                });
            }
        }
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) return "HEADER".hashCode();
        if (position == getItemCount() - 1) return "SPACE".hashCode();
        return mSortedList.get(position - 1).getPath().hashCode();
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
