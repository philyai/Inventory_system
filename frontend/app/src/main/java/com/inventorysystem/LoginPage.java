package com.inventorysystem;

import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.util.Log;
import android.util.Base64;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.inventorysystem.ConnectivityandService.ApiService;
import com.inventorysystem.ConnectivityandService.ApiErrorHandler;
import com.inventorysystem.ConnectivityandService.BackendServerSelector;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.LoginRequest;
import com.inventorysystem.Model.LoginResponse;
import com.inventorysystem.offline.BackendPreferences;
import com.inventorysystem.offline.OfflineItemSyncScheduler;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.Locale;
import org.json.JSONObject;

public class LoginPage extends AppCompatActivity {

    private static final String TAG = "LoginPage";

    private SessionManager sessionManager;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private MaterialButton btnSignIn;
    private CheckBox cbRemember;
    private TextView txtServerStatus;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_page);

        sessionManager = new SessionManager(this);
        etUsername = findViewById(R.id.username);
        etPassword = findViewById(R.id.Password);
        btnSignIn = findViewById(R.id.txtSignIn);
        cbRemember = findViewById(R.id.cbRemember);
        txtServerStatus = findViewById(R.id.txtServerStatus);
        if (getIntent().getBooleanExtra("session_expired", false)) {
            Toast.makeText(this,
                    "Your session has expired. Please sign in again.",
                    Toast.LENGTH_LONG).show();
        }

        btnSignIn.setOnClickListener(v -> {
            if (apiService == null) {
                selectBackendServer();
            } else {
                attemptLogin();
            }
        });

        selectBackendServer();
    }

    private void selectBackendServer() {
        apiService = null;
        btnSignIn.setEnabled(false);
        btnSignIn.setText(R.string.Sign);
        txtServerStatus.setText("Checking backend connection…");

        new BackendServerSelector().findAvailableServer(
                new BackendServerSelector.Callback() {
                    @Override
                    public void onServerSelected(String baseUrl) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            RetrofitClient.configureBaseUrl(LoginPage.this, baseUrl);
                            BackendPreferences.save(LoginPage.this, baseUrl);
                            apiService = RetrofitClient.getApiService();
                            txtServerStatus.setText("Connected to " + baseUrl);
                            btnSignIn.setText(R.string.Sign);
                            btnSignIn.setEnabled(true);

                            if (sessionManager.isRememberMeEnabled()
                                    && sessionManager.isLoggedIn()) {
                                openDashboard(
                                        sessionManager.getUsername(),
                                        sessionManager.getRole()
                                );
                            }
                        });
                    }

                    @Override
                    public void onNoServerAvailable() {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            String savedUrl = BackendPreferences.get(LoginPage.this);
                            if (savedUrl != null && sessionManager.isRememberMeEnabled()
                                    && sessionManager.isLoggedIn() && isTokenUnexpired(sessionManager.getToken())) {
                                RetrofitClient.configureBaseUrl(LoginPage.this, savedUrl);
                                txtServerStatus.setText("Offline mode");
                                openDashboard(sessionManager.getUsername(), sessionManager.getRole());
                                return;
                            }
                            txtServerStatus.setText("Backend unavailable on configured networks");
                            btnSignIn.setText("Retry connection");
                            btnSignIn.setEnabled(true);
                        });
                    }
                }
        );
    }

    private void attemptLogin() {
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString();

        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please enter username and password",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        btnSignIn.setEnabled(false);
        LoginRequest request = new LoginRequest(user, pass);

        sessionManager.clearSession();
        apiService.login(getDeviceName(), request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(
                    Call<LoginResponse> call,
                    Response<LoginResponse> response
            ) {
                btnSignIn.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();
                    String token = loginResponse.getToken();
                    String username = loginResponse.getUser().getUsername();
                    String role = loginResponse.getUser().getRole();

                    sessionManager.saveSession(
                            token,
                            loginResponse.getUser().getUsersId(),
                            username,
                            loginResponse.getUser().getEmail(),
                            role,
                            cbRemember.isChecked()
                    );

                    Toast.makeText(
                            LoginPage.this,
                            "Welcome, " + username,
                            Toast.LENGTH_SHORT
                    ).show();
                    OfflineItemSyncScheduler.enqueue(LoginPage.this);
                    openDashboard(username, role);
                } else {
                    Toast.makeText(
                            LoginPage.this,
                            ApiErrorHandler.message(response),
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable throwable) {
                btnSignIn.setEnabled(true);
                Log.e(TAG, "Login request failed", throwable);
                Toast.makeText(
                        LoginPage.this,
                        "Network error: " + throwable.getMessage(),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private boolean isTokenUnexpired(String token) {
        try {
            if (token == null) return false;
            String[] parts = token.split("\\.");
            if (parts.length < 2) return false;
            String payload = new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP), java.nio.charset.StandardCharsets.UTF_8);
            long expiresAtSeconds = new JSONObject(payload).optLong("exp", 0L);
            return expiresAtSeconds > System.currentTimeMillis() / 1000L;
        } catch (Exception exception) {
            Log.w(TAG, "Unable to validate saved session expiry", exception);
            return false;
        }
    }
    private String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model == null || model.trim().isEmpty()) {
            return manufacturer == null || manufacturer.trim().isEmpty()
                    ? "Android device" : manufacturer;
        }
        if (manufacturer == null || manufacturer.trim().isEmpty()
                || model.toLowerCase(Locale.US)
                .startsWith(manufacturer.toLowerCase(Locale.US))) {
            return model;
        }
        return manufacturer + " " + model;
    }

    private void openDashboard(String username, String role) {
        Intent intent = new Intent(LoginPage.this, DashboardActivity.class);
        intent.putExtra("username", username);
        intent.putExtra("role", role);
        startActivity(intent);
        finish();
    }
}
