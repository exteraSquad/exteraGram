/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.data;

import androidx.annotation.Nullable;

import com.exteragram.messenger.ai.AiConfig;

import java.util.Objects;

public class Role implements Comparable<Role> {
    private String name;
    private String prompt;
    private long emojiId;
    private boolean isSuggestion;

    public Role(String name, String prompt) {
        this.name = name;
        this.prompt = prompt;
    }

    @Override
    public int compareTo(Role otherPerson) {
        return this.name.compareTo(otherPerson.getName());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Role otherRole = (Role) obj;

        return name.equals(otherRole.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public long getEmojiId() {
        return emojiId;
    }

    public Role setEmojiId(long emojiId) {
        this.emojiId = emojiId;
        return this;
    }

    public boolean isSuggestion() {
        return isSuggestion;
    }

    public Role setSuggestion(boolean isSuggestion) {
        this.isSuggestion = isSuggestion;
        return this;
    }

    public boolean isSelected() {
        return Objects.equals(AiConfig.getSelectedRole(), name);
    }
}
