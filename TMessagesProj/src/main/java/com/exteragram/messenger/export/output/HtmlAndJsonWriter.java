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
import java.util.function.Function;

public class HtmlAndJsonWriter extends AbstractWriter {

    private final HtmlWriter html = new HtmlWriter();
    private final JsonWriter json = new JsonWriter();

    private Result invoke(Function<AbstractWriter, Result> method) {
        Result result = method.apply(html);
        return result.isSuccess() ? method.apply(json) : result;
    }

    @Override
    public Result start(ExportSettings settings, OutputFile.Stats stats) {
        return invoke(w -> w.start(settings, stats));
    }

    @Override
    public Result writePersonal(ExportData.PersonalInfo data) {
        return invoke(w -> w.writePersonal(data));
    }

    @Override
    public Result writeUserpicsStart(int count) {
        return invoke(w -> w.writeUserpicsStart(count));
    }

    @Override
    public Result writeUserpicsSlice(ArrayList<ExportData.Photo> data) {
        return invoke(w -> w.writeUserpicsSlice(data));
    }

    @Override
    public Result writeUserpicsEnd() {
        return invoke(AbstractWriter::writeUserpicsEnd);
    }

    @Override
    public Result writeStoriesStart(int count) {
        return invoke(w -> w.writeStoriesStart(count));
    }

    @Override
    public Result writeStoriesSlice(ExportData.StoriesSlice data) {
        return invoke(w -> w.writeStoriesSlice(data));
    }

    @Override
    public Result writeStoriesEnd() {
        return invoke(AbstractWriter::writeStoriesEnd);
    }

    @Override
    public Result writeProfileMusicStart(int count) {
        return invoke(w -> w.writeProfileMusicStart(count));
    }

    @Override
    public Result writeProfileMusicSlice(ArrayList<ExportData.Message> data) {
        return invoke(w -> w.writeProfileMusicSlice(data));
    }

    @Override
    public Result writeProfileMusicEnd() {
        return invoke(AbstractWriter::writeProfileMusicEnd);
    }

    @Override
    public Result writeContactsList(ExportData.ContactsList data) {
        return invoke(w -> w.writeContactsList(data));
    }

    @Override
    public Result writeSessionsList(ExportData.SessionsList data) {
        return invoke(w -> w.writeSessionsList(data));
    }

    @Override
    public Result writeOtherData(ExportData.File data) {
        return invoke(w -> w.writeOtherData(data));
    }

    @Override
    public Result writeDialogsStart(ExportData.DialogsInfo data) {
        return invoke(w -> w.writeDialogsStart(data));
    }

    @Override
    public Result writeDialogStart(ExportData.DialogInfo data) {
        return invoke(w -> w.writeDialogStart(data));
    }

    @Override
    public Result writeDialogSlice(ExportData.MessagesSlice data) {
        return invoke(w -> w.writeDialogSlice(data));
    }

    @Override
    public Result writeDialogEnd() {
        return invoke(AbstractWriter::writeDialogEnd);
    }

    @Override
    public Result writeDialogsEnd() {
        return invoke(AbstractWriter::writeDialogsEnd);
    }

    @Override
    public Result finish() {
        return invoke(AbstractWriter::finish);
    }

    @Override
    public String mainFilePath() {
        return html.mainFilePath();
    }
}
