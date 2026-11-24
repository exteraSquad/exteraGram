/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.ai.data.Role;
import com.exteragram.messenger.ai.data.Service;
import com.exteragram.messenger.ai.data.Suggestions;
import com.exteragram.messenger.ai.network.backend.OnDeviceAvailability;
import com.exteragram.messenger.ai.network.backend.OnDeviceBackend;
import com.exteragram.messenger.utils.chats.ChatUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class AiController {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 160;

    private final List<Role> roles = new ArrayList<>();
    private final List<Service> services = new ArrayList<>();

    {
        loadRoles();
        loadServices();
    }

    public static void clearHistory(BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, boolean confirm) {
        clearHistory(fragment, resourcesProvider, confirm, null);
    }

    public static void clearHistory(BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, boolean confirm, Runnable onCleared) {
        if (fragment == null) {
            return;
        }
        if (confirm) {
            AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), resourcesProvider);
            builder.setMessage(AndroidUtilities.replaceTags(getString(R.string.ClearConversationHistoryInfo)));
            builder.setTitle(getString(R.string.ClearHistory));
            builder.setNegativeButton(getString(R.string.Cancel), null);
            builder.setPositiveButton(getString(R.string.ClearButton), (dialog, which) -> {
                AiConfig.clearConversationHistory();
                if (onCleared != null) {
                    onCleared.run();
                }
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.ic_delete, getString(R.string.HistoryCleared)).show();
            });
            AlertDialog dialog = builder.create();
            fragment.showDialog(dialog);
            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        } else {
            AiConfig.clearConversationHistory();
            if (onCleared != null) {
                onCleared.run();
            }
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.ic_delete, getString(R.string.HistoryCleared)).show();
        }
    }

    public static void showErrorBulletin(ViewGroup containerView, Theme.ResourcesProvider resourcesProvider, int errorCode) {
        showErrorBulletin(null, containerView, resourcesProvider, errorCode, null, null);
    }

    public static void showErrorBulletin(ViewGroup containerView, Theme.ResourcesProvider resourcesProvider, int errorCode, String errorMessage, Runnable onChangeModel) {
        showErrorBulletin(null, containerView, resourcesProvider, errorCode, errorMessage, onChangeModel);
    }

    public static void showErrorBulletin(BaseFragment fragment, int errorCode) {
        showErrorBulletin(fragment, null, null, errorCode, null, null);
    }

    public static void showErrorBulletin(BaseFragment fragment, int errorCode, String errorMessage) {
        showErrorBulletin(fragment, null, null, errorCode, errorMessage, null);
    }

    public static boolean isOnDeviceError(int errorCode) {
        return errorCode == OnDeviceBackend.ERROR_UNAVAILABLE
                || errorCode == OnDeviceBackend.ERROR_DOWNLOAD_REQUIRED
                || errorCode == OnDeviceBackend.ERROR_DOWNLOADING
                || errorCode == OnDeviceBackend.ERROR_INPUT_TOO_LONG
                || errorCode == OnDeviceBackend.ERROR_INFERENCE_FAILED;
    }

    private static void showErrorBulletin(BaseFragment fragment, ViewGroup containerView, Theme.ResourcesProvider resourcesProvider, int errorCode, String errorMessage, Runnable onChangeModel) {
        int icon, title, subtitle;
        boolean generic = false;

        switch (errorCode) {
            case OnDeviceBackend.ERROR_UNAVAILABLE -> {
                icon = 3;
                title = R.string.AIErrorOnDevice;
                subtitle = R.string.AIErrorOnDeviceInfo;
            }
            case OnDeviceBackend.ERROR_DOWNLOAD_REQUIRED -> {
                icon = 8;
                title = R.string.AIErrorOnDeviceDownload;
                subtitle = R.string.AIErrorOnDeviceDownloadInfo;
            }
            case OnDeviceBackend.ERROR_DOWNLOADING -> {
                icon = 5;
                title = R.string.AIErrorOnDeviceDownloading;
                subtitle = R.string.AIErrorOnDeviceDownloadingInfo;
            }
            case OnDeviceBackend.ERROR_INPUT_TOO_LONG -> {
                icon = 2;
                title = R.string.AIErrorOnDeviceTooLong;
                subtitle = R.string.AIErrorOnDeviceTooLongInfo;
            }
            case OnDeviceBackend.ERROR_INFERENCE_FAILED -> {
                icon = 3;
                title = R.string.AIErrorOnDeviceStopped;
                subtitle = R.string.AIErrorOnDeviceStoppedInfo;
            }
            case 204 -> {
                icon = 2;
                title = R.string.AIErrorEmpty;
                subtitle = R.string.AIErrorEmptyInfo;
            }
            case 400 -> {
                icon = 2;
                title = R.string.AIError400;
                subtitle = R.string.AIError400Info;
            }
            case 401 -> {
                icon = 3;
                title = R.string.AIError401;
                subtitle = R.string.AIError401Info;
            }
            case 402 -> {
                icon = 8;
                title = R.string.AIError402;
                subtitle = R.string.AIError402Info;
            }
            case 403 -> {
                icon = 9;
                title = R.string.AIError403;
                subtitle = R.string.AIError403Info;
            }
            case 408 -> {
                icon = 5;
                title = R.string.AIError408;
                subtitle = R.string.AIError408Info;
            }
            case 429 -> {
                icon = 6;
                title = R.string.AIError429;
                subtitle = R.string.AIError429Info;
            }
            case 502 -> {
                icon = 3;
                title = R.string.AIError502;
                subtitle = R.string.AIError502Info;
            }
            case 503 -> {
                icon = 3;
                title = R.string.AIError503;
                subtitle = R.string.AIError503Info;
            }
            default -> {
                icon = 3;
                title = R.string.AIError;
                subtitle = R.string.AIErrorInfo;
                generic = true;
            }
        }

        String detail = generic || errorCode == 400 || errorCode == 403 ? trimErrorMessage(errorMessage) : null;
        final int bulletinIcon = icon;
        final int bulletinTitle = generic && detail != null ? R.string.UnknownError : title;
        final CharSequence bulletinSubtitle = detail != null ? detail : getString(subtitle);

        var bf = containerView != null ? BulletinFactory.of((FrameLayout) containerView, resourcesProvider) : fragment != null ? BulletinFactory.of(fragment) : BulletinFactory.global();
        Context context = containerView != null ? containerView.getContext() : fragment != null ? fragment.getContext() : null;
        AndroidUtilities.runOnUIThread(() -> {
            if (onChangeModel == null || context == null || !isOnDeviceError(errorCode)) {
                bf.createSimpleBulletin(getString(bulletinTitle), bulletinSubtitle, bulletinIcon).show();
                return;
            }
            Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(context, resourcesProvider);
            layout.setSticker(bulletinIcon);
            layout.titleTextView.setText(getString(bulletinTitle));
            layout.subtitleTextView.setText(bulletinSubtitle);
            layout.setButton(new Bulletin.UndoButton(context, true, resourcesProvider)
                    .setText(getString(R.string.AIChangeModel))
                    .setUndoAction(onChangeModel));
            bf.create(layout, Bulletin.DURATION_PROLONG).show();
        });
    }

    @Nullable
    private static String trimErrorMessage(String errorMessage) {
        if (TextUtils.isEmpty(errorMessage)) {
            return null;
        }
        String message = errorMessage.trim();
        if (message.isEmpty()) {
            return null;
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH).trim() + "…"
                : message;
    }

    public static AiController getInstance() {
        return AiController.SingletonHolder.INSTANCE;
    }

    public static boolean canUseAI() {
        Service selected = getInstance().getSelected();
        if (selected.isOnDevice()) {
            return OnDeviceAvailability.isReady();
        }
        return !TextUtils.isEmpty(selected.getKey());
    }

    public void loadRoles() {
        List<Role> loadedRoles = AiConfig.getRoles();
        roles.clear();
        roles.addAll(loadedRoles);
        roles.removeIf(role -> role == null || role.getName() == null || role.getPrompt() == null);
    }

    @NonNull
    public List<Role> getRoles() {
        return Collections.unmodifiableList(roles);
    }

    @NonNull
    public List<Role> getSuggestedRoles() {
        return Arrays.stream(Suggestions.values())
                .map(Suggestions::getRole)
                .collect(Collectors.toList());
    }

    public boolean isCustomRole(@Nullable Role role) {
        if (role == null) return false;
        return roles.contains(role);
    }

    public boolean isSuggestedRole(@Nullable Role role) {
        if (role == null) return false;
        return getSuggestedRoles().contains(role);
    }

    @NonNull
    public Role getSelectedRole() {
        for (Role role : roles) {
            if (role.isSelected()) {
                return role;
            }
        }
        for (Role suggestedRole : getSuggestedRoles()) {
            if (suggestedRole.isSelected()) {
                return suggestedRole;
            }
        }
        return Suggestions.ASSISTANT.getRole();
    }

    public boolean addRole(@NonNull Role role) {
        if (isSuggestedRole(role) || isCustomRole(role)) {
            return false;
        }

        roles.add(0, role);
        saveRoles();
        return true;
    }

    public boolean removeRole(@Nullable Role role) {
        if (role == null) return false;

        boolean removed = roles.remove(role);
        if (removed) {
            saveRoles();
        }
        return removed;
    }

    public boolean updateRole(@NonNull Role oldRole, @NonNull Role newRole) {
        int index = roles.indexOf(oldRole);
        if (index != -1) {
            if (isSuggestedRole(newRole) && !oldRole.equals(newRole)) {
                return false;
            }

            roles.set(index, newRole);
            saveRoles();
            return true;
        }
        return false;
    }

    public void saveRoles() {
        AiConfig.saveRoles(new ArrayList<>(roles));
    }

    public void loadServices() {
        List<Service> loadedServices = AiConfig.getServices();
        services.clear();
        services.addAll(loadedServices);
        services.removeIf(Service::isOnDevice);
        if (OnDeviceAvailability.isSupported()) {
            String onDeviceModel = OnDeviceAvailability.getModelName();
            AiConfig.ON_DEVICE_SERVICE.setModel(TextUtils.isEmpty(onDeviceModel) ? AiConfig.ON_DEVICE_DEFAULT_MODEL : onDeviceModel);
            AiConfig.ON_DEVICE_SERVICE.setReasoningEnabled(OnDeviceAvailability.isThinkingSupported() && AiConfig.getOnDeviceReasoning());
            services.add(AiConfig.ON_DEVICE_SERVICE);
        }
        if (!OnDeviceAvailability.isReady() && AiConfig.ON_DEVICE_SERVICE.getId().equals(AiConfig.getSelectedServiceId())) {
            AiConfig.setSelectedServiceId(null);
        }
    }

    public List<Service> getAll() {
        return Collections.unmodifiableList(services);
    }

    public boolean isServicesEmpty() {
        return services.isEmpty();
    }

    public boolean contains(Service service) {
        return services.contains(service);
    }

    public void addService(Service service) {
        if (service == null || service.isOnDevice() || services.contains(service)) {
            return;
        }
        services.add(service);
        saveServices();
    }

    public void updateService(Service oldService, Service newService) {
        if (oldService == null || oldService.isOnDevice() || newService == null || newService.isOnDevice()) {
            return;
        }
        int index = services.indexOf(oldService);
        if (index != -1) {
            newService.setId(oldService.getId());
            services.set(index, newService);
            saveServices();
        }
    }

    public boolean removeService(Service service) {
        if (service == null || service.isOnDevice()) {
            return false;
        }
        boolean removed = services.remove(service);
        if (removed) {
            saveServices();
        }
        return removed;
    }

    public Service getSelected() {
        Optional<Service> selected = services.stream().filter(Service::isSelected).findFirst();
        if (selected.isPresent()) {
            return selected.get();
        }
        for (Service service : services) {
            if (!service.isOnDevice()) {
                return service;
            }
        }
        return AiConfig.DEFAULT_SERVICE;
    }

    public void saveServices() {
        services.sort(Comparator.comparing(Service::getModel,
                Comparator.nullsLast(Comparator.naturalOrder())));

        ArrayList<Service> persisted = new ArrayList<>();
        for (Service service : services) {
            if (!service.isOnDevice()) {
                persisted.add(service);
            }
        }
        AiConfig.saveServices(persisted);
    }

    public static boolean canSendImage(MessageObject messageObject) {
        var path = ChatUtils.getInstance().getPathToMessage(messageObject);
        return messageObject != null && canSendImage(path);
    }

    public static boolean canSendImage(String filePath) {
        if (filePath == null) return false;
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        String lowerFilePath = filePath.toLowerCase(Locale.ROOT);
        return lowerFilePath.endsWith(".png") || lowerFilePath.endsWith(".jpg") ||
               lowerFilePath.endsWith(".jpeg") || lowerFilePath.endsWith(".webp") ||
               lowerFilePath.endsWith(".heic") || lowerFilePath.endsWith(".heif");
    }

    private static class SingletonHolder {
        private static final AiController INSTANCE = new AiController();
    }
}
