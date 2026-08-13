package com.inventorysystem;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.inventorysystem.ConnectivityandService.ApiErrorHandler;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.ActivityLogModel;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivityLogsActivity extends AppCompatActivity {
    private ActivityLogAdapter adapter;
    private ProgressBar progress;
    private TextView state;
    private SwipeRefreshLayout refresh;

    @Override protected void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        setContentView(R.layout.activity_activity_logs);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        progress = findViewById(R.id.progress);
        state = findViewById(R.id.txtState);
        refresh = findViewById(R.id.swipeRefresh);
        RecyclerView list = findViewById(R.id.recyclerLogs);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ActivityLogAdapter();
        list.setAdapter(adapter);
        refresh.setOnRefreshListener(this::load);
        state.setOnClickListener(v -> load());
        load();
    }

    private void load() {
        progress.setVisibility(refresh.isRefreshing() ? View.GONE : View.VISIBLE);
        state.setVisibility(View.GONE);
        RetrofitClient.getApiService().getActivityLogs(50)
                .enqueue(new Callback<List<ActivityLogModel>>() {
                    @Override public void onResponse(Call<List<ActivityLogModel>> call,
                                                     Response<List<ActivityLogModel>> response) {
                        finishLoading();
                        if (response.isSuccessful() && response.body() != null) {
                            adapter.submitList(response.body());
                            state.setText("No activity logs yet");
                            state.setVisibility(response.body().isEmpty()
                                    ? View.VISIBLE : View.GONE);
                        } else {
                            showError(ApiErrorHandler.message(response));
                        }
                    }
                    @Override public void onFailure(Call<List<ActivityLogModel>> call, Throwable t) {
                        finishLoading();
                        showError("Unable to load activity logs. Tap to retry.");
                    }
                });
    }

    private void finishLoading() {
        progress.setVisibility(View.GONE);
        refresh.setRefreshing(false);
    }
    private void showError(String message) {
        state.setText(message);
        state.setVisibility(View.VISIBLE);
    }
}
