package com.inventorysystem;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.inventorysystem.ConnectivityandService.ApiErrorHandler;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.SystemInformationModel;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AboutSystemActivity extends AppCompatActivity {
    private ProgressBar progress;
    private TextView state;

    @Override protected void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        setContentView(R.layout.activity_about_system);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        progress = findViewById(R.id.progress);
        state = findViewById(R.id.txtState);
        label(R.id.rowSystemName, "System Name");
        label(R.id.rowFirmware, "Firmware Version");
        label(R.id.rowApplication, "Application Version");
        label(R.id.rowApi, "API Version");
        label(R.id.rowRuntime, "Runtime");
        label(R.id.rowOs, "Operating System");
        label(R.id.rowArchitecture, "Architecture");
        label(R.id.rowDatabase, "Database");
        label(R.id.rowUptime, "Server Uptime");
        state.setOnClickListener(v -> load());
        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        state.setVisibility(View.GONE);
        RetrofitClient.getApiService().getSystemInformation()
                .enqueue(new Callback<SystemInformationModel>() {
                    @Override public void onResponse(Call<SystemInformationModel> call,
                                                     Response<SystemInformationModel> response) {
                        progress.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) {
                            bind(response.body());
                        } else {
                            showError(ApiErrorHandler.message(response));
                        }
                    }
                    @Override public void onFailure(Call<SystemInformationModel> call, Throwable t) {
                        progress.setVisibility(View.GONE);
                        showError("Unable to load system information. Tap to retry.");
                    }
                });
    }

    private void bind(SystemInformationModel model) {
        set(R.id.rowSystemName, model.getSystemName());
        set(R.id.rowFirmware, model.getFirmwareVersion());
        set(R.id.rowApplication, model.getApplicationVersion());
        set(R.id.rowApi, model.getApiVersion());
        String runtime = join(model.getPlatform(), model.getNodeVersion());
        set(R.id.rowRuntime, runtime);
        set(R.id.rowOs, model.getOperatingSystem());
        set(R.id.rowArchitecture, model.getArchitecture());
        SystemInformationModel.DatabaseInformation db = model.getDatabase();
        set(R.id.rowDatabase, db == null ? null : join(db.getDialect(), db.getStatus()));
        set(R.id.rowUptime, formatUptime(model.getServerUptimeSeconds()));
    }

    private void label(int rowId, String text) {
        findViewById(rowId).<TextView>findViewById(R.id.label).setText(text);
    }
    private void set(int rowId, String value) {
        findViewById(rowId).<TextView>findViewById(R.id.value).setText(
                value == null || value.trim().isEmpty() ? "—" : value);
    }
    private String join(String first, String second) {
        if (first == null || first.trim().isEmpty()) return second;
        if (second == null || second.trim().isEmpty()) return first;
        return first + " — " + second;
    }
    private String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
    private void showError(String message) {
        state.setText(message);
        state.setVisibility(View.VISIBLE);
    }
}
