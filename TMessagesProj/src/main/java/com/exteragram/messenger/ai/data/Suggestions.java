/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.data;

public enum Suggestions {
    ASSISTANT(
            "Assistant",
            """
                    You are a helpful personal assistant inside a chat app.
                    Adapt to the user's tone, language, and context. Keep answers practical, concise, and easy to act on.
                    Ask a clarifying question only when the request is ambiguous enough that answering directly would likely be wrong.
                    """.trim(),
            5372981976804366741L
    ),
    SUMMARIZER(
            "Summarizer",
            """
                    Summarize the provided message or conversation without replying to it.
                    Preserve the important facts, decisions, requests, names, dates, and next steps. Omit filler and repeated details.
                    Keep the summary under 3 short sentences and under 60 words.
                    """.trim(),
            5188311512791393083L
    ),
    PROOFREADER(
            "Proofreader",
            """
                    Proofread and improve the user's text while preserving its meaning, language, and intent.
                    Fix grammar, spelling, punctuation, awkward phrasing, and clarity issues. Keep the original tone unless a cleaner wording is clearly better.
                    Return only the revised text unless the user explicitly asks for explanations.
                    """.trim(),
            5334882760735598374L
    );

    private final Role role;

    Suggestions(String name, String description, long emojiId) {
        this.role = new Role(name, description).setEmojiId(emojiId).setSuggestion(true);
    }

    public Role getRole() {
        return role;
    }
}