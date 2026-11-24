/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export;

import org.telegram.messenger.Utilities;

public abstract class ExportState {

    private ExportState() {
    }

    public static final class Processing extends ExportState {
        public enum Step {
            Initializing, DialogsList, PersonalInfo, Userpics, Stories, ProfileMusic, Contacts, Sessions, OtherData, Dialogs, Topic
        }

        public enum EntityType {
            Chat, SavedMessages, RepliesMessages, VerifyCodes, Topic, Other
        }

        public Step step = Step.Initializing;
        public int substepsPassed;
        public int substepsNow;
        public int substepsTotal;
        public int stepIndex;
        public int stepCount;
        public EntityType entityType = EntityType.Other;
        public String entityName = "";
        public long entityDialogId;
        public int entityIndex;
        public int entityCount;
        public int itemIndex;
        public int itemCount;
        public String bytesName = "";
        public long bytesLoaded;
        public long bytesCount;

        public float progress() {
            if (substepsTotal <= 0) {
                return 0f;
            }
            float done = substepsPassed / (float) substepsTotal;
            int index = entityIndex;
            int count = entityCount;
            if (entityCount == 1 && entityIndex == 0) {
                index = itemIndex;
                count = itemCount;
            }
            float add = count > 0 ? (substepsNow * (float) index) / (substepsTotal * (float) count) : 0f;
            return Math.max(0f, Math.min(1f, done + add));
        }
    }

    public static final class Finished extends ExportState {
        public final String path;
        public final int filesCount;
        public final long bytesCount;
        public final int chatsCount;
        public final int messagesCount;
        public final int durationSeconds;

        public Finished(String path, int filesCount, long bytesCount, int chatsCount, int messagesCount, int durationSeconds) {
            this.path = path;
            this.filesCount = filesCount;
            this.bytesCount = bytesCount;
            this.chatsCount = chatsCount;
            this.messagesCount = messagesCount;
            this.durationSeconds = durationSeconds;
        }
    }

    public static final class ApiError extends ExportState {
        private static final String TAKEOUT_DELAY_PREFIX = "TAKEOUT_INIT_DELAY_";

        public final String text;
        public final long time = System.currentTimeMillis();

        public ApiError(String text) {
            this.text = text != null ? text : "";
        }

        public boolean isDelay() {
            return text.startsWith(TAKEOUT_DELAY_PREFIX);
        }

        public long delayUntil() {
            if (!isDelay()) {
                return 0;
            }
            return time + Utilities.parseInt(text.substring(TAKEOUT_DELAY_PREFIX.length())) * 1000L;
        }
    }

    public static final class IoError extends ExportState {
        public final String path;

        public IoError(String path) {
            this.path = path != null ? path : "";
        }
    }

    public static final class Cancelled extends ExportState {
    }
}
