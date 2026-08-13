package com.inventorysystem;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.MessageResponse;
import com.inventorysystem.ConnectivityandService.ApiErrorHandler;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        NavigationUi.attachDrawer(this, findViewById(R.id.btnMenuProfile));

        TextView username = findViewById(R.id.txtProfileUsername);
        TextView role = findViewById(R.id.txtProfileRole);
        TextView email = findViewById(R.id.txtProfileEmail);
        SessionManager session = new SessionManager(this);
        username.setText(session.getUsername());
        role.setText(session.getRole());
        showEmail(email, session.getEmail());
        ProfileStore.loadOnce(this, profile -> {
            username.setText(profile.getUsername());
            role.setText(profile.getRole());
            showEmail(email, profile.getEmail());
        });

        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> logout(btnLogout));
        findViewById(R.id.btnActivityLogs).setOnClickListener(v ->
                startActivity(new Intent(this, ActivityLogsActivity.class)));
        MaterialButton create = findViewById(R.id.btnCreateAccount);
        create.setVisibility("Admin IT".equals(session.getRole()) ? View.VISIBLE : View.GONE);
        create.setOnClickListener(v ->
                startActivity(new Intent(this, CreateAccountActivity.class)));
        findViewById(R.id.btnChangePassword).setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));
        findViewById(R.id.btnAboutSystem).setOnClickListener(v ->
                startActivity(new Intent(this, AboutSystemActivity.class)));
    }

    private void showEmail(TextView view, String value) {
        boolean available = value != null && !value.trim().isEmpty();
        view.setVisibility(available ? View.VISIBLE : View.GONE);
        if (available) view.setText(value);
    }

    private void logout(MaterialButton button) {
        button.setEnabled(false);
        RetrofitClient.getApiService().logoutAuthenticated()
                .enqueue(new Callback<MessageResponse>() {
                    @Override
                    public void onResponse(Call<MessageResponse> call,
                                           Response<MessageResponse> response) {
                        button.setEnabled(true);
                        if (response.isSuccessful() || response.code() == 401) {
                            ProfileStore.clearAndSignIn(ProfileActivity.this);
                        } else {
                            Toast.makeText(ProfileActivity.this,
                                    ApiErrorHandler.message(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<MessageResponse> call, Throwable t) {
                        button.setEnabled(true);
                        new AlertDialog.Builder(ProfileActivity.this)
                                .setTitle("Unable to log out")
                                .setMessage("The server could not record your logout time.")
                                .setPositiveButton("Retry", (d, w) -> logout(button))
                                .setNegativeButton("Log out locally", (d, w) ->
                                        ProfileStore.clearAndSignIn(ProfileActivity.this))
                                .show();
                    }
                });
    }
}
