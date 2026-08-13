package com.inventorysystem;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.inventorysystem.ConnectivityandService.AuthenticatedImageUrl;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.DisposalRequestModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Adapter for a list of disposal requests.
 *
 * Pass showActions = false for the read-only "Disposal" tab in
 * ReportsActivity, or true for the actionable list in
 * DisposalApprovalActivity (shows Approve / Reject buttons).
 */
public class DisposalRequestAdapter extends RecyclerView.Adapter<DisposalRequestAdapter.ViewHolder> {

    public interface OnActionListener {
        void onApprove(DisposalRequestModel request);
        void onReject(DisposalRequestModel request);
        default void onFinalize(DisposalRequestModel request) { }
    }

    private List<DisposalRequestModel> items = new ArrayList<>();
    private final boolean showActions;
    private final OnActionListener listener;
    private final boolean canReview;
    private final boolean canFinalize;

    public DisposalRequestAdapter(boolean showActions, OnActionListener listener) {
        this(showActions, false, false, listener);
    }

    public DisposalRequestAdapter(boolean showActions, boolean canReview,
                                  boolean canFinalize, OnActionListener listener) {
        this.showActions = showActions;
        this.canReview = canReview;
        this.canFinalize = canFinalize;
        this.listener = listener;
    }

    public void submitList(List<DisposalRequestModel> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    /** Removes a single request from the list once it's been acted on (e.g. after approve/reject). */
    public void removeItem(DisposalRequestModel request) {
        int index = items.indexOf(request);
        if (index >= 0) {
            items.remove(index);
            notifyItemRemoved(index);
        }
    }

    @NonNull
    @Override
    @SuppressLint("ResourceType")
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.xml.item_disposal_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DisposalRequestModel item = items.get(position);

        holder.itemName.setText(item.getItemName());
        holder.itemCode.setText(item.getItemCode());
        holder.qty.setText("Qty: " + item.getQuantity());
        if ("Disposed".equals(item.getStatus()) && item.getApprovedDate() != null) {
            holder.date.setText("Approved: " + formatDisplayDate(item.getApprovedDate()));
        } else {
            holder.date.setText(item.getDateRequested() != null
                    ? "Added: " + formatDisplayDate(item.getDateRequested())
                    : "");
        }
        holder.reason.setText(item.getReason() != null ? item.getReason() : "");
        String rawStatus = item.getStatus();
        holder.status.setText(rawStatus == null || rawStatus.trim().isEmpty()
                ? "Pending Approval" : rawStatus);
        String imageUrl = RetrofitClient.resolveImageUrl(item.getImageUrl());
        Glide.with(holder.itemView.getContext())
                .load(AuthenticatedImageUrl.from(holder.itemView.getContext(), imageUrl))
                .placeholder(R.drawable.img_placeholder)
                .error(R.drawable.img_placeholder)
                .fallback(R.drawable.img_placeholder)
                .centerCrop()
                .into(holder.image);

        String status = item.getStatus() != null ? item.getStatus().toLowerCase(Locale.US) : "";
        int color;
        int background;
        switch (status) {
            case "for disposal":
                color = R.color.green;
                background = R.drawable.bg_status_approved;
                break;
            case "rejected":
                color = R.color.red;
                background = R.drawable.bg_status_rejected;
                break;
            case "disposed":
                color = R.color.red;
                background = R.drawable.bg_status_disposed;
                break;
            default:
                color = R.color.orange;
                background = R.drawable.bg_status_pending;
                break;
        }
        holder.status.setTextColor(holder.itemView.getResources().getColor(color));
        holder.status.setBackgroundResource(background);

        if (showActions && canReview && "pending approval".equals(status)) {
            holder.actionsRow.setVisibility(View.VISIBLE);
            holder.reject.setVisibility(View.VISIBLE);
            holder.reject.setEnabled(true);
            holder.approve.setEnabled(true);
            holder.approve.setText("Approve");
            holder.reject.setOnClickListener(v -> {
                if (listener != null) listener.onReject(item);
            });
            holder.approve.setOnClickListener(v -> {
                if (listener != null) listener.onApprove(item);
            });
        } else if (showActions && canFinalize && "for disposal".equals(status)) {
            holder.actionsRow.setVisibility(View.VISIBLE);
            holder.reject.setVisibility(View.GONE);
            holder.approve.setEnabled(true);
            holder.approve.setText("Finalize Disposal");
            holder.approve.setOnClickListener(v -> {
                if (listener != null) listener.onFinalize(item);
            });
        } else {
            holder.actionsRow.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatDisplayDate(String rawDate) {
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView itemName, itemCode, qty, date, reason, status;
        View actionsRow;
        MaterialButton reject, approve;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.drImage);
            itemName = itemView.findViewById(R.id.drItemName);
            itemCode = itemView.findViewById(R.id.drItemCode);
            qty = itemView.findViewById(R.id.drQty);
            date = itemView.findViewById(R.id.drDate);
            reason = itemView.findViewById(R.id.drReason);
            status = itemView.findViewById(R.id.drStatus);
            actionsRow = itemView.findViewById(R.id.drActionsRow);
            reject = itemView.findViewById(R.id.drReject);
            approve = itemView.findViewById(R.id.drApprove);
        }
    }
}
