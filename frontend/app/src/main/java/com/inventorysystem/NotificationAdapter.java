package com.inventorysystem;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.inventorysystem.ConnectivityandService.AuthenticatedImageUrl;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.NotificationModel;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter
        extends RecyclerView.Adapter<NotificationAdapter.Holder> {
    public interface OnNotificationClick {
        void onClick(NotificationModel notification);
    }

    private final List<NotificationModel> entries = new ArrayList<>();
    private final OnNotificationClick listener;

    public NotificationAdapter(OnNotificationClick listener) {
        this.listener = listener;
    }

    public void submitList(List<NotificationModel> notifications) {
        entries.clear();
        if (notifications != null) entries.addAll(notifications);
        notifyDataSetChanged();
    }

    public List<NotificationModel> getEntries() { return entries; }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NotificationModel item = entries.get(position);
        holder.message.setText(item.getMessage());
        holder.date.setText(formatDisplayDate(item.getCreatedAt()));
        String itemName = item.getItemName();
        boolean hasItem = item.getItemId() > 0
                || (itemName != null && !itemName.trim().isEmpty())
                || (item.getImageUrl() != null && !item.getImageUrl().trim().isEmpty());
        holder.itemName.setText(itemName);
        holder.itemName.setVisibility(itemName != null && !itemName.trim().isEmpty()
                ? View.VISIBLE : View.GONE);
        holder.image.setVisibility(hasItem ? View.VISIBLE : View.GONE);
        if (hasItem) {
            String imageUrl = RetrofitClient.resolveImageUrl(item.getImageUrl());
            Glide.with(holder.itemView.getContext())
                    .load(AuthenticatedImageUrl.from(holder.itemView.getContext(), imageUrl))
                    .placeholder(R.drawable.img_placeholder)
                    .error(R.drawable.img_placeholder)
                    .fallback(R.drawable.img_placeholder)
                    .centerCrop()
                    .into(holder.image);
        } else {
            Glide.with(holder.itemView.getContext()).clear(holder.image);
        }
        holder.message.setTypeface(null, item.isRead()
                ? Typeface.NORMAL : Typeface.BOLD);
        holder.itemView.setBackgroundColor(Color.parseColor(
                item.isRead() ? "#FFFFFF" : "#EEE8FF"));
        holder.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override public int getItemCount() { return entries.size(); }

    private String formatDisplayDate(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) return "";

        DateTimeFormatter output = DateTimeFormatter.ofPattern(
                "yyyy-MM-dd - h:mm a", Locale.US);
        try {
            Instant instant = Instant.parse(rawDate);
            return output.format(instant.atZone(ZoneId.systemDefault()));
        } catch (Exception ignored) {
            try {
                ZonedDateTime local = OffsetDateTime.parse(rawDate)
                        .atZoneSameInstant(ZoneId.systemDefault());
                return output.format(local);
            } catch (Exception ignoredAgain) {
                return rawDate;
            }
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView itemName;
        final TextView message;
        final TextView date;
        Holder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imgNotificationItem);
            itemName = itemView.findViewById(R.id.txtNotificationItemName);
            message = itemView.findViewById(R.id.txtNotificationMessage);
            date = itemView.findViewById(R.id.txtNotificationDate);
        }
    }
}
