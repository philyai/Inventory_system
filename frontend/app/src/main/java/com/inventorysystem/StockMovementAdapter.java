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
import com.inventorysystem.ConnectivityandService.AuthenticatedImageUrl;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.StockMovementModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for the Stock Movement report tab.
 *
 * Call {@link #submitList(List)} with the FULL list fetched from the
 * backend, and {@link #setTypeFilter(String)} to switch between
 * All / In / Out / Adjustment (null = All). The adapter re-groups the
 * filtered rows under "Today" / "Yesterday" / "Earlier" headers.
 */
public class StockMovementAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    private List<StockMovementModel> fullList = new ArrayList<>();
    private final List<Object> visibleItems = new ArrayList<>(); // String header or StockMovementModel
    private String typeFilter = null; // null = All, else "in"/"out"/"adjust"

    public void submitList(List<StockMovementModel> movements) {
        this.fullList = movements != null ? movements : new ArrayList<>();
        rebuildVisibleItems();
    }

    public void setTypeFilter(String typeFilter) {
        this.typeFilter = typeFilter;
        rebuildVisibleItems();
    }

    private void rebuildVisibleItems() {
        visibleItems.clear();

        String currentHeader = null;
        for (StockMovementModel m : fullList) {
            if (typeFilter != null && !typeFilter.equalsIgnoreCase(m.getType())) {
                continue;
            }
            String header = groupLabelFor(m.getTimestamp());
            if (!header.equals(currentHeader)) {
                visibleItems.add(header);
                currentHeader = header;
            }
            visibleItems.add(m);
        }
        notifyDataSetChanged();
    }

    private String groupLabelFor(String isoTimestamp) {
        if (isoTimestamp == null) return "Earlier";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            Date date = sdf.parse(isoTimestamp.replace("Z", ""));
            Calendar rowCal = Calendar.getInstance();
            rowCal.setTime(date != null ? date : new Date());

            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);

            if (isSameDay(rowCal, today)) return "Today";
            if (isSameDay(rowCal, yesterday)) return "Yesterday";

            SimpleDateFormat display = new SimpleDateFormat("MMM d, yyyy", Locale.US);
            return display.format(rowCal.getTime());
        } catch (ParseException e) {
            return "Earlier";
        }
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    public int getItemViewType(int position) {
        return visibleItems.get(position) instanceof String ? TYPE_HEADER : TYPE_ROW;
    }

    @NonNull
    @Override
    @SuppressLint("ResourceType")
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.xml.item_stock_movement_header, parent, false);
            return new HeaderViewHolder(view);
        }
        View view = inflater.inflate(R.xml.item_stock_movement, parent, false);
        return new RowViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = visibleItems.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).label.setText((String) item);
        } else if (holder instanceof RowViewHolder) {
            bindRow((RowViewHolder) holder, (StockMovementModel) item);
        }
    }

    private void bindRow(RowViewHolder holder, StockMovementModel m) {
        String imageUrl = RetrofitClient.resolveImageUrl(m.getImageUrl());
        Glide.with(holder.itemView.getContext())
                .load(AuthenticatedImageUrl.from(holder.itemView.getContext(), imageUrl))
                .placeholder(R.drawable.img_placeholder)
                .error(R.drawable.img_placeholder)
                .fallback(R.drawable.img_placeholder)
                .centerCrop()
                .into(holder.image);

        String type = m.getType() != null ? m.getType() : "";
        String arrow;
        int color;
        int sign = 1;
        switch (type.toLowerCase(Locale.US)) {
            case "in":
                arrow = "\u2191"; // ↑
                color = R.color.green;
                break;
            case "out":
                arrow = "\u2193"; // ↓
                color = R.color.red;
                sign = -1;
                break;
            default:
                arrow = "\u21bb"; // ↻
                color = R.color.orange;
                break;
        }
        holder.icon.setText(arrow);
        holder.itemName.setText(m.getItemName());
        holder.quantity.setText((sign > 0 ? "+" : "-") + Math.abs(m.getQuantity()));
        holder.quantity.setTextColor(holder.itemView.getResources().getColor(color));
        StringBuilder details = new StringBuilder();
        if (m.getItemCode() != null && !m.getItemCode().isEmpty()) {
            details.append(m.getItemCode());
        }
        if (m.getNote() != null && !m.getNote().isEmpty()) {
            if (details.length() > 0) details.append(" \u2022 ");
            details.append(m.getNote());
        }
        if (m.getTimestamp() != null && !m.getTimestamp().isEmpty()) {
            if (details.length() > 0) details.append(" \u2022 ");
            details.append(formatTimestamp(m.getTimestamp()));
        }
        holder.note.setText(details.toString());
    }

    private String formatTimestamp(String timestamp) {
        String normalized = timestamp.trim().replace('T', ' ');
        if (normalized.length() < 16) return timestamp;

        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            parser.setLenient(false);
            Date date = parser.parse(normalized.substring(0, 16));
            if (date == null) return timestamp;

            SimpleDateFormat display = new SimpleDateFormat("yyyy-MM-dd - h:mm a", Locale.US);
            return display.format(date);
        } catch (ParseException e) {
            return timestamp;
        }
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView label;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.mvHeaderLabel);
        }
    }

    static class RowViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView icon, itemName, quantity, note;

        RowViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.mvImage);
            icon = itemView.findViewById(R.id.mvIcon);
            itemName = itemView.findViewById(R.id.mvItemName);
            quantity = itemView.findViewById(R.id.mvQuantity);
            note = itemView.findViewById(R.id.mvNote);
        }
    }
}
