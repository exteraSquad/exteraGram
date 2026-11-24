/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output;

import com.exteragram.messenger.export.ExportSettings;
import com.exteragram.messenger.export.api.ExportData;
import com.exteragram.messenger.export.output.html.HtmlWriter;
import com.exteragram.messenger.export.output.json.JsonWriter;

import java.util.ArrayList;

public abstract class AbstractWriter {

    public static AbstractWriter create(ExportSettings.Format format) {
        return switch (format) {
            case Html -> new HtmlWriter();
            case Json -> new JsonWriter();
            case HtmlAndJson -> new HtmlAndJsonWriter();
        };
    }

    public abstract Result start(ExportSettings settings, OutputFile.Stats stats);

    public abstract Result writePersonal(ExportData.PersonalInfo data);

    public abstract Result writeUserpicsStart(int count);

    public abstract Result writeUserpicsSlice(ArrayList<ExportData.Photo> data);

    public abstract Result writeUserpicsEnd();

    public abstract Result writeStoriesStart(int count);

    public abstract Result writeStoriesSlice(ExportData.StoriesSlice data);

    public abstract Result writeStoriesEnd();

    public abstract Result writeProfileMusicStart(int count);

    public abstract Result writeProfileMusicSlice(ArrayList<ExportData.Message> data);

    public abstract Result writeProfileMusicEnd();

    public abstract Result writeContactsList(ExportData.ContactsList data);

    public abstract Result writeSessionsList(ExportData.SessionsList data);

    public abstract Result writeOtherData(ExportData.File data);

    public abstract Result writeDialogsStart(ExportData.DialogsInfo data);

    public abstract Result writeDialogStart(ExportData.DialogInfo data);

    public abstract Result writeDialogSlice(ExportData.MessagesSlice data);

    public abstract Result writeDialogEnd();

    public abstract Result writeDialogsEnd();

    public abstract Result finish();

    public abstract String mainFilePath();
}
