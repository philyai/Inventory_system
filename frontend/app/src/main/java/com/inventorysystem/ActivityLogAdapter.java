package com.inventorysystem;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.inventorysystem.Model.ActivityLogModel;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ActivityLogAdapter extends RecyclerView.Adapter<ActivityLogAdapter.Holder> {
    private final List<ActivityLogModel> entries = new ArrayList<>();
    private final DateTimeFormatter formatter = DateTimeFormatter
            .ofPattern("MMM d, yyyy, h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault());

    public void submitList(List<ActivityLogModel> logs) {
        entries.clear();
        if (logs != null) entries.addAll(logs);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity_log, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ActivityLogModel log = entries.get(position);
        holder.device.setText(value(log.getDeviceInfo(), "Unknown device"));
        holder.login.setText("Login: " + formatDate(log.getLoginTime()));
        holder.logout.setText("Logout: " + formatDate(log.getLogoutTime()));
        holder.status.setText("Status: " + formatStatus(log.getStatus()));
        String ip = log.getIpAddress();
        holder.ip.setVisibility(ip == null || ip.trim().isEmpty() ? View.GONE : View.VISIBLE);
        holder.ip.setText("IP address: " + ip);
    }

    @Override public int getItemCount() { return entries.size(); }

    private String formatDate(String value) {
        if (value == null || value.trim().isEmpty()) return "—";
        try { return formatter.format(Instant.parse(value)); }
        catch (Exception ignored) { return value; }
    }

    private String formatStatus(String status) {
        if ("active".equalsIgnoreCase(status)) return "Active";
        if ("logged_out".equalsIgnoreCase(status)) return "Logged out";
        if ("replaced".equalsIgnoreCase(status)) return "Ended by another login";
        return value(status, "Unknown");
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView device, login, logout, status, ip;
        Holder(View view) {
            super(view);
            device = view.findViewById(R.id.txtLogDevice);
            login = view.findViewById(R.id.txtLogLogin);
            logout = view.findViewById(R.id.txtLogLogout);
            status = view.findViewById(R.id.txtLogStatus);
            ip = view.findViewById(R.id.txtLogIp);
        }
    }
}
