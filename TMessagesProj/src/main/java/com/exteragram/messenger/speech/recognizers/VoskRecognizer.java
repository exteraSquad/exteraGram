/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.speech.recognizers;

import android.text.TextUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.speech.VoiceRecognitionController;
import com.exteragram.messenger.speech.utils.FormatConverter;
import com.exteragram.messenger.utils.network.ExteraHttpClient;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechStreamService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class VoskRecognizer implements VoiceRecognitionController.RecognitionProvider, AutoCloseable {
    private final OkHttpClient client = ExteraHttpClient.INSTANCE.getClient();
    private final File modelsDir = new File(
            ApplicationLoader.applicationContext.getExternalFilesDir(null), "Vosk Models"
    );

    private final List<VoiceRecognitionController.RecognitionModel> models = new ArrayList<>() {{
        add(new VoiceRecognitionController.RecognitionModel("ca", "https://alphacephei.com/vosk/models/vosk-model-small-ca-0.4.zip", 43405881));
        add(new VoiceRecognitionController.RecognitionModel("cs", "https://alphacephei.com/vosk/models/vosk-model-small-cs-0.4-rhasspy.zip", 46088666));
        add(new VoiceRecognitionController.RecognitionModel("de", "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip", 46499967));
        add(new VoiceRecognitionController.RecognitionModel("en", "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", 41205931));
        add(new VoiceRecognitionController.RecognitionModel("eo", "https://alphacephei.com/vosk/models/vosk-model-small-eo-0.42.zip", 43839401));
        add(new VoiceRecognitionController.RecognitionModel("es", "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip", 39817833));
        add(new VoiceRecognitionController.RecognitionModel("fa", "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip", 53431220));
        add(new VoiceRecognitionController.RecognitionModel("fr", "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip", 42233323));
        add(new VoiceRecognitionController.RecognitionModel("gu", "https://alphacephei.com/vosk/models/vosk-model-small-gu-0.42.zip", 108054987));
        add(new VoiceRecognitionController.RecognitionModel("hi", "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip", 44458845));
        add(new VoiceRecognitionController.RecognitionModel("it", "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip", 49665141));
        add(new VoiceRecognitionController.RecognitionModel("ja", "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip", 49704573));
        add(new VoiceRecognitionController.RecognitionModel("kk", "https://alphacephei.com/vosk/models/vosk-model-small-kz-0.42.zip", 59697294));
        add(new VoiceRecognitionController.RecognitionModel("ko", "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip", 86914329));
        add(new VoiceRecognitionController.RecognitionModel("nl", "https://alphacephei.com/vosk/models/vosk-model-small-nl-0.22.zip", 40441176));
        add(new VoiceRecognitionController.RecognitionModel("pl", "https://alphacephei.com/vosk/models/vosk-model-small-pl-0.22.zip", 52979372));
        add(new VoiceRecognitionController.RecognitionModel("pt", "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip", 32453112));
        add(new VoiceRecognitionController.RecognitionModel("ru", "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip", 46236750));
        add(new VoiceRecognitionController.RecognitionModel("tg", "https://alphacephei.com/vosk/models/vosk-model-small-tg-0.22.zip", 51879043));
        add(new VoiceRecognitionController.RecognitionModel("tr", "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip", 36855784));
        add(new VoiceRecognitionController.RecognitionModel("uk", "https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-small.zip", 143914407));
        add(new VoiceRecognitionController.RecognitionModel("uz", "https://alphacephei.com/vosk/models/vosk-model-small-uz-0.22.zip", 51061189));
        add(new VoiceRecognitionController.RecognitionModel("vi", "https://alphacephei.com/vosk/models/vosk-model-small-vn-0.4.zip", 33656337));
        add(new VoiceRecognitionController.RecognitionModel("zh", "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip", 43898754));
    }};

    private final Map<String, Model> loadedModels = new ConcurrentHashMap<>();

    private static void unpackZip(String zipFilePath, String destinationPath) throws IOException {
        File destDir = new File(destinationPath);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry zipEntry = zis.getNextEntry();

        // Extract the name of the top-level folder
        String topLevelFolder = null;
        if (zipEntry != null) {
            topLevelFolder = zipEntry.getName().split("/")[0];
        }

        while (zipEntry != null) {
            // Skip the top-level folder entry
            if (zipEntry.getName().equals(topLevelFolder + "/")) {
                zipEntry = zis.getNextEntry();
                continue;
            }

            // Remove the top-level folder from the path
            String entryName = zipEntry.getName().substring(topLevelFolder.length() + 1);
            File newFile = new File(destDir, entryName);

            // Create parent directories if they don't exist
            new File(newFile.getParent()).mkdirs();

            if (!zipEntry.isDirectory()) {
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }

            zipEntry = zis.getNextEntry();
        }

        zis.closeEntry();
        zis.close();
    }

    @Override
    public List<VoiceRecognitionController.RecognitionModel> listAvailableModels() {
        return models;
    }

    @Override
    public List<VoiceRecognitionController.RecognitionModel> listDownloadedModels() {
        return models.stream()
                .filter(model -> {
                    File modelDir = new File(modelsDir, model.getLanguage());
                    File zipFile = new File(modelDir, "model.zip");
                    return modelDir.exists()
                            && !zipFile.exists()
                            && !isDirectoryEmpty(modelDir);
                })
                .collect(Collectors.toList());
    }

    private boolean isDirectoryEmpty(File directory) {
        String[] files = directory.list();
        return files == null || files.length == 0;
    }

    @Override
    public void downloadModel(String modelName, VoiceRecognitionController.DownloadModelCallback callback) {
        VoiceRecognitionController.RecognitionModel model = models.stream()
                .filter(m -> m.getLanguage().equals(modelName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelName));

        File modelDir = new File(modelsDir, model.getLanguage());

        File zipFile = new File(modelDir, "model.zip");
        if (zipFile.exists()) {
            try {
                deleteDirectory(modelDir);
            } catch (IOException e) {
                callback.onError(new IOException("Failed to delete existing model directory", e));
                return;
            }
        }

        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }

        try {
            Request request = new Request.Builder().url(model.getUrl()).build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                FileLog.e("Failed to download: " + response);
            }

            zipFile = new File(modelDir, "model.zip");
            try (InputStream inputStream = response.body().byteStream();
                 OutputStream outputStream = new FileOutputStream(zipFile)) {
                var total = response.body().contentLength();

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);

                    callback.onProgress((float) zipFile.length() / total);
                }
            }

            unpackZip(zipFile.getAbsolutePath(), modelDir.getAbsolutePath());

            try {
                if (!zipFile.delete()) {
                    zipFile.deleteOnExit();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            callback.onCompleted();
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    @Override
    public void deleteModel(String modelName) throws Exception {
        VoiceRecognitionController.RecognitionModel model = models.stream()
                .filter(m -> m.getLanguage().equals(modelName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelName));

        Model loadedModel = loadedModels.remove(model.getLanguage());
        if (loadedModel != null) {
            loadedModel.close();
        }

        File modelDir = new File(modelsDir, model.getLanguage());
        if (!modelDir.exists()) {
            throw new IllegalStateException("Model is not downloaded: " + model.getLanguage());
        }

        deleteDirectory(modelDir);
    }

    private void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        if (!directory.delete()) {
            FileLog.e("Failed to delete file or directory: " + directory.getAbsolutePath());
        }
    }

    @Override
    public void recognize(String filePath, String language, VoiceRecognitionController.RecognitionCallback callback) {
        var availableModel = models.stream()
                .filter(model -> model.getLanguage().equals(language))
                .findFirst()
                .orElse(null);

        if (availableModel == null) {
            callback.onLanguageNotSupported(language);
            return;
        }

        var downloadedModel = listDownloadedModels().stream()
                .filter(model -> model.getLanguage().equals(language))
                .findFirst()
                .orElse(null);
        if (downloadedModel == null) {
            callback.onLanguageNotDownloaded(language);
            return;
        }

        try {
            if (!loadedModels.containsKey(language)) {
                FileLog.d("Loading model: " + language);
                LibVosk.setLogLevel(LogLevel.INFO);
                Model loaded = new Model(modelsDir + "/" + language);
                loadedModels.put(language, loaded);
                FileLog.d("Model loaded: " + language);
            }
            Model model = loadedModels.get(language);

            var is = FormatConverter.extractAndConvertToPcm(filePath, false);
            var sampleRate = FormatConverter.getSampleRate(filePath);

            FileLog.d("Recognizing: " + filePath);

            Recognizer recognizer = new Recognizer(model, sampleRate);

            var streamService = new SpeechStreamService(recognizer, is, sampleRate);
            streamService.start(new RecognitionListener() {
                @Override
                public void onPartialResult(String s) {
                    // sentence is not ended, ignore
                }

                @Override
                public void onResult(String s) {
                    FileLog.d("Result: " + s);
                    if (TextUtils.isEmpty(s)) {
                        return;
                    }

                    var obj = ExteraConfig.getGSON().fromJson(s, Map.class);
                    var text = (String) obj.get("text");

                    callback.onChunk(text);
                }

                @Override
                public void onFinalResult(String s) {
                    FileLog.d("Final result: " + s);

                    var obj = ExteraConfig.getGSON().fromJson(s, Map.class);
                    var text = (String) obj.get("text");

                    callback.onCompleted(text);
                    recognizer.close();
                }

                @Override
                public void onError(Exception e) {
                    FileLog.e("Failed to recognize", e);
                    callback.onError(e);
                    recognizer.close();
                }

                @Override
                public void onTimeout() {
                    // not called
                }
            });
        } catch (IOException e) {
            FileLog.e("Failed to recognize", e);
            callback.onError(e);
        }
    }

    @Override
    public void unloadModels() {
        loadedModels.values().forEach(Model::close);
        loadedModels.clear();
    }

    @Override
    public boolean hasLoadedModels() {
        return !loadedModels.isEmpty();
    }

    @Override
    public void close() {
        unloadModels();
    }
}
