package com.inventorysystem;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.GenericResponse;
import com.inventorysystem.Model.ChangePasswordResponse;
import com.inventorysystem.Model.MessageResponse;
import com.inventorysystem.ConnectivityandService.ApiErrorHandler;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class NavigationUi {
    private NavigationUi() { }

    public static void attachDrawer(Activity activity, View menuButton) {
        if (!new SessionManager(activity).isLoggedIn()) {
            ProfileStore.clearAndSignIn(activity);
            return;
        }
        ViewGroup windowContent = activity.findViewById(android.R.id.content);
        if (windowContent.getChildCount() == 0
                || windowContent.getChildAt(0) instanceof DrawerLayout) return;

        View page = windowContent.getChildAt(0);
        windowContent.removeView(page);
        DrawerLayout drawer = new DrawerLayout(activity);
        drawer.setId(View.generateViewId());
        drawer.addView(page, new DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 20));
        panel.setBackgroundColor(Color.parseColor("#EEF3F8"));

        LinearLayout drawerContainer = new LinearLayout(activity);
        drawerContainer.setOrientation(LinearLayout.HORIZONTAL);
        drawerContainer.setBackgroundColor(Color.WHITE);
        drawerContainer.addView(panel, new LinearLayout.LayoutParams(
                dp(activity, 300), ViewGroup.LayoutParams.MATCH_PARENT));

        MaterialCardView identityCard = new MaterialCardView(activity);
        identityCard.setRadius(dp(activity, 14));
        identityCard.setCardElevation(dp(activity, 2));
        identityCard.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.ghostwhite));
        LinearLayout identity = new LinearLayout(activity);
        identity.setOrientation(LinearLayout.HORIZONTAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setPadding(dp(activity, 12), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));

        ImageView avatar = new ImageView(activity);
        avatar.setImageResource(R.drawable.ic_person);
        avatar.setPadding(dp(activity, 8), dp(activity, 8),
                dp(activity, 8), dp(activity, 8));
        identity.addView(avatar, new LinearLayout.LayoutParams(dp(activity, 56), dp(activity, 56)));

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        detailsParams.setMarginStart(dp(activity, 10));
        TextView username = new TextView(activity);
        username.setText(new SessionManager(activity).getUsername());
        username.setTextColor(ContextCompat.getColor(activity, R.color.dark_gray));
        username.setTextSize(16);
        username.setTypeface(username.getTypeface(), android.graphics.Typeface.BOLD);
        details.addView(username);

        TextView email = new TextView(activity);
        email.setText("—");
        email.setTextColor(ContextCompat.getColor(activity, R.color.gray_blue));
        email.setTextSize(12);
        email.setSingleLine(true);
        email.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(email);

        TextView roleBadge = new TextView(activity);
        roleBadge.setText(new SessionManager(activity).getRole());
        roleBadge.setTextSize(11);
        roleBadge.setPadding(dp(activity, 9), dp(activity, 3),
                dp(activity, 9), dp(activity, 3));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.topMargin = dp(activity, 5);
        details.addView(roleBadge, badgeParams);
        styleRoleBadge(activity, roleBadge, new SessionManager(activity).getRole());
        identity.addView(details, detailsParams);
        identityCard.addView(identity);
        panel.addView(identityCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        SessionManager session = new SessionManager(activity);
        ProfileStore.loadOnce(activity, profile -> {
            username.setText(profile.getUsername());
            email.setText(profile.getEmail() == null || profile.getEmail().trim().isEmpty()
                    ? "—" : profile.getEmail());
            roleBadge.setText(profile.getRole());
            styleRoleBadge(activity, roleBadge, profile.getRole());
        });

        FrameLayout detailPanel = new FrameLayout(activity);
        detailPanel.setBackgroundColor(Color.WHITE);
        detailPanel.setVisibility(View.GONE);
        drawerContainer.addView(detailPanel, new LinearLayout.LayoutParams(
                dp(activity, 300), ViewGroup.LayoutParams.MATCH_PARENT));

        addSettingCard(activity, panel, "Activity Logs",
                v -> activity.startActivity(new Intent(activity, ActivityLogsActivity.class)));

        String role = ProfileStore.getProfile() != null
                ? ProfileStore.getProfile().getRole() : session.getRole();
        if ("Admin IT".equals(role)) {
            addSettingCard(activity, panel, "Create Account",
                    v -> activity.startActivity(new Intent(activity, CreateAccountActivity.class)));
        }
        addSettingCard(activity, panel, "Change Password",
                v -> activity.startActivity(new Intent(activity, ChangePasswordActivity.class)));
        addSettingCard(activity, panel, "About the System",
                v -> activity.startActivity(new Intent(activity, AboutSystemActivity.class)));

        MaterialButton logout = new MaterialButton(activity);
        logout.setText(R.string.logout);
        logout.setAllCaps(false);
        logout.setLetterSpacing(0f);
        logout.setTextSize(14);
        logout.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams logoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48));
        logoutParams.topMargin = dp(activity, 8);
        logout.setOnClickListener(v -> {
            logout.setEnabled(false);
            RetrofitClient.getApiService().logoutAuthenticated()
                    .enqueue(new Callback<MessageResponse>() {
                        @Override
                        public void onResponse(Call<MessageResponse> call,
                                               Response<MessageResponse> response) {
                            logout.setEnabled(true);
                            if (response.isSuccessful() || response.code() == 401) {
                                ProfileStore.clearAndSignIn(activity);
                            } else {
                                android.widget.Toast.makeText(activity,
                                        ApiErrorHandler.message(response),
                                        android.widget.Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<MessageResponse> call, Throwable t) {
                            logout.setEnabled(true);
                            new AlertDialog.Builder(activity)
                                    .setTitle("Unable to log out")
                                    .setMessage("The server could not record your logout time.")
                                    .setPositiveButton("Retry", (dialog, which) ->
                                            logout.performClick())
                                    .setNegativeButton("Log out locally", (dialog, which) ->
                                            ProfileStore.clearAndSignIn(activity))
                                    .show();
                        }
                    });
        });
        panel.addView(logout, logoutParams);

        DrawerLayout.LayoutParams drawerParams = new DrawerLayout.LayoutParams(
                dp(activity, 300), ViewGroup.LayoutParams.MATCH_PARENT);
        drawerParams.gravity = GravityCompat.START;
        drawer.addView(drawerContainer, drawerParams);
        windowContent.addView(drawer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        menuButton.setOnClickListener(v -> {
            selectSettingCard(activity, panel, null);
            detailPanel.setVisibility(View.GONE);
            LinearLayout.LayoutParams menuParams =
                    (LinearLayout.LayoutParams) panel.getLayoutParams();
            menuParams.width = dp(activity, 300);
            panel.setLayoutParams(menuParams);
            DrawerLayout.LayoutParams params =
                    (DrawerLayout.LayoutParams) drawerContainer.getLayoutParams();
            params.width = dp(activity, 300);
            drawerContainer.setLayoutParams(params);
            drawer.openDrawer(GravityCompat.START);
        });
    }

    private static void showDetailExtension(Activity activity, DrawerLayout drawer,
                                            LinearLayout drawerContainer,
                                            FrameLayout detailPanel) {
        detailPanel.setVisibility(View.VISIBLE);
        DrawerLayout.LayoutParams params =
                (DrawerLayout.LayoutParams) drawerContainer.getLayoutParams();
        int expandedWidth = Math.min(activity.getResources().getDisplayMetrics().widthPixels,
                dp(activity, 600));
        params.width = expandedWidth;
        drawerContainer.setLayoutParams(params);
        int paneWidth = expandedWidth / 2;
        ((LinearLayout.LayoutParams) drawerContainer.getChildAt(0).getLayoutParams()).width =
                paneWidth;
        ((LinearLayout.LayoutParams) detailPanel.getLayoutParams()).width = paneWidth;
        drawerContainer.getChildAt(0).requestLayout();
        detailPanel.requestLayout();
        drawer.openDrawer(GravityCompat.START);
    }

    private static void populateChangePassword(Activity activity, FrameLayout detailPanel) {
        detailPanel.removeAllViews();
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity, 20), dp(activity, 48),
                dp(activity, 20), dp(activity, 20));

        TextView title = new TextView(activity);
        title.setText("Change Password");
        title.setTextColor(ContextCompat.getColor(activity, R.color.dark_navy));
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(activity, 20);
        form.addView(title, titleParams);

        EditText currentPassword = addPasswordField(activity, form, "Current Password",
                "Enter current password");
        EditText newPassword = addPasswordField(activity, form, "New Password",
                "Enter new password");
        EditText confirmPassword = addPasswordField(activity, form, "Confirm New Password",
                "Re-enter new password");

        View spacer = new View(activity);
        form.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        MaterialButton savePassword = new MaterialButton(activity);
        savePassword.setText("Save Password");
        savePassword.setTextColor(Color.WHITE);
        savePassword.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.dark_navy)));
        savePassword.setStrokeColor(ColorStateList.valueOf(Color.BLACK));
        savePassword.setStrokeWidth(dp(activity, 2));
        savePassword.setCornerRadius(dp(activity, 10));
        savePassword.setOnClickListener(v -> {
            String current = currentPassword.getText().toString();
            String next = newPassword.getText().toString();
            String confirmation = confirmPassword.getText().toString();
            currentPassword.setError(null);
            newPassword.setError(null);
            confirmPassword.setError(null);

            if (current.isEmpty() || next.isEmpty() || confirmation.isEmpty()) {
                if (current.isEmpty()) currentPassword.setError("Current password is required");
                if (next.isEmpty()) newPassword.setError("New password is required");
                if (confirmation.isEmpty()) {
                    confirmPassword.setError("Please confirm the new password");
                }
                return;
            }
            if (current.equals(next)) {
                newPassword.setError("New password cannot be the current password");
                newPassword.requestFocus();
                return;
            }
            if (next.length() < 8) {
                newPassword.setError("New password must be at least 8 characters");
                newPassword.requestFocus();
                return;
            }
            if (current.equals(confirmation)) {
                confirmPassword.setError("Confirmation cannot be the current password");
                confirmPassword.requestFocus();
                return;
            }
            if (!next.equals(confirmation)) {
                confirmPassword.setError("Passwords do not match");
                confirmPassword.requestFocus();
                return;
            }
            showPasswordConfirmation(activity, current, next, confirmation, savePassword);
        });
        form.addView(savePassword, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52)));

        detailPanel.addView(form, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static void showPasswordConfirmation(Activity activity, String currentPassword,
                                                 String newPassword, String confirmPassword,
                                                 MaterialButton submitButton) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Save Password")
                .setMessage("Are you sure you want to change your password?")
                .setNegativeButton("No", null)
                .setPositiveButton("Yes", (d, which) ->
                        submitPasswordChange(activity, currentPassword, newPassword,
                                confirmPassword, submitButton))
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.red)));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.green)));
        });
        dialog.show();
    }

    private static void submitPasswordChange(Activity activity, String currentPassword,
                                             String newPassword, String confirmPassword,
                                             MaterialButton submitButton) {
        submitButton.setEnabled(false);
        submitButton.setText("Saving...");
        SessionManager session = new SessionManager(activity);
        Map<String, String> request = new HashMap<>();
        request.put("current_password", currentPassword);
        request.put("new_password", newPassword);
        request.put("confirm_password", confirmPassword);
        RetrofitClient.getApiService().changePassword("Bearer " + session.getToken(), request)
                .enqueue(new Callback<ChangePasswordResponse>() {
                    @Override
                    public void onResponse(Call<ChangePasswordResponse> call,
                                           Response<ChangePasswordResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            android.widget.Toast.makeText(activity,
                                    response.body().getMessage(),
                                    android.widget.Toast.LENGTH_LONG).show();
                            if (response.body().requiresReauthentication()) {
                                ProfileStore.clearAndSignIn(activity);
                                return;
                            }
                            submitButton.setEnabled(true);
                            submitButton.setText("Save Password");
                        } else {
                            submitButton.setEnabled(true);
                            submitButton.setText("Save Password");
                            android.widget.Toast.makeText(activity,
                                    changePasswordError(response),
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ChangePasswordResponse> call, Throwable t) {
                        submitButton.setEnabled(true);
                        submitButton.setText("Save Password");
                        android.widget.Toast.makeText(activity,
                                "Unable to change password",
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                });
    }

    private static String changePasswordError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String message = new JSONObject(response.errorBody().string())
                        .optString("message");
                if (!message.isEmpty()) return message;
            }
        } catch (Exception ignored) { }
        return "Unable to change password";
    }

    private static EditText addPasswordField(Activity activity, LinearLayout form,
                                             String label, String hint) {
        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextColor(ContextCompat.getColor(activity, R.color.dark_gray));
        labelView.setTextSize(13);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(activity, 10);
        form.addView(labelView, labelParams);

        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextSize(14);
        input.setPadding(dp(activity, 12), dp(activity, 10),
                dp(activity, 12), dp(activity, 10));
        GradientDrawable box = new GradientDrawable();
        box.setColor(Color.WHITE);
        box.setStroke(dp(activity, 1),
                ContextCompat.getColor(activity, R.color.light_gray));
        box.setCornerRadius(dp(activity, 8));
        input.setBackground(box);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48));
        inputParams.topMargin = dp(activity, 6);
        form.addView(input, inputParams);
        return input;
    }

    private static void addLink(Activity activity, LinearLayout panel, String label,
                                Class<? extends Activity> target, String reportTab) {
        MaterialButton link = new MaterialButton(activity);
        link.setText(label);
        link.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        String selectedReportTab = activity.getIntent().getStringExtra("SELECT_REPORT_TAB");
        boolean active = activity.getClass().equals(target);
        if (target.equals(ReportsActivity.class)) {
            active = active && (reportTab == null
                    ? selectedReportTab == null : reportTab.equals(selectedReportTab));
        }
        link.setTextColor(ContextCompat.getColor(activity,
                active ? R.color.white : R.color.dark_navy));
        link.setBackgroundColor(ContextCompat.getColor(activity,
                active ? R.color.dark_navy : R.color.white));
        link.setOnClickListener(v -> {
            Intent intent = new Intent(activity, target);
            if (reportTab != null) intent.putExtra("SELECT_REPORT_TAB", reportTab);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        });
        panel.addView(link, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void addSettingCard(Activity activity, LinearLayout panel, String label,
                                       View.OnClickListener listener) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setRadius(dp(activity, 12));
        card.setCardElevation(dp(activity, 1));
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeColor(ContextCompat.getColor(activity, R.color.light_gray));
        card.setStrokeWidth(dp(activity, 1));
        card.setTag("setting_card");
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            selectSettingCard(activity, panel, card);
            listener.onClick(v);
        });

        TextView text = new TextView(activity);
        text.setText(label);
        text.setTextColor(ContextCompat.getColor(activity, R.color.dark_gray));
        text.setTextSize(14);
        text.setPadding(dp(activity, 16), dp(activity, 14),
                dp(activity, 16), dp(activity, 14));
        card.addView(text);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(activity, 8);
        panel.addView(card, params);
    }

    private static void selectSettingCard(Activity activity, LinearLayout panel,
                                          MaterialCardView selected) {
        for (int i = 0; i < panel.getChildCount(); i++) {
            View child = panel.getChildAt(i);
            if (!(child instanceof MaterialCardView)
                    || !"setting_card".equals(child.getTag())) continue;
            MaterialCardView card = (MaterialCardView) child;
            boolean active = card == selected;
            card.setCardBackgroundColor(ContextCompat.getColor(activity,
                    active ? R.color.dark_navy : R.color.white));
            card.setStrokeColor(ContextCompat.getColor(activity,
                    active ? R.color.dark_navy : R.color.light_gray));
            if (card.getChildCount() > 0 && card.getChildAt(0) instanceof TextView) {
                ((TextView) card.getChildAt(0)).setTextColor(ContextCompat.getColor(activity,
                        active ? R.color.white : R.color.dark_gray));
            }
        }
    }

    private static void styleRoleBadge(Activity activity, TextView badge, String role) {
        int background;
        int foreground;
        if ("IT".equals(role)) {
            background = Color.parseColor("#DBEAFE");
            foreground = Color.parseColor("#1D4ED8");
        } else if ("Admin IT".equals(role)) {
            background = Color.parseColor("#EDE9FE");
            foreground = Color.parseColor("#6D28D9");
        } else {
            background = Color.parseColor("#E5E7EB");
            foreground = Color.parseColor("#4B5563");
        }
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(activity, 20));
        badge.setBackground(shape);
        badge.setTextColor(foreground);
    }

    public static void selectBottomItem(View selected, View... allItems) {
        for (View item : allItems) {
            boolean active = item == selected;
            item.setSelected(active);
            item.setAlpha(active ? 1f : 0.48f);
            if (item instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) item;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setTextColor(ContextCompat.getColor(
                                child.getContext(), active ? R.color.dark_navy : R.color.gray));
                    } else {
                        child.setAlpha(active ? 1f : 0.55f);
                    }
                }
            }
        }
    }

    public static void selectChoice(View selected, View... choices) {
        for (View choice : choices) {
            boolean active = choice == selected;
            choice.setSelected(active);
            choice.setBackgroundResource(active ? R.drawable.bg_selected : R.drawable.bg_unselected);
            if (choice instanceof TextView) {
                ((TextView) choice).setTextColor(ContextCompat.getColor(choice.getContext(),
                        active ? R.color.white : R.color.gray));
            }
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
