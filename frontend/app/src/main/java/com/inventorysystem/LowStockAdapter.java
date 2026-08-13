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
import com.inventorysystem.Model.LowStockModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LowStockAdapter extends RecyclerView.Adapter<LowStockAdapter.ViewHolder> {

    private List<LowStockModel> fullList = new ArrayList<>();
    private List<LowStockModel> visibleList = new ArrayList<>();

    public void submitList(List<LowStockModel> items) {
        this.fullList = items != null ? items : new ArrayList<>();
        this.visibleList = new ArrayList<>(this.fullList);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        visibleList.clear();
        if (needle.isEmpty()) {
            visibleList.addAll(fullList);
        } else {
            for (LowStockModel item : fullList) {
                if (item.getItemName() != null && item.getItemName().toLowerCase(Locale.US).contains(needle)) {
                    visibleList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    @SuppressLint("ResourceType")
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.xml.item_low_stock, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LowStockModel item = visibleList.get(position);

        String imageUrl = RetrofitClient.resolveImageUrl(item.getImageUrl());
        Glide.with(holder.itemView.getContext())
                .load(AuthenticatedImageUrl.from(holder.itemView.getContext(), imageUrl))
                .placeholder(R.drawable.img_placeholder)
                .error(R.drawable.img_placeholder)
                .fallback(R.drawable.img_placeholder)
                .centerCrop()
                .into(holder.image);

        holder.itemName.setText(item.getItemName());
        holder.sku.setText(item.getSku());
        holder.badge.setText(item.getStatus());

        boolean critical = "critical".equalsIgnoreCase(item.getStatus());
        int badgeColor = critical
                ? holder.itemView.getResources().getColor(R.color.red)
                : holder.itemView.getResources().getColor(R.color.orange);
        holder.badge.setTextColor(badgeColor);

        String qtyLine = item.getCurrentQty() + " in stock (min " + item.getMinQty() + ")"
                + (item.getLocation() != null ? " \u2022 " + item.getLocation() : "");
        holder.qtyLine.setText(qtyLine);
    }

    @Override
    public int getItemCount() {
        return visibleList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView itemName, sku, badge, qtyLine;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.lsImage);
            itemName = itemView.findViewById(R.id.lsItemName);
            sku = itemView.findViewById(R.id.lsSku);
            badge = itemView.findViewById(R.id.lsBadge);
            qtyLine = itemView.findViewById(R.id.lsQtyLine);
        }
    }
}
