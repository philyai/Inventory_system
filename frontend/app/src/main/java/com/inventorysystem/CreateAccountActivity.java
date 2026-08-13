package com.inventorysystem;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.inventorysystem.ConnectivityandService.ApiErrorHandler;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.CreateAccountRequest;
import com.inventorysystem.Model.CreateAccountResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreateAccountActivity extends AppCompatActivity {
    private TextInputLayout usernameLayout, emailLayout, passwordLayout, confirmLayout;
    private TextInputEditText username, email, password, confirm;
    private AutoCompleteTextView role;
    private MaterialButton submit;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!"Admin IT".equals(new SessionManager(this).getRole())) {
            Toast.makeText(this, "Only Admin IT can create accounts", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_create_account);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        usernameLayout = findViewById(R.id.layoutUsername);
        emailLayout = findViewById(R.id.layoutEmail);
        passwordLayout = findViewById(R.id.layoutPassword);
        confirmLayout = findViewById(R.id.layoutConfirmPassword);
        username = findViewById(R.id.inputUsername);
        email = findViewById(R.id.inputEmail);
        password = findViewById(R.id.inputPassword);
        confirm = findViewById(R.id.inputConfirmPassword);
        role = findViewById(R.id.inputRole);
        submit = findViewById(R.id.btnSubmit);
        String[] roles = {"Admin IT", "IT", "Purchasing"};
        role.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, roles));
        role.setText("IT", false);
        submit.setOnClickListener(v -> create());
    }

    private void create() {
        clearErrors();
        String name = text(username).trim();
        String mail = text(email).trim();
        String secret = text(password);
        String confirmation = text(confirm);
        if (name.isEmpty()) { usernameLayout.setError("Username is required"); return; }
        if (mail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
            emailLayout.setError("Enter a valid email"); return;
        }
        if (secret.length() < 8) {
            passwordLayout.setError("Password must be at least 8 characters"); return;
        }
        if (!secret.equals(confirmation)) {
            confirmLayout.setError("Passwords do not match"); return;
        }
        submit.setEnabled(false);
        RetrofitClient.getApiService().createAccount(
                new CreateAccountRequest(name, mail, secret, role.getText().toString()))
                .enqueue(new Callback<CreateAccountResponse>() {
                    @Override public void onResponse(Call<CreateAccountResponse> call,
                                                     Response<CreateAccountResponse> response) {
                        submit.setEnabled(true);
                        if (response.code() == 201 || response.isSuccessful()) {
                            Toast.makeText(CreateAccountActivity.this,
                                    "Account created successfully", Toast.LENGTH_LONG).show();
                            username.setText(""); email.setText(""); password.setText("");
                            confirm.setText(""); role.setText("IT", false);
                        } else {
                            Toast.makeText(CreateAccountActivity.this,
                                    ApiErrorHandler.message(response), Toast.LENGTH_LONG).show();
                        }
                    }
                    @Override public void onFailure(Call<CreateAccountResponse> call, Throwable t) {
                        submit.setEnabled(true);
                        Toast.makeText(CreateAccountActivity.this,
                                "Unable to create account", Toast.LENGTH_LONG).show();
                    }
                });
    }
    private void clearErrors() {
        usernameLayout.setError(null); emailLayout.setError(null);
        passwordLayout.setError(null); confirmLayout.setError(null);
    }
    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
