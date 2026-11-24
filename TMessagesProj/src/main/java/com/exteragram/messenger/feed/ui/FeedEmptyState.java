/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import com.exteragram.messenger.feed.FeedConfig;
import com.exteragram.messenger.feed.FeedController;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.StickerEmptyView;

public final class FeedEmptyState {

    private FeedEmptyState() {
    }

    public static void bind(BaseFragment fragment, StickerEmptyView view) {
        final int account = fragment.getCurrentAccount();
        final FeedController controller = FeedController.getInstance(account);
        final FeedConfig config = FeedConfig.getInstance(account);
        view.subtitle.setVisibility(View.GONE);
        view.button.setVisibility(View.GONE);
        if (!controller.hasChannels() || controller.hasIncludedChannels()) {
            view.title.setText(getString(R.string.FeedNoPosts));
            return;
        }
        final FeedConfig.Category category = config.getSelectedCategory();
        if (category != null) {
            view.title.setText(getString(R.string.FeedCategoryEmpty));
            view.subtitle.setText(getString(R.string.FeedCategoryEmptyInfo));
            view.subtitle.setVisibility(View.VISIBLE);
            setButton(view, getString(R.string.FeedCategoryAddChannels), () -> fragment.presentFragment(new FeedCategoryEditActivity(category.getId(), false)));
        } else {
            view.title.setText(getString(R.string.FeedAllChannelsExcluded));
            if (!config.getVisibleCategories().isEmpty()) {
                view.subtitle.setText(getString(R.string.FeedAllChannelsExcludedInfo));
                view.subtitle.setVisibility(View.VISIBLE);
            }
            setButton(view, getString(R.string.FeedSettings), () -> fragment.presentFragment(new FeedChannelsActivity()));
        }
    }

    private static void setButton(StickerEmptyView view, CharSequence text, Runnable action) {
        view.button.setText(text, false);
        view.button.setOnClickListener(v -> action.run());
        view.button.setVisibility(View.VISIBLE);
    }
}
