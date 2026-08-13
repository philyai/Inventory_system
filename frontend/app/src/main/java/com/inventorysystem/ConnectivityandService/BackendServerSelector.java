package com.inventorysystem.ConnectivityandService;

import com.inventorysystem.BuildConfig;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class BackendServerSelector {

    private static final String[] DEBUG_CANDIDATE_BASE_URLS = {
            "http://192.168.0.137:3001/",
            "http://172.21.224.1:3001/",
            "http://192.168.2.216:3001/",
            "http://192.168.68.106:3001/",
            "http://192.168.254.112:3001/"
    };

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build();

    public interface Callback {
        void onServerSelected(String baseUrl);

        void onNoServerAvailable();
    }

    public void findAvailableServer(Callback callback) {
        String[] candidates = BuildConfig.DEBUG
                ? DEBUG_CANDIDATE_BASE_URLS
                : productionCandidates();
        checkCandidate(candidates, 0, callback);
    }

    private String[] productionCandidates() {
        String configured = BuildConfig.BACKEND_BASE_URL == null
                ? "" : BuildConfig.BACKEND_BASE_URL.trim();
        return configured.isEmpty() ? new String[0] : new String[]{configured};
    }

    private void checkCandidate(String[] candidates, int index, Callback callback) {
        if (index >= candidates.length) {
            callback.onNoServerAvailable();
            return;
        }

        String baseUrl = candidates[index];
        if (!baseUrl.endsWith("/")) baseUrl += "/";
        final String candidateBaseUrl = baseUrl;
        Request request = new Request.Builder()
                .url(candidateBaseUrl + "health")
                .get()
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException exception) {
                checkCandidate(candidates, index + 1, callback);
            }

            @Override
            public void onResponse(Call call, Response response) {
                boolean healthy = false;
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject body = new JSONObject(response.body().string());
                        healthy = "ok".equalsIgnoreCase(body.optString("status"))
                                && "connected".equalsIgnoreCase(
                                body.optString("database"));
                    }
                } catch (Exception ignored) {
                    healthy = false;
                } finally {
                    response.close();
                }

                if (healthy) {
                    callback.onServerSelected(candidateBaseUrl);
                } else {
                    checkCandidate(candidates, index + 1, callback);
                }
            }
        });
    }
}
