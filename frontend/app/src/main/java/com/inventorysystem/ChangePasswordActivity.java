package com.inventorysystem;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.inventorysystem.ConnectivityandService.ApiErrorHandler;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.ChangePasswordRequest;
import com.inventorysystem.Model.ChangePasswordResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChangePasswordActivity extends AppCompatActivity {
    private TextInputLayout currentLayout, newLayout, confirmLayout;
    private TextInputEditText username, current, next, confirm;
    private MaterialButton submit;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_change_password);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        username = findViewById(R.id.inputUsername);
        current = findViewById(R.id.inputCurrentPassword);
        next = findViewById(R.id.inputNewPassword);
        confirm = findViewById(R.id.inputConfirmPassword);
        currentLayout = findViewById(R.id.layoutCurrentPassword);
        newLayout = findViewById(R.id.layoutNewPassword);
        confirmLayout = findViewById(R.id.layoutConfirmPassword);
        submit = findViewById(R.id.btnSubmit);
        username.setText(new SessionManager(this).getUsername());
        username.setEnabled(false);
        submit.setOnClickListener(v -> change());
    }

    private void change() {
        currentLayout.setError(null); newLayout.setError(null); confirmLayout.setError(null);
        String oldSecret = text(current);
        String newSecret = text(next);
        String confirmation = text(confirm);
        if (oldSecret.isEmpty()) { currentLayout.setError("Current password is required"); return; }
        if (newSecret.isEmpty()) { newLayout.setError("New password is required"); return; }
        if (confirmation.isEmpty()) {
            confirmLayout.setError("Please confirm the new password"); return;
        }
        if (newSecret.length() < 8) {
            newLayout.setError("Password must be at least 8 characters"); return;
        }
        if (!newSecret.equals(confirmation)) {
            confirmLayout.setError("Passwords do not match"); return;
        }
        submit.setEnabled(false);
        RetrofitClient.getApiService().changePassword(new ChangePasswordRequest(
                oldSecret, newSecret, confirmation))
                .enqueue(new Callback<ChangePasswordResponse>() {
                    @Override public void onResponse(Call<ChangePasswordResponse> call,
                                                     Response<ChangePasswordResponse> response) {
                        submit.setEnabled(true);
                        if (response.isSuccessful() && response.body() != null) {
                            current.setText(""); next.setText(""); confirm.setText("");
                            Toast.makeText(ChangePasswordActivity.this,
                                    response.body().getMessage(), Toast.LENGTH_LONG).show();
                            if (response.body().requiresReauthentication()) {
                                ProfileStore.clearAndSignIn(ChangePasswordActivity.this);
                            }
                            return;
                        }
                        String message = ApiErrorHandler.message(response);
                        Toast.makeText(ChangePasswordActivity.this, message,
                                Toast.LENGTH_LONG).show();
                        if (response.code() == 401 && isExpiredToken(message)) {
                            ProfileStore.clearAndSignIn(ChangePasswordActivity.this);
                        }
                    }
                    @Override public void onFailure(Call<ChangePasswordResponse> call, Throwable t) {
                        submit.setEnabled(true);
                        Toast.makeText(ChangePasswordActivity.this,
                                "Unable to change password", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean isExpiredToken(String message) {
        String value = message == null ? "" : message.toLowerCase();
        return value.contains("token") || value.contains("session")
                || value.contains("expired") || value.contains("unauthorized");
    }
    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
