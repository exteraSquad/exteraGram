/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ai.AiConfig;
import com.exteragram.messenger.ai.AiController;
import com.exteragram.messenger.ai.data.Message;
import com.exteragram.messenger.ai.data.Role;
import com.exteragram.messenger.ai.data.Service;
import com.exteragram.messenger.ai.network.Client;
import com.exteragram.messenger.ai.network.GenerationCallback;
import com.exteragram.messenger.ai.network.backend.OnDeviceAvailability;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LinkifyPort;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EllipsizeSpanAnimator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.MarkdownParser;
import org.telegram.ui.Components.QuoteSpan;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TypingDotsDrawable;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.iv.RichMessageConvert;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class AiResponseAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private static final int CONTEXT_MESSAGES_LIMIT = 6;
    private static final int CONTEXT_MAX_LINES = 3;

    private final HeaderView headerView;
    private final LoadingTextView loadingTextView;
    private final ThinkingDotsView thinkingDotsView;
    private final TextView promptTextView;
    private final LinearLayout contextView;
    private final View contextDivider;
    private final RichMessageLayout.PreviewView previewView;

    private final ButtonWithCounterView mainButton;
    private final RecyclerListView listView;
    private final ResponseContainer responseContainer;
    private final PaddedAdapter adapter;
    private final Client client;
    private final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper;
    private final TextSelectionHelper<?>.TextSelectionOverlay textSelectionOverlay;
    private final AnimatedFloat sheetTopAnimated;
    EllipsizeSpanAnimator ellipsizeSpanAnimator;
    private boolean useHistory;
    private boolean sheetTopNotAnimate;
    private boolean thinkingVisible;
    private boolean generating;
    private boolean errorState;
    private boolean turnSaved;
    private String prompt;
    private String imagePath;
    private CharSequence currentResponse;
    private TL_iv.RichMessage currentRichMessage;
    private String pendingMarkdown;
    private boolean parsingMarkdown;
    private int parseGeneration;
    private Utilities.Callback3<String, CharSequence, TL_iv.RichMessage> onInsertPress;
    private Utilities.CallbackReturn<URLSpan, Boolean> onLinkPress;
    private BaseFragment fragment;
    private String currentRequestId;

    private AiResponseAlert(
            Context context,
            Client client,
            String prompt,
            String imagePath,
            boolean useHistory,
            Theme.ResourcesProvider resourcesProvider
    ) {
        super(context, false, resourcesProvider);

        this.client = client;
        this.imagePath = imagePath;
        this.useHistory = useHistory;

        backgroundPaddingLeft = 0;

        fixNavigationBar();

        containerView = new ContainerView(context);
        sheetTopAnimated = new AnimatedFloat(containerView, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        loadingTextView = new LoadingTextView(context);
        loadingTextView.setPadding(dp(22), dp(12), dp(22), dp(6));
        loadingTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize);
        loadingTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        loadingTextView.setTypeface(AndroidUtilities.regular());
        loadingTextView.setLinkTextColor(Theme.multAlpha(getThemedColor(Theme.key_dialogTextBlack), .2f));
        thinkingDotsView = new ThinkingDotsView(context, getThemedColor(Theme.key_dialogTextBlack));

        promptTextView = new TextView(context);
        promptTextView.setPadding(dp(22), dp(12), dp(22), 0);
        promptTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize);
        promptTextView.setTypeface(AndroidUtilities.regular());
        promptTextView.setTextColor(getThemedColor(Theme.key_player_actionBarSubtitle));

        previewView = new RichMessageLayout.PreviewView(context, UserConfig.selectedAccount, createRichResourcesProvider());
        previewView.setPadding(dp(22), dp(12), dp(22), dp(6));
        previewView.setOnLinkPress(span -> openUrl(span.getURL()));

        contextView = new LinearLayout(context) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        contextView.setOrientation(LinearLayout.VERTICAL);
        contextView.setAlpha(.55f);
        contextView.setVisibility(View.GONE);

        contextDivider = new View(context);
        contextDivider.setBackgroundColor(Theme.multAlpha(getThemedColor(Theme.key_divider), .75f));
        contextDivider.setVisibility(View.GONE);

        responseContainer = new ResponseContainer(context);
        responseContainer.addView(contextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        responseContainer.addView(contextDivider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 22, 10, 22, 0));
        responseContainer.addView(promptTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        responseContainer.addView(previewView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setPrompt(prompt.trim());

        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < getSheetTop() - getTop()) {
                    dismiss();
                    return true;
                }

                return super.dispatchTouchEvent(ev);
            }

            @Override
            protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
                return true;
            }

            @Override
            public void requestChildFocus(View child, View focused) {
            }
        };
        listView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        listView.setPadding(0, AndroidUtilities.statusBarHeight + dp(56), 0, dp(80));
        listView.setClipToPadding(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        listView.setLayoutManager(layoutManager);
        adapter = new PaddedAdapter(context, loadingTextView);
        listView.setAdapter(adapter);
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                containerView.invalidate();
                textSelectionHelper.onParentScrolled();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    sheetTopNotAnimate = false;
                }
                if ((newState == RecyclerView.SCROLL_STATE_IDLE || newState == RecyclerView.SCROLL_STATE_SETTLING) && getSheetTop(false) > 0 && getSheetTop(false) < dp(64 + 32) && listView.canScrollVertically(1) && hasEnoughHeight()) {
                    sheetTopNotAnimate = true;
                    listView.smoothScrollBy(0, (int) getSheetTop(false));
                }
            }
        });
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onChangeAnimationUpdate(RecyclerView.ViewHolder holder) {
                containerView.invalidate();
            }

            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                containerView.invalidate();
            }
        };
        itemAnimator.setDurations(180);
        itemAnimator.setInterpolator(new LinearInterpolator());
        listView.setItemAnimator(itemAnimator);
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        textSelectionHelper = new TextSelectionHelper.ArticleTextSelectionHelper();
        textSelectionHelper.setParentView(listView);
        textSelectionHelper.layoutManager = layoutManager;
        textSelectionOverlay = textSelectionHelper.getOverlayView(context);
        AndroidUtilities.removeFromParent(textSelectionOverlay);
        containerView.addView(textSelectionOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        previewView.setTextSelectionHelper(textSelectionHelper);

        headerView = new HeaderView(context);
        containerView.addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 78, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        mainButton = new ButtonWithCounterView(context, resourcesProvider);
        mainButton.setRound();
        mainButton.setColor(getThemedColor(Theme.key_featuredStickers_addButton));
        mainButton.setText(LocaleController.getString(R.string.Stop), false);
        generating = true;
        mainButton.setOnClickListener(e -> {
            if (errorState) {
                regenerate();
            } else if (generating) {
                stopGeneration();
            } else {
                dismiss();
            }
        });
        containerView.addView(mainButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 16, 16, 16, 16));
    }

    private Theme.ResourcesProvider createRichResourcesProvider() {
        final Theme.ResourcesProvider parent = resourcesProvider;
        return new Theme.ResourcesProvider() {
            @Override
            public int getColor(int key) {
                if (key == Theme.key_chat_messageTextIn || key == Theme.key_chat_messageTextOut) {
                    key = Theme.key_dialogTextBlack;
                } else if (key == Theme.key_chat_messageLinkIn || key == Theme.key_chat_messageLinkOut) {
                    key = Theme.key_dialogTextLink;
                }
                return Theme.getColor(key, parent);
            }

            @Override
            public Drawable getDrawable(String drawableKey) {
                return parent == null ? null : parent.getDrawable(drawableKey);
            }

            @Override
            public Paint getPaint(String paintKey) {
                return parent == null ? Theme.getThemePaint(paintKey) : parent.getPaint(paintKey);
            }

            @Override
            public boolean hasGradientService() {
                return parent != null && parent.hasGradientService();
            }

            @Override
            public boolean isDark() {
                return parent == null ? Theme.isCurrentThemeDark() : parent.isDark();
            }

            @Override
            public void applyServiceShaderMatrix(int w, int h, float translationX, float translationY) {
                if (parent == null) {
                    Theme.applyServiceShaderMatrix(w, h, translationX, translationY);
                } else {
                    parent.applyServiceShaderMatrix(w, h, translationX, translationY);
                }
            }
        };
    }

    public static AiResponseAlert showAlert(BaseFragment fragment, Client client, String text, boolean useHistory, boolean noforwards, Utilities.CallbackReturn<URLSpan, Boolean> onLinkPress, Runnable onDismiss, Utilities.Callback3<String, CharSequence, TL_iv.RichMessage> onInsertPress) {
        return showAlert(fragment, client, text, null, useHistory, noforwards, onLinkPress, onDismiss, onInsertPress);
    }

    public static AiResponseAlert showAlert(BaseFragment fragment, Client client, String text, String imagePath, boolean useHistory, boolean noforwards, Utilities.CallbackReturn<URLSpan, Boolean> onLinkPress, Runnable onDismiss, Utilities.Callback3<String, CharSequence, TL_iv.RichMessage> onInsertPress) {
        AiResponseAlert alert = new AiResponseAlert(fragment.getContext(), client, text, imagePath, useHistory, fragment.getResourceProvider()) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (onDismiss != null) {
                    onDismiss.run();
                }
            }
        };
        alert.setNoforwards(noforwards);
        alert.setFragment(fragment);
        alert.setOnLinkPress(onLinkPress);
        alert.setOnInsertPress(onInsertPress);
        if (fragment.getParentActivity() != null) {
            fragment.showDialog(alert);
        }
        alert.generate();
        return alert;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
        loadingTextView.setText(Emoji.replaceEmoji(this.prompt, loadingTextView.getPaint().getFontMetricsInt(), true));
        formatPrompt();
    }

    private void updateMainButton(boolean isGenerating) {
        updateMainButton(isGenerating, false);
    }

    private void updateMainButton(boolean isGenerating, boolean isError) {
        generating = isGenerating;
        errorState = isError;
        mainButton.setText(LocaleController.getString(isGenerating ? R.string.Stop : isError ? R.string.Retry : R.string.Close), true);
        boolean hasResponse = !isGenerating && !isError && !TextUtils.isEmpty(currentResponse);
        AndroidUtilities.updateViewVisibilityAnimated(headerView.optionsButton, hasResponse, 0.5f, true);
        if (onInsertPress != null) {
            AndroidUtilities.updateViewVisibilityAnimated(headerView.insertButton, hasResponse, 0.5f, true);
        }
    }

    private void stopGeneration() {
        client.stopRequest(currentRequestId);
        currentRequestId = null;
        stopThinking();
        if (TextUtils.isEmpty(currentResponse)) {
            dismiss();
            return;
        }
        adapter.updateMainView(responseContainer);
        updateMainButton(false);
    }

    private void regenerate() {
        dropLastTurnFromHistory();
        showLoadingView();
        generate();
    }

    private void dropLastTurnFromHistory() {
        if (turnSaved) {
            AiConfig.removeLastFromHistory();
            turnSaved = false;
        }
    }

    private void formatPrompt() {
        if (AiConfig.getShowResponseOnly() || TextUtils.isEmpty(prompt)) {
            promptTextView.setVisibility(View.GONE);
            promptTextView.setText("");
            return;
        }

        promptTextView.setVisibility(View.VISIBLE);
        promptTextView.setText(Emoji.replaceEmoji(formatPromptText(prompt), promptTextView.getPaint().getFontMetricsInt(), false));
    }

    private CharSequence formatPromptText(String text) {
        ColoredImageSpan span = new ColoredImageSpan(R.drawable.msg_mini_arrow_mediathin);
        span.setColorKey(Theme.key_player_actionBarSubtitle);
        span.setScale(1, 1);
        span.setTranslateY(dpf2(1.5f));
        span.spaceScaleX = .95f;

        SpannableStringBuilder ssb = new SpannableStringBuilder("→ " + text);
        ssb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new DialogCell.FixedWidthSpan(dp(4)), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new RelativeSizeSpan(0.8f), 2, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(getThemedColor(Theme.key_player_actionBarSubtitle)), 2, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ssb;
    }

    private ArrayList<Service> availableServices() {
        ArrayList<Service> services = new ArrayList<>();
        for (Service available : AiController.getInstance().getAll()) {
            if (available.isOnDevice() && !OnDeviceAvailability.isReady()) {
                continue;
            }
            services.add(available);
        }
        return services;
    }

    private void updateContextView() {
        contextView.removeAllViews();
        List<Message> history = useHistory && !AiConfig.getShowResponseOnly() ? AiConfig.getConversationHistory() : null;
        ArrayList<Message> shown = new ArrayList<>();
        if (history != null) {
            for (int i = Math.max(0, history.size() - CONTEXT_MESSAGES_LIMIT); i < history.size(); ++i) {
                Message message = history.get(i);
                if (message == null || TextUtils.isEmpty(message.content())) {
                    continue;
                }
                shown.add(message);
                contextView.addView(createContextTextView(message), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        }
        boolean hasContext = contextView.getChildCount() > 0;
        contextView.setVisibility(hasContext ? View.VISIBLE : View.GONE);
        contextDivider.setVisibility(hasContext ? View.VISIBLE : View.GONE);
        formatContextAsync(shown);
    }

    private void formatContextAsync(ArrayList<Message> shown) {
        if (shown.isEmpty()) {
            return;
        }
        final int generation = parseGeneration;
        Utilities.globalQueue.postRunnable(() -> {
            final ArrayList<CharSequence> formatted = new ArrayList<>(shown.size());
            for (Message message : shown) {
                formatted.add(Message.ASSISTANT_ROLE.equals(message.role()) ? MarkdownPreview.format(message.content()) : null);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (generation != parseGeneration || contextView.getChildCount() != formatted.size()) {
                    return;
                }
                for (int i = 0; i < formatted.size(); ++i) {
                    CharSequence text = formatted.get(i);
                    if (text == null || !(contextView.getChildAt(i) instanceof TextView textView)) {
                        continue;
                    }
                    textView.setText(Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false));
                }
            });
        });
    }

    private TextView createContextTextView(Message message) {
        boolean fromUser = !Message.ASSISTANT_ROLE.equals(message.role());
        TextView textView = new TextView(getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize);
        textView.setTypeface(AndroidUtilities.regular());
        textView.setMaxLines(CONTEXT_MAX_LINES);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setPadding(dp(22), dp(fromUser ? 12 : 6), dp(22), 0);
        textView.setTextColor(getThemedColor(fromUser ? Theme.key_player_actionBarSubtitle : Theme.key_dialogTextBlack));
        textView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
        CharSequence text = fromUser ? formatPromptText(message.content()) : message.content();
        textView.setText(Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false));
        return textView;
    }

    private CharSequence getRawResponse(CharSequence response) {
        if (AiConfig.getShowResponseOnly() || TextUtils.isEmpty(prompt)) {
            return response;
        } else {
            return "→ " + prompt + "\n\n" + response;
        }
    }

    private CharSequence getInsertResponse() {
        if (currentRichMessage == null) {
            return getRawResponse(currentResponse);
        }
        CharSequence response = RichMessageConvert.blocksToCharSequence(currentRichMessage.blocks);
        if (AiConfig.getInsertAsQuote()) {
            response = AndroidUtilities.removeSpans(response, QuoteSpan.QuoteStyleSpan.class);
            response = AndroidUtilities.removeSpans(response, QuoteSpan.class);
        }
        if (AiConfig.getShowResponseOnly() || TextUtils.isEmpty(prompt)) {
            return response;
        }
        return new SpannableStringBuilder("→ " + prompt + "\n\n").append(response);
    }

    private void generate() {
        currentResponse = null;
        currentRichMessage = null;
        pendingMarkdown = null;
        turnSaved = false;
        parseGeneration++;
        updateMainButton(true);
        updateContextView();
        textSelectionHelper.clear(true);
        stopThinking();
        if (AiController.getInstance().getSelected().isReasoningEnabled() && AiConfig.getResponseStreaming()) {
            showThinking();
        }

        currentRequestId = client.getResponse(prompt, useHistory, AiConfig.getResponseStreaming(), imagePath, new GenerationCallback() {
            @Override
            public void onThinking() {
                showThinking();
            }

            @Override
            public void onResponse(String response) {
                if (!TextUtils.isEmpty(response)) {
                    currentResponse = response;
                    turnSaved = useHistory;
                    setResponse(response);
                    updateMainButton(false);
                }
            }

            @Override
            public void onChunk(String chunk) {
                if (!TextUtils.isEmpty(chunk)) {
                    currentResponse = chunk;
                    setResponse(chunk);
                }
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
                stopThinking();
                Runnable onChangeModel = AiController.isOnDeviceError(errorCode) && availableServices().size() > 1
                        ? () -> headerView.openModelSelect()
                        : null;
                AiController.showErrorBulletin(containerView, resourcesProvider, errorCode, errorMessage, onChangeModel);
                adapter.updateMainView(responseContainer);
                updateMainButton(false, true);
            }
        });
    }

    private void showThinking() {
        if (thinkingVisible || !TextUtils.isEmpty(currentResponse)) {
            return;
        }
        thinkingVisible = true;
        thinkingDotsView.start();
        adapter.updateMainView(thinkingDotsView);
    }

    private void stopThinking() {
        thinkingVisible = false;
        thinkingDotsView.stop();
    }

    private void showLoadingView() {
        textSelectionHelper.clear(true);
        adapter.updateMainView(loadingTextView);
    }

    private void setResponse(String response) {
        pendingMarkdown = response;
        parseMarkdownAsync();
    }

    private void parseMarkdownAsync() {
        if (parsingMarkdown || pendingMarkdown == null) {
            return;
        }
        final String source = pendingMarkdown;
        final int generation = parseGeneration;
        pendingMarkdown = null;
        parsingMarkdown = true;
        Utilities.globalQueue.postRunnable(() -> {
            final TL_iv.RichMessage richMessage = parseMarkdown(source);
            AndroidUtilities.runOnUIThread(() -> {
                parsingMarkdown = false;
                if (generation != parseGeneration) {
                    return;
                }
                stopThinking();
                currentRichMessage = richMessage;
                previewView.set(richMessage);
                adapter.updateMainView(responseContainer);
                parseMarkdownAsync();
            });
        });
    }

    private static TL_iv.RichMessage parseMarkdown(String source) {
        final TL_iv.RichMessage richMessage = new TL_iv.RichMessage();
        try {
            MarkdownParser.parse(source, richMessage.blocks);
        } catch (Throwable t) {
            FileLog.e(t);
            richMessage.blocks.clear();
        }
        if (richMessage.blocks.isEmpty()) {
            final TL_iv.pageBlockParagraph paragraph = new TL_iv.pageBlockParagraph();
            paragraph.text = plainText(source);
            richMessage.blocks.add(paragraph);
        }
        linkifyBlocks(richMessage.blocks);
        return richMessage;
    }

    private static void linkifyBlocks(ArrayList<TL_iv.PageBlock> blocks) {
        for (TL_iv.PageBlock block : blocks) {
            if (block instanceof TL_iv.pageBlockPreformatted) {
                continue;
            }
            if (block instanceof TL_iv.pageBlockDetails details) {
                details.title = linkify(details.title);
                linkifyBlocks(details.blocks);
            } else if (block instanceof TL_iv.pageBlockList bulletList) {
                for (TL_iv.PageListItem item : bulletList.items) {
                    if (item instanceof TL_iv.TL_pageListItemText textItem) {
                        textItem.text = linkify(textItem.text);
                    } else if (item instanceof TL_iv.TL_pageListItemBlocks blocksItem) {
                        linkifyBlocks(blocksItem.blocks);
                    }
                }
            } else if (block instanceof TL_iv.pageBlockOrderedList orderedList) {
                for (TL_iv.PageListOrderedItem item : orderedList.items) {
                    if (item instanceof TL_iv.TL_pageListOrderedItemText textItem) {
                        textItem.text = linkify(textItem.text);
                    } else if (item instanceof TL_iv.TL_pageListOrderedItemBlocks blocksItem) {
                        linkifyBlocks(blocksItem.blocks);
                    }
                }
            } else if (block instanceof TL_iv.pageBlockTable table) {
                for (TL_iv.pageTableRow row : table.rows) {
                    for (TL_iv.pageTableCell cell : row.cells) {
                        cell.text = linkify(cell.text);
                    }
                }
            } else {
                block.text = linkify(block.text);
            }
        }
    }

    private static TL_iv.RichText linkify(TL_iv.RichText text) {
        if (text == null || text instanceof TL_iv.textEmpty || text instanceof TL_iv.textUrl ||
                text instanceof TL_iv.textEmail || text instanceof TL_iv.textPhone ||
                text instanceof TL_iv.textFixed || text instanceof TL_iv.textMath ||
                text instanceof TL_iv.textImage) {
            return text;
        }
        if (text instanceof TL_iv.textConcat) {
            text.texts.replaceAll(AiResponseAlert::linkify);
            return text;
        }
        if (text instanceof TL_iv.textPlain plain) {
            return linkifyPlain(plain);
        }
        text.text = linkify(text.text);
        return text;
    }

    private static TL_iv.RichText linkifyPlain(TL_iv.textPlain plain) {
        if (LinkifyPort.WEB_URL == null || TextUtils.isEmpty(plain.text)) {
            return plain;
        }
        final String value = plain.text;
        final Matcher matcher = LinkifyPort.WEB_URL.matcher(value);
        TL_iv.textConcat concat = null;
        int lastMatchEnd = 0;
        while (matcher.find()) {
            final String url = matcher.group();
            if (TextUtils.isEmpty(url)) {
                continue;
            }
            if (concat == null) {
                concat = new TL_iv.textConcat();
            }
            if (matcher.start() > lastMatchEnd) {
                concat.texts.add(plainText(value.substring(lastMatchEnd, matcher.start())));
            }
            final TL_iv.textUrl link = new TL_iv.textUrl();
            link.text = plainText(url);
            link.url = url;
            concat.texts.add(link);
            lastMatchEnd = matcher.end();
        }
        if (concat == null) {
            return plain;
        }
        if (lastMatchEnd < value.length()) {
            concat.texts.add(plainText(value.substring(lastMatchEnd)));
        }
        return concat;
    }

    private static TL_iv.RichText plainText(String text) {
        final TL_iv.textPlain plain = new TL_iv.textPlain();
        plain.text = text == null ? "" : text;
        return plain;
    }

    private void openUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        if (onLinkPress != null) {
            if (onLinkPress.run(new URLSpan(url))) {
                dismiss();
            }
        } else if (fragment != null) {
            AlertsCreator.showOpenUrlAlert(fragment, url, false, false);
        }
    }

    private boolean hasEnoughHeight() {
        float height = 0;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) == 1)
                height += child.getHeight();
        }
        return height >= listView.getHeight() - listView.getPaddingTop() - listView.getPaddingBottom();
    }

    public void setFragment(BaseFragment fragment) {
        this.fragment = fragment;
    }

    public void setOnLinkPress(Utilities.CallbackReturn<URLSpan, Boolean> onLinkPress) {
        this.onLinkPress = onLinkPress;
    }

    private void setOnInsertPress(Utilities.Callback3<String, CharSequence, TL_iv.RichMessage> onInsertPress) {
        this.onInsertPress = onInsertPress;
    }

    public void setNoforwards(boolean noforwards) {
        previewView.setTextSelectionHelper(noforwards ? null : textSelectionHelper);
        if (noforwards) {
            textSelectionHelper.clear(true);
        }
        var window = getWindow();
        if (window != null) {
            if (noforwards) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    private float getSheetTop() {
        return getSheetTop(true);
    }

    private float getSheetTop(boolean animated) {
        float top = listView.getTop();
        if (listView.getChildCount() >= 1) {
            top += Math.max(0, listView.getChildAt(listView.getChildCount() - 1).getTop());
        }
        top = Math.max(0, top - dp(78));
        if (animated && sheetTopAnimated != null) {
            if (!listView.scrollingByUser && !sheetTopNotAnimate) {
                top = sheetTopAnimated.set(top);
            } else {
                sheetTopAnimated.set(top, true);
            }
        }
        return top;
    }

    @Override
    public void show() {
        super.show();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void dismiss() {
        super.dismiss();
        client.stopRequest(currentRequestId);
        currentRequestId = null;
        stopThinking();
        pendingMarkdown = null;
        parseGeneration++;
        textSelectionHelper.clear(true);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            loadingTextView.invalidate();
            promptTextView.invalidate();
            previewView.invalidate();
        }
    }

    private class ResponseContainer extends LinearLayout implements TextSelectionHelper.ArticleSelectableView {

        public ResponseContainer(Context context) {
            super(context);
            setOrientation(VERTICAL);
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            int from = blocks.size();
            previewView.fillTextLayoutBlocks(blocks);
            for (int i = from; i < blocks.size(); ++i) {
                blocks.set(i, new OffsetTextLayoutBlock(blocks.get(i), previewView.getLeft(), previewView.getTop()));
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (previewView != null) {
                previewView.invalidate();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    private record OffsetTextLayoutBlock(TextSelectionHelper.TextLayoutBlock block, int offsetX,
                                         int offsetY) implements TextSelectionHelper.TextLayoutBlock {

        @Override
            public Layout getLayout() {
                return block.getLayout();
            }

            @Override
            public int getX() {
                return block.getX() + offsetX;
            }

            @Override
            public int getY() {
                return block.getY() + offsetY;
            }

            @Override
            public int getRow() {
                return block.getRow();
            }

            @Override
            public CharSequence getPrefix() {
                return block.getPrefix();
            }

            @Override
            public CharSequence getText() {
                return block.getText();
            }
        }

    private static class LoadingTextView extends TextView {

        private final LinkPath path = new LinkPath(true);
        private final LoadingDrawable loadingDrawable = new LoadingDrawable();

        public LoadingTextView(Context context) {
            super(context);
            loadingDrawable.usePath(path);
            loadingDrawable.setSpeed(.65f);
            loadingDrawable.setRadiiDp(4);
            setBackground(loadingDrawable);
        }

        @Override
        public void setTextColor(int color) {
            super.setTextColor(Theme.multAlpha(color, .2f));
            loadingDrawable.setColors(
                    Theme.multAlpha(color, 0.03f),
                    Theme.multAlpha(color, 0.175f),
                    Theme.multAlpha(color, 0.2f),
                    Theme.multAlpha(color, 0.45f)
            );
        }

        private void updateDrawable() {
            if (path == null || loadingDrawable == null) {
                return;
            }

            path.rewind();
            if (getLayout() != null) {
                getLayout().getText();
                path.setCurrentLayout(getLayout(), 0, getPaddingLeft(), getPaddingTop());
                getLayout().getSelectionPath(0, getLayout().getText().length(), path);
            }
            loadingDrawable.updateBounds();
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text, type);
            updateDrawable();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            updateDrawable();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            loadingDrawable.reset();
        }
    }

    private static class ThinkingDotsView extends View {

        private final TypingDotsDrawable drawable = new TypingDotsDrawable(true);

        public ThinkingDotsView(Context context, int color) {
            super(context);
            drawable.setCallback(this);
            drawable.setIgnoreAnimationLocks();
            drawable.setColor(color);
        }

        public void start() {
            if (!drawable.isStarted()) {
                drawable.start();
            }
        }

        public void stop() {
            drawable.stop();
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return super.verifyDrawable(who) || who == drawable;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(42));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (drawable.isStarted()) {
                invalidate();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            DrawableUtils.setBounds(drawable, dp(22), h / 2f, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            drawable.draw(canvas);
            if (drawable.isStarted()) {
                invalidate();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            stop();
        }
    }

    private static class PaddedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final Context mContext;
        private View mMainView;
        private int mainViewType = 1;

        public PaddedAdapter(Context context, View mainView) {
            mContext = context;
            mMainView = mainView;
        }

        public void updateMainView(View newMainView) {
            if (mMainView == newMainView) {
                return;
            }
            mainViewType++;
            mMainView = newMainView;
            notifyItemChanged(1);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                return new RecyclerListView.Holder(new View(mContext) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(
                                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec((int) (AndroidUtilities.displaySize.y * .4f), MeasureSpec.EXACTLY)
                        );
                    }
                });
            } else {
                return new RecyclerListView.Holder(mMainView);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 0;
            } else {
                return mainViewType;
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    private class HeaderView extends FrameLayout {

        private void showOptionsMenu() {
            final ItemOptions options = ItemOptions.makeOptions(container, resourcesProvider, optionsButton);
            options.add(R.drawable.msg_copy, LocaleController.getString(R.string.Copy), () -> optionsDelegate.onItemClick(1));
            options.add(R.drawable.msg_retry, LocaleController.getString(R.string.Retry), () -> optionsDelegate.onItemClick(2));
            options.add(R.drawable.msg_edit, LocaleController.getString(R.string.Edit), () -> optionsDelegate.onItemClick(3));
            if (useHistory && turnSaved) {
                options.add(R.drawable.msg_discuss, LocaleController.getString(R.string.AIAskMore), () -> optionsDelegate.onItemClick(4));
            }
            showSimpleMenu(optionsButton, options);
        }

        public final ImageView insertButton;
        public final ActionBarMenuItem optionsButton;
        private final ActionBarMenuItem.ActionBarMenuItemDelegate optionsDelegate;
        private final ImageView backButton;
        private final TextView titleTextView;
        private final LinearLayout subtitleView;
        private final AnimatedTextView modelSelector;

        private final View shadow;

        public HeaderView(Context context) {
            super(context);

            View backgroundView = new View(context);
            backgroundView.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 12, 0, 0));

            backButton = new ImageView(context);
            backButton.setScaleType(ImageView.ScaleType.CENTER);
            backButton.setImageResource(R.drawable.ic_ab_back);
            backButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
            backButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
            backButton.setAlpha(0f);
            backButton.setOnClickListener(e -> dismiss());
            addView(backButton, LayoutHelper.createFrame(54, 54, Gravity.TOP, 1, 1, 1, 1));

            insertButton = new ImageView(context);
            ScaleStateListAnimator.apply(insertButton, .15f, 1.5f);
            insertButton.setScaleType(ImageView.ScaleType.CENTER);
            insertButton.setImageResource(R.drawable.msg_send);
            insertButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_player_actionBarSubtitle), PorterDuff.Mode.MULTIPLY));
            insertButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
            insertButton.setVisibility(GONE);
            insertButton.setOnClickListener(v -> {
                onInsertPress.run(prompt, getInsertResponse(), currentRichMessage);
                dismiss();
            });
            addView(insertButton, LayoutHelper.createFrame(48, 54, Gravity.TOP | Gravity.RIGHT, 1, 1, 64, 1));

            optionsButton = new ActionBarMenuItem(context, null, 0, Theme.key_player_actionBarSubtitle, false, resourcesProvider);
            optionsButton.setLongClickEnabled(false);
            optionsButton.setShowSubmenuByMove(false);
            optionsButton.setIcon(R.drawable.ic_ab_other);
            optionsButton.setSubMenuOpenSide(2);
            optionsButton.setVisibility(GONE);
            optionsButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, AndroidUtilities.dp(18)));
            addView(optionsButton, LayoutHelper.createFrame(48, 54, Gravity.TOP | Gravity.RIGHT, 1, 1, 16, 1));
            optionsButton.setOnClickListener(v -> showOptionsMenu());
            optionsDelegate = id -> {
                switch (id) {
                    case 1 -> {
                        if (AndroidUtilities.addToClipboard(getRawResponse(currentResponse))) {
                            BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
                        }
                    }
                    case 2 -> regenerate();
                    case 3 ->
                            new GenerateFromMessageBottomSheet(prompt, imagePath, fragment, getContext(), data -> {
                                dropLastTurnFromHistory();
                                setPrompt(data.prompt());
                                imagePath = data.imagePath();
                                useHistory = data.useHistory();
                                showLoadingView();
                                generate();
                            }, useHistory).show();
                    case 4 -> new GenerateFromMessageBottomSheet(fragment, getContext(), data -> {
                        setPrompt(data.prompt());
                        imagePath = null;
                        useHistory = data.useHistory();
                        showLoadingView();
                        generate();
                    }, false).show();
                }
            };
            optionsButton.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));

            titleTextView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    if (LocaleController.isRTL) {
                        titleTextView.setPivotX(getMeasuredWidth());
                    }
                }
            };
            titleTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setSingleLine(true);
            titleTextView.setEllipsize(TextUtils.TruncateAt.END);
            titleTextView.setText(AiConfig.getSelectedRole());
            titleTextView.setIncludeFontPadding(false);
            titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            titleTextView.setMaxWidth(AndroidUtilities.displaySize.x - dp(22) - dp(112) - dp(2));
            var roleArrowDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_arrow_drop_down);
            if (roleArrowDrawable != null) {
                roleArrowDrawable = roleArrowDrawable.mutate();
                roleArrowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
                roleArrowDrawable.setBounds(0, -dp(1), dp(22), dp(21));
                if (LocaleController.isRTL) {
                    titleTextView.setCompoundDrawables(roleArrowDrawable, null, null, null);
                } else {
                    titleTextView.setCompoundDrawables(null, null, roleArrowDrawable, null);
                }
            }
            titleTextView.setCompoundDrawablePadding(dp(2));
            titleTextView.setPadding(0, 0, 0, 0);
            titleTextView.setOnClickListener(v -> openRoleSelect());
            titleTextView.setPivotX(0);
            titleTextView.setPivotY(0);
            addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 22, 20, 112, 0));

            subtitleView = new LinearLayout(context);
            subtitleView.setPivotX(0);
            subtitleView.setPivotY(0);

            modelSelector = new AnimatedTextView(context) {
                private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final LinkSpanDrawable.LinkCollector links = new LinkSpanDrawable.LinkCollector();

                @Override
                protected void onDraw(Canvas canvas) {
                    AndroidUtilities.rectTmp.set(0, (getHeight() - dp(18)) / 2f, width(), (getHeight() + dp(18)) / 2f);
                    bgPaint.setColor(Theme.multAlpha(getThemedColor(Theme.key_player_actionBarSubtitle), .1175f));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), bgPaint);
                    if (links.draw(canvas)) {
                        invalidate();
                    }

                    super.onDraw(canvas);
                }

                @SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        LinkSpanDrawable<CharacterStyle> link = new LinkSpanDrawable<>(null, resourcesProvider, event.getX(), event.getY());
                        link.setColor(Theme.multAlpha(getThemedColor(Theme.key_player_actionBarSubtitle), .1175f));
                        LinkPath path = link.obtainNewPath();
                        AndroidUtilities.rectTmp.set(0, (getHeight() - dp(18)) / 2f, width(), (getHeight() + dp(18)) / 2f);
                        path.addRect(AndroidUtilities.rectTmp, Path.Direction.CW);
                        links.addLink(link);
                        invalidate();
                        return true;
                    } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            performClick();
                        }
                        links.clear();
                        invalidate();
                    }
                    return super.onTouchEvent(event);
                }
            };
            modelSelector.setAnimationProperties(.25f, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
            modelSelector.setTextColor(getThemedColor(Theme.key_player_actionBarSubtitle));
            modelSelector.setTextSize(dp(14));
            modelSelector.setText(AiController.getInstance().getSelected().getShortModel());
            modelSelector.setPadding(dp(4), dp(2), dp(4), dp(2));
            modelSelector.setOnClickListener(e -> openModelSelect());

            subtitleView.addView(modelSelector, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 3, 0));

            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 22, 43, 22, 0));

            shadow = new View(context);
            shadow.setBackgroundColor(getThemedColor(Theme.key_divider));
            shadow.setAlpha(0);
            addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight() / dpf2(1), Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 56, 0, 0));

            backButton.bringToFront();
            insertButton.bringToFront();
            optionsButton.bringToFront();
        }

        public void openModelSelect() {
            if (client.isGenerating()) {
                return;
            }

            ItemOptions options = ItemOptions.makeOptions(containerView, resourcesProvider, modelSelector, false, false, true)
                    .setDrawScrim(false)
                    .setDimAlpha(0)
                    .setMaxHeight(dp(336))
                    .setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)
                    .translate(LocaleController.isRTL ? dp(8) : -dp(8), 0);

            for (Service service : availableServices()) {
                ActionBarMenuSubItem button = new ActionBarMenuSubItem(getContext(), 2, false, false, resourcesProvider);
                button.setText(service.getModel());
                button.setSubtext(service.isOnDevice() ? LocaleController.getString(R.string.AIOnDevice) : service.getUrl());
                button.subtextView.setPadding(0, 0, service.isSelected() ? dp(34) : 0, 0);
                button.setMinimumWidth(dp(196));
                button.setItemHeight(56);
                button.setChecked(service.isSelected());
                button.setOnClickListener(e -> {
                    options.dismiss();

                    if (service.isSelected()) {
                        return;
                    }

                    dropLastTurnFromHistory();
                    AiConfig.setSelectedServices(service);
                    modelSelector.setText(service.getShortModel());
                    showLoadingView();
                    generate();
                });
                options.add(button);
            }

            options.show();
        }

        public void openRoleSelect() {
            if (client.isGenerating()) {
                return;
            }

            ItemOptions options = ItemOptions.makeOptions(containerView, resourcesProvider, titleTextView)
                    .setDrawScrim(false)
                    .setDimAlpha(0)
                    .setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)
                    .translate(LocaleController.isRTL ? dp(8) : -dp(8), 0);

            ArrayList<Role> roles = new ArrayList<>(AiController.getInstance().getSuggestedRoles());
            for (Role role : AiController.getInstance().getRoles()) {
                if (!roles.contains(role)) {
                    roles.add(role);
                }
            }

            for (Role role : roles) {
                final Role finalRole = role;
                options.addChecked(finalRole.isSelected(), finalRole.getName(), () -> {
                    if (finalRole.isSelected()) {
                        return;
                    }

                    dropLastTurnFromHistory();
                    titleTextView.setText(finalRole.getName());
                    AiConfig.setSelectedRole(finalRole.getName());
                    showLoadingView();
                    generate();
                });
            }

            options.show();
        }

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);

            float t = MathUtils.clamp((translationY - AndroidUtilities.statusBarHeight) / dp(64), 0, 1);
            if (!hasEnoughHeight()) {
                t = 1;
            }
            t = CubicBezierInterpolator.EASE_OUT.getInterpolation(t);

            titleTextView.setScaleX(AndroidUtilities.lerp(.85f, 1f, t));
            titleTextView.setScaleY(AndroidUtilities.lerp(.85f, 1f, t));
            titleTextView.setTranslationY(AndroidUtilities.lerp(dpf2(-12), 0, t));
            titleTextView.setTranslationX(AndroidUtilities.lerp(dpf2(50), 0, t));
            subtitleView.setTranslationX(AndroidUtilities.lerp(dpf2(50), 0, t));

            subtitleView.setTranslationY(AndroidUtilities.lerp(dpf2(-22), 0, t));

            backButton.setTranslationX(AndroidUtilities.lerp(0, dpf2(-25), t));
            backButton.setAlpha(1f - t);

            insertButton.setTranslationX(AndroidUtilities.lerp(dpf2(14), dpf2(8), t));
            insertButton.setTranslationY(AndroidUtilities.lerp(dpf2(0), dpf2(16), t));
            insertButton.setColorFilter(ColorUtils.blendARGB(getThemedColor(Theme.key_dialogTextBlack), getThemedColor(Theme.key_player_actionBarSubtitle), t), PorterDuff.Mode.MULTIPLY);

            optionsButton.setTranslationX(AndroidUtilities.lerp(dpf2(14), dpf2(8), t));
            optionsButton.setTranslationY(AndroidUtilities.lerp(dpf2(0), dpf2(16), t));
            optionsButton.setIconColor(ColorUtils.blendARGB(getThemedColor(Theme.key_dialogTextBlack), getThemedColor(Theme.key_player_actionBarSubtitle), t));

            shadow.setTranslationY(AndroidUtilities.lerp(0, dpf2(22), t));
            shadow.setAlpha(1f - t);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(78), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (ellipsizeSpanAnimator != null) {
                ellipsizeSpanAnimator.onAttachedToWindow();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (ellipsizeSpanAnimator != null) {
                ellipsizeSpanAnimator.onDetachedFromWindow();
            }
        }
    }

    private class ContainerView extends FrameLayout {
        private final Path bgPath = new Path();
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Boolean lightStatusBarFull;

        public ContainerView(Context context) {
            super(context);

            bgPaint.setColor(getThemedColor(Theme.key_dialogBackground));
            Theme.applyDefaultShadow(bgPaint);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (textSelectionHelper.isInSelectionMode() && textSelectionOverlay.onTouchEvent(ev)) {
                return true;
            }
            if (textSelectionOverlay.checkOnTap(ev)) {
                ev.setAction(MotionEvent.ACTION_CANCEL);
            }
            return super.dispatchTouchEvent(ev);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {

            float top = getSheetTop();
            final float R = AndroidUtilities.lerp(0, dp(12), MathUtils.clamp(top / dpf2(24), 0, 1));
            headerView.setTranslationY(Math.max(AndroidUtilities.statusBarHeight, top));
            updateLightStatusBar(top <= AndroidUtilities.statusBarHeight / 2f);

            bgPath.rewind();
            AndroidUtilities.rectTmp.set(0, top, getWidth(), getHeight() + R);
            bgPath.addRoundRect(AndroidUtilities.rectTmp, R, R, Path.Direction.CW);
            canvas.drawPath(bgPath, bgPaint);

            super.dispatchDraw(canvas);
        }

        private void updateLightStatusBar(boolean full) {
            if (lightStatusBarFull == null || lightStatusBarFull != full) {
                lightStatusBarFull = full;
                AndroidUtilities.setLightStatusBar(getWindow(), AndroidUtilities.computePerceivedBrightness(
                        full ?
                                getThemedColor(Theme.key_dialogBackground) :
                                Theme.blendOver(
                                        getThemedColor(Theme.key_actionBarDefault),
                                        0x33000000
                                )
                ) > .721f);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Bulletin.addDelegate(this, new Bulletin.Delegate() {
                @Override
                public int getBottomOffset(int tag) {
                    return AndroidUtilities.dp(16 + 48 + 16);
                }
            });
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            Bulletin.removeDelegate(this);
        }
    }
}
