/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.data;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.exteragram.messenger.ai.AiConfig;

import java.util.Objects;
import java.util.UUID;

public class Service {

    public enum Kind {
        REST,
        ON_DEVICE
    }

    private String id;
    private String url;
    private String model;
    private String key;
    private boolean reasoningEnabled;
    private Kind kind;

    public Service(String url, String model, String key) {
        this(url, model, key, false);
    }

    public Service(String url, String model, String key, boolean reasoningEnabled) {
        this(UUID.randomUUID().toString(), url, model, key, reasoningEnabled);
    }

    public Service(String id, String url, String model, String key) {
        this(id, url, model, key, false);
    }

    public Service(String id, String url, String model, String key, boolean reasoningEnabled) {
        this.id = id;
        this.url = url;
        this.model = model;
        this.key = key;
        this.reasoningEnabled = reasoningEnabled;
    }

    public Kind getKind() {
        return kind == null ? Kind.REST : kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public boolean isOnDevice() {
        return getKind() == Kind.ON_DEVICE;
    }

    public String getId() {
        ensureId();
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean ensureId() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            return true;
        }
        return false;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getShortModel() {
        if (model == null) {
            return "";
        }
        String[] parts = model.split("/");

        String modelName = parts[parts.length - 1];

        int colonIndex = modelName.indexOf(':');
        if (colonIndex != -1) {
            modelName = modelName.substring(0, colonIndex);
        }

        return modelName;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public boolean isReasoningEnabled() {
        return reasoningEnabled;
    }

    public void setReasoningEnabled(boolean reasoningEnabled) {
        this.reasoningEnabled = reasoningEnabled;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Service service = (Service) obj;

        return getKind() == service.getKind()
                && Objects.equals(url, service.url)
                && Objects.equals(model, service.model)
                && Objects.equals(key, service.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKind(), url, model, key);
    }

    public int getLegacyHash() {
        return (url + model + key).hashCode();
    }

    public boolean isSelected() {
        String selectedServiceId = AiConfig.getSelectedServiceId();
        if (!TextUtils.isEmpty(selectedServiceId)) {
            return Objects.equals(selectedServiceId, getId());
        }
        return Objects.equals(AiConfig.getSelectedService().getId(), getId());
    }
}
