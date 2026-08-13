package com.inventorysystem;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.inventorysystem.Model.CategoryBreakdownModel;

import java.util.ArrayList;
import java.util.List;

public class CategoryBreakdownAdapter extends RecyclerView.Adapter<CategoryBreakdownAdapter.ViewHolder> {

    private List<CategoryBreakdownModel> items = new ArrayList<>();

    public void submitList(List<CategoryBreakdownModel> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        int grandTotal = 0;
        for (CategoryBreakdownModel item : this.items) {
            grandTotal += item.getTotalQuantity();
        }
        for (CategoryBreakdownModel item : this.items) {
            int percentage = grandTotal > 0
                    ? Math.round(item.getTotalQuantity() * 100f / grandTotal)
                    : 0;
            item.setPercentage(percentage);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    @SuppressLint("ResourceType")
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.xml.item_category_breakdown, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryBreakdownModel item = items.get(position);

        holder.name.setText(item.getCategoryName());
        holder.percentage.setText(item.getPercentage() + "%");
        holder.itemCount.setText(item.getTotalQuantity() + " units \u2022 "
                + item.getItemCount() + " item types");

        // Set the fill width as a percentage of the track's own width, once
        // the track has been laid out (post() ensures getWidth() is valid).
        holder.progressTrack.post(() -> {
            int trackWidth = holder.progressTrack.getWidth();
            int fillWidth = Math.round(trackWidth * (item.getPercentage() / 100f));
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) holder.progressFill.getLayoutParams();
            params.width = fillWidth;
            holder.progressFill.setLayoutParams(params);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, percentage, itemCount;
        FrameLayout progressTrack;
        View progressFill;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.catName);
            percentage = itemView.findViewById(R.id.catPercentage);
            itemCount = itemView.findViewById(R.id.catItemCount);
            progressFill = itemView.findViewById(R.id.catProgressFill);
            progressTrack = (FrameLayout) progressFill.getParent();
        }
    }
}
